package com.platform.features.psychotraumatology;

import com.platform.core.DomainScenarioState;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTestRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

public class PsychotraumatologySteps {

    private final PsychotraumatologyTestRepository testRepository;
    private final DomainScenarioState state;

    public PsychotraumatologySteps(
            PsychotraumatologyTestRepository testRepository,
            DomainScenarioState state
    ) {
        this.testRepository = testRepository;
        this.state = state;
    }

    @Before(order = 20400)
    public void cleanPsychotraumatologyTests() {
        testRepository.deleteAll();
    }

    @When("therapist {string} submits a PCL-5 trauma assessment for {string} with total score {int} and severity {string}")
    public void therapistSubmitsPcl5Assessment(
            String therapistUsername,
            String clientUsername,
            int totalScore,
            String severity
    ) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("therapistUuid", therapistUuid);
        payload.put("testType", "PCL-5");
        payload.put("score", totalScore);
        payload.put("severity", severity);
        payload.put("answersJson", "{\"totalScore\":" + totalScore + "}");

        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/psychotraumatology/tests");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @Then("the PCL-5 assessment should be stored with score {int} and severity {string}")
    public void pcl5AssessmentStored(int expectedScore, String expectedSeverity) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(response.jsonPath().getInt("score"), is(expectedScore));
        assertThat(response.jsonPath().getString("severity"), is(expectedSeverity));
    }

    @Then("client {string} retrieving their PCL-5 assessments should include score {int}")
    public void clientRetrievingPcl5IncludesScore(String clientUsername, int expectedScore) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/psychotraumatology/tests/client/{uuid}", clientUuid);
        assertThat(response.statusCode(), is(200));
        List<Integer> scores = response.jsonPath().getList("score", Integer.class);
        assertThat(scores, hasItem(expectedScore));
    }

    @When("client {string} attempts to retrieve PCL-5 assessments for {string}")
    public void clientAttemptsRetrieveOthersPcl5(String actorClientUsername, String targetClientUsername) {
        UUID targetUuid = state.getUserIdsByUsername().get(targetClientUsername);
        Response response = state.requestAs(actorClientUsername, "CLIENT")
                .get("/api/v1/psychotraumatology/tests/client/{uuid}", targetUuid);
        state.setLastResponse(response);
    }
}
