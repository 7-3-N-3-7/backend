package com.platform.features.psychotraumatology;

import com.platform.core.CucumberScenarioContext;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTestRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static io.restassured.RestAssured.given;

public class PsychotraumatologySteps {

    @Autowired
    private CucumberScenarioContext context;

    @Autowired
    private PsychotraumatologyTestRepository psychotraumatologyTestRepository;

    @Given("a completed assessment test for client UUID {string}")
    public void a_completed_assessment_test_for_client_uuid(String clientUuid) {
        context.setCurrentClientUuid(clientUuid);
        context.setRequestSpec(given().header("Authorization", "Bearer valid-client-token-for-" + clientUuid));
    }

    @Given("supervising therapist UUID {string}")
    public void supervising_therapist_uuid(String therapistUuid) {
        context.setCurrentTherapistUuid(therapistUuid);
        Assertions.assertNotNull(UUID.fromString(therapistUuid));
    }

    @When("the assessment result is posted to {string} with body:")
    public void the_assessment_result_is_posted_to_with_body(String endpoint, String docString) {
        context.setLastResponse(context.getRequestSpec()
                .contentType("application/json")
                .body(docString)
                .when()
                .post(endpoint));
    }

    @Then("the assessment record in PostgreSQL must reference client UUID {string} and therapist UUID {string}")
    public void the_assessment_record_in_postgre_sql_must_reference_client_uuid_and_therapist_uuid(String expectedClientUuid, String expectedTherapistUuid) {
        UUID testId = UUID.fromString(context.getLastResponse().jsonPath().getString("id"));
        PsychotraumatologyTest savedTest = psychotraumatologyTestRepository.findById(testId)
                .orElseThrow(() -> new AssertionError("PsychotraumatologyTest must be saved in PostgreSQL"));
        Assertions.assertEquals(UUID.fromString(expectedClientUuid), savedTest.getClientUuid());
        Assertions.assertEquals(UUID.fromString(expectedTherapistUuid), savedTest.getTherapistUuid());
    }

    @Given("client A with UUID {string}")
    public void client_a_with_uuid(String clientAUuid) {
        context.setCurrentClientUuid(clientAUuid);
        context.setRequestSpec(given().header("Authorization", "Bearer valid-client-token-for-" + clientAUuid));
    }

    @Given("client B with UUID {string}")
    public void client_b_with_uuid(String clientBUuid) {
        PsychotraumatologyTest clientBTest = new PsychotraumatologyTest();
        clientBTest.setClientUuid(UUID.fromString(clientBUuid));
        clientBTest.setTherapistUuid(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));
        clientBTest.setTestType("PCL-5-PTSD-ASSESSMENT");
        clientBTest.setScore(30);
        clientBTest.setSeverity("HIGH");
        clientBTest.setAnswersJson("{\"q1\":4}");
        psychotraumatologyTestRepository.save(clientBTest);
    }

    @When("client A attempts to access GET {string}")
    public void client_a_attempts_to_access_get(String endpoint) {
        context.setLastResponse(context.getRequestSpec().when().get(endpoint));
    }

    @Then("the system should deny access with HTTP status {int} Forbidden")
    public void the_system_should_deny_access_with_http_status_forbidden(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }
}
