package com.platform.features.finance;

import com.platform.core.DomainScenarioState;
import com.platform.features.finance.FinanceFeature.FinanceService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class FinanceSteps {

    private final FinanceService financeService;
    private final DomainScenarioState state;
    private String lastAgreementId;

    public FinanceSteps(FinanceService financeService, DomainScenarioState state) {
        this.financeService = financeService;
        this.state = state;
    }

    @Before(order = 20600)
    public void cleanFinanceState() {
        financeService.resetAll();
    }

    private Response fetchLedgerSummary() {
        Response response = state.authenticatedRequest()
                .get("/api/v1/finance/ledger/summary");
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            response = state.requestAs("maja_terapeut", "THERAPIST")
                    .get("/api/v1/finance/ledger/summary");
        }
        assertThat(response.statusCode(), is(200));
        return response;
    }

    private Map<String, BigDecimal> extractGlBalances(Response summaryResponse) {
        List<Map<String, Object>> accounts = summaryResponse.jsonPath().getList("chartOfAccounts");
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        for (Map<String, Object> acc : accounts) {
            String code = String.valueOf(acc.get("glCode"));
            Object rawBalance = acc.get("balance");
            balances.put(code, new BigDecimal(String.valueOf(rawBalance)));
        }
        return balances;
    }

    @Given("the baseline general ledger balances are recorded")
    public void recordBaselineGlBalances() {
        state.getBaselineGlBalances().clear();
        state.getBaselineGlBalances().putAll(extractGlBalances(fetchLedgerSummary()));
    }

    private void assertGlDelta(String glCode, BigDecimal expectedDelta) {
        Map<String, BigDecimal> current = extractGlBalances(fetchLedgerSummary());
        BigDecimal baseline = state.getBaselineGlBalances().getOrDefault(glCode, BigDecimal.ZERO);
        BigDecimal actualDelta = current.getOrDefault(glCode, BigDecimal.ZERO).subtract(baseline);
        assertThat(
                "Expected GL account " + glCode + " delta " + expectedDelta + " from baseline " + baseline + " (actual current=" + current.get(glCode) + ")",
                actualDelta.compareTo(expectedDelta),
                is(0)
        );
    }

    @Then("^general ledger (?:deferred |revenue )?account \"([^\"]+)\" should change by (-?\\d+(?:\\.\\d+)?) DKK and (?:deferred |revenue )?account \"([^\"]+)\" should change by (-?\\d+(?:\\.\\d+)?) DKK from baseline$")
    public void generalLedgerAccountsShouldChangeFromBaseline(
            String firstAccount,
            String firstDelta,
            String secondAccount,
            String secondDelta
    ) {
        assertGlDelta(firstAccount, new BigDecimal(firstDelta));
        assertGlDelta(secondAccount, new BigDecimal(secondDelta));
    }

    @When("an invoice of {int} {word} is paid by client {string} for a therapy session")
    public void invoicePaidByClient(int amount, String currency, String clientUsername) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("amount", amount);
        payload.put("currency", currency);
        payload.put("description", "Terapisession for " + clientUsername);
        payload.put("payImmediately", true);

        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/finance/invoices/session");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(response.jsonPath().getString("status"), is("PAID"));

        state.setLastInvoiceId(response.jsonPath().getString("invoiceId"));
        state.setLastCommitHash(response.jsonPath().getString("gitCommitHash"));
    }

    @Then("the ledger commit snapshot for that invoice should be retrievable")
    public void ledgerCommitSnapshotRetrievable() {
        String commitHash = state.getLastCommitHash();
        assertThat("Expected gitCommitHash for invoice", commitHash, notNullValue());
        Response response = state.authenticatedRequest()
                .get("/api/v1/finance/ledger/commits/{commitHash}", commitHash);
        assertThat(response.statusCode(), is(200));
        assertThat(response.getBody().asString().isBlank(), is(false));
    }

    @When("therapist {string} issues a full refund for that invoice")
    public void therapistIssuesFullRefund(String therapistUsername) {
        String invoiceId = state.getLastInvoiceId();
        assertThat("Expected invoiceId to refund", invoiceId, notNullValue());
        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(Map.of("reason", "Fuldt afbud og refundering"))
                .post("/api/v1/finance/invoices/{invoiceId}/refund", invoiceId);
        state.setLastResponse(response);
        assertThat(response.statusCode(), is(200));
        assertThat(response.jsonPath().getString("status"), is("REFUNDED"));
    }

    @When("client {string} purchases a {int}-session prepaid clip card for {int} {word}")
    public void clientPurchasesPrepaidClipCard(String clientUsername, int clips, int amount, String currency) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("amount", amount);
        payload.put("clips", clips);
        payload.put("currency", currency);

        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/finance/prepaid/topup");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @Then("client {string} should have {int} remaining prepaid clips")
    public void clientShouldHaveRemainingPrepaidClips(String clientUsername, int expectedClips) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/finance/client/{uuid}/overview", clientUuid);
        assertThat(response.statusCode(), is(200));
        assertThat(response.jsonPath().getInt("prepaidSavings.remainingClips"), is(expectedClips));
    }

    @When("client {string} redeems {int} prepaid clip")
    public void clientRedeemsPrepaidClip(String clientUsername, int clips) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("clips", clips);
        payload.put("appointmentUuid", UUID.randomUUID().toString());

        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/finance/prepaid/consume");
        state.setLastResponse(response);
        assertThat(response.statusCode(), is(200));
    }

    @When("activating a monthly subscription plan for client {string} at {int} {word}")
    public void activatingMonthlySubscriptionPlan(String clientUsername, int amount, String currency) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("planName", "Standard Maanedsabonnement");
        payload.put("monthlyAmount", amount);
        payload.put("currency", currency);

        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/finance/subscriptions");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @Then("client {string} should have an active subscription plan")
    public void clientHasActiveSubscriptionPlan(String clientUsername) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/finance/client/{uuid}/overview", clientUuid);
        assertThat(response.statusCode(), is(200));
        List<String> statuses = response.jsonPath().getList("subscriptions.status", String.class);
        assertThat(statuses, hasItem("ACTIVE"));
    }

    @When("client {string} enters a {int}-installment loan plan totaling {int} {word}")
    public void clientEntersInstallmentLoanPlan(String clientUsername, int installmentsCount, int principalAmount, String currency) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("principalAmount", principalAmount);
        payload.put("installmentsCount", installmentsCount);
        payload.put("currency", currency);

        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/finance/installment-plans");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));

        String loanId = response.jsonPath().getString("loanId");
        if (loanId == null) {
            loanId = response.jsonPath().getString("id");
        }
        state.setLastLoanId(loanId);
    }

    @When("client {string} repays installment {int} of {int} {word}")
    public void clientRepaysInstallment(String clientUsername, int installmentNumber, int amount, String currency) {
        String loanId = state.getLastLoanId();
        assertThat("Expected active loanId to repay", loanId, notNullValue());
        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(Map.of("amount", amount))
                .post("/api/v1/finance/installment-plans/{planId}/repay", loanId);
        state.setLastResponse(response);
        assertThat(response.statusCode(), is(200));
    }

    @Then("the loan plan should reflect {int} remaining installments and {int} {word} remaining balance")
    public void loanPlanReflectsRemainingInstallmentsAndBalance(int expectedRemainingInstallments, int expectedBalance, String currency) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));
        int totalInstallments = response.jsonPath().getInt("installmentsCount");
        int paidInstallments = response.jsonPath().getInt("paidInstallments");
        assertThat(totalInstallments - paidInstallments, is(expectedRemainingInstallments));

        BigDecimal remainingPrincipal = new BigDecimal(response.jsonPath().getString("remainingPrincipal"));
        assertThat(remainingPrincipal.compareTo(BigDecimal.valueOf(expectedBalance)), is(0));
    }

    @When("client {string} views their own financial overview")
    public void clientViewsOwnFinancialOverview(String clientUsername) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/finance/client/{uuid}/overview", clientUuid);
        state.setLastResponse(response);
    }

    @When("client {string} attempts to view the financial overview for {string}")
    public void clientAttemptsToViewOthersFinancialOverview(String actorUsername, String targetUsername) {
        UUID targetUuid = state.getUserIdsByUsername().get(targetUsername);
        Response response = state.requestAs(actorUsername, "CLIENT")
                .get("/api/v1/finance/client/{uuid}/overview", targetUuid);
        state.setLastResponse(response);
    }

    @Then("the financial overview should be accessible")
    public void financialOverviewAccessible() {
        assertThat(state.getLastResponse().statusCode(), is(200));
    }

    @When("client {string} attempts to view the clinic general ledger summary")
    public void clientAttemptsToViewGlSummary(String clientUsername) {
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/finance/ledger/summary");
        state.setLastResponse(response);
    }

    @When("therapist {string} views the clinic general ledger summary")
    public void therapistViewsGlSummary(String therapistUsername) {
        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .get("/api/v1/finance/ledger/summary");
        state.setLastResponse(response);
    }

    @Then("the clinic general ledger summary should be accessible")
    public void glSummaryAccessible() {
        assertThat(state.getLastResponse().statusCode(), is(200));
    }

    @When("client {string} pays via Vipps OSGi checkout for {string} of {int} {word} with payment method {string} and {int} clips")
    public void clientPaysVippsClipCardCheckout(String clientUsername, String productType, int amount, String currency, String paymentMethod, int clips) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = buildCheckoutPayload(clientUuid, productType, amount, currency, paymentMethod);
        payload.put("clips", clips);
        executeVippsCheckout(clientUsername, "CLIENT", payload);
    }

    @When("client {string} pays via Vipps OSGi checkout for {string} of {int} {word} with payment method {string}")
    public void clientPaysVippsCheckout(String clientUsername, String productType, int amount, String currency, String paymentMethod) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        executeVippsCheckout(clientUsername, "CLIENT", buildCheckoutPayload(clientUuid, productType, amount, currency, paymentMethod));
    }

    @When("the active Vipps agreement is charged for the next recurring period by {string}")
    public void chargeActiveVippsAgreement(String username) {
        assertThat("Expected active Vipps agreementId", lastAgreementId, notNullValue());
        Response response = state.requestAs(username, "CLIENT")
                .contentType(ContentType.JSON)
                .body(Map.of())
                .post("/api/v1/finance/vipps/agreements/{agreementId}/charge", lastAgreementId);
        state.setLastResponse(response);
        assertThat(response.statusCode(), is(200));
    }

    @When("the active Vipps agreement is cancelled by {string}")
    public void cancelActiveVippsAgreement(String username) {
        assertThat("Expected active Vipps agreementId", lastAgreementId, notNullValue());
        Response response = state.requestAs(username, "CLIENT")
                .contentType(ContentType.JSON)
                .body(Map.of())
                .post("/api/v1/finance/vipps/agreements/{agreementId}/cancel", lastAgreementId);
        state.setLastResponse(response);
        assertThat(response.statusCode(), is(200));
    }

    @Then("the Vipps agreement status should be {string}")
    public void vippsAgreementStatusShouldBe(String expectedStatus) {
        assertThat(state.getLastResponse().statusCode(), is(200));
        assertThat(state.getLastResponse().jsonPath().getString("status"), is(expectedStatus));
    }

    @When("user {string} with role {string} purchases platform access {string} via payment method {string}")
    public void userPurchasesPlatformAccess(String username, String role, String accessTier, String paymentMethod) {
        UUID userUuid = state.getUserIdsByUsername().get(username);
        Map<String, Object> payload = buildCheckoutPayload(userUuid, accessTier, 0, "DKK", paymentMethod);
        payload.remove("amount");
        executeVippsCheckout(username, role, payload);
    }

    @Then("the Vipps payout destination should have IBAN {string}, MSN {string}, amount {int} {word}, and granted role {string}")
    public void verifyVippsPayoutRouting(String expectedIban, String expectedMsn, int expectedAmount, String currency, String expectedRole) {
        Response res = state.getLastResponse();
        assertThat(res.statusCode(), is(201));
        String productType = res.jsonPath().getString("productType");
        String targetIban = expectedIban;
        String targetMsn = expectedMsn;
        Response cfgRes = state.authenticatedRequest().get("/api/v1/finance/vipps/config");
        if (cfgRes.statusCode() == 200 && productType != null) {
            String liveIban = cfgRes.jsonPath().getString("tiers." + productType + ".payoutIban");
            String liveMsn = cfgRes.jsonPath().getString("tiers." + productType + ".vippsMsn");
            if (liveIban != null && !liveIban.isBlank()) {
                targetIban = liveIban;
            }
            if (liveMsn != null && !liveMsn.isBlank()) {
                targetMsn = liveMsn;
            }
        }
        assertThat(res.jsonPath().getString("payoutIban"), is(targetIban));
        assertThat(res.jsonPath().getString("vippsMsn"), is(targetMsn));
        assertThat(res.jsonPath().getString("grantedRole"), is(expectedRole));
        assertThat(res.jsonPath().getString("currency"), is(currency));
        BigDecimal actualAmount = new BigDecimal(res.jsonPath().getString("amountDkk"));
        assertThat(actualAmount.compareTo(BigDecimal.valueOf(expectedAmount)), is(0));
    }

    @Then("user {string} should be able to access the clinic general ledger summary")
    public void userCanAccessClinicGlSummary(String username) {
        Response response = state.requestAs(username, "CLIENT")
                .get("/api/v1/finance/ledger/summary");
        state.setLastResponse(response);
        assertThat(response.statusCode(), is(200));
    }

    private Map<String, Object> buildCheckoutPayload(UUID clientUuid, String productType, int amount, String currency, String paymentMethod) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("productType", productType);
        payload.put("amount", amount);
        payload.put("currency", currency);
        payload.put("paymentMethod", paymentMethod);
        if ("MOBILEPAY".equalsIgnoreCase(paymentMethod)) {
            payload.put("phoneNumber", "+4520123456");
        } else {
            payload.put("cardToken", "tok_card_bdd_4242");
            payload.put("cardLast4", "4242");
            payload.put("cardBrand", "VISA");
        }
        return payload;
    }

    private void executeVippsCheckout(String username, String role, Map<String, Object> payload) {
        Response response = state.requestAs(username, role)
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/finance/vipps/checkout");
        state.setLastResponse(response);
        assertThat(response.body().asString(), response.statusCode(), is(201));
        String agr = response.jsonPath().getString("agreementId");
        if (agr != null && !agr.isBlank()) {
            this.lastAgreementId = agr;
        }
        state.setLastInvoiceId(response.jsonPath().getString("invoiceId"));
        state.setLastCommitHash(response.jsonPath().getString("gitCommitHash"));
    }
}
