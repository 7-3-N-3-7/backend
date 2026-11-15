package com.platform.features.finance;

import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: FinanceFeature (Vertical Feature Slice)
 * ============================================================================
 * @Spejler: frontend/src/features/finance/FinanceFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Demonstrerer finansiel sporbarhed og integration med enterprise billing- og
 * core-banking-systemer (Kill Bill & Apache Fineract) kombineret med kryptografisk
 * SHA-1 bilagsrevision i Eclipse JGit:
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: InvoiceDto], [Record/DTO: PaymentDto], [Record/DTO: RefundDto],
 *      [Record/DTO: FinancialVoucherDto] & [Record/DTO: LedgerSummaryDto]
 *      Java 17 `record` kontrakter der implementerer [Interface: HasId].
 *   2. [Service: FinanceGitService] (Persistent JGit Bilagsbogføring)
 *      Skriver enhver økonomisk hændelse til både klientens hovedbog
 *      (`clients/<uuid>/ledger.md`) og klinikkens hovedbog (`general-ledger/clinic-gl.md`)
 *      med en uforanderlig SHA-1 commit-hash.
 *   3. [Service: FineractClient] & [Service: KillBillClient]
 *      - [Service: FineractClient]: Dobbelt bogholderi (Debet = Kredit) mod kontoplanen
 *        (`1100 Bank`, `1200 Debitorer`, `2100 Klippekort-forpligtelser`,
 *        `4100 Konsultationsomsætning`, `4200 Abonnementsomsætning`).
 *      - [Service: KillBillClient]: Fakturaudstedelse, kreditnotaer og abonnementer.
 *   4. [Service: FinanceService]
 *      Orkestrerer samspillet mellem Kill Bill, Fineract og JGit og håndhæver
 *      Zero-Trust RBAC (en klient kan kun se egne bilag; kun terapeuter/admin
 *      kan udstede kreditnotaer eller læse klinikkens hovedbog).
 *   5. [Controller: FinanceController]
 *      REST-controller under `/api/v1/finance`.
 * ============================================================================
 */
public final class FinanceFeature {

    /** Konto 1100: Likvide beholdninger / Bank (Aktiv / ASSET). */
    public static final String GL_ACCOUNT_BANK = "1100";

    /** Konto 1200: Tilgodehavender fra salg / Debitorer (Aktiv / ASSET). */
    public static final String GL_ACCOUNT_ACCOUNTS_RECEIVABLE = "1200";

    /** Konto 2100: Forudbetalte klippekort-forpligtelser (Passiv / LIABILITY). */
    public static final String GL_ACCOUNT_PREPAID_CLIPS_LIABILITY = "2100";

    /** Konto 4100: Konsultationsomsætning (Indtægt / INCOME). */
    public static final String GL_ACCOUNT_CONSULTATION_REVENUE = "4100";

    /** Konto 4200: Abonnementsomsætning (Indtægt / INCOME). */
    public static final String GL_ACCOUNT_SUBSCRIPTION_REVENUE = "4200";

    private FinanceFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    public record InvoiceDto(
            UUID id,
            UUID clientUuid,
            UUID therapistUuid,
            UUID appointmentId,
            String invoiceNumber,
            BigDecimal amountNetDkk,
            BigDecimal vatRatePercent,
            BigDecimal amountVatDkk,
            BigDecimal amountGrossDkk,
            String currency,
            String status,
            String gitCommitHash,
            String createdAt
    ) implements HasId {}

    public record PaymentDto(
            UUID id,
            UUID invoiceId,
            UUID clientUuid,
            BigDecimal amountDkk,
            String paymentMethod,
            String status,
            String gitCommitHash,
            String paidAt
    ) implements HasId {}

    public record RefundDto(
            UUID id,
            UUID invoiceId,
            UUID clientUuid,
            BigDecimal refundAmountDkk,
            String reason,
            String status,
            String gitCommitHash,
            String refundedAt
    ) implements HasId {}

    public record FinancialVoucherDto(
            UUID id,
            UUID clientUuid,
            String voucherType,
            String summaryLine,
            String markdownBlock,
            String authorName,
            String commitHash,
            String referenceHash
    ) implements HasId {}

    public record LedgerSummaryDto(
            UUID id,
            UUID clientUuid,
            BigDecimal totalInvoicedGrossDkk,
            BigDecimal totalPaidDkk,
            BigDecimal totalRefundedDkk,
            BigDecimal outstandingReceivablesDkk,
            int invoiceCount,
            int voucherCommitCount
    ) implements HasId {}

    // =========================================================================
    // 2. INFRASTRUCTURE CLIENTS & JGIT LEDGER PORTS (Kill Bill, Fineract, JGit)
    // =========================================================================

    @Service
    public static class FinanceGitService {

        @Value("${finance.git.repo-path:./finance-repo}")
        private String repoPath;

        private Git git;

