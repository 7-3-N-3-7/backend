package com.platform.features.finance;

import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.finance.FinanceFeature.FinanceController;
import com.platform.features.finance.FinanceFeature.FinanceGitService;
import com.platform.features.finance.FinanceFeature.FinanceService;
import com.platform.features.finance.FinanceFeature.FinancialVoucherDto;
import com.platform.features.finance.FinanceFeature.FineractClient;
import com.platform.features.finance.FinanceFeature.InvoiceDto;
import com.platform.features.finance.FinanceFeature.KillBillClient;
import com.platform.features.finance.FinanceFeature.LedgerSummaryDto;
import com.platform.features.finance.FinanceFeature.PaymentDto;
import com.platform.features.finance.FinanceFeature.RefundDto;
import com.platform.features.finance.FinanceFeature.VippsAgreementDto;
import com.platform.features.finance.FinanceFeature.VippsMobilePayOsgiPlugin;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class FinanceServiceTest {

    @TempDir
    Path tempDir;

    private FinanceGitService financeGitService;
    private KillBillClient killBillClient;
    private FineractClient fineractClient;
    private VippsMobilePayOsgiPlugin vippsPlugin;
    private FinanceService financeService;
    private FinanceController financeController;
    private HttpServer mockServer;
    private final AtomicInteger mockStatusCode = new AtomicInteger(200);

    private final UUID clientA = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private final UUID clientB = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private final UUID therapistUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() throws Exception {
        financeGitService = new FinanceGitService();
        ReflectionTestUtils.setField(financeGitService, "repoPath", tempDir.resolve("finance-git").toString());
        financeGitService.init();

        killBillClient = new KillBillClient();
        ReflectionTestUtils.setField(killBillClient, "apiKey", "test-key");
        ReflectionTestUtils.setField(killBillClient, "apiSecret", "test-secret");
        ReflectionTestUtils.setField(killBillClient, "username", "admin");
        ReflectionTestUtils.setField(killBillClient, "password", "password");
        killBillClient.setRemoteConfiguration("http://127.0.0.1:18090", false);

        fineractClient = new FineractClient();
        ReflectionTestUtils.setField(fineractClient, "tenantId", "default");
        ReflectionTestUtils.setField(fineractClient, "username", "mifos");
        ReflectionTestUtils.setField(fineractClient, "password", "password");
        fineractClient.setRemoteConfiguration("http://127.0.0.1:18090/fineract-provider/api/v1", false);
        fineractClient.initChartOfAccounts();

        vippsPlugin = new VippsMobilePayOsgiPlugin();
        vippsPlugin.setRemoteConfiguration("http://127.0.0.1:18090", false);

        financeService = new FinanceService(killBillClient, fineractClient, financeGitService, vippsPlugin);
        financeController = new FinanceController(financeService);

        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.createContext("/", exchange -> {
            byte[] resp = "{\"status\":\"OK\"}".getBytes();
            exchange.sendResponseHeaders(mockStatusCode.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
        financeGitService.close();
        ReflectionTestUtils.setField(financeGitService, "git", null);
        financeGitService.close();
    }

    private Authentication createJwtAuth(UUID userUuid, String fullName, String role) {
        Map<String, Object> claims = new LinkedHashMap<>();
        if (userUuid != null) {
            claims.put("sub", userUuid.toString());
        }
        if (fullName != null) {
            claims.put("name", fullName);
        } else {
            claims.put("preferred_username", "fallback-user");
        }
        Jwt jwt = new Jwt(
                "token-val",
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                claims
        );
        return new UsernamePasswordAuthenticationToken(
                jwt,
                "token-val",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    void testEndToEndControllerFlowsAndRemoteHttpAdapters() {
        int port = mockServer.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;
        killBillClient.setRemoteConfiguration(baseUrl, true);
        fineractClient.setRemoteConfiguration(baseUrl + "/fineract-provider/api/v1", true);

        Authentication therapistAuth = createJwtAuth(therapistUuid, "Dr. Maja Lind", "therapist");
        Authentication adminAuth = createJwtAuth(therapistUuid, "   ", "admin");
        Authentication clientAAuth = createJwtAuth(clientA, null, "client");
        Authentication clientBAuth = createJwtAuth(clientB, "Client B", "client");

        // 1. Bill unpaid session (payImmediately = false) and refund unpaid invoice (credits 1200)
        Map<String, Object> unpaidPayload = new LinkedHashMap<>();
        unpaidPayload.put("clientUuid", clientA.toString());
        unpaidPayload.put("amount", "2.00");
        unpaidPayload.put("payImmediately", false);
        unpaidPayload.put("description", "   ");
        ResponseEntity<Map<String, Object>> unpaidResp = financeController.billSingleSession(unpaidPayload, adminAuth);
        assertEquals(HttpStatus.CREATED, unpaidResp.getStatusCode());
        String unpaidInvoiceId = (String) unpaidResp.getBody().get("invoiceId");
        assertEquals("COMMITTED", unpaidResp.getBody().get("status"));
        assertNull(unpaidResp.getBody().get("settlementJournalEntryId"));

        // Second session for same client reuses existing Kill Bill account
        Map<String, Object> paidPayload = new LinkedHashMap<>();
        paidPayload.put("clientUuid", clientA.toString());
        paidPayload.put("amount", "2.00");
        paidPayload.put("payImmediately", true);
        paidPayload.put("clinicalCommitHash", "   ");
        ResponseEntity<Map<String, Object>> paidResp = financeController.billSingleSession(paidPayload, clientAAuth);

        // Also test default payImmediately (when key is omitted)
        Map<String, Object> defaultPayPayload = new LinkedHashMap<>();
        defaultPayPayload.put("clientUuid", clientA.toString());
        defaultPayPayload.put("amount", "2.00");
        financeController.billSingleSession(defaultPayPayload, clientAAuth);
        assertEquals(HttpStatus.CREATED, paidResp.getStatusCode());
        String paidInvoiceId = (String) paidResp.getBody().get("invoiceId");
        String commitHash = (String) paidResp.getBody().get("gitCommitHash");
        assertNotNull(paidResp.getBody().get("settlementJournalEntryId"));

        // Refund unpaid invoice with custom amount
        ResponseEntity<Map<String, Object>> refundUnpaidResp = financeController.refundInvoice(
                unpaidInvoiceId,
                Map.of("amount", "2.00", "reason", "Kreditnota ubetalt"),
                therapistAuth
        );
        assertEquals(HttpStatus.OK, refundUnpaidResp.getStatusCode());
        assertEquals("REFUNDED", refundUnpaidResp.getBody().get("status"));

        // Refund paid invoice with default full amount
        ResponseEntity<Map<String, Object>> refundPaidResp = financeController.refundInvoice(
                paidInvoiceId,
                Map.of(),
                therapistAuth
        );
        assertEquals(HttpStatus.OK, refundPaidResp.getStatusCode());

        // 2. Prepaid Klippekort top-up & full clip consumption
        ResponseEntity<Map<String, Object>> topupResp = financeController.topUpPrepaidCard(
                Map.of("clientUuid", clientA.toString(), "amount", "3.00", "clips", 2),
                clientAAuth
        );
        assertEquals(HttpStatus.CREATED, topupResp.getStatusCode());

        // Consume 1 clip, then consume remaining 1 clip
        ResponseEntity<Map<String, Object>> consume1 = financeController.consumePrepaidClip(
                Map.of("clientUuid", clientA.toString(), "clips", 1, "appointmentUuid", "apt-1"),
                therapistAuth
        );
        assertEquals(HttpStatus.OK, consume1.getStatusCode());
        assertEquals(1, consume1.getBody().get("remainingClips"));

        ResponseEntity<Map<String, Object>> consume2 = financeController.consumePrepaidClip(
                Map.of("clientUuid", clientA.toString(), "clips", 1, "appointmentUuid", "apt-2"),
                therapistAuth
        );
        assertEquals(HttpStatus.OK, consume2.getStatusCode());
        assertEquals(0, consume2.getBody().get("remainingClips"));

        // Consuming when 0 clips left throws 400 BAD_REQUEST
        DomainException clipEx = assertThrows(DomainException.class, () ->
                financeController.consumePrepaidClip(
                        Map.of("clientUuid", clientA.toString(), "clips", 1),
                        therapistAuth
                )
        );
        assertEquals(400, clipEx.getStatus());

        // 3. Subscription & Installment Plan (repay custom amount + final installment closing loan)
        ResponseEntity<Map<String, Object>> subResp = financeController.createSubscription(
                Map.of("clientUuid", clientA.toString(), "planName", "monthly-care", "monthlyAmount", "3.00"),
                clientAAuth
        );
        assertEquals(HttpStatus.CREATED, subResp.getStatusCode());

        ResponseEntity<Map<String, Object>> loanResp = financeController.createInstallmentPlan(
                Map.of("clientUuid", clientA.toString(), "principalAmount", "6.00", "installmentsCount", 2),
                therapistAuth
        );
        assertEquals(HttpStatus.CREATED, loanResp.getStatusCode());
        String loanId = (String) loanResp.getBody().get("loanId");

        // First repayment with default installment amount (3.00)
        ResponseEntity<Map<String, Object>> repay1 = financeController.repayInstallment(
                loanId,
                Map.of(),
                clientAAuth
        );
        assertEquals(HttpStatus.OK, repay1.getStatusCode());
        assertEquals("ACTIVE", repay1.getBody().get("status"));

        // Second repayment with explicit amount (3.00) closes loan
        ResponseEntity<Map<String, Object>> repay2 = financeController.repayInstallment(
                loanId,
                Map.of("amount", "3.00"),
                therapistAuth
        );
        assertEquals(HttpStatus.OK, repay2.getStatusCode());
        assertEquals("CLOSED_OBLIGATIONS_MET", repay2.getBody().get("status"));

        // 4. Overview, GL Summary, and Commit Snapshot RBAC
        ResponseEntity<Map<String, Object>> overviewResp = financeController.getClientFinanceOverview(clientA, clientAAuth);
        assertEquals(HttpStatus.OK, overviewResp.getStatusCode());

        ResponseEntity<Map<String, Object>> glSummaryResp = financeController.getGeneralLedgerSummary(therapistAuth);
        assertEquals(HttpStatus.OK, glSummaryResp.getStatusCode());

        ResponseEntity<Map<String, Object>> snapByTherapist = financeController.getCommitSnapshot(commitHash, therapistAuth);
        assertEquals(HttpStatus.OK, snapByTherapist.getStatusCode());

        ResponseEntity<Map<String, Object>> snapByOwnerClient = financeController.getCommitSnapshot(commitHash, clientAAuth);
        assertEquals(HttpStatus.OK, snapByOwnerClient.getStatusCode());

        DomainException snapForbidden = assertThrows(DomainException.class, () ->
                financeController.getCommitSnapshot(commitHash, clientBAuth)
        );
        assertEquals(403, snapForbidden.getStatus());
    }

    @Test
    void testValidationErrorsAndEdgeCases() throws Exception {
        Authentication therapistAuth = createJwtAuth(therapistUuid, "Dr. Maja Lind", "therapist");
        Authentication clientAAuth = createJwtAuth(clientA, "Client A", "client");

        // Empty history when client has no commits yet
        financeService.resetAll();
        killBillClient.ensureAccount(clientA, "DKK", "local-test");
        Map<String, Object> localInv = killBillClient.createInvoiceAndCharge(
                clientA, "Local", BigDecimal.TEN, "DKK", false, null, "local-test"
        );
        killBillClient.refundInvoice((String) localInv.get("invoiceId"), null, "null-amount-refund", "local-test");
        Map<String, Object> localLoan = fineractClient.createInstallmentLoan(
                clientA, "Local Loan", BigDecimal.valueOf(200), 2, "DKK"
        );
        fineractClient.repayInstallmentLoan((String) localLoan.get("loanId"), null, "INV-LOCAL");
        financeService.resetAll();

        assertTrue(financeGitService.getClientFinanceHistory(UUID.randomUUID()).isEmpty());
        assertTrue(killBillClient.getClientInvoices(UUID.randomUUID()).isEmpty());
        assertTrue(killBillClient.getClientSubscriptions(UUID.randomUUID()).isEmpty());
        assertNull(killBillClient.getInvoice("NON-EXISTENT"));
        assertNull(fineractClient.getLoan("NON-EXISTENT"));
        assertTrue(fineractClient.getClientLoans(UUID.randomUUID()).isEmpty());

        // Re-initializing FinanceGitService when .git already exists
        financeGitService.init();

        // Snapshot lookup for unknown or invalid ref
        assertNull(financeGitService.getCommitSnapshot("deadbeef"));
        assertNull(financeGitService.getCommitSnapshot("invalid..ref..syntax"));

        // TreeWalk path not found in commit returns empty string + blank referenceHash branch
        financeGitService.commitFinancialVoucher(
                clientA, "TEST_BLANK_REF", "Summary", "- test", "Author", "   "
        );
        String hash = financeGitService.commitFinancialVoucher(
                clientA, "TEST", "Summary", "- test", "Author", "ref123"
        );
        Method readBlob = FinanceGitService.class.getDeclaredMethod(
                "readBlobAtCommit",
                org.eclipse.jgit.lib.Repository.class,
                org.eclipse.jgit.revwalk.RevCommit.class,
                String.class
        );
        readBlob.setAccessible(true);
        org.eclipse.jgit.lib.Repository repo = ((org.eclipse.jgit.api.Git) ReflectionTestUtils.getField(financeGitService, "git")).getRepository();
        org.eclipse.jgit.revwalk.RevCommit revCommit = repo.parseCommit(repo.resolve(hash));
        assertEquals("", readBlob.invoke(financeGitService, repo, revCommit, "nonexistent/path.md"));

        // Direct client exceptions
        assertThrows(IllegalArgumentException.class, () ->
                killBillClient.refundInvoice("MISSING", BigDecimal.TEN, "reason", "tester")
        );
        assertThrows(IllegalArgumentException.class, () ->
                fineractClient.postJournalEntry("9999", "4100", BigDecimal.TEN, "DKK", "ref", "msg")
        );
        assertThrows(IllegalArgumentException.class, () ->
                fineractClient.postJournalEntry("1100", "9999", BigDecimal.TEN, "DKK", "ref", "msg")
        );
        assertThrows(IllegalArgumentException.class, () ->
                fineractClient.repayInstallmentLoan("MISSING-LOAN", BigDecimal.TEN, "INV-1")
        );

        // Deposit 0 clips with 0 balance edge case & withdrawal with >0 clips but 0 balance
        fineractClient.depositToSavings(clientB, BigDecimal.ZERO, 0, "DKK", "INV-0");
        Map<String, Object> savB = fineractClient.getOrCreateSavingsAccount(clientB, "DKK");
        savB.put("remainingClips", 2);
        savB.put("balance", BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () ->
                fineractClient.withdrawFromSavings(clientB, 1, "apt-zero")
        );

        // FinanceService validation errors (400 / 401 / 403 / 404)
        assertThrows(DomainException.class, () ->
                financeService.billSingleSession(Map.of(), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.billSingleSession(Map.of("clientUuid", "   "), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.billSingleSession(Map.of("clientUuid", "not-a-uuid"), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.billSingleSession(Map.of("clientUuid", clientA.toString()), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.billSingleSession(Map.of("clientUuid", clientA.toString(), "amount", "0"), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.refundInvoice("UNKNOWN-INV", Map.of(), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.repayInstallment("UNKNOWN-LOAN", Map.of(), therapistAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.getCommitSnapshot("deadbeef", therapistAuth)
        );

        // Auth edge cases (null auth, non-Jwt principal, null subject, invalid UUID subject, forbidden role)
        assertThrows(DomainException.class, () ->
                financeService.getGeneralLedgerSummary(null)
        );
        Authentication stringPrincipalAuth = new UsernamePasswordAuthenticationToken("plain-string", "pw", List.of());
        assertThrows(DomainException.class, () ->
                financeService.getGeneralLedgerSummary(stringPrincipalAuth)
        );
        Authentication nullSubAuth = createJwtAuth(null, "No Sub", "therapist");
        assertThrows(DomainException.class, () ->
                financeService.getGeneralLedgerSummary(nullSubAuth)
        );
        Jwt badUuidJwt = new Jwt(
                "tok",
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "not-a-valid-uuid")
        );
        Authentication badUuidAuth = new UsernamePasswordAuthenticationToken(badUuidJwt, "tok", List.of());
        assertThrows(DomainException.class, () ->
                financeService.getGeneralLedgerSummary(badUuidAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.getGeneralLedgerSummary(clientAAuth)
        );
        assertThrows(DomainException.class, () ->
                financeService.getClientFinanceOverview(clientB, clientAAuth)
        );

        // Remote HTTP 500 error & network error handling in KillBillClient and FineractClient
        int port = mockServer.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;
        mockStatusCode.set(500);
        killBillClient.setRemoteConfiguration(baseUrl, true);
        fineractClient.setRemoteConfiguration(baseUrl, true);

        assertThrows(IllegalStateException.class, () ->
                killBillClient.ensureAccount(UUID.randomUUID(), "DKK", "tester")
        );
        assertThrows(IllegalStateException.class, () ->
                fineractClient.postJournalEntry("1100", "4100", BigDecimal.TEN, "DKK", "ref", "err")
        );

        // Unreachable port triggers catch (Exception e)
        killBillClient.setRemoteConfiguration("http://127.0.0.1:1", true);
        fineractClient.setRemoteConfiguration("http://127.0.0.1:1", true);
        assertThrows(IllegalStateException.class, () ->
                killBillClient.ensureAccount(UUID.randomUUID(), "DKK", "tester")
        );
        assertThrows(IllegalStateException.class, () ->
                fineractClient.postJournalEntry("1100", "4100", BigDecimal.TEN, "DKK", "ref", "err")
        );

        // FinanceGitService error branches when repoPath is a regular file instead of directory
        Path regularFile = tempDir.resolve("not-a-dir.txt");
        Files.writeString(regularFile, "file");
        FinanceGitService brokenGitService = new FinanceGitService();
        ReflectionTestUtils.setField(brokenGitService, "repoPath", regularFile.toString());
        assertThrows(IllegalStateException.class, brokenGitService::init);
        assertThrows(IllegalStateException.class, () ->
                brokenGitService.commitFinancialVoucher(clientA, "ERR", "msg", "md", "author", null)
        );

        // Force readFileCommitHistory exception by setting null git on existing file
        ReflectionTestUtils.setField(financeGitService, "git", null);
        assertThrows(IllegalStateException.class, () ->
                financeGitService.getClientFinanceHistory(clientA)
        );

        // Exercise all Java Record DTOs in FinanceFeature
        UUID id1 = UUID.randomUUID();
        InvoiceDto invDto = new InvoiceDto(id1, clientA, UUID.randomUUID(), UUID.randomUUID(), "INV-1", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, "DKK", "PAID", "h1", "now");
        assertEquals(id1, invDto.id());
        PaymentDto payDto = new PaymentDto(id1, id1, clientA, BigDecimal.TEN, "CARD", "COMPLETED", "h2", "now");
        assertEquals(id1, payDto.id());
        RefundDto refDto = new RefundDto(id1, id1, clientA, BigDecimal.ONE, "Reason", "REFUNDED", "h3", "now");
        assertEquals(id1, refDto.id());
        FinancialVoucherDto voucherDto = new FinancialVoucherDto(id1, clientA, "INVOICE", "Sum", "## MD", "Author", "h1", null);
        assertEquals(id1, voucherDto.id());
        LedgerSummaryDto ledgerDto = new LedgerSummaryDto(id1, clientA, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 2);
        assertEquals(id1, ledgerDto.id());
    }

    @Test
    void testRootInvoiceListAndCreateEndpoints() {
        // Root GET /api/v1/finance & POST /api/v1/finance (listInvoices & createInvoice)
        Authentication therapistJwtAuth = createJwtAuth(therapistUuid, "Behandler Bo", "therapist");
        Authentication clientAJwtAuth = createJwtAuth(clientA, "Anders Sand", "client");
        Authentication clientBJwtAuth = createJwtAuth(clientB, "Anden Klient", "client");
        financeService.resetAll();

        ResponseEntity<Map<String, Object>> createdA = financeController.createInvoice(
                Map.of("clientUuid", clientA.toString(), "amountGrossDkk", "2", "description", "Session A"),
                therapistJwtAuth
        );
        assertEquals(HttpStatus.CREATED, createdA.getStatusCode());
        assertNotNull(createdA.getBody().get("id"));
        assertEquals(new BigDecimal("2"), createdA.getBody().get("amountGrossDkk"));

        ResponseEntity<Map<String, Object>> createdB = financeController.createInvoice(
                Map.of("clientUuid", clientB.toString(), "amount", "3", "description", "Session B"),
                therapistJwtAuth
        );
        assertEquals(HttpStatus.CREATED, createdB.getStatusCode());

        List<Map<String, Object>> therapistInvoices = financeController.listInvoices(therapistJwtAuth).getBody();
        assertNotNull(therapistInvoices);
        assertEquals(2, therapistInvoices.size());

        List<Map<String, Object>> clientAInvoices = financeController.listInvoices(clientAJwtAuth).getBody();
        assertNotNull(clientAInvoices);
        assertEquals(1, clientAInvoices.size());
        assertEquals("Session A", clientAInvoices.get(0).get("description"));

        List<Map<String, Object>> clientBInvoices = financeController.listInvoices(clientBJwtAuth).getBody();
        assertNotNull(clientBInvoices);
        assertEquals(1, clientBInvoices.size());
    }

    @Test
    void testVippsMobilePayOsgiCheckoutAccessRoutingAndRemoteBranches() {
        int port = mockServer.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;
        AtomicBoolean returnInitialCharge = new AtomicBoolean(true);
        mockServer.removeContext("/");
        mockServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String json;
            if ("/accesstoken/get".equals(path)) {
                json = "{\"access_token\":\"vipps-live-token\"}";
            } else if ("/recurring/v3/agreements".equals(path)) {
                json = returnInitialCharge.getAndSet(false)
                        ? "{\"agreementId\":\"AGR-LIVE-1\",\"chargeId\":\"CHG-LIVE-1\",\"vippsConfirmationUrl\":\"https://landing.vipps.no/agr-1\"}"
                        : "{\"agreementId\":\"AGR-LIVE-2\"}";
            } else if ("/epayment/v1/payments".equals(path)) {
                json = "{\"reference\":\"EPAY-LIVE-1\",\"redirectUrl\":\"https://landing.vipps.no/epay-1\"}";
            } else if (path.endsWith("/charges")) {
                json = "{\"chargeId\":\"CHG-LIVE-NEXT\"}";
            } else {
                json = "{\"status\":\"STOPPED\"}";
            }
            byte[] resp = json.getBytes();
            exchange.sendResponseHeaders(mockStatusCode.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        vippsPlugin.setRemoteConfiguration(baseUrl, true);
        mockStatusCode.set(200);
        financeService.resetAll();

        Authentication therapistAuth = createJwtAuth(therapistUuid, "Behandler Bo", "therapist");
        Authentication clientAAuth = createJwtAuth(clientA, "Anders Sand", "client");
        Authentication clientBAuth = createJwtAuth(clientB, "Client B", "client");

        // 0. Static helpers, null/blank MSISDN & single shared MSN override
        assertEquals("4520123456", VippsMobilePayOsgiPlugin.normalizeMsisdn(null));
        assertEquals("4520123456", VippsMobilePayOsgiPlugin.normalizeMsisdn("   "));
        assertEquals("4540112233", VippsMobilePayOsgiPlugin.normalizeMsisdn("40112233"));
        assertEquals("4520123456", VippsMobilePayOsgiPlugin.normalizeMsisdn("+4520123456"));
        assertEquals("", VippsMobilePayOsgiPlugin.sanitizeJson(null));
        assertEquals("fb", VippsMobilePayOsgiPlugin.extractJsonField(Pattern.compile("\"k\":\"([^\"]+)\""), null, "fb"));
        assertEquals("fb", VippsMobilePayOsgiPlugin.extractJsonField(Pattern.compile("\"k\":\"([^\"]+)\""), "{}", "fb"));

        vippsPlugin.setSingleMerchantSerialNumber("554433");
        ResponseEntity<Map<String, Object>> singleMsnCfg = financeController.getVippsConfig(clientAAuth);
        assertEquals("73N37", singleMsnCfg.getBody().get("merchantName"));
        assertEquals("46688244", singleMsnCfg.getBody().get("merchantCvr"));
        assertEquals("554433", singleMsnCfg.getBody().get("clinicVippsMsn"));
        vippsPlugin.setSingleMerchantSerialNumber(null);

        // 1. Public config endpoint (with default MSN tiers restored)
        ResponseEntity<Map<String, Object>> cfgResp = financeController.getVippsConfig(clientAAuth);
        assertEquals(HttpStatus.OK, cfgResp.getStatusCode());
        assertEquals("com.platform.killbill.vipps-mobilepay-plugin", cfgResp.getBody().get("bundleSymbolicName"));

        // 2. Client A purchases THERAPIST_ACCESS (4 DKK) via MOBILEPAY (returns initialCharge + vippsConfirmationUrl)
        Map<String, Object> therapistCheckout = new LinkedHashMap<>();
        therapistCheckout.put("productType", "THERAPIST_ACCESS");
        therapistCheckout.put("paymentMethod", "MOBILEPAY");
        therapistCheckout.put("phoneNumber", "+4520123456");
        ResponseEntity<Map<String, Object>> tResp = financeController.vippsCheckout(therapistCheckout, clientAAuth);
        assertEquals(HttpStatus.CREATED, tResp.getStatusCode());
        assertEquals("DK8130000012345678", tResp.getBody().get("payoutIban"));
        assertEquals("700101", tResp.getBody().get("vippsMsn"));
        assertEquals("THERAPIST", tResp.getBody().get("grantedRole"));
        assertEquals("https://landing.vipps.no/agr-1", tResp.getBody().get("redirectUrl"));
        assertTrue(vippsPlugin.hasPlatformAccess(clientA));
        assertEquals(HttpStatus.OK, financeController.getGeneralLedgerSummary(clientAAuth).getStatusCode());

        String therapistAgreementId = (String) tResp.getBody().get("agreementId");
        ResponseEntity<Map<String, Object>> chargedResp = financeController.chargeVippsAgreement(therapistAgreementId, clientAAuth);
        assertEquals(HttpStatus.OK, chargedResp.getStatusCode());
        assertEquals(2, chargedResp.getBody().get("chargeCount"));

        // 3. Purchase MANAGEMENT_ACCESS (5 DKK, hits fallback /charges when initialChargeId absent),
        //    SUBSCRIPTION (omitted amount -> 3.00 DKK), CLIP_CARD (3.00 DKK CARD), SESSION_INVOICE (omitted amount -> 2.00 DKK WALLET)
        ResponseEntity<Map<String, Object>> mgmtResp = financeController.vippsCheckout(
                Map.of(
                        "clientUuid", therapistUuid.toString(),
                        "productType", "MANAGEMENT_ACCESS",
                        "paymentMethod", "CREDIT_CARD",
                        "cardToken", "tok_card_mgmt_4242",
                        "cardLast4", "4242"
                ),
                therapistAuth
        );
        assertEquals("DK9230000087654321", mgmtResp.getBody().get("payoutIban"));
        assertEquals("700202", mgmtResp.getBody().get("vippsMsn"));
        assertEquals("ADMIN", mgmtResp.getBody().get("grantedRole"));

        ResponseEntity<Map<String, Object>> subResp = financeController.vippsCheckout(
                Map.of(
                        "productType", "SUBSCRIPTION",
                        "paymentMethod", "MOBILEPAY",
                        "phoneNumber", "40112233"
                ),
                clientBAuth
        );
        assertEquals(HttpStatus.CREATED, subResp.getStatusCode());
        assertEquals(FinanceFeature.PRICE_SUBSCRIPTION_DKK, subResp.getBody().get("amountDkk"));
        String subAgreementId = (String) subResp.getBody().get("agreementId");

        ResponseEntity<Map<String, Object>> clipResp = financeController.vippsCheckout(
                Map.of(
                        "clientUuid", clientB.toString(),
                        "productType", "CLIP_CARD",
                        "amount", "3.00",
                        "clips", 5,
                        "paymentMethod", "CREDIT_CARD",
                        "cardToken", "tok_card_clip_1234",
                        "cardLast4", "1234",
                        "cardBrand", "Mastercard"
                ),
                clientBAuth
        );
        assertEquals(HttpStatus.CREATED, clipResp.getStatusCode());

        ResponseEntity<Map<String, Object>> sessionResp = financeController.vippsCheckout(
                Map.of(
                        "clientUuid", clientB.toString(),
                        "productType", "SESSION_INVOICE",
                        "paymentMethod", "MOBILEPAY",
                        "phoneNumber", "30405060"
                ),
                clientBAuth
        );
        assertEquals(HttpStatus.CREATED, sessionResp.getStatusCode());
        assertEquals(FinanceFeature.PRICE_SINGLE_SESSION_DKK, sessionResp.getBody().get("amountDkk"));
        assertEquals("https://landing.vipps.no/epay-1", sessionResp.getBody().get("redirectUrl"));

        // Cancel one subscription agreement (exercises both matching & non-matching subscription loop branches)
        ResponseEntity<Map<String, Object>> cancelResp = financeController.cancelVippsAgreement(therapistAgreementId, clientAAuth);
        assertEquals("STOPPED", cancelResp.getBody().get("status"));
        ResponseEntity<Map<String, Object>> cancelSubResp = financeController.cancelVippsAgreement(subAgreementId, clientBAuth);
        assertEquals("STOPPED", cancelSubResp.getBody().get("status"));

        // Direct KillBillClient.cancelSubscription null/blank guards + multiple subs branch
        killBillClient.cancelSubscription(clientA, null, "tester");
        killBillClient.cancelSubscription(clientA, "   ", "tester");
        killBillClient.createSubscription(clientB, "extra-plan", BigDecimal.TEN, "DKK", "tester");
        killBillClient.cancelSubscription(clientB, "NON-MATCHING-SUB", "tester");

        // Stopped agreement cannot be charged again (400)
        assertThrows(DomainException.class, () ->
                financeController.chargeVippsAgreement(therapistAgreementId, clientAAuth)
        );

        // Missing agreement returns 404 on both charge and cancel & attachCommitHash no-ops safely
        vippsPlugin.attachCommitHash("MISSING-AGR", "abc12345");
        assertThrows(DomainException.class, () -> financeController.chargeVippsAgreement("MISSING-AGR", clientAAuth));
        assertThrows(DomainException.class, () -> financeController.cancelVippsAgreement("MISSING-AGR", clientAAuth));
        assertThrows(DomainException.class, () -> vippsPlugin.chargeRecurringAgreement("MISSING-AGR"));
        assertThrows(DomainException.class, () -> vippsPlugin.cancelAgreement("MISSING-AGR"));

        // Validation guards in VippsMobilePayOsgiPlugin
        assertThrows(DomainException.class, () -> vippsPlugin.resolveSpec(null, BigDecimal.TEN, "Plan"));
        assertThrows(DomainException.class, () -> vippsPlugin.resolveSpec("UNKNOWN_TIER", BigDecimal.TEN, "Plan"));
        assertEquals("Vipps MobilePay (•••5678)", vippsPlugin.validateAndMaskInstrument(null, Map.of("phoneNumber", "+4512345678")));
        assertThrows(DomainException.class, () -> vippsPlugin.validateAndMaskInstrument("MOBILEPAY", Map.of("phoneNumber", "123")));
        assertThrows(DomainException.class, () -> vippsPlugin.validateAndMaskInstrument("CRYPTO", Map.of()));
        assertThrows(DomainException.class, () -> vippsPlugin.validateAndMaskInstrument("CREDIT_CARD", Map.of("cardToken", "  ")));
        assertThrows(DomainException.class, () -> vippsPlugin.validateAndMaskInstrument("CREDIT_CARD", Map.of("cardToken", "4532015112830366", "cardLast4", "0366")));
        assertThrows(DomainException.class, () -> vippsPlugin.validateAndMaskInstrument("CREDIT_CARD", Map.of("cardToken", "tok_safe", "cardLast4", "12")));

        // Remote HTTP error handling on token endpoint & API endpoint + unreachable port
        mockStatusCode.set(500);
        assertThrows(IllegalStateException.class, () ->
                financeController.vippsCheckout(therapistCheckout, clientAAuth)
        );
        AtomicInteger callSeq = new AtomicInteger(0);
        vippsPlugin.setRemoteConfiguration(baseUrl, true);
        mockServer.removeContext("/");
        mockServer.createContext("/", exchange -> {
            int code = callSeq.getAndIncrement() == 0 ? 200 : 500;
            byte[] resp = "{\"access_token\":\"t\"}".getBytes();
            exchange.sendResponseHeaders(code, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        assertThrows(IllegalStateException.class, () ->
                financeController.vippsCheckout(therapistCheckout, clientAAuth)
        );
        vippsPlugin.setRemoteConfiguration("http://127.0.0.1:1", true);
        assertThrows(IllegalStateException.class, () ->
                financeController.vippsCheckout(therapistCheckout, clientAAuth)
        );

        // Exercise VippsAgreementDto record
        UUID agrUuid = UUID.randomUUID();
        VippsAgreementDto dto = new VippsAgreementDto(
                agrUuid, clientA, "AGR-1", "THERAPIST_ACCESS", "Therapist-adgang",
                FinanceFeature.PRICE_THERAPIST_ACCESS_DKK, "DKK", "MOBILEPAY",
                "Vipps MobilePay (•••3456)", "DK8130000012345678", "700101",
                "THERAPIST", "ACTIVE", "abc12345"
        );
        assertEquals(agrUuid, dto.id());
        assertEquals("AGR-1", dto.agreementId());
    }
}
