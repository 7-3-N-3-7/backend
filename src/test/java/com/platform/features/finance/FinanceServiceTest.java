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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FinanceServiceTest {

    @TempDir
    Path tempDir;

    private FinanceGitService financeGitService;
    private KillBillClient killBillClient;
    private FineractClient fineractClient;
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

        financeService = new FinanceService(killBillClient, fineractClient, financeGitService);
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
        unpaidPayload.put("amount", "850.00");
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
        paidPayload.put("amount", "1200.00");
        paidPayload.put("payImmediately", true);
        paidPayload.put("clinicalCommitHash", "   ");
        ResponseEntity<Map<String, Object>> paidResp = financeController.billSingleSession(paidPayload, clientAAuth);

        // Also test default payImmediately (when key is omitted)
        Map<String, Object> defaultPayPayload = new LinkedHashMap<>();
        defaultPayPayload.put("clientUuid", clientA.toString());
        defaultPayPayload.put("amount", "500.00");
        financeController.billSingleSession(defaultPayPayload, clientAAuth);
        assertEquals(HttpStatus.CREATED, paidResp.getStatusCode());
        String paidInvoiceId = (String) paidResp.getBody().get("invoiceId");
        String commitHash = (String) paidResp.getBody().get("gitCommitHash");
        assertNotNull(paidResp.getBody().get("settlementJournalEntryId"));

        // Refund unpaid invoice with custom amount
        ResponseEntity<Map<String, Object>> refundUnpaidResp = financeController.refundInvoice(
                unpaidInvoiceId,
                Map.of("amount", "850.00", "reason", "Kreditnota ubetalt"),
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
                Map.of("clientUuid", clientA.toString(), "amount", "2000.00", "clips", 2),
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
                Map.of("clientUuid", clientA.toString(), "planName", "monthly-care", "monthlyAmount", "1500.00"),
                clientAAuth
        );
        assertEquals(HttpStatus.CREATED, subResp.getStatusCode());

        ResponseEntity<Map<String, Object>> loanResp = financeController.createInstallmentPlan(
                Map.of("clientUuid", clientA.toString(), "principalAmount", "3000.00", "installmentsCount", 2),
                therapistAuth
        );
        assertEquals(HttpStatus.CREATED, loanResp.getStatusCode());
        String loanId = (String) loanResp.getBody().get("loanId");

        // First repayment with default installment amount (1500)
        ResponseEntity<Map<String, Object>> repay1 = financeController.repayInstallment(
                loanId,
                Map.of(),
                clientAAuth
        );
        assertEquals(HttpStatus.OK, repay1.getStatusCode());
        assertEquals("ACTIVE", repay1.getBody().get("status"));

        // Second repayment with explicit amount (1500) closes loan
        ResponseEntity<Map<String, Object>> repay2 = financeController.repayInstallment(
                loanId,
                Map.of("amount", "1500.00"),
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
}