        @PostConstruct
        public synchronized void init() {
            try {
                File repoDir = new File(repoPath);
                File gitDir = new File(repoDir, ".git");
                if (!gitDir.exists()) {
                    Files.createDirectories(Path.of(repoPath));
                    git = Git.init().setDirectory(repoDir).call();
                } else {
                    Repository repository = new FileRepositoryBuilder()
                            .setGitDir(gitDir)
                            .readEnvironment()
                            .build();
                    git = new Git(repository);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize finance Git repository", e);
            }
        }

        public synchronized String commitFinancialVoucher(
                UUID clientUuid,
                String voucherType,
                String summaryLine,
                String markdownBlock,
                String authorName,
                String referenceHash
        ) {
            try {
                String clientRelPath = "clients/" + clientUuid + "/ledger.md";
                String glRelPath = "general-ledger/clinic-gl.md";

                File clientFile = new File(repoPath, clientRelPath);
                File glFile = new File(repoPath, glRelPath);
                Files.createDirectories(clientFile.getParentFile().toPath());
                Files.createDirectories(glFile.getParentFile().toPath());

                String refSuffix = (referenceHash != null && !referenceHash.isBlank())
                        ? " (Ref: #" + referenceHash + ")"
                        : "";
                String commitMessage = "[" + voucherType + "] " + summaryLine + refSuffix;

                String formattedEntry = "### " + commitMessage + "\n"
                        + "- **Forfatter:** " + authorName + "\n"
                        + "- **Klient UUID:** `" + clientUuid + "`\n"
                        + markdownBlock + "\n\n";

                Files.writeString(
                        clientFile.toPath(),
                        formattedEntry,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
                Files.writeString(
                        glFile.toPath(),
                        formattedEntry,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );

                git.add()
                        .addFilepattern(clientRelPath)
                        .addFilepattern(glRelPath)
                        .call();

                RevCommit commit = git.commit()
                        .setSign(false)
                        .setAuthor(authorName, "finance-audit@platform.local")
                        .setMessage(commitMessage)
                        .call();

                return commit.getName().substring(0, 8);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to commit financial voucher to JGit", e);
            }
        }

        public synchronized List<Map<String, String>> getClientFinanceHistory(UUID clientUuid) {
            String clientRelPath = "clients/" + clientUuid + "/ledger.md";
            return readFileCommitHistory(clientRelPath);
        }

        public synchronized List<Map<String, String>> getGeneralLedgerHistory() {
            return readFileCommitHistory("general-ledger/clinic-gl.md");
        }

        private List<Map<String, String>> readFileCommitHistory(String relativePath) {
            List<Map<String, String>> history = new ArrayList<>();
            File targetFile = new File(repoPath, relativePath);
            if (!targetFile.exists()) {
                return history;
            }
            try {
                Iterable<RevCommit> commits = git.log().addPath(relativePath).call();
                Repository repository = git.getRepository();
                for (RevCommit commit : commits) {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("commitHash", commit.getName().substring(0, 8));
                    item.put("fullHash", commit.getName());
                    item.put("message", commit.getFullMessage());
                    item.put("author", commit.getAuthorIdent().getName());
                    item.put("timestamp", Instant.ofEpochSecond(commit.getCommitTime()).toString());
                    item.put("snapshot", readBlobAtCommit(repository, commit, relativePath));
                    history.add(item);
                }
                return history;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read finance Git history for " + relativePath, e);
            }
        }

        public synchronized Map<String, Object> getCommitSnapshot(String commitHashPrefix) {
            try {
                Repository repository = git.getRepository();
                ObjectId resolved = repository.resolve(commitHashPrefix);
                if (resolved == null) {
                    return null;
                }
                try (RevWalk revWalk = new RevWalk(repository)) {
                    RevCommit commit = revWalk.parseCommit(resolved);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("commitHash", commit.getName().substring(0, 8));
                    result.put("fullHash", commit.getName());
                    result.put("message", commit.getFullMessage());
                    result.put("author", commit.getAuthorIdent().getName());
                    result.put("timestamp", Instant.ofEpochSecond(commit.getCommitTime()).toString());
                    result.put("generalLedgerSnapshot", readBlobAtCommit(repository, commit, "general-ledger/clinic-gl.md"));
                    return result;
                }
            } catch (Exception e) {
                return null;
            }
        }

        private String readBlobAtCommit(Repository repository, RevCommit commit, String relativePath) throws IOException {
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(relativePath));
                if (treeWalk.next()) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    return new String(loader.getBytes(), StandardCharsets.UTF_8);
                }
                return "";
            }
        }

