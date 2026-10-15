package com.platform.cucumber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.crm.repository.TherapistClinicalNoteRepository;
import com.platform.entity.Appointment;
import com.platform.entity.ClientProfile;
import com.platform.entity.ClientTherapistHandshake;
import com.platform.entity.Organisation;
import com.platform.entity.ProviderProfile;
import com.platform.entity.Service;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.ClientProfileRepository;
import com.platform.repository.HandshakeRepository;
import com.platform.repository.OrganisationRepository;
import com.platform.repository.ProviderProfileRepository;
import com.platform.repository.ServiceRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class ClinicPlatformSteps {

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private HandshakeRepository handshakeRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private TherapistClinicalNoteRepository clinicalNoteRepository;

    @Autowired
    private PsychotraumatologyTestRepository psychotraumatologyTestRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Response lastResponse;
    private UUID lastServiceId;
    private UUID lastHandshakeId;
    private UUID lastAppointmentId;

    @Given("the clinic marketplace tables are clean in PostgreSQL")
    public void the_clinic_marketplace_tables_are_clean_in_postgresql() {
        appointmentRepository.deleteAll();
        handshakeRepository.deleteAll();
        serviceRepository.deleteAll();
        providerProfileRepository.deleteAll();
        organisationRepository.deleteAll();
        clientProfileRepository.deleteAll();
        journalEntryRepository.deleteAll();
        clinicalNoteRepository.deleteAll();
        psychotraumatologyTestRepository.deleteAll();
    }

    @Given("an onboarded therapist {string} \\({string}\\) in organisation {string} with CVR {string}")
    public void an_onboarded_therapist_in_organisation_with_cvr(
            String therapistUuidStr,
            String displayName,
            String orgName,
            String cvrNumber
    ) {
        UUID therapistUuid = UUID.fromString(therapistUuidStr);
        Organisation org = organisationRepository.findByCvrNumber(cvrNumber)
                .orElseGet(() -> organisationRepository.save(new Organisation(cvrNumber, orgName)));

        ProviderProfile profile = providerProfileRepository.findByUserUuid(therapistUuid)
                .orElseGet(ProviderProfile::new);
        profile.setUserUuid(therapistUuid);
        profile.setDisplayName(displayName);
        profile.setOrganisation(org);
        profile.setBiography("Autoriseret klinisk psykolog");
        profile.setWorkingHoursJson("{\"mon\":\"09:00-16:00\"}");
        providerProfileRepository.save(profile);
    }

    @Given("a registered client profile for {string} named {string} with email {string}")
    public void a_registered_client_profile_for_named_with_email(
            String clientUuidStr,
            String fullName,
            String email
    ) {
        UUID clientUuid = UUID.fromString(clientUuidStr);
        ClientProfile cp = clientProfileRepository.findByUserId(clientUuid)
                .orElseGet(ClientProfile::new);
        cp.setUserId(clientUuid);
        cp.setFullName(fullName);
        cp.setDateOfBirth("1990-05-14");
        cp.setEmail(email);
        cp.setPhoneNumber("+45 20 30 40 50");
        cp.setHomeAddress("Østerbrogade 12");
        cp.setPostalCode("2100");
        clientProfileRepository.save(cp);
    }

    @When("the therapist {string} creates a clinic service with:")
    public void the_therapist_creates_a_clinic_service_with(String therapistUuidStr, DataTable dataTable) throws Exception {
        Map<String, String> rows = dataTable.asMap(String.class, String.class);
        UUID therapistUuid = UUID.fromString(therapistUuidStr);
        ProviderProfile provider = providerProfileRepository.findByUserUuid(therapistUuid).orElse(null);

        Map<String, Object> payload = new HashMap<>(rows);
        if (provider != null) {
            payload.put("providerProfileId", provider.getId().toString());
            if (provider.getOrganisation() != null) {
                payload.put("organisationId", provider.getOrganisation().getId().toString());
            }
        }

        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/clinic/services");

        if (lastResponse.getStatusCode() == 201 || lastResponse.getStatusCode() == 200) {
            String idStr = lastResponse.jsonPath().getString("id");
            if (idStr != null) {
                lastServiceId = UUID.fromString(idStr);
            }
        }
    }

    @Then("the clinic service response status should be {int}")
    public void the_clinic_service_response_status_should_be(Integer expectedStatus) {
        Assertions.assertNotNull(lastResponse);
        lastResponse.then().statusCode(expectedStatus);
    }

    @Then("the created service should be active in PostgreSQL with name {string} and price {double}")
    public void the_created_service_should_be_active_in_postgresql_with_name_and_price(String expectedName, Double expectedPrice) {
        Assertions.assertNotNull(lastServiceId, "Expected a created service ID");
        Service saved = serviceRepository.findById(lastServiceId)
                .orElseThrow(() -> new AssertionError("Service must exist in PostgreSQL"));
        Assertions.assertTrue(saved.getIsActive(), "Newly created service must be active");
        Assertions.assertEquals(expectedName, saved.getName());
        Assertions.assertEquals(expectedPrice, saved.getPrice(), 0.01);
    }

    @When("a client fetches the clinic marketplace directory at {string}")
    public void a_client_fetches_the_clinic_marketplace_directory_at(String endpoint) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-5e357ca1-4913-414f-91a6-81a7de9042ab")
                .when()
                .get(endpoint);
    }

    @Then("the marketplace directory should contain organisation {string}, provider {string}, and service {string}")
    public void the_marketplace_directory_should_contain_organisation_provider_and_service(
            String expectedOrg,
            String expectedProvider,
            String expectedService
    ) {
        lastResponse.then().statusCode(200);
        lastResponse.then().body(containsString(expectedOrg));
        lastResponse.then().body(containsString(expectedProvider));
        lastResponse.then().body(containsString(expectedService));
    }

    @When("the therapist toggles the active status of the created service")
    public void the_therapist_toggles_the_active_status_of_the_created_service() {
        Assertions.assertNotNull(lastServiceId, "Service ID must be present before toggling");
        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-4c421f46-836c-4897-8dae-ba9587473273")
                .when()
                .patch("/api/v1/clinic/services/" + lastServiceId + "/toggle");
    }

    @Then("the service active status in PostgreSQL should be {word}")
    public void the_service_active_status_in_postgresql_should_be(String expectedBoolStr) {
        boolean expected = Boolean.parseBoolean(expectedBoolStr);
        lastResponse.then().statusCode(200);
        Assertions.assertEquals(expected, lastResponse.jsonPath().getBoolean("isActive"));
        Service saved = serviceRepository.findById(lastServiceId).orElseThrow();
        Assertions.assertEquals(expected, saved.getIsActive());
    }

    @When("client {string} grants an {string} consent handshake to therapist {string}")
    public void client_grants_an_consent_handshake_to_therapist(
            String clientUuidStr,
            String status,
            String therapistUuidStr
    ) throws Exception {
        Map<String, Object> body = Map.of(
                "clientUuid", clientUuidStr,
                "therapistUuid", therapistUuidStr,
                "status", status
        );
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(body))
                .when()
                .post("/api/v1/clinic/handshakes");

        Assertions.assertTrue(
                lastResponse.getStatusCode() == 201 || lastResponse.getStatusCode() == 200,
                "Expected 201 or 200 when creating/updating handshake"
        );
        lastHandshakeId = UUID.fromString(lastResponse.jsonPath().getString("id"));
    }

    @When("the handshake status is updated to {string}")
    public void the_handshake_status_is_updated_to(String newStatus) throws Exception {
        Assertions.assertNotNull(lastHandshakeId, "Handshake ID must exist before status update");
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-5e357ca1-4913-414f-91a6-81a7de9042ab")
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(Map.of("status", newStatus)))
                .when()
                .patch("/api/v1/clinic/handshakes/" + lastHandshakeId + "/status");
        lastResponse.then().statusCode(200);
    }

    @Then("the handshake record in PostgreSQL should have status {string}")
    public void the_handshake_record_in_postgresql_should_have_status(String expectedStatus) {
        ClientTherapistHandshake hs = handshakeRepository.findById(lastHandshakeId)
                .orElseThrow(() -> new AssertionError("Handshake must exist in PostgreSQL"));
        Assertions.assertEquals(expectedStatus, hs.getStatus());
        if ("REVOKED".equalsIgnoreCase(expectedStatus)) {
            Assertions.assertNotNull(hs.getRevokedAt(), "Revoked handshake must record revokedAt timestamp");
        } else if ("ACTIVE".equalsIgnoreCase(expectedStatus)) {
            Assertions.assertNotNull(hs.getGrantedAt(), "Active handshake must record grantedAt timestamp");
            Assertions.assertNull(hs.getRevokedAt(), "Re-activated handshake must clear revokedAt");
        }
    }

    @Given("a scheduled appointment for client {string} with therapist {string} from {string} to {string}")
    public void a_scheduled_appointment_for_client_with_therapist_from_to(
            String clientUuidStr,
            String therapistUuidStr,
            String startIso,
            String endIso
    ) {
        Appointment apt = new Appointment(
                UUID.fromString(clientUuidStr),
                UUID.fromString(therapistUuidStr),
                LocalDateTime.parse(startIso),
                LocalDateTime.parse(endIso),
                "Individuel Terapisamtale",
                "SCHEDULED"
        );
        Appointment saved = appointmentRepository.save(apt);
        lastAppointmentId = saved.getId();
    }

    @When("the client reschedules the appointment to {string} through {string}")
    public void the_client_reschedules_the_appointment_to_through(String newStartIso, String newEndIso) throws Exception {
        Assertions.assertNotNull(lastAppointmentId, "Appointment ID must be present to reschedule");
        Appointment existing = appointmentRepository.findById(lastAppointmentId).orElseThrow();
        Map<String, Object> body = Map.of(
                "clientUuid", existing.getClientUuid().toString(),
                "therapistUuid", existing.getTherapistUuid().toString(),
                "startTime", newStartIso,
                "endTime", newEndIso,
                "title", existing.getTitle()
        );
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + existing.getClientUuid())
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(body))
                .when()
                .put("/api/v1/appointments/" + lastAppointmentId);
    }

    @Then("the rescheduled appointment response status should be {int}")
    public void the_rescheduled_appointment_response_status_should_be(Integer expectedStatus) {
        lastResponse.then().statusCode(expectedStatus);
    }

    @Then("the appointment start time in PostgreSQL should be {string}")
    public void the_appointment_start_time_in_postgresql_should_be(String expectedStartIso) {
        Appointment updated = appointmentRepository.findById(lastAppointmentId).orElseThrow();
        Assertions.assertEquals(LocalDateTime.parse(expectedStartIso), updated.getStartTime());
    }

    @When("client {string} fetches their own PCL-5 test history")
    public void client_fetches_their_own_pcl5_test_history(String clientUuidStr) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .when()
                .get("/api/v1/psychotraumatology/tests/client/" + clientUuidStr);
    }

    @Then("the PCL-5 test list should contain {int} assessment with score {int} and severity {string}")
    public void the_pcl5_test_list_should_contain_assessment_with_score_and_severity(
            Integer expectedCount,
            Integer expectedScore,
            String expectedSeverity
    ) {
        lastResponse.then().statusCode(200);
        List<Map<String, Object>> list = lastResponse.jsonPath().getList("$");
        Assertions.assertEquals(expectedCount, list.size());
        Assertions.assertEquals(expectedScore, ((Number) list.get(0).get("score")).intValue());
        Assertions.assertEquals(expectedSeverity, list.get(0).get("severity"));
    }

    @When("therapist {string} requests their clinic overview")
    public void therapist_requests_their_clinic_overview(String therapistUuidStr) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuidStr)
                .when()
                .get("/api/v1/clinic/therapist/" + therapistUuidStr + "/overview");
    }

    @Then("the therapist overview client roster should include {string} with handshake {string} and PCL-5 score {int}")
    public void the_therapist_overview_client_roster_should_include_with_handshake_and_pcl5_score(
            String clientUuidStr,
            String expectedHandshakeStatus,
            Integer expectedScore
    ) {
        lastResponse.then().statusCode(200);
        List<Map<String, Object>> clients = lastResponse.jsonPath().getList("clients");
        Map<String, Object> matchingClient = clients.stream()
                .filter(c -> clientUuidStr.equals(String.valueOf(c.get("uuid"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Client " + clientUuidStr + " not found in therapist roster"));
        Assertions.assertEquals(expectedHandshakeStatus, matchingClient.get("handshakeStatus"));
        Assertions.assertEquals(expectedScore, ((Number) matchingClient.get("latestPcl5Score")).intValue());
    }

    @When("client {string} requests their JGit markdown timeline at {string}")
    public void client_requests_their_jgit_markdown_timeline_at(String clientUuidStr, String endpoint) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .when()
                .get(endpoint);
    }

    @When("therapist {string} requests the JGit markdown timeline at {string}")
    public void therapist_requests_the_jgit_markdown_timeline_at(String therapistUuidStr, String endpoint) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuidStr)
                .when()
                .get(endpoint);
    }

    @Then("the timeline response should have status {int} and contain {string}")
    public void the_timeline_response_should_have_status_and_contain(Integer expectedStatus, String expectedSubstring) {
        lastResponse.then().statusCode(expectedStatus);
        lastResponse.then().body(containsString(expectedSubstring));
    }

    @Then("the timeline response should NOT contain {string}")
    public void the_timeline_response_should_not_contain(String forbiddenSubstring) {
        Assertions.assertFalse(
                lastResponse.getBody().asString().contains(forbiddenSubstring),
                "Expected timeline response NOT to contain: " + forbiddenSubstring
        );
    }
}
