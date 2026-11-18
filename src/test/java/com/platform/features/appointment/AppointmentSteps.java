package com.platform.features.appointment;

import com.platform.core.DomainScenarioState;
import com.platform.features.appointment.AppointmentFeature.AppointmentDto;
import com.platform.features.appointment.AppointmentFeature.AppointmentRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class AppointmentSteps {

    private final AppointmentRepository appointmentRepository;
    private final DomainScenarioState state;

    public AppointmentSteps(AppointmentRepository appointmentRepository, DomainScenarioState state) {
        this.appointmentRepository = appointmentRepository;
        this.state = state;
    }

    @Before(order = 20100)
    public void cleanAppointments() {
        appointmentRepository.deleteAll();
    }

    private Response postAppointment(
            String clientUsername,
            String therapistUsername,
            String serviceName,
            String startTime,
            String endTime
    ) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);
        UUID serviceId = state.getServiceIdsByName().get(serviceName);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("therapistUuid", therapistUuid);
        payload.put("serviceId", serviceId);
        payload.put("clientName", state.getUserFullNamesByUsername().getOrDefault(clientUsername, clientUsername));
        payload.put("therapistName", state.getUserFullNamesByUsername().getOrDefault(therapistUsername, therapistUsername));
        payload.put("title", serviceName);
        payload.put("startTime", startTime);
        payload.put("endTime", endTime);

        Response response = state.requestAs(clientUsername, "CLIENT")
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/appointments");

        state.setLastResponse(response);
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            String id = response.jsonPath().getString("id");
            if (id != null && !id.isBlank()) {
                state.setLastAppointmentId(UUID.fromString(id));
            }
        }
        return response;
    }

    @When("client {string} books an appointment with therapist {string} for service {string} from {string} to {string}")
    public void clientBooksAppointment(
            String clientUsername,
            String therapistUsername,
            String serviceName,
            String startTime,
            String endTime
    ) {
        postAppointment(clientUsername, therapistUsername, serviceName, startTime, endTime);
    }

    @When("client {string} attempts to book an appointment with therapist {string} for service {string} from {string} to {string}")
    public void clientAttemptsToBookAppointment(
            String clientUsername,
            String therapistUsername,
            String serviceName,
            String startTime,
            String endTime
    ) {
        postAppointment(clientUsername, therapistUsername, serviceName, startTime, endTime);
    }

    @Given("client {string} has a booked appointment with therapist {string} for service {string} from {string} to {string}")
    public void clientHasBookedAppointment(
            String clientUsername,
            String therapistUsername,
            String serviceName,
            String startTime,
            String endTime
    ) {
        Response response = postAppointment(clientUsername, therapistUsername, serviceName, startTime, endTime);
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(state.getLastAppointmentId(), notNullValue());
    }

    @Then("the appointment booking should succeed with status {string}")
    public void bookingShouldSucceedWithStatus(String expectedStatus) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(response.jsonPath().getString("status"), is(expectedStatus));
    }

    @Then("the appointment booking should succeed")
    public void bookingShouldSucceed() {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(response.jsonPath().getString("id"), notNullValue());
    }

    @Then("the booking should be rejected as a scheduling conflict")
    public void bookingShouldBeRejectedAsConflict() {
        assertThat(state.getLastResponse().statusCode(), is(409));
    }

    @When("client {string} attempts to reschedule that appointment to {string} until {string}")
    public void clientAttemptsToRescheduleAppointment(String clientUsername, String startTime, String endTime) {
        sendReschedule(clientUsername, startTime, endTime);
    }

    @When("client {string} reschedules that appointment to {string} until {string}")
    public void clientReschedulesAppointment(String clientUsername, String startTime, String endTime) {
        sendReschedule(clientUsername, startTime, endTime);
    }

    private void sendReschedule(String clientUsername, String startTime, String endTime) {
        UUID appointmentId = state.getLastAppointmentId();
        assertThat("Expected an existing appointment ID to reschedule", appointmentId, notNullValue());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", appointmentId);
        payload.put("clientUuid", state.getUserIdsByUsername().get(clientUsername));
        payload.put("startTime", startTime);
        payload.put("endTime", endTime);

        Response response = state.requestAs(clientUsername, "CLIENT")
                .contentType(ContentType.JSON)
                .body(payload)
                .put("/api/v1/appointments/{id}", appointmentId);
        state.setLastResponse(response);
    }

    @Then("the appointment start time should be updated to {string}")
    public void appointmentStartTimeShouldBeUpdated(String expectedStartTime) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));
        assertThat(response.jsonPath().getString("startTime"), startsWith(expectedStartTime));
    }

    @When("client {string} cancels the scheduled appointment")
    public void clientCancelsScheduledAppointment(String clientUsername) {
        UUID appointmentId = state.getLastAppointmentId();
        assertThat("Expected an existing appointment ID to cancel", appointmentId, notNullValue());

        Response deleteResponse = state.requestAs(clientUsername, "CLIENT")
                .delete("/api/v1/appointments/{id}", appointmentId);
        state.setLastResponse(deleteResponse);

        Response historyResponse = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/appointments/{id}/git-history", appointmentId);
        state.setSecondaryResponse(historyResponse);
    }

    @Then("the cancellation should complete successfully")
    public void cancellationShouldCompleteSuccessfully() {
        assertThat(state.getLastResponse().statusCode(), anyOf(is(200), is(204)));
        Response historyResponse = state.getSecondaryResponse();
        if (historyResponse != null) {
            assertThat(historyResponse.statusCode(), is(200));
            List<Object> commits = historyResponse.jsonPath().getList("$");
            assertThat(commits.size(), greaterThan(0));
        }
        Response verifyGet = state.authenticatedRequest()
                .get("/api/v1/appointments/{id}", state.getLastAppointmentId());
        assertThat(verifyGet.statusCode(), is(200));
        assertThat(verifyGet.jsonPath().getString("status"), is("CANCELLED"));
    }

    @When("client {string} filters appointments from {string} to {string}")
    public void clientFiltersAppointments(String clientUsername, String fromIso, String toIso) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        String fromDate = fromIso.length() >= 10 ? fromIso.substring(0, 10) : fromIso;
        String toDate = toIso.length() >= 10 ? toIso.substring(0, 10) : toIso;

        Response response = state.requestAs(clientUsername, "CLIENT")
                .queryParam("from", fromDate)
                .queryParam("to", toDate)
                .get("/api/v1/appointments/client/{uuid}", clientUuid);
        state.setLastResponse(response);
    }

    @Then("the filtered schedule should include {string} and exclude {string}")
    public void filteredScheduleShouldIncludeAndExclude(String includedStart, String excludedStart) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));
        List<String> startTimes = response.jsonPath().getList("startTime", String.class);
        assertThat(startTimes, hasItem(startsWith(includedStart)));
        assertThat(startTimes, everyItem(not(startsWith(excludedStart))));
    }

    @Given("therapist {string} configures multi-window availability from {string} to {string} and {string} to {string}")
    public void therapistConfiguresMultiWindowAvailability(
            String therapistUsername,
            String w1Start,
            String w1End,
            String w2Start,
            String w2End
    ) {
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);
        Map<String, Object> onboarding = new LinkedHashMap<>();
        onboarding.put("userUuid", therapistUuid != null ? therapistUuid.toString() : null);
        onboarding.put("displayName", state.getUserFullNamesByUsername().getOrDefault(therapistUsername, therapistUsername));
        onboarding.put("cvrNumber", "CVR-" + therapistUsername.toUpperCase());
        onboarding.put("organisationName", "Klinik " + therapistUsername);
        onboarding.put("iban", "DK5000400440116243");
        onboarding.put("currency", "DKK");
        onboarding.put("address", "Bredgade 42");
        onboarding.put("postalCode", "1260");
        onboarding.put("phoneNumber", "+45 33 14 25 36");
        onboarding.put("workingHoursJson", w1Start + "-" + w1End + ", " + w2Start + "-" + w2End);

        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(onboarding)
                .post("/api/v1/clinic/therapist-onboarding");
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
    }

    @When("requesting optimal conflict-free slots for therapist {string} on {string}")
    public void requestingOptimalSlotsForTherapist(String therapistUsername, String date) {
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);
        Response response = state.authenticatedRequest()
                .queryParam("therapistUuid", therapistUuid.toString())
                .queryParam("date", date)
                .queryParam("durationMinutes", 60)
                .get("/api/v1/appointments/timefold-slots");
        state.setLastResponse(response);
    }

    @Then("a feasible recommended slot avoiding {string} should be returned")
    public void feasibleRecommendedSlotShouldBeReturned(String blockedStartTime) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));
        assertThat(response.jsonPath().getBoolean("feasible"), is(true));
        String recommendedSlot = response.jsonPath().getString("recommendedSlot");
        assertThat(recommendedSlot, notNullValue());

        List<Map<String, Object>> slots = response.jsonPath().getList("slots");
        assertThat(slots, not(empty()));

        Map<String, Object> chosenSlot = slots.stream()
                .filter(slot -> recommendedSlot.equals(String.valueOf(slot.get("slot"))))
                .findFirst()
                .orElse(null);
        assertThat("Expected slots to contain recommendedSlot " + recommendedSlot, chosenSlot, notNullValue());

        String chosenStart = String.valueOf(chosenSlot.get("startTime"));
        String blockedPrefix = blockedStartTime.length() >= 16 ? blockedStartTime.substring(0, 16) : blockedStartTime;
        assertThat(chosenStart, not(startsWith(blockedPrefix)));
    }

    @When("quick-booking an unassigned appointment for client name {string} at {string}")
    public void quickBookingUnassignedAppointment(String clientName, String startTime) {
        AppointmentDto dto = new AppointmentDto(null, clientName, LocalDateTime.parse(startTime));
        Response response = state.authenticatedRequest()
                .contentType(ContentType.JSON)
                .body(dto)
                .post("/api/v1/appointments");
        state.setLastResponse(response);
    }
}