        @PreDestroy
        public synchronized void close() {
            if (git != null) {
                git.close();
            }
        }
    }

    @Service
    public static class FineractClient {

        @Value("${fineract.url:http://fineract:8080/fineract-provider/api/v1}")
        private String fineractUrl;

        @Value("${fineract.tenant-id:default}")
        private String tenantId;

        @Value("${fineract.username:mifos}")
        private String username;

        @Value("${fineract.password:password}")
        private String password;

        @Value("${finance.remote.enabled:false}")
        private boolean remoteEnabled;

        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        private final Map<String, Map<String, Object>> glAccounts = new LinkedHashMap<>();
        private final List<Map<String, Object>> journalEntries = new ArrayList<>();
        private final Map<UUID, Map<String, Object>> savingsByClient = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> loansById = new ConcurrentHashMap<>();
        private final Map<UUID, List<String>> clientLoanIds = new ConcurrentHashMap<>();

        @PostConstruct
        public synchronized void initChartOfAccounts() {
            glAccounts.clear();
            journalEntries.clear();
            savingsByClient.clear();
            loansById.clear();
            clientLoanIds.clear();

            registerGlAccount("1100", "Bank / Likvider", "ASSET");
            registerGlAccount("1200", "Debitorer / Tilgodehavender", "ASSET");
            registerGlAccount("2100", "Forudbetalte Klippekort & Klient-saldi", "LIABILITY");
            registerGlAccount("4100", "Konsultationsomsætning", "INCOME");
            registerGlAccount("4200", "Abonnementsomsætning", "INCOME");
        }

        private void registerGlAccount(String glCode, String name, String type) {
            Map<String, Object> acc = new LinkedHashMap<>();
            acc.put("glCode", glCode);
            acc.put("name", name);
            acc.put("type", type);
            acc.put("totalDebits", BigDecimal.ZERO);
            acc.put("totalCredits", BigDecimal.ZERO);
            acc.put("balance", BigDecimal.ZERO);
            glAccounts.put(glCode, acc);
        }

        public void setRemoteConfiguration(String url, boolean enabled) {
            this.fineractUrl = url;
            this.remoteEnabled = enabled;
        }

        public synchronized Map<String, Object> postJournalEntry(
                String debitGlCode,
                String creditGlCode,
                BigDecimal amount,
                String currency,
                String referenceId,
                String comments
        ) {
            Map<String, Object> debitAcc = glAccounts.get(debitGlCode);
            Map<String, Object> creditAcc = glAccounts.get(creditGlCode);
            if (debitAcc == null || creditAcc == null) {
                throw new IllegalArgumentException("Unknown Fineract GL code: " + debitGlCode + " or " + creditGlCode);
            }

            String entryId = "FIN-JE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            sendFineractRequest(
                    "/journalentries",
                    "{\"currencyCode\":\"" + currency + "\",\"referenceNumber\":\"" + referenceId + "\","
                            + "\"comments\":\"" + comments + "\","
                            + "\"debits\":[{\"glAccountCode\":\"" + debitGlCode + "\",\"amount\":" + amount + "}],"
                            + "\"credits\":[{\"glAccountCode\":\"" + creditGlCode + "\",\"amount\":" + amount + "}]}"
            );

            updateAccountTotals(debitAcc, amount, true);
            updateAccountTotals(creditAcc, amount, false);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("journalEntryId", entryId);
            entry.put("debitGlCode", debitGlCode);
            entry.put("debitAccountName", debitAcc.get("name"));
            entry.put("creditGlCode", creditGlCode);
            entry.put("creditAccountName", creditAcc.get("name"));
            entry.put("amount", amount);
            entry.put("currency", currency);
            entry.put("referenceId", referenceId);
            entry.put("comments", comments);
            entry.put("createdAt", Instant.now().toString());

            journalEntries.add(entry);
            return new LinkedHashMap<>(entry);
        }

        private void updateAccountTotals(Map<String, Object> account, BigDecimal amount, boolean isDebit) {
            BigDecimal debits = (BigDecimal) account.get("totalDebits");
            BigDecimal credits = (BigDecimal) account.get("totalCredits");
            if (isDebit) {
                debits = debits.add(amount);
                account.put("totalDebits", debits);
            } else {
                credits = credits.add(amount);
                account.put("totalCredits", credits);
            }
            String type = (String) account.get("type");
            BigDecimal balance = "ASSET".equals(type) ? debits.subtract(credits) : credits.subtract(debits);
            account.put("balance", balance);
        }

        public synchronized Map<String, Object> depositToSavings(
                UUID clientUuid,
                BigDecimal amount,
                int clipsAdded,
                String currency,
                String invoiceId
        ) {
            Map<String, Object> savings = getOrCreateSavingsAccount(clientUuid, currency);
            String savingsId = (String) savings.get("savingsAccountId");

            sendFineractRequest(
                    "/savingsaccounts/" + savingsId + "/transactions?command=deposit",
                    "{\"transactionAmount\":" + amount + ",\"note\":\"Top-up via " + invoiceId + "\"}"
            );

            BigDecimal newBalance = ((BigDecimal) savings.get("balance")).add(amount);
            int newClips = ((Integer) savings.get("remainingClips")) + clipsAdded;
            BigDecimal unitPrice = newClips > 0
                    ? newBalance.divide(BigDecimal.valueOf(newClips), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            savings.put("balance", newBalance);
            savings.put("remainingClips", newClips);
            savings.put("clipUnitPrice", unitPrice);
            savings.put("updatedAt", Instant.now().toString());

            // Trin 2: Dobbelt bogholderi — Debiter Bank (1100) og krediter Klippekort-forpligtelser (2100)
            Map<String, Object> glEntry = postJournalEntry(
                    GL_ACCOUNT_BANK,
                    GL_ACCOUNT_PREPAID_CLIPS_LIABILITY,
                    amount,
                    currency,
                    invoiceId,
                    "Klippekort optankning (" + clipsAdded + " klip)"
            );

            Map<String, Object> result = new LinkedHashMap<>(savings);
            result.put("journalEntryId", glEntry.get("journalEntryId"));
            return result;
        }

        public synchronized Map<String, Object> withdrawFromSavings(
                UUID clientUuid,
                int clipsToUse,
                String appointmentUuid
        ) {
            Map<String, Object> savings = getOrCreateSavingsAccount(clientUuid, "DKK");
            int currentClips = (Integer) savings.get("remainingClips");
            BigDecimal currentBalance = (BigDecimal) savings.get("balance");

            if (currentClips < clipsToUse || currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Insufficient prepaid clips or balance on Fineract savings account");
            }

            BigDecimal unitPrice = (BigDecimal) savings.get("clipUnitPrice");
            BigDecimal deduction = (currentClips == clipsToUse)
                    ? currentBalance
                    : unitPrice.multiply(BigDecimal.valueOf(clipsToUse));

            String savingsId = (String) savings.get("savingsAccountId");
            sendFineractRequest(
                    "/savingsaccounts/" + savingsId + "/transactions?command=withdrawal",
                    "{\"transactionAmount\":" + deduction + ",\"note\":\"Session clip consumption\"}"
            );

            int remainingClips = currentClips - clipsToUse;
            BigDecimal remainingBalance = currentBalance.subtract(deduction);
            savings.put("remainingClips", remainingClips);
            savings.put("balance", remainingBalance);
            savings.put("updatedAt", Instant.now().toString());

            // Trin 2: Dobbelt bogholderi — Debiter Klippekort-forpligtelser (2100) og indtægtsfør på Konsultationsomsætning (4100)
            Map<String, Object> glEntry = postJournalEntry(
                    GL_ACCOUNT_PREPAID_CLIPS_LIABILITY,
                    GL_ACCOUNT_CONSULTATION_REVENUE,
                    deduction,
                    (String) savings.get("currency"),
                    appointmentUuid,
                    "Klippekort forbrug (" + clipsToUse + " klip)"
            );

            Map<String, Object> result = new LinkedHashMap<>(savings);
            result.put("deductedAmount", deduction);
            result.put("journalEntryId", glEntry.get("journalEntryId"));
            return result;
        }

        public synchronized Map<String, Object> createInstallmentLoan(
                UUID clientUuid,
                String description,
                BigDecimal principalAmount,
                int installmentsCount,
                String currency
        ) {
            String loanId = "FIN-LOAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            BigDecimal installmentAmount = principalAmount.divide(
                    BigDecimal.valueOf(installmentsCount), 2, RoundingMode.HALF_UP
            );

            sendFineractRequest(
                    "/loans",
                    "{\"clientId\":\"" + clientUuid + "\",\"principal\":" + principalAmount
                            + ",\"numberOfRepayments\":" + installmentsCount + "}"
            );

            // Trin 2: Dobbelt bogholderi — Debiter Debitorer (1200) og krediter Konsultationsomsætning (4100)
            Map<String, Object> glEntry = postJournalEntry(
                    GL_ACCOUNT_ACCOUNTS_RECEIVABLE,
                    GL_ACCOUNT_CONSULTATION_REVENUE,
                    principalAmount,
                    currency,
                    loanId,
                    "Oprettelse af afdragsordning: " + description
            );

            Map<String, Object> loan = new LinkedHashMap<>();
            loan.put("loanId", loanId);
            loan.put("clientUuid", clientUuid.toString());
            loan.put("description", description);
            loan.put("principalAmount", principalAmount);
            loan.put("remainingPrincipal", principalAmount);
            loan.put("installmentsCount", installmentsCount);
            loan.put("paidInstallments", 0);
            loan.put("installmentAmount", installmentAmount);
            loan.put("currency", currency);
            loan.put("status", "ACTIVE");
            loan.put("journalEntryId", glEntry.get("journalEntryId"));
            loan.put("createdAt", Instant.now().toString());

            loansById.put(loanId, loan);
            clientLoanIds.computeIfAbsent(clientUuid, k -> new ArrayList<>()).add(loanId);
            return new LinkedHashMap<>(loan);
        }

        public synchronized Map<String, Object> repayInstallmentLoan(
                String loanId,
                BigDecimal customAmount,
                String invoiceId
        ) {
            Map<String, Object> loan = loansById.get(loanId);
            if (loan == null) {
                throw new IllegalArgumentException("Fineract loan not found: " + loanId);
            }

            BigDecimal remaining = (BigDecimal) loan.get("remainingPrincipal");
            BigDecimal defaultRate = (BigDecimal) loan.get("installmentAmount");
            BigDecimal paymentAmount = customAmount != null ? customAmount : defaultRate.min(remaining);

            sendFineractRequest(
                    "/loans/" + loanId + "/transactions?command=repayment",
                    "{\"transactionAmount\":" + paymentAmount + ",\"externalId\":\"" + invoiceId + "\"}"
            );

            BigDecimal newRemaining = remaining.subtract(paymentAmount).max(BigDecimal.ZERO);
            int paidInstallments = ((Integer) loan.get("paidInstallments")) + 1;
            String status = newRemaining.compareTo(BigDecimal.ZERO) == 0 ? "CLOSED_OBLIGATIONS_MET" : "ACTIVE";

            loan.put("remainingPrincipal", newRemaining);
            loan.put("paidInstallments", paidInstallments);
            loan.put("status", status);

            // Trin 2: Dobbelt bogholderi — Debiter Bank (1100) og nedskriv Debitor-tilgodehavende (1200)
            Map<String, Object> glEntry = postJournalEntry(
                    GL_ACCOUNT_BANK,
                    GL_ACCOUNT_ACCOUNTS_RECEIVABLE,
                    paymentAmount,
                    (String) loan.get("currency"),
                    invoiceId,
                    "Afdrag på lån " + loanId + " (rate #" + paidInstallments + ")"
            );

            Map<String, Object> result = new LinkedHashMap<>(loan);
            result.put("paidAmount", paymentAmount);
            result.put("repaymentJournalEntryId", glEntry.get("journalEntryId"));
            return result;
        }

        public synchronized Map<String, Object> getOrCreateSavingsAccount(UUID clientUuid, String currency) {
            return savingsByClient.computeIfAbsent(clientUuid, uuid -> {
                String savingsId = "FIN-SAV-" + UUID.nameUUIDFromBytes(("fin-sav:" + uuid).getBytes(StandardCharsets.UTF_8))
                        .toString().substring(0, 8).toUpperCase();
                Map<String, Object> acc = new LinkedHashMap<>();
                acc.put("savingsAccountId", savingsId);
                acc.put("clientUuid", uuid.toString());
                acc.put("balance", BigDecimal.ZERO);
                acc.put("remainingClips", 0);
                acc.put("clipUnitPrice", BigDecimal.ZERO);
                acc.put("currency", currency);
                acc.put("updatedAt", Instant.now().toString());
                return acc;
            });
        }

        public synchronized Map<String, Object> getLoan(String loanId) {
            Map<String, Object> loan = loansById.get(loanId);
            return loan != null ? new LinkedHashMap<>(loan) : null;
        }

        public synchronized List<Map<String, Object>> getClientLoans(UUID clientUuid) {
            List<String> ids = clientLoanIds.getOrDefault(clientUuid, List.of());
            List<Map<String, Object>> result = new ArrayList<>();
            for (String id : ids) {
                result.add(new LinkedHashMap<>(loansById.get(id)));
            }
            return result;
        }

        public synchronized List<Map<String, Object>> getChartOfAccounts() {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> acc : glAccounts.values()) {
                list.add(new LinkedHashMap<>(acc));
            }
            return list;
        }

        public synchronized List<Map<String, Object>> getJournalEntries() {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> entry : journalEntries) {
                list.add(new LinkedHashMap<>(entry));
            }
            return list;
        }

        private void sendFineractRequest(String path, String jsonBody) {
            if (!remoteEnabled) {
                return;
            }
            try {
                String basicAuth = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fineractUrl + path))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .header("Fineract-Platform-TenantId", tenantId)
                        .header("Authorization", "Basic " + basicAuth)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("Fineract API returned HTTP " + response.statusCode());
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to communicate with Fineract REST API at " + fineractUrl, e);
            }
        }
    }

    @Service
    public static class KillBillClient {

        @Value("${killbill.url:http://killbill:8080}")
        private String killbillUrl;

        @Value("${killbill.api-key:nordic-mind-key}")
        private String apiKey;

        @Value("${killbill.api-secret:nordic-mind-secret}")
        private String apiSecret;

        @Value("${killbill.username:admin}")
        private String username;

        @Value("${killbill.password:password}")
        private String password;

        @Value("${finance.remote.enabled:false}")
        private boolean remoteEnabled;

        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        private final Map<UUID, Map<String, Object>> accountsByClient = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> invoicesById = new ConcurrentHashMap<>();
        private final Map<UUID, List<String>> clientInvoiceIds = new ConcurrentHashMap<>();
        private final Map<UUID, List<Map<String, Object>>> subscriptionsByClient = new ConcurrentHashMap<>();

        public synchronized void resetState() {
            accountsByClient.clear();
            invoicesById.clear();
            clientInvoiceIds.clear();
            subscriptionsByClient.clear();
        }

        public void setRemoteConfiguration(String url, boolean enabled) {
            this.killbillUrl = url;
            this.remoteEnabled = enabled;
        }

        public synchronized Map<String, Object> ensureAccount(UUID clientUuid, String currency, String createdBy) {
            Map<String, Object> existing = accountsByClient.get(clientUuid);
            if (existing != null) {
                return existing;
            }
            String accountId = UUID.nameUUIDFromBytes(("kb-account:" + clientUuid).getBytes(StandardCharsets.UTF_8)).toString();
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("accountId", accountId);
            account.put("externalKey", clientUuid.toString());
            account.put("currency", currency);
            account.put("createdAt", Instant.now().toString());

            sendKillBillRequest(
                    "/1.0/kb/accounts",
                    "{\"accountId\":\"" + accountId + "\",\"externalKey\":\"" + clientUuid + "\",\"currency\":\"" + currency + "\"}",
                    createdBy
            );

            accountsByClient.put(clientUuid, account);
            return account;
        }

        public synchronized Map<String, Object> createInvoiceAndCharge(
                UUID clientUuid,
                String description,
                BigDecimal amount,
                String currency,
                boolean payImmediately,
                String appointmentUuid,
                String createdBy
        ) {
            Map<String, Object> account = ensureAccount(clientUuid, currency, createdBy);
            String invoiceId = "KB-INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String status = payImmediately ? "PAID" : "COMMITTED";
            String paymentId = payImmediately ? "KB-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : null;

            sendKillBillRequest(
                    "/1.0/kb/invoices/charges/" + account.get("accountId") + "?autoCommit=true",
                    "[{\"amount\":" + amount + ",\"currency\":\"" + currency + "\",\"description\":\"" + description + "\"}]",
                    createdBy
            );

            if (payImmediately) {
                sendKillBillRequest(
                        "/1.0/kb/invoices/" + invoiceId + "/payments?externalPayment=true",
                        "{\"accountId\":\"" + account.get("accountId") + "\",\"purchasedAmount\":" + amount + "}",
                        createdBy
                );
            }

            Map<String, Object> invoice = new LinkedHashMap<>();
            invoice.put("invoiceId", invoiceId);
            invoice.put("accountId", account.get("accountId"));
            invoice.put("clientUuid", clientUuid.toString());
            invoice.put("description", description);
            invoice.put("amount", amount);
            invoice.put("refundedAmount", BigDecimal.ZERO);
            invoice.put("currency", currency);
            invoice.put("status", status);
            invoice.put("paymentId", paymentId);
            invoice.put("appointmentUuid", appointmentUuid);
            invoice.put("createdAt", Instant.now().toString());

            invoicesById.put(invoiceId, invoice);
            clientInvoiceIds.computeIfAbsent(clientUuid, k -> new ArrayList<>()).add(invoiceId);
            return new LinkedHashMap<>(invoice);
        }

        public synchronized Map<String, Object> refundInvoice(
                String invoiceId,
                BigDecimal refundAmount,
                String reason,
                String createdBy
        ) {
            Map<String, Object> invoice = invoicesById.get(invoiceId);
            if (invoice == null) {
                throw new IllegalArgumentException("Kill Bill invoice not found: " + invoiceId);
            }

            BigDecimal originalAmount = (BigDecimal) invoice.get("amount");
            BigDecimal effectiveRefund = refundAmount != null ? refundAmount : originalAmount;
            String creditId = "KB-CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            sendKillBillRequest(
                    "/1.0/kb/credits",
                    "[{\"invoiceId\":\"" + invoiceId + "\",\"amount\":" + effectiveRefund + ",\"description\":\"" + reason + "\"}]",
                    createdBy
            );

            invoice.put("refundedAmount", effectiveRefund);
            invoice.put("status", "REFUNDED");
            invoice.put("creditNoteId", creditId);
            invoice.put("refundReason", reason);

            return new LinkedHashMap<>(invoice);
        }

        public synchronized Map<String, Object> createSubscription(
                UUID clientUuid,
                String planName,
                BigDecimal monthlyAmount,
                String currency,
                String createdBy
        ) {
            Map<String, Object> account = ensureAccount(clientUuid, currency, createdBy);
            String subscriptionId = "KB-SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            sendKillBillRequest(
                    "/1.0/kb/subscriptions",
                    "{\"accountId\":\"" + account.get("accountId") + "\",\"planName\":\"" + planName + "\"}",
                    createdBy
            );

            Map<String, Object> initialInvoice = createInvoiceAndCharge(
                    clientUuid,
                    "Abonnement (" + planName + ") - 1. periode",
                    monthlyAmount,
                    currency,
                    true,
                    null,
                    createdBy
            );

            Map<String, Object> subscription = new LinkedHashMap<>();
            subscription.put("subscriptionId", subscriptionId);
            subscription.put("accountId", account.get("accountId"));
            subscription.put("clientUuid", clientUuid.toString());
            subscription.put("planName", planName);
            subscription.put("monthlyAmount", monthlyAmount);
            subscription.put("currency", currency);
            subscription.put("status", "ACTIVE");
            subscription.put("initialInvoiceId", initialInvoice.get("invoiceId"));
            subscription.put("createdAt", Instant.now().toString());

            subscriptionsByClient.computeIfAbsent(clientUuid, k -> new ArrayList<>()).add(subscription);
            return new LinkedHashMap<>(subscription);
        }

        public synchronized Map<String, Object> getInvoice(String invoiceId) {
            Map<String, Object> inv = invoicesById.get(invoiceId);
            return inv != null ? new LinkedHashMap<>(inv) : null;
        }

        public synchronized List<Map<String, Object>> getClientInvoices(UUID clientUuid) {
            List<String> ids = clientInvoiceIds.getOrDefault(clientUuid, List.of());
            List<Map<String, Object>> result = new ArrayList<>();
            for (String id : ids) {
                result.add(new LinkedHashMap<>(invoicesById.get(id)));
            }
            return result;
        }

        public synchronized List<Map<String, Object>> getClientSubscriptions(UUID clientUuid) {
            List<Map<String, Object>> subs = subscriptionsByClient.getOrDefault(clientUuid, List.of());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> s : subs) {
                result.add(new LinkedHashMap<>(s));
            }
            return result;
        }

        private void sendKillBillRequest(String path, String jsonBody, String createdBy) {
            if (!remoteEnabled) {
                return;
            }
            try {
                String basicAuth = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(killbillUrl + path))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Basic " + basicAuth)
                        .header("X-Killbill-ApiKey", apiKey)
                        .header("X-Killbill-ApiSecret", apiSecret)
                        .header("X-Killbill-CreatedBy", createdBy)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("Kill Bill API returned HTTP " + response.statusCode());
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to communicate with Kill Bill REST API at " + killbillUrl, e);
            }
        }
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    @Service
    static class FinanceService {

        private final KillBillClient killBillClient;
        private final FineractClient fineractClient;
        private final FinanceGitService financeGitService;
        private final Map<String, String> invoiceCommitHashes = new ConcurrentHashMap<>();

        FinanceService(
                KillBillClient killBillClient,
                FineractClient fineractClient,
                FinanceGitService financeGitService
        ) {
            this.killBillClient = killBillClient;
            this.fineractClient = fineractClient;
            this.financeGitService = financeGitService;
        }

        public synchronized void resetAll() {
            killBillClient.resetState();
            fineractClient.initChartOfAccounts();
            invoiceCommitHashes.clear();
        }

        public Map<String, Object> billSingleSession(Map<String, Object> payload, Authentication auth) {
            UUID clientUuid = parseUuid(payload.get("clientUuid"), "clientUuid");
            requireClientSelfOrTherapist(clientUuid, auth);

            BigDecimal amount = parsePositiveAmount(payload.get("amount"), "amount");
            String currency = parseString(payload.get("currency"), "DKK");
            String description = parseString(payload.get("description"), "Klinisk Terapisession");
            String appointmentUuid = parseString(payload.get("appointmentUuid"), null);
            String clinicalCommitHash = parseString(payload.get("clinicalCommitHash"), appointmentUuid);
            boolean payImmediately = payload.get("payImmediately") == null || Boolean.parseBoolean(String.valueOf(payload.get("payImmediately")));
            String authorName = extractAuthorName(auth);

            Map<String, Object> invoice = killBillClient.createInvoiceAndCharge(
                    clientUuid,
                    description,
                    amount,
                    currency,
                    payImmediately,
                    appointmentUuid,
                    authorName
            );
            String invoiceId = (String) invoice.get("invoiceId");

            Map<String, Object> invoiceGl = fineractClient.postJournalEntry(
                    "1200",
                    "4100",
                    amount,
                    currency,
                    invoiceId,
                    "Fakturering af session: " + description
            );

            String settlementJournalEntryId = null;
            if (payImmediately) {
                Map<String, Object> paymentGl = fineractClient.postJournalEntry(
                        "1100",
                        "1200",
                        amount,
                        currency,
                        invoiceId,
                        "Betaling af faktura " + invoiceId
                );
                settlementJournalEntryId = (String) paymentGl.get("journalEntryId");
            }

            String markdown = "- **Kill Bill Faktura:** `" + invoiceId + "` (" + invoice.get("status") + ")\n"
                    + "- **Beløb:** " + amount + " " + currency + "\n"
                    + "- **Fineract Bogføring:** `" + invoiceGl.get("journalEntryId") + "` (Debet 1200 / Kredit 4100)";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "SESSION_INVOICE",
                    description + " - " + amount + " " + currency,
                    markdown,
                    authorName,
                    clinicalCommitHash
            );
            invoiceCommitHashes.put(invoiceId, commitHash);

            Map<String, Object> response = new LinkedHashMap<>(invoice);
            response.put("journalEntryId", invoiceGl.get("journalEntryId"));
            response.put("settlementJournalEntryId", settlementJournalEntryId);
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> refundInvoice(String invoiceId, Map<String, Object> payload, Authentication auth) {
            requireTherapist(auth);
            Map<String, Object> existing = killBillClient.getInvoice(invoiceId);
            ErrorCode.NOT_FOUND.require(existing != null, "Invoice not found: " + invoiceId);

            String previousStatus = (String) existing.get("status");
            UUID clientUuid = UUID.fromString((String) existing.get("clientUuid"));
            String currency = (String) existing.get("currency");
            BigDecimal originalAmount = (BigDecimal) existing.get("amount");
            BigDecimal refundAmount = payload.get("amount") != null
                    ? parsePositiveAmount(payload.get("amount"), "amount")
                    : originalAmount;
            String reason = parseString(payload.get("reason"), "Aflyst session / Kreditnota");
            String authorName = extractAuthorName(auth);

            Map<String, Object> updatedInvoice = killBillClient.refundInvoice(invoiceId, refundAmount, reason, authorName);
            String creditGlAccount = "PAID".equals(previousStatus) ? "1100" : "1200";

            Map<String, Object> reversalGl = fineractClient.postJournalEntry(
                    "4100",
                    creditGlAccount,
                    refundAmount,
                    currency,
                    (String) updatedInvoice.get("creditNoteId"),
                    "Kreditnota / Refundering for " + invoiceId + ": " + reason
            );

            String originalCommitHash = invoiceCommitHashes.get(invoiceId);
            String markdown = "- **Kreditnota ID:** `" + updatedInvoice.get("creditNoteId") + "`\n"
                    + "- **Oprindelig Faktura:** `" + invoiceId + "`\n"
                    + "- **Refunderet Beløb:** " + refundAmount + " " + currency + "\n"
                    + "- **Fineract Modpostering:** `" + reversalGl.get("journalEntryId") + "` (Debet 4100 / Kredit " + creditGlAccount + ")";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "CREDIT_NOTE",
                    "Kreditnota for " + invoiceId + " (" + refundAmount + " " + currency + ")",
                    markdown,
                    authorName,
                    originalCommitHash
            );

            Map<String, Object> response = new LinkedHashMap<>(updatedInvoice);
            response.put("reversalJournalEntryId", reversalGl.get("journalEntryId"));
            response.put("referencedInvoiceCommitHash", originalCommitHash);
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> topUpPrepaidCard(Map<String, Object> payload, Authentication auth) {
            UUID clientUuid = parseUuid(payload.get("clientUuid"), "clientUuid");
            requireClientSelfOrTherapist(clientUuid, auth);

            BigDecimal amount = parsePositiveAmount(payload.get("amount"), "amount");
            int clips = Integer.parseInt(String.valueOf(payload.getOrDefault("clips", 5)));
            String currency = parseString(payload.get("currency"), "DKK");
            String authorName = extractAuthorName(auth);

            Map<String, Object> invoice = killBillClient.createInvoiceAndCharge(
                    clientUuid,
                    "Klippekort optankning (" + clips + " klip)",
                    amount,
                    currency,
                    true,
                    null,
                    authorName
            );
            String invoiceId = (String) invoice.get("invoiceId");

            Map<String, Object> savings = fineractClient.depositToSavings(
                    clientUuid,
                    amount,
                    clips,
                    currency,
                    invoiceId
            );

            String markdown = "- **Fineract Klippekort Konto:** `" + savings.get("savingsAccountId") + "`\n"
                    + "- **Kill Bill Faktura:** `" + invoiceId + "`\n"
                    + "- **Tilføjede Klip:** " + clips + " (Ny saldo: " + savings.get("remainingClips") + " klip / " + savings.get("balance") + " " + currency + ")\n"
                    + "- **Fineract Bogføring:** `" + savings.get("journalEntryId") + "` (Debet 1100 / Kredit 2100)";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "PREPAID_TOPUP",
                    "Klippekort optankning +" + clips + " klip (" + amount + " " + currency + ")",
                    markdown,
                    authorName,
                    null
            );
            invoiceCommitHashes.put(invoiceId, commitHash);

            Map<String, Object> response = new LinkedHashMap<>(savings);
            response.put("invoiceId", invoiceId);
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> consumePrepaidClip(Map<String, Object> payload, Authentication auth) {
            UUID clientUuid = parseUuid(payload.get("clientUuid"), "clientUuid");
            requireClientSelfOrTherapist(clientUuid, auth);
            int clipsToUse = Integer.parseInt(String.valueOf(payload.getOrDefault("clips", 1)));
            String appointmentUuid = parseString(payload.get("appointmentUuid"), "SESSION");
            String clinicalCommitHash = parseString(payload.get("clinicalCommitHash"), appointmentUuid);
            String authorName = extractAuthorName(auth);

            Map<String, Object> savings;
            try {
                savings = fineractClient.withdrawFromSavings(clientUuid, clipsToUse, appointmentUuid);
            } catch (IllegalStateException e) {
                throw ErrorCode.BAD_REQUEST.toException(e.getMessage());
            }

            String markdown = "- **Fineract Klippekort Konto:** `" + savings.get("savingsAccountId") + "`\n"
                    + "- **Forbrugte Klip:** " + clipsToUse + " (Rest: " + savings.get("remainingClips") + " klip)\n"
                    + "- **Bogført Omsætning:** " + savings.get("deductedAmount") + " " + savings.get("currency") + "\n"
                    + "- **Fineract Bogføring:** `" + savings.get("journalEntryId") + "` (Debet 2100 / Kredit 4100)";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "PREPAID_CONSUME",
                    "Klippekort træk -" + clipsToUse + " klip for aftale " + appointmentUuid,
                    markdown,
                    authorName,
                    clinicalCommitHash
            );

            Map<String, Object> response = new LinkedHashMap<>(savings);
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> createSubscription(Map<String, Object> payload, Authentication auth) {
            UUID clientUuid = parseUuid(payload.get("clientUuid"), "clientUuid");
            requireClientSelfOrTherapist(clientUuid, auth);

            String planName = parseString(payload.get("planName"), "monthly-therapy-care");
            BigDecimal monthlyAmount = parsePositiveAmount(payload.get("monthlyAmount"), "monthlyAmount");
            String currency = parseString(payload.get("currency"), "DKK");
            String authorName = extractAuthorName(auth);

            Map<String, Object> subscription = killBillClient.createSubscription(
                    clientUuid,
                    planName,
                    monthlyAmount,
                    currency,
                    authorName
            );
            String initialInvoiceId = (String) subscription.get("initialInvoiceId");

            Map<String, Object> glEntry = fineractClient.postJournalEntry(
                    "1100",
                    "4200",
                    monthlyAmount,
                    currency,
                    initialInvoiceId,
                    "Abonnementsoprettelse (" + planName + ")"
            );

            String markdown = "- **Kill Bill Abonnement:** `" + subscription.get("subscriptionId") + "` (" + planName + ")\n"
                    + "- **Månedligt Beløb:** " + monthlyAmount + " " + currency + "\n"
                    + "- **Første Faktura:** `" + initialInvoiceId + "`\n"
                    + "- **Fineract Bogføring:** `" + glEntry.get("journalEntryId") + "` (Debet 1100 / Kredit 4200)";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "SUBSCRIPTION_CREATED",
                    "Abonnement oprettet: " + planName + " (" + monthlyAmount + " " + currency + "/md)",
                    markdown,
                    authorName,
                    null
            );
            invoiceCommitHashes.put(initialInvoiceId, commitHash);

            Map<String, Object> response = new LinkedHashMap<>(subscription);
            response.put("journalEntryId", glEntry.get("journalEntryId"));
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> createInstallmentPlan(Map<String, Object> payload, Authentication auth) {
            requireTherapist(auth);
            UUID clientUuid = parseUuid(payload.get("clientUuid"), "clientUuid");
            String description = parseString(payload.get("description"), "Traumebehandlingsforløb");
            BigDecimal principalAmount = parsePositiveAmount(payload.get("principalAmount"), "principalAmount");
            int installmentsCount = Integer.parseInt(String.valueOf(payload.getOrDefault("installmentsCount", 4)));
            String currency = parseString(payload.get("currency"), "DKK");
            String authorName = extractAuthorName(auth);

            Map<String, Object> loan = fineractClient.createInstallmentLoan(
                    clientUuid,
                    description,
                    principalAmount,
                    installmentsCount,
                    currency
            );

            String markdown = "- **Fineract Låne/Afdrags-ID:** `" + loan.get("loanId") + "`\n"
                    + "- **Hovedstol:** " + principalAmount + " " + currency + " over " + installmentsCount + " rater á " + loan.get("installmentAmount") + " " + currency + "\n"
                    + "- **Fineract Bogføring:** `" + loan.get("journalEntryId") + "` (Debet 1200 / Kredit 4100)";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "INSTALLMENT_PLAN_CREATED",
                    "Afdragsordning oprettet: " + description + " (" + principalAmount + " " + currency + ")",
                    markdown,
                    authorName,
                    null
            );

            Map<String, Object> response = new LinkedHashMap<>(loan);
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> repayInstallment(String loanId, Map<String, Object> payload, Authentication auth) {
            Map<String, Object> existingLoan = fineractClient.getLoan(loanId);
            ErrorCode.NOT_FOUND.require(existingLoan != null, "Installment plan not found: " + loanId);

            UUID clientUuid = UUID.fromString((String) existingLoan.get("clientUuid"));
            requireClientSelfOrTherapist(clientUuid, auth);

            BigDecimal customAmount = payload.get("amount") != null
                    ? parsePositiveAmount(payload.get("amount"), "amount")
                    : null;
            BigDecimal effectiveAmount = customAmount != null
                    ? customAmount
                    : (BigDecimal) existingLoan.get("installmentAmount");
            String currency = (String) existingLoan.get("currency");
            String authorName = extractAuthorName(auth);

            Map<String, Object> invoice = killBillClient.createInvoiceAndCharge(
                    clientUuid,
                    "Afdrag på " + loanId,
                    effectiveAmount,
                    currency,
                    true,
                    null,
                    authorName
            );
            String invoiceId = (String) invoice.get("invoiceId");

            Map<String, Object> updatedLoan = fineractClient.repayInstallmentLoan(loanId, effectiveAmount, invoiceId);

            String markdown = "- **Fineract Låne/Afdrags-ID:** `" + loanId + "`\n"
                    + "- **Kill Bill Rate-Faktura:** `" + invoiceId + "`\n"
                    + "- **Betalt Afdrag:** " + updatedLoan.get("paidAmount") + " " + currency
                    + " (Restgæld: " + updatedLoan.get("remainingPrincipal") + " " + currency + ")\n"
                    + "- **Fineract Bogføring:** `" + updatedLoan.get("repaymentJournalEntryId") + "` (Debet 1100 / Kredit 1200)";

            String commitHash = financeGitService.commitFinancialVoucher(
                    clientUuid,
                    "INSTALLMENT_REPAYMENT",
                    "Afdrag betalt på " + loanId + " (" + updatedLoan.get("paidAmount") + " " + currency + ")",
                    markdown,
                    authorName,
                    null
            );
            invoiceCommitHashes.put(invoiceId, commitHash);

            Map<String, Object> response = new LinkedHashMap<>(updatedLoan);
            response.put("invoiceId", invoiceId);
            response.put("gitCommitHash", commitHash);
            return response;
        }

        public Map<String, Object> getClientFinanceOverview(UUID clientUuid, Authentication auth) {
            requireClientSelfOrTherapist(clientUuid, auth);

            Map<String, Object> overview = new LinkedHashMap<>();
            overview.put("clientUuid", clientUuid.toString());
            overview.put("invoices", killBillClient.getClientInvoices(clientUuid));
            overview.put("prepaidSavings", fineractClient.getOrCreateSavingsAccount(clientUuid, "DKK"));
            overview.put("subscriptions", killBillClient.getClientSubscriptions(clientUuid));
            overview.put("installmentPlans", fineractClient.getClientLoans(clientUuid));
            overview.put("gitHistory", financeGitService.getClientFinanceHistory(clientUuid));
            return overview;
        }

        public Map<String, Object> getGeneralLedgerSummary(Authentication auth) {
            requireTherapist(auth);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("chartOfAccounts", fineractClient.getChartOfAccounts());
            summary.put("journalEntries", fineractClient.getJournalEntries());
            summary.put("gitHistory", financeGitService.getGeneralLedgerHistory());
            return summary;
        }

        public Map<String, Object> getCommitSnapshot(String commitHash, Authentication auth) {
            UUID callerUuid = extractUserUuid(auth);
            Map<String, Object> snapshot = financeGitService.getCommitSnapshot(commitHash);
            ErrorCode.NOT_FOUND.require(snapshot != null, "Finance commit not found: " + commitHash);

            if (!isTherapist(auth)) {
                String glSnapshot = String.valueOf(snapshot.get("generalLedgerSnapshot"));
                ErrorCode.FORBIDDEN.require(
                        glSnapshot.contains(callerUuid.toString()),
                        "Client is not permitted to inspect another client's voucher commit"
                );
            }
            return snapshot;
        }

        private void requireTherapist(Authentication auth) {
            extractUserUuid(auth);
            ErrorCode.FORBIDDEN.require(
                    isTherapist(auth),
                    "Therapist role is required for this financial operation"
            );
        }

        private void requireClientSelfOrTherapist(UUID targetClientUuid, Authentication auth) {
            UUID callerUuid = extractUserUuid(auth);
            ErrorCode.FORBIDDEN.require(
                    isTherapist(auth) || callerUuid.equals(targetClientUuid),
                    "Access denied to another client's financial records"
            );
        }

        private boolean isTherapist(Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String role = authority.getAuthority();
                if ("ROLE_therapist".equalsIgnoreCase(role) || "ROLE_admin".equalsIgnoreCase(role)) {
                    return true;
                }
            }
            return false;
        }

        private UUID extractUserUuid(Authentication auth) {
            if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt) || jwt.getSubject() == null) {
                throw ErrorCode.UNAUTHORIZED.toException("Valid authenticated Keycloak JWT is required");
            }
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException e) {
                throw ErrorCode.UNAUTHORIZED.toException("Subject is not a valid UUID");
            }
        }

        private String extractAuthorName(Authentication auth) {
            Jwt jwt = (Jwt) auth.getPrincipal();
            String name = jwt.getClaimAsString("name");
            return (name != null && !name.isBlank()) ? name : "Klinik Økonomi";
        }

        private UUID parseUuid(Object raw, String fieldName) {
            ErrorCode.BAD_REQUEST.require(
                    raw != null && !String.valueOf(raw).isBlank(),
                    "Missing required UUID field: " + fieldName
            );
            try {
                return UUID.fromString(String.valueOf(raw).trim());
            } catch (IllegalArgumentException e) {
                throw ErrorCode.BAD_REQUEST.toException("Invalid UUID for field: " + fieldName);
            }
        }

        private BigDecimal parsePositiveAmount(Object raw, String fieldName) {
            ErrorCode.BAD_REQUEST.require(raw != null, "Missing amount field: " + fieldName);
            BigDecimal value = new BigDecimal(String.valueOf(raw));
            ErrorCode.BAD_REQUEST.require(
                    value.compareTo(BigDecimal.ZERO) > 0,
                    "Amount must be positive: " + fieldName
            );
            return value;
        }

        private String parseString(Object raw, String fallback) {
            if (raw == null) {
                return fallback;
            }
            String text = String.valueOf(raw).trim();
            return text.isEmpty() ? fallback : text;
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    @RestController
    @RequestMapping("/api/v1/finance")
    @CrossOrigin(origins = "*")
    static class FinanceController {

        private final FinanceService financeService;

        FinanceController(FinanceService financeService) {
            this.financeService = financeService;
        }

        @GetMapping("/client/{clientUuid}/overview")
        public ResponseEntity<Map<String, Object>> getClientFinanceOverview(
                @PathVariable UUID clientUuid,
                Authentication auth
        ) {
            return ResponseEntity.ok(financeService.getClientFinanceOverview(clientUuid, auth));
        }

        @PostMapping("/invoices/session")
        public ResponseEntity<Map<String, Object>> billSingleSession(
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(financeService.billSingleSession(payload, auth));
        }

        @PostMapping("/invoices/{invoiceId}/refund")
        public ResponseEntity<Map<String, Object>> refundInvoice(
                @PathVariable String invoiceId,
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.ok(financeService.refundInvoice(invoiceId, payload, auth));
        }

        @PostMapping("/prepaid/topup")
        public ResponseEntity<Map<String, Object>> topUpPrepaidCard(
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(financeService.topUpPrepaidCard(payload, auth));
        }

        @PostMapping("/prepaid/consume")
        public ResponseEntity<Map<String, Object>> consumePrepaidClip(
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.ok(financeService.consumePrepaidClip(payload, auth));
        }

        @PostMapping("/subscriptions")
        public ResponseEntity<Map<String, Object>> createSubscription(
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(financeService.createSubscription(payload, auth));
        }

        @PostMapping("/installment-plans")
        public ResponseEntity<Map<String, Object>> createInstallmentPlan(
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(financeService.createInstallmentPlan(payload, auth));
        }

        @PostMapping("/installment-plans/{planId}/repay")
        public ResponseEntity<Map<String, Object>> repayInstallment(
                @PathVariable String planId,
                @RequestBody Map<String, Object> payload,
                Authentication auth
        ) {
            return ResponseEntity.ok(financeService.repayInstallment(planId, payload, auth));
        }

        @GetMapping("/ledger/summary")
        public ResponseEntity<Map<String, Object>> getGeneralLedgerSummary(Authentication auth) {
            return ResponseEntity.ok(financeService.getGeneralLedgerSummary(auth));
        }

        @GetMapping("/ledger/commits/{commitHash}")
        public ResponseEntity<Map<String, Object>> getCommitSnapshot(
                @PathVariable String commitHash,
                Authentication auth
        ) {
            return ResponseEntity.ok(financeService.getCommitSnapshot(commitHash, auth));
        }
    }
}
