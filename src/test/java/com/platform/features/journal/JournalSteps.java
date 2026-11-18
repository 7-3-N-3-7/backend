package com.platform.features.journal;

import com.platform.core.DomainScenarioState;
import com.platform.features.journal.JournalFeature.JournalEntryRepository;
import com.platform.features.journal.JournalFeature.TherapistClinicalNoteRepository;
import com.platform.features.journal.JournalFeature.TherapistNoteRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class JournalSteps {

    private final JournalEntryRepository journalEntryRepository;
    private final TherapistClinicalNoteRepository clinicalNoteRepository;
    private final TherapistNoteRepository therapistNoteRepository;
    private final DomainScenarioState state;

    public JournalSteps(
            JournalEntryRepository journalEntryRepository,
            TherapistClinicalNoteRepository clinicalNoteRepository,
            TherapistNoteRepository therapistNoteRepository,
            DomainScenarioState state
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.clinicalNoteRepository = clinicalNoteRepository;
        this.therapistNoteRepository = therapistNoteRepository;
        this.state = state;
    }

    @Before(order = 20300)
    public void cleanJournals() {
        clinicalNoteRepository.deleteAll();
        therapistNoteRepository.deleteAll();
        journalEntryRepository.deleteAll();
    }

    @When("client {string} writes a personal journal entry titled {string} with content {string}")
    public void clientWritesPersonalJournalEntry(String clientUsername, String title, String content) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("moodRating", 7);
        payload.put("sharedWithTherapists", true);
        payload.put("gitBranch", "main");

        Response response = state.requestAs(clientUsername, "CLIENT")
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/journals");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @Then("viewing the journal entries for {string} should include {string}")
    public void viewingJournalEntriesIncludes(String clientUsername, String expectedTitle) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/journals/client/{uuid}", clientUuid);
        assertThat(response.statusCode(), is(200));
        assertThat(response.getBody().asString(), containsString(expectedTitle));
    }

    @When("therapist {string} records a shared clinical note {string} for {string}")
    public void therapistRecordsSharedClinicalNote(String therapistUsername, String noteContent, String clientUsername) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("title", "Klinisk deling fra " + state.getUserFullNamesByUsername().getOrDefault(therapistUsername, therapistUsername));
        payload.put("content", noteContent);
        payload.put("moodRating", 8);
        payload.put("sharedWithTherapists", true);
        payload.put("gitBranch", "main");

        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/journals");
        state.setLastResponse(response);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @When("therapist {string} records a confidential clinical note {string} for {string}")
    public void therapistRecordsConfidentialClinicalNote(String therapistUsername, String noteContent, String clientUsername) {
        Response response = postTherapistClinicalNote(therapistUsername, "THERAPIST", clientUsername, noteContent);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @When("client {string} attempts to record a confidential therapist clinical note for {string}")
    public void clientAttemptsToRecordClinicalNote(String actorClientUsername, String targetClientUsername) {
        postTherapistClinicalNote(actorClientUsername, "CLIENT", targetClientUsername, "Uautoriseret klinisk notat");
    }

    private Response postTherapistClinicalNote(
            String actorUsername,
            String role,
            String clientUsername,
            String markdownNote
    ) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("therapistNote", markdownNote);
        payload.put("therapistName", state.getUserFullNamesByUsername().getOrDefault(actorUsername, actorUsername));
        payload.put("organisationName", "Klinik " + actorUsername);

        Response response = state.requestAs(actorUsername, role)
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/journals/therapist-notes");
        state.setLastResponse(response);
        return response;
    }

    @Then("when client {string} views their journal and unified timeline, {string} should be visible and {string} should be hidden")
    public void clientViewsJournalAndTimeline(String clientUsername, String visibleText, String hiddenText) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);

        Response journalResponse = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/journals/client/{uuid}", clientUuid);
        assertThat(journalResponse.statusCode(), is(200));
        String journalBody = journalResponse.getBody().asString();
        assertThat(journalBody, containsString(visibleText));
        assertThat(journalBody, not(containsString(hiddenText)));

        Response timelineResponse = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/journals/timeline/{uuid}", clientUuid);
        assertThat(timelineResponse.statusCode(), is(200));
        String timelineBody = timelineResponse.getBody().asString();
        assertThat(timelineBody, containsString(visibleText));
        assertThat(timelineBody, not(containsString(hiddenText)));
    }

    @Then("when therapist {string} views the clinical history and unified timeline for {string}, both {string} and {string} should be visible")
    public void therapistViewsClinicalHistoryAndTimeline(
            String therapistUsername,
            String clientUsername,
            String sharedText,
            String confidentialText
    ) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);

        Response historyResponse = state.requestAs(therapistUsername, "THERAPIST")
                .queryParam("therapistUuid", therapistUuid.toString())
                .get("/api/v1/journals/history/{uuid}", clientUuid);
        assertThat(historyResponse.statusCode(), is(200));

        Response timelineResponse = state.requestAs(therapistUsername, "THERAPIST")
                .get("/api/v1/journals/timeline/{uuid}", clientUuid);
        assertThat(timelineResponse.statusCode(), is(200));

        String combinedBody = historyResponse.getBody().asString() + "\n" + timelineResponse.getBody().asString();
        assertThat(combinedBody, containsString(sharedText));
        assertThat(combinedBody, containsString(confidentialText));
    }

    @Then("when unlinked therapist {string} views the journal bundle and clinical history for {string}, no entries or notes should be returned")
    public void unlinkedTherapistViewsEmptyJournalAndHistory(String unlinkedTherapist, String clientUsername) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        UUID therapistUuid = state.getUserIdsByUsername().get(unlinkedTherapist);

        Response bundleResponse = state.requestAs(unlinkedTherapist, "THERAPIST")
                .queryParam("therapistUuid", therapistUuid.toString())
                .get("/api/v1/journals/client/{uuid}", clientUuid);
        assertThat(bundleResponse.statusCode(), is(200));
        List<Object> entries = bundleResponse.jsonPath().getList("entries");
        List<Object> clinicalNotes = bundleResponse.jsonPath().getList("clinicalNotes");
        assertThat(entries, empty());
        assertThat(clinicalNotes, empty());

        Response historyResponse = state.requestAs(unlinkedTherapist, "THERAPIST")
                .queryParam("therapistUuid", therapistUuid.toString())
                .get("/api/v1/journals/history/{uuid}", clientUuid);
        assertThat(historyResponse.statusCode(), is(200));
        List<Object> history = historyResponse.jsonPath().getList("$");
        assertThat(history, empty());
    }
}
