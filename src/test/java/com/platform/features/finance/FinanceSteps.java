package com.platform.features.finance;

import com.platform.features.finance.FinanceFeature.FinanceService;
import com.platform.features.finance.FinanceFeature.FineractClient;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class FinanceSteps {

    @Autowired
    private FinanceService financeService;

    @Autowired
    private FineractClient fineractClient;

    private Response lastResponse;
    private String lastInvoiceId;
    private String lastInvoiceCommitHash;
    private String lastLoanId;

    @Given("the finance billing and ledger state is reset")
    public void the_finance_billing_and_ledger_state_is_reset() {
        financeService.resetAll();
        lastResponse = null;
        lastInvoiceId = null;
        lastInvoiceCommitHash = null;
        lastLoanId = null;
    }

    @When("therapist {string} \\({string}\\) bills a paid session of {double} DKK for client {string} linked to appointment {string} and clinical commit {string}")
    public void therapist_bills_a_paid_session(
            String therapistUuid,
            String therapistName,
            double amount,
            String clientUuid,
            String appointmentUuid,
            String clinicalCommit
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientUuid", clientUuid);
        body.put("amount", BigDecimal.valueOf(amount));
        body.put("currency", "DKK");
        body.put("description", "Traumeterapi Session");
        body.put("appointmentUuid", appointmentUuid);
        body.put("clinicalCommitHash", clinicalCommit);
        body.put("payImmediately", true);

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", therapistUuid)
                .header("X-Auth-Full-Name", therapistName)
                .header("X-Auth-Roles", "therapist")
                .body(body)
                .when()
                .post("/api/v1/finance/invoices/session");

        if (lastResponse.statusCode() == 201) {
            lastInvoiceId = lastResponse.jsonPath().getString("invoiceId");
            lastInvoiceCommitHash = lastResponse.jsonPath().getString("gitCommitHash");
        }
    }

    @Then("the finance response status should be {int}")
    public void the_finance_response_status_should_be(int expectedStatus) {
        Assertions.assertNotNull(lastResponse);
        Assertions.assertEquals(expectedStatus, lastResponse.statusCode(), lastResponse.getBody().asString());
    }

    @Then("the invoice status should be {string} with a valid JGit commit hash")
    public void the_invoice_status_should_be_with_valid_commit_hash(String expectedStatus) {
        Assertions.assertEquals(expectedStatus, lastResponse.jsonPath().getString("status"));
        Assertions.assertNotNull(lastInvoiceCommitHash);
        Assertions.assertEquals(8, lastInvoiceCommitHash.length());
    }

    @Then("the Fineract General Ledger account {string} balance should be {double} DKK")
    public void the_fineract_gl_account_balance_should_be(String glCode, double expectedBalance) {
        List<Map<String, Object>> accounts = fineractClient.getChartOfAccounts();
        BigDecimal actual = accounts.stream()
                .filter(a -> glCode.equals(a.get("glCode")))
                .map(a -> (BigDecimal) a.get("balance"))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(0, BigDecimal.valueOf(expectedBalance).compareTo(actual),
                "Expected GL " + glCode + " balance " + expectedBalance + " but got " + actual);
    }

    @When("therapist {string} refunds the last invoice with reason {string}")
    public void therapist_refunds_the_last_invoice_with_reason(String therapistUuid, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", therapistUuid)
                .header("X-Auth-Full-Name", "Dr. Maja Lind")
                .header("X-Auth-Roles", "therapist")
                .body(body)
                .when()
                .post("/api/v1/finance/invoices/" + lastInvoiceId + "/refund");
    }

    @Then("the refunded invoice status should be {string} referencing the original invoice JGit commit hash")
    public void the_refunded_invoice_status_should_reference_original_commit(String expectedStatus) {
        Assertions.assertEquals(expectedStatus, lastResponse.jsonPath().getString("status"));
        Assertions.assertEquals(lastInvoiceCommitHash, lastResponse.jsonPath().getString("referencedInvoiceCommitHash"));
        Assertions.assertNotNull(lastResponse.jsonPath().getString("gitCommitHash"));
    }

    @Given("client {string} tops up a prepaid Klippekort with {int} clips for {double} DKK")
    public void client_tops_up_prepaid_klippekort(String clientUuid, int clips, double amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientUuid", clientUuid);
        body.put("clips", clips);
        body.put("amount", BigDecimal.valueOf(amount));
        body.put("currency", "DKK");

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", clientUuid)
                .header("X-Auth-Full-Name", "Anders Sand")
                .header("X-Auth-Roles", "client")
                .body(body)
                .when()
                .post("/api/v1/finance/prepaid/topup");
    }

    @Then("the client Fineract savings account should have {int} remaining clips and balance {double} DKK")
    public void the_client_fineract_savings_account_should_have(int expectedClips, double expectedBalance) {
        Assertions.assertEquals(expectedClips, lastResponse.jsonPath().getInt("remainingClips"));
        BigDecimal balance = new BigDecimal(lastResponse.jsonPath().getString("balance"));
        Assertions.assertEquals(0, BigDecimal.valueOf(expectedBalance).compareTo(balance));
    }

    @When("therapist {string} consumes {int} prepaid clip for client {string} on appointment {string}")
    public void therapist_consumes_prepaid_clip(String therapistUuid, int clips, String clientUuid, String appointmentUuid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientUuid", clientUuid);
        body.put("clips", clips);
        body.put("appointmentUuid", appointmentUuid);

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", therapistUuid)
                .header("X-Auth-Full-Name", "Dr. Maja Lind")
                .header("X-Auth-Roles", "therapist")
                .body(body)
                .when()
                .post("/api/v1/finance/prepaid/consume");
    }

    @When("client {string} subscribes to plan {string} at {double} DKK per month")
    public void client_subscribes_to_plan(String clientUuid, String planName, double monthlyAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientUuid", clientUuid);
        body.put("planName", planName);
        body.put("monthlyAmount", BigDecimal.valueOf(monthlyAmount));
        body.put("currency", "DKK");

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", clientUuid)
                .header("X-Auth-Full-Name", "Anders Sand")
                .header("X-Auth-Roles", "client")
                .body(body)
                .when()
                .post("/api/v1/finance/subscriptions");
    }

    @When("therapist {string} creates a {int}-installment Fineract loan plan of {double} DKK for client {string}")
    public void therapist_creates_installment_loan_plan(String therapistUuid, int installments, double principal, String clientUuid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientUuid", clientUuid);
        body.put("description", "EMDR Intensivt Forløb");
        body.put("principalAmount", BigDecimal.valueOf(principal));
        body.put("installmentsCount", installments);
        body.put("currency", "DKK");

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", therapistUuid)
                .header("X-Auth-Full-Name", "Dr. Maja Lind")
                .header("X-Auth-Roles", "therapist")
                .body(body)
                .when()
                .post("/api/v1/finance/installment-plans");

        if (lastResponse.statusCode() == 201) {
            lastLoanId = lastResponse.jsonPath().getString("loanId");
        }
    }

    @When("client {string} repays one installment on the active loan plan")
    public void client_repays_one_installment(String clientUuid) {
        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", clientUuid)
                .header("X-Auth-Full-Name", "Anders Sand")
                .header("X-Auth-Roles", "client")
                .body(Map.of())
                .when()
                .post("/api/v1/finance/installment-plans/" + lastLoanId + "/repay");
    }

    @Then("the loan remaining principal should be {double} DKK with {int} paid installment")
    public void the_loan_remaining_principal_should_be(double expectedRemaining, int expectedPaid) {
        BigDecimal actualRemaining = new BigDecimal(lastResponse.jsonPath().getString("remainingPrincipal"));
        Assertions.assertEquals(0, BigDecimal.valueOf(expectedRemaining).compareTo(actualRemaining));
        Assertions.assertEquals(expectedPaid, lastResponse.jsonPath().getInt("paidInstallments"));
    }

    @When("client {string} requests their own finance overview")
    public void client_requests_their_own_finance_overview(String clientUuid) {
        lastResponse = given()
                .header("X-Auth-User-Uuid", clientUuid)
                .header("X-Auth-Roles", "client")
                .when()
                .get("/api/v1/finance/client/" + clientUuid + "/overview");
    }

    @Then("the client finance overview should contain {int} JGit voucher commit")
    public void the_client_finance_overview_should_contain_commits(int expectedCommits) {
        List<Map<String, Object>> history = lastResponse.jsonPath().getList("gitHistory");
        Assertions.assertTrue(history.size() >= expectedCommits);
    }

    @When("client {string} attempts to request the finance overview of client {string}")
    public void client_attempts_to_request_other_client_overview(String callerUuid, String targetUuid) {
        lastResponse = given()
                .header("X-Auth-User-Uuid", callerUuid)
                .header("X-Auth-Roles", "client")
                .when()
                .get("/api/v1/finance/client/" + targetUuid + "/overview");
    }

    @When("client {string} attempts to request the clinic general ledger summary")
    public void client_attempts_to_request_general_ledger(String callerUuid) {
        lastResponse = given()
                .header("X-Auth-User-Uuid", callerUuid)
                .header("X-Auth-Roles", "client")
                .when()
                .get("/api/v1/finance/ledger/summary");
    }

    @When("therapist {string} requests the clinic general ledger summary")
    public void therapist_requests_general_ledger_summary(String therapistUuid) {
        lastResponse = given()
                .header("X-Auth-User-Uuid", therapistUuid)
                .header("X-Auth-Roles", "therapist")
                .when()
                .get("/api/v1/finance/ledger/summary");
    }
}
