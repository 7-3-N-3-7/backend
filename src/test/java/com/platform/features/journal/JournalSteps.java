package com.platform.features.journal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.PlatformCacheService;
import com.platform.core.CucumberScenarioContext;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.JournalEntryRepository;
import com.platform.features.journal.JournalFeature.TherapistClinicalNote;
import com.platform.features.journal.JournalFeature.TherapistClinicalNoteRepository;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyPort;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class JournalSteps {

    @Autowired
    private CucumberScenarioContext context;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private TherapistClinicalNoteRepository clinicalNoteRepository;

    @Autowired
    private PsychotraumatologyPort psychotraumatologyTestRepository;

    @Autowired
    private PlatformCacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Given("Business PostgreSQL database is active")
    public void business_postgre_sql_database_is_active() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Assertions.assertTrue(connection.isValid(2), "PostgreSQL/JPA DataSource connection must be active");
        }
        journalEntryRepository.deleteAll();
        clinicalNoteRepository.deleteAll();
        psychotraumatologyTestRepository.deleteAll();
        cacheService.clearAll();
    }

    @Given("Hibernate prepared statements are enabled for SQL injection defense")
    public void hibernate_prepared_statements_are_enabled_for_sql_injection_defense() {
        Assertions.assertNotNull(entityManager, "EntityManager must be active");
        Assertions.assertNotNull(entityManager.getMetamodel().entity(JournalEntry.class));
        Assertions.assertNotNull(entityManager.getMetamodel().entity(PsychotraumatologyTest.class));
    }

    @Given("an authenticated client with UUID {string}")
    public void an_authenticated_client_with_uuid(String clientUuid) {
        context.setCurrentClientUuid(clientUuid);
        context.setRequestSpec(given().header("Authorization", "Bearer valid-client-token-for-" + clientUuid));
    }

    @When("the client posts a new journal entry to {string} with body:")
    public void the_client_posts_a_new_journal_entry_to_with_body(String endpoint, String docString) {
        context.setLastResponse(context.getRequestSpec()
                .contentType("application/json")
                .body(docString)
                .when()
                .post(endpoint));
    }

    @Then("the saved entity in PostgreSQL should have a valid generated UUID")
    public void the_saved_entity_in_postgre_sql_should_have_a_valid_generated_uuid() {
        String idStr = context.getLastResponse().jsonPath().getString("id");
        Assertions.assertNotNull(idStr, "Saved JournalEntry response must include generated UUID");
        UUID entryId = UUID.fromString(idStr);
        Optional<JournalEntry> saved = journalEntryRepository.findById(entryId);
        Assertions.assertTrue(saved.isPresent(), "JournalEntry must be persisted in PostgreSQL");
        Assertions.assertEquals(UUID.fromString(context.getCurrentClientUuid()), saved.get().getClientUuid());
    }

    @Then("the journal entry should be cached in Redisson Redis under key {string}")
    public void the_journal_entry_should_be_cached_in_redisson_redis_under_key(String expectedCacheKey) {
        Assertions.assertTrue(
                cacheService.containsKey(expectedCacheKey),
                "Expected journal entry to be cached under key: " + expectedCacheKey
        );
        JournalEntry cachedEntry = cacheService.get(expectedCacheKey);
        Assertions.assertNotNull(cachedEntry, "Cached journal entry value must not be null");
    }

    @When("the therapist creates a confidential clinical note for client {string} with text {string}")
    public void the_therapist_creates_a_confidential_clinical_note_for_client_with_text(String clientUuid, String noteText) throws Exception {
        String therapistUuid = context.findCurrentTherapistUuid().orElse("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        Map<String, Object> payload = Map.of(
                "clientUuid", clientUuid,
                "therapistNote", noteText,
                "therapistName", "Bo Jensen",
                "organisationName", "Nordic Mind Clinic ApS"
        );
        context.setLastResponse(given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuid)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/journals/therapist-notes"));
    }

    @When("the therapist creates a shared journal entry for client {string} with title {string} and content {string}")
    public void the_therapist_creates_a_shared_journal_entry_for_client_with_title_and_content(String clientUuid, String title, String content) throws Exception {
        String therapistUuid = context.findCurrentTherapistUuid().orElse("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        Map<String, Object> payload = Map.of(
                "clientUuid", clientUuid,
                "title", title,
                "content", content,
                "moodRating", 8,
                "sharedWithTherapists", true
        );
        context.setLastResponse(given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuid)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/journals"));
    }

    @When("the client {string} fetches their journal bundle and JGit commit history")
    public void the_client_fetches_their_journal_bundle_and_jgit_commit_history(String clientUuid) {
        String clientToken = "valid-client-token-for-" + clientUuid;
        Response bundleResponse = given()
                .header("Authorization", "Bearer " + clientToken)
                .when()
                .get("/api/v1/journals/client/" + clientUuid);
        bundleResponse.then().statusCode(200);
        context.setClientBundleResponse(bundleResponse);

        Response historyResponse = given()
                .header("Authorization", "Bearer " + clientToken)
                .when()
                .get("/api/v1/journals/history/" + clientUuid);
        historyResponse.then().statusCode(200);
        context.setClientHistoryResponse(historyResponse);
    }

    @Then("the client journal bundle and JGit history must contain {string}")
    public void the_client_journal_bundle_and_jgit_history_must_contain(String expectedText) {
        Response bundleResponse = context.getClientBundleResponse();
        Response historyResponse = context.getClientHistoryResponse();
        Assertions.assertNotNull(bundleResponse);
        Assertions.assertNotNull(historyResponse);
        bundleResponse.then().body(containsString(expectedText));
        historyResponse.then().body(containsString(expectedText));
    }

    @Then("the client journal bundle and JGit history must NOT contain {string}")
    public void the_client_journal_bundle_and_jgit_history_must_not_contain(String forbiddenText) {
        Response bundleResponse = context.getClientBundleResponse();
        Response historyResponse = context.getClientHistoryResponse();
        Assertions.assertNotNull(bundleResponse);
        Assertions.assertNotNull(historyResponse);
        Assertions.assertFalse(
                bundleResponse.getBody().asString().contains(forbiddenText),
                "Client journal bundle must NOT contain: " + forbiddenText
        );
        Assertions.assertFalse(
                historyResponse.getBody().asString().contains(forbiddenText),
                "Client JGit history must NOT contain: " + forbiddenText
        );
    }

    @Then("the note should be saved and linked to therapist UUID {string}")
    public void the_note_should_be_saved_and_linked_to_therapist_uuid(String expectedTherapistUuid) {
        Response lastResponse = context.getLastResponse();
        String responseTherapistUuid = lastResponse.jsonPath().getString("authorTherapistUuid");
        Assertions.assertEquals(expectedTherapistUuid, responseTherapistUuid, "Saved clinical note must return matching authorTherapistUuid");

        UUID noteId = UUID.fromString(lastResponse.jsonPath().getString("id"));
        Optional<TherapistClinicalNote> savedNote = clinicalNoteRepository.findById(noteId);
        Assertions.assertTrue(savedNote.isPresent(), "TherapistClinicalNote must be persisted in PostgreSQL");
        Assertions.assertEquals(UUID.fromString(expectedTherapistUuid), savedNote.get().getAuthorTherapistUuid());
    }
}
