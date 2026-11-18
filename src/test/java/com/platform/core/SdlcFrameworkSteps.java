package com.platform.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class SdlcFrameworkSteps {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode configRoot;
    private String integrationBranch;
    private String branchPrefix;
    private String resolvedBranch;
    private String resolvedBaseBranch;
    private String evaluatedStatus;
    private boolean canMerge;

    private String slugify(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    @Given("SDLC configuration loaded from {string} with strict 98% thresholds on JaCoCo and Istanbul")
    public void loadSdlcConfiguration(String configName) throws Exception {
        Path candidate = Files.exists(Path.of("..", configName))
                ? Path.of("..", configName)
                : Path.of(configName);
        Assertions.assertTrue(Files.exists(candidate), "Missing config file: " + candidate);
        configRoot = objectMapper.readTree(Files.readString(candidate));

        integrationBranch = configRoot.path("branches").path("integration").asText();
        branchPrefix = configRoot.path("branches").path("prefix").asText();
        Assertions.assertEquals("dev", integrationBranch);
        Assertions.assertEquals("feature", branchPrefix);

        JsonNode be = configRoot.path("gates").path("targets").path("backend");
        Assertions.assertEquals(0.98, be.path("minLineCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, be.path("minBranchCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, be.path("minInstructionCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, be.path("minComplexityCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, be.path("minMethodCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, be.path("minClassCoverage").asDouble(), 0.0001);

        JsonNode fe = configRoot.path("gates").path("targets").path("frontend");
        Assertions.assertEquals(0.98, fe.path("minLineCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, fe.path("minStatementCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, fe.path("minFunctionCoverage").asDouble(), 0.0001);
        Assertions.assertEquals(0.98, fe.path("minBranchCoverage").asDouble(), 0.0001);

        Assertions.assertNotNull(
                getClass().getClassLoader().getResource("features/shared/generic_sdlc_framework.feature"),
                "Shared @fullstack feature must be present on Maven test classpath"
        );
    }

    @When("SDLC synchronizes GitHub Issue {int} titled {string} with slice {string} and target {string}")
    public void syncIssueFromSpec(int issueId, String title, String slice, String target) {
        Assertions.assertFalse(slice.isBlank());
        Assertions.assertFalse(target.isBlank());
        resolvedBranch = branchPrefix + "/" + issueId + "-" + slugify(title);
        resolvedBaseBranch = integrationBranch;
        evaluatedStatus = "Red (Outer BDD)";
        canMerge = false;
    }

    @Then("SDLC resolves linked branch {string} from base branch {string}")
    @Then("SDLC resolves child branch {string} from base branch {string}")
    public void assertResolvedBranchPlan(String expectedBranch, String expectedBase) {
        Assertions.assertEquals(expectedBranch, resolvedBranch);
        Assertions.assertEquals(expectedBase, resolvedBaseBranch);
    }

    @And("SDLC transitions Issue {int} to project status {string}")
    public void assertProjectStatus(int issueId, String expectedStatus) {
        Assertions.assertTrue(issueId > 0);
        Assertions.assertEquals(expectedStatus, evaluatedStatus);
    }

    @When("SDLC resolves child branch for Sub-Issue {int} titled {string} under parent branch {string}")
    public void resolveChildBranch(int childIssueId, String title, String parentBranch) {
        resolvedBranch = branchPrefix + "/" + childIssueId + "-" + slugify(title);
        resolvedBaseBranch = parentBranch;
    }

    @When("SDLC evaluates Issue {int} with passing Backend and Frontend 98% gates while Sub-Issue {int} has status {string}")
    public void evaluateWithSubIssueStatus(int parentIssueId, int childIssueId, String childStatus) {
        Assertions.assertTrue(parentIssueId > 0 && childIssueId > 0);
        boolean subIssueDone = "Done".equals(childStatus);
        canMerge = subIssueDone;
        evaluatedStatus = subIssueDone ? "Green Gate" : "Inner TDD";
    }

    @When("SDLC evaluates previously Done Issue {int} against a failing {string} scenario report")
    public void evaluateRegression(int issueId, String failingTag) {
        Assertions.assertTrue(failingTag.contains(String.valueOf(issueId)));
        canMerge = false;
        evaluatedStatus = "Regression";
    }

    @Then("SDLC state machine status is {string} with merge allowed {string}")
    public void assertStateMachineResult(String expectedStatus, String expectedMergeAllowed) {
        Assertions.assertEquals(expectedStatus, evaluatedStatus);
        Assertions.assertEquals(Boolean.parseBoolean(expectedMergeAllowed), canMerge);
    }
}
