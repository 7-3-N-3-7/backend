package com.platform.features.clinic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentPort;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfileRepository;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakeRepository;
import com.platform.features.clinic.ClinicPlatformFeature.Organisation;
import com.platform.features.clinic.ClinicPlatformFeature.OrganisationRepository;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfileRepository;
import com.platform.features.clinic.ClinicPlatformFeature.Service;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceRepository;
import com.platform.features.finance.FinanceFeature.FineractClient;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private AppointmentPort appointmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FineractClient fineractClient;

    @Autowired
    private ObjectMapper objectMapper;

    private Response lastResponse;
    private Response lastHistoryResponse;
    private UUID lastServiceId;
    private UUID lastHandshakeId;
    private UUID lastAppointmentId;
    private final Map<String, UUID> createdUserIds = new LinkedHashMap<>();
    private final Map<String, String> createdUserFullNames = new LinkedHashMap<>();
    private final List<Map<String, Object>> bookingPayments = new ArrayList<>();

    @Given("the clinic marketplace tables are clean in PostgreSQL")
    public void the_clinic_marketplace_tables_are_clean_in_postgresql() {
        jdbcTemplate.execute("DELETE FROM appointments");
        handshakeRepository.deleteAll();
        serviceRepository.deleteAll();
        providerProfileRepository.deleteAll();
        organisationRepository.deleteAll();
        clientProfileRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM journal_entries");
        jdbcTemplate.execute("DELETE FROM therapist_clinical_notes");
        jdbcTemplate.execute("DELETE FROM psychotraumatology_tests");
        jdbcTemplate.execute("DELETE FROM app_users");
        createdUserIds.clear();
        createdUserFullNames.clear();
        bookingPayments.clear();
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
        org.setIban("DK5000400440116243");
        org.setCurrency("DKK");
        organisationRepository.save(org);

        ProviderProfile profile = providerProfileRepository.findByUserUuid(therapistUuid)
                .orElseGet(ProviderProfile::new);
        profile.setUserUuid(therapistUuid);
        profile.setDisplayName(displayName);
        profile.setOrganisation(org);
        profile.setBiography("Autoriseret klinisk psykolog");
        profile.setWorkingHoursJson("{\"mon\":\"09:00-16:00\"}");
        profile.setIban("DK5000400440116243");
        profile.setCurrency("DKK");
        providerProfileRepository.save(profile);

        ClientProfile cp = clientProfileRepository.findByUserId(therapistUuid)
                .orElseGet(ClientProfile::new);
        cp.setUserId(therapistUuid);
        cp.setFullName(displayName);
        cp.setDateOfBirth("1980-01-01");
        cp.setEmail("therapist-" + therapistUuidStr.substring(0, 8) + "@clinic.dk");
        cp.setPhoneNumber("+45 20 12 34 56");
        cp.setHomeAddress("Psykologklinikvej 4");
        cp.setPostalCode("1050");
        clientProfileRepository.save(cp);
    }

    @Given("a therapist {string} \\({string}\\) in organisation {string} with CVR {string} WITHOUT an IBAN configured")
    public void a_therapist_in_organisation_with_cvr_without_an_iban_configured(
            String therapistUuidStr,
            String displayName,
            String orgName,
            String cvrNumber
    ) {
        UUID therapistUuid = UUID.fromString(therapistUuidStr);
        Organisation org = organisationRepository.findByCvrNumber(cvrNumber)
                .orElseGet(() -> organisationRepository.save(new Organisation(cvrNumber, orgName)));
        org.setIban(null);
        org.setAddress("Psykologklinikvej 4");
        org.setPostalCode("1050");
        org.setPhoneNumber("+45 20 12 34 56");
        organisationRepository.save(org);

        ProviderProfile profile = providerProfileRepository.findByUserUuid(therapistUuid)
                .orElseGet(ProviderProfile::new);
        profile.setUserUuid(therapistUuid);
        profile.setDisplayName(displayName);
        profile.setOrganisation(org);
        profile.setBiography("Autoriseret klinisk psykolog");
        profile.setWorkingHoursJson("{\"mon\":\"09:00-16:00\"}");
        profile.setIban(null);
        providerProfileRepository.save(profile);

        ClientProfile cp = clientProfileRepository.findByUserId(therapistUuid)
                .orElseGet(ClientProfile::new);
        cp.setUserId(therapistUuid);
        cp.setFullName(displayName);
        cp.setDateOfBirth("1980-01-01");
        cp.setEmail("therapist-" + therapistUuidStr.substring(0, 8) + "@clinic.dk");
        cp.setPhoneNumber("+45 20 12 34 56");
        cp.setHomeAddress("Psykologklinikvej 4");
        cp.setPostalCode("1050");
        clientProfileRepository.save(cp);
    }

    @Then("the therapist overview should report canCreateServices as false and missingProfileFields containing {string}")
    public void the_therapist_overview_should_report_can_create_services_as_false_and_missing_profile_fields_containing(String expectedField) {
        lastResponse.then().statusCode(200);
        Assertions.assertFalse(lastResponse.jsonPath().getBoolean("canCreateServices"));
        List<String> missing = lastResponse.jsonPath().getList("missingProfileFields");
        Assertions.assertTrue(missing.contains(expectedField), "Expected missingProfileFields to contain " + expectedField + ", got: " + missing);
    }

    @Then("the clinic service error message should mention {string}")
    public void the_clinic_service_error_message_should_mention(String expectedText) {
        Assertions.assertTrue(
                lastResponse.getBody().asString().contains(expectedText),
                "Expected error response to contain " + expectedText + ", got: " + lastResponse.getBody().asString()
        );
    }

    @When("the therapist {string} completes clinic setup with IBAN {string} and currency {string}")
    public void the_therapist_completes_clinic_setup_with_iban_and_currency(
            String therapistUuidStr,
            String iban,
            String currency
    ) throws Exception {
        UUID therapistUuid = UUID.fromString(therapistUuidStr);
        ProviderProfile existing = providerProfileRepository.findByUserUuid(therapistUuid).orElseThrow();
        Map<String, String> payload = new HashMap<>();
        payload.put("userUuid", therapistUuidStr);
        payload.put("cvrNumber", existing.getOrganisation().getCvrNumber());
        payload.put("organisationName", existing.getOrganisation().getName());
        payload.put("displayName", existing.getDisplayName());
        payload.put("biography", existing.getBiography());
        payload.put("workingHoursJson", existing.getWorkingHoursJson());
        payload.put("iban", iban);
        payload.put("currency", currency);

        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/clinic/therapist-onboarding");
        lastResponse.then().statusCode(200);
    }

    @Then("the therapist profile should have IBAN {string} and java.util.Currency {string}")
    public void the_therapist_profile_should_have_iban_and_java_util_currency(String expectedIban, String expectedCurrency) {
        String actualIban = lastResponse.jsonPath().getString("iban");
        String actualCurrency = lastResponse.jsonPath().getString("currency");
        Assertions.assertEquals(expectedIban, actualIban);
        Assertions.assertEquals(Currency.getInstance(expectedCurrency).getCurrencyCode(), actualCurrency);
    }

    @Then("the therapist overview should report canCreateServices as true and missingProfileFields should be empty")
    public void the_therapist_overview_should_report_can_create_services_as_true_and_missing_profile_fields_should_be_empty() {
        lastResponse.then().statusCode(200);
        Assertions.assertTrue(lastResponse.jsonPath().getBoolean("canCreateServices"));
        List<String> missing = lastResponse.jsonPath().getList("missingProfileFields");
        Assertions.assertTrue(missing.isEmpty(), "Expected missingProfileFields to be empty, got: " + missing);
    }

    @Then("the created service should have java.util.Currency {string} and price {double}")
    public void the_created_service_should_have_java_util_currency_and_price(String expectedCurrency, Double expectedPrice) {
        Assertions.assertNotNull(lastServiceId);
        Service saved = serviceRepository.findById(lastServiceId).orElseThrow();
        Assertions.assertEquals(Currency.getInstance(expectedCurrency), saved.resolveJavaCurrency());
        Assertions.assertEquals(expectedPrice, saved.getPrice(), 0.01);
    }

    @When("client {string} commits a journal entry on JGit branch {string} with title {string} and content {string}")
    public void client_commits_a_journal_entry_on_jgit_branch_with_title_and_content(
            String clientUuidStr,
            String branchName,
            String title,
            String content
    ) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientUuid", clientUuidStr);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("moodRating", 8);
        payload.put("sharedWithTherapists", true);
        payload.put("gitBranch", branchName);

        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/journals");
        lastResponse.then().statusCode(200);
    }

    @When("client {string} books the created service with therapist {string} from {string} to {string}")
    public void client_books_the_created_service_with_therapist_from_to(
            String clientUuidStr,
            String therapistUuidStr,
            String startIso,
            String endIso
    ) throws Exception {
        Assertions.assertNotNull(lastServiceId, "Expected lastServiceId before booking");
        Service srv = serviceRepository.findById(lastServiceId).orElseThrow();
        Map<String, Object> body = new HashMap<>();
        body.put("clientUuid", clientUuidStr);
        body.put("therapistUuid", therapistUuidStr);
        body.put("serviceId", lastServiceId.toString());
        body.put("startTime", startIso);
        body.put("endTime", endIso);
        body.put("title", srv.getName());
        body.put("location", srv.getLocation());

        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(body))
                .when()
                .post("/api/v1/appointments");
        lastResponse.then().statusCode(201);
        lastAppointmentId = UUID.fromString(lastResponse.jsonPath().getString("id"));
    }

    @When("client {string} grants an {string} handshake to therapist {string} restricted to only JGit branch {string} and custom restrictions {string}")
    public void client_grants_an_handshake_to_therapist_restricted_to_only_jgit_branch_and_custom_restrictions(
            String clientUuidStr,
            String status,
            String therapistUuidStr,
            String allowedBranch,
            String customRestrictions
    ) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("clientUuid", clientUuidStr);
        body.put("therapistUuid", therapistUuidStr);
        body.put("status", status);
        body.put("allowedBranches", allowedBranch);
        body.put("customRestrictions", customRestrictions);

        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(body))
                .when()
                .post("/api/v1/clinic/handshakes");
        Assertions.assertTrue(lastResponse.getStatusCode() == 201 || lastResponse.getStatusCode() == 200);
        lastHandshakeId = UUID.fromString(lastResponse.jsonPath().getString("id"));
    }

    @Then("the handshake should only permit JGit branch {string} and have custom restrictions {string}")
    public void the_handshake_should_only_permit_jgit_branch_and_have_custom_restrictions(
            String expectedBranch,
            String expectedRestrictions
    ) {
        ClientTherapistHandshake hs = handshakeRepository.findById(lastHandshakeId).orElseThrow();
        Assertions.assertEquals(List.of(expectedBranch), hs.getAllowedBranchesList());
        Assertions.assertTrue(hs.isBranchAllowed(expectedBranch));
        Assertions.assertFalse(hs.isBranchAllowed("branch-private-diary"));
        Assertions.assertEquals(expectedRestrictions, hs.getCustomRestrictions());
    }

    @When("therapist {string} fetches the client journal bundle and JGit history for {string}")
    public void therapist_fetches_the_client_journal_bundle_and_jgit_history_for(
            String therapistUuidStr,
            String clientUuidStr
    ) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuidStr)
                .queryParam("therapistUuid", therapistUuidStr)
                .when()
                .get("/api/v1/journals/client/" + clientUuidStr);
        lastHistoryResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuidStr)
                .queryParam("therapistUuid", therapistUuidStr)
                .when()
                .get("/api/v1/journals/history/" + clientUuidStr);
    }

    @Then("the therapist should see the JGit commit and entry from branch {string} containing {string}")
    public void the_therapist_should_see_the_jgit_commit_and_entry_from_branch_containing(
            String expectedBranch,
            String expectedContent
    ) {
        lastResponse.then().statusCode(200);
        lastHistoryResponse.then().statusCode(200);
        List<Map<String, Object>> entries = lastResponse.jsonPath().getList("entries");
        Assertions.assertTrue(
                entries.stream().anyMatch(e -> expectedBranch.equals(e.get("gitBranch"))
                        && String.valueOf(e.get("content")).contains(expectedContent)),
                "Expected therapist journal bundle to contain entry on branch " + expectedBranch
        );
        List<Map<String, String>> commits = lastHistoryResponse.jsonPath().getList("$");
        Assertions.assertTrue(
                commits.stream().anyMatch(c -> expectedBranch.equals(c.get("branch"))
                        && String.valueOf(c.get("snapshot")).contains(expectedContent)),
                "Expected therapist JGit history to contain commit on branch " + expectedBranch
        );
    }

    @Then("the therapist should NOT see any JGit commit or entry from branch {string} containing {string}")
    public void the_therapist_should_not_see_any_jgit_commit_or_entry_from_branch_containing(
            String forbiddenBranch,
            String forbiddenContent
    ) {
        List<Map<String, Object>> entries = lastResponse.jsonPath().getList("entries");
        Assertions.assertFalse(
                entries.stream().anyMatch(e -> forbiddenBranch.equals(e.get("gitBranch"))
                        || String.valueOf(e.get("content")).contains(forbiddenContent)),
                "Therapist must NOT see entry from unpermitted branch " + forbiddenBranch
        );
        List<Map<String, String>> commits = lastHistoryResponse.jsonPath().getList("$");
        Assertions.assertFalse(
                commits.stream().anyMatch(c -> forbiddenBranch.equals(c.get("branch"))),
                "Therapist must NOT see JGit commit from unpermitted branch " + forbiddenBranch
        );
    }

    @Then("the mood rating on the therapist's view of the entry should be masked as null due to {string}")
    public void the_mood_rating_on_the_therapists_view_of_the_entry_should_be_masked_as_null_due_to(String restrictionFlag) {
        List<String> activeRestrictions = lastResponse.jsonPath().getList("customRestrictions");
        Assertions.assertTrue(activeRestrictions.contains(restrictionFlag));
        List<Map<String, Object>> entries = lastResponse.jsonPath().getList("entries");
        Assertions.assertFalse(entries.isEmpty());
        for (Map<String, Object> entry : entries) {
            Assertions.assertNull(entry.get("moodRating"), "moodRating should be null when NO_MOOD_SCORES restriction is active");
        }
    }

    @When("client {string} fetches their own journal bundle and JGit history")
    public void client_fetches_their_own_journal_bundle_and_jgit_history(String clientUuidStr) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .when()
                .get("/api/v1/journals/client/" + clientUuidStr);
        lastHistoryResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .when()
                .get("/api/v1/journals/history/" + clientUuidStr);
    }

    @Then("the client should see both JGit branches {string} and {string}")
    public void the_client_should_see_both_jgit_branches_and(String branch1, String branch2) {
        lastResponse.then().statusCode(200);
        lastHistoryResponse.then().statusCode(200);
        List<Map<String, Object>> entries = lastResponse.jsonPath().getList("entries");
        Assertions.assertTrue(entries.stream().anyMatch(e -> branch1.equals(e.get("gitBranch"))));
        Assertions.assertTrue(entries.stream().anyMatch(e -> branch2.equals(e.get("gitBranch"))));
        List<Map<String, String>> commits = lastHistoryResponse.jsonPath().getList("$");
        Assertions.assertTrue(commits.stream().anyMatch(c -> branch1.equals(c.get("branch"))));
        Assertions.assertTrue(commits.stream().anyMatch(c -> branch2.equals(c.get("branch"))));
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
        payload.put("therapistUuid", therapistUuidStr);
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
        Appointment saved = appointmentRepository.saveAppointment(apt);
        lastAppointmentId = saved.getId();
    }

    @When("the client reschedules the appointment to {string} through {string}")
    public void the_client_reschedules_the_appointment_to_through(String newStartIso, String newEndIso) throws Exception {
        Assertions.assertNotNull(lastAppointmentId, "Appointment ID must be present to reschedule");
        Appointment existing = appointmentRepository.findAppointmentById(lastAppointmentId).orElseThrow();
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
        Appointment updated = appointmentRepository.findAppointmentById(lastAppointmentId).orElseThrow();
        Assertions.assertEquals(LocalDateTime.parse(expectedStartIso), updated.getStartTime());
    }

    @When("the client attempts to cancel the past appointment")
    public void the_client_attempts_to_cancel_the_past_appointment() {
        Assertions.assertNotNull(lastAppointmentId, "Appointment ID must be present to cancel");
        Appointment existing = appointmentRepository.findAppointmentById(lastAppointmentId).orElseThrow();
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + existing.getClientUuid())
                .when()
                .delete("/api/v1/appointments/" + lastAppointmentId);
    }

    @When("the client requests Timefold Solver slot recommendations for therapist {string} on {string} with preferred time {string}")
    public void the_client_requests_timefold_solver_slot_recommendations_for_therapist_on_with_preferred_time(
            String therapistUuidStr,
            String dateStr,
            String preferredTimeStr
    ) {
        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-5e357ca1-4913-414f-91a6-81a7de9042ab")
                .queryParam("therapistUuid", therapistUuidStr)
                .queryParam("date", dateStr)
                .queryParam("durationMinutes", 60)
                .queryParam("preferredTime", preferredTimeStr)
                .when()
                .get("/api/v1/appointments/timefold-slots");
    }

    @Then("the Timefold Solver response should be feasible and recommend a non-overlapping slot")
    public void the_timefold_solver_response_should_be_feasible_and_recommend_a_non_overlapping_slot() {
        lastResponse.then().statusCode(200);
        Assertions.assertTrue(lastResponse.jsonPath().getBoolean("feasible"), "Timefold solution should be feasible");
        String recommendedSlot = lastResponse.jsonPath().getString("recommendedSlot");
        Assertions.assertNotNull(recommendedSlot, "Recommended slot must not be null");
        Assertions.assertNotEquals("10:00", recommendedSlot, "Recommended slot must avoid overlapping 10:00 booking");
        Assertions.assertTrue(
                List.of("09:00", "11:00").contains(recommendedSlot),
                "Timefold should recommend an adjacent back-to-back slot (09:00 or 11:00) for compact schedule, got: " + recommendedSlot
        );
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

    // =========================================================================
    // END-TO-END 3-USER FLOW: 1 USER PAYS TO BECOME THERAPIST & 2 USERS PAY 50 DKK EACH
    // =========================================================================

    @Given("3 platform users are created via {string}:")
    public void three_platform_users_are_created_via(String endpoint, DataTable dataTable) throws Exception {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        Assertions.assertEquals(3, rows.size(), "Expected exactly 3 users to be created");

        for (Map<String, String> row : rows) {
            String alias = row.get("alias");
            String username = row.get("username");
            String role = row.get("role");
            String fullName = row.get("fullName");
            String email = row.get("email");

            Map<String, String> userPayload = Map.of(
                    "username", username,
                    "password", "SecurePass!2026",
                    "role", role
            );
            Response userResp = given()
                    .header("Authorization", "Bearer dummy-token")
                    .contentType("application/json")
                    .body(objectMapper.writeValueAsString(userPayload))
                    .when()
                    .post(endpoint);
            userResp.then().statusCode(201);
            UUID userId = UUID.fromString(userResp.jsonPath().getString("id"));
            createdUserIds.put(alias, userId);
            createdUserFullNames.put(alias, fullName);

            Map<String, Object> profilePayload = new LinkedHashMap<>();
            profilePayload.put("userId", userId.toString());
            profilePayload.put("fullName", fullName);
            profilePayload.put("dateOfBirth", "1990-04-12");
            profilePayload.put("homeAddress", "Bredgade 42");
            profilePayload.put("postalCode", "1260");
            profilePayload.put("phoneNumber", "+45 20 30 40 50");
            profilePayload.put("email", email);

            Response profileResp = given()
                    .contentType("application/json")
                    .body(objectMapper.writeValueAsString(profilePayload))
                    .when()
                    .post("/api/v1/public/client-profile");
            Assertions.assertTrue(profileResp.statusCode() == 201 || profileResp.statusCode() == 200);
        }
    }

    @When("user {string} pays {double} in java.util.Currency {string} for subscription plan {string} to become a therapist")
    public void user_pays_in_java_util_currency_for_subscription_plan_to_become_a_therapist(
            String userAlias,
            double amount,
            String currencyStr,
            String planName
    ) throws Exception {
        UUID userId = createdUserIds.get(userAlias);
        Assertions.assertNotNull(userId, "User alias not found: " + userAlias);
        Currency currency = Currency.getInstance(currencyStr);

        Map<String, Object> subPayload = new LinkedHashMap<>();
        subPayload.put("clientUuid", userId.toString());
        subPayload.put("planName", planName);
        subPayload.put("monthlyAmount", BigDecimal.valueOf(amount));
        subPayload.put("currency", currency.getCurrencyCode());

        lastResponse = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", userId.toString())
                .header("X-Auth-Full-Name", createdUserFullNames.getOrDefault(userAlias, "Maja Lind"))
                .header("X-Auth-Roles", "client")
                .body(objectMapper.writeValueAsString(subPayload))
                .when()
                .post("/api/v1/finance/subscriptions");
        lastResponse.then().statusCode(201);

        jdbcTemplate.update("UPDATE app_users SET role = 'THERAPIST' WHERE id = ?", userId);
    }

    @Then("the therapist upgrade payment should be {string} in java.util.Currency {string} and Fineract GL {string} should have balance {double} DKK")
    public void the_therapist_upgrade_payment_should_be_in_java_util_currency_and_fineract_gl_should_have_balance(
            String expectedStatus,
            String expectedCurrency,
            String glCode,
            double expectedBalance
    ) {
        Assertions.assertEquals(expectedStatus, lastResponse.jsonPath().getString("status"));
        Assertions.assertEquals(
                Currency.getInstance(expectedCurrency).getCurrencyCode(),
                Currency.getInstance(lastResponse.jsonPath().getString("currency")).getCurrencyCode()
        );
        Assertions.assertNotNull(lastResponse.jsonPath().getString("gitCommitHash"));

        BigDecimal actualGlBalance = fineractClient.getChartOfAccounts().stream()
                .filter(a -> glCode.equals(a.get("glCode")))
                .map(a -> (BigDecimal) a.get("balance"))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(0, BigDecimal.valueOf(expectedBalance).compareTo(actualGlBalance));
    }

    @When("user {string} completes mandatory therapist clinic setup with CVR {string}, organisation {string}, IBAN {string}, and java.util.Currency {string}")
    public void user_completes_mandatory_therapist_clinic_setup_with_cvr_organisation_iban_and_java_util_currency(
            String userAlias,
            String cvr,
            String orgName,
            String iban,
            String currencyStr
    ) throws Exception {
        UUID userId = createdUserIds.get(userAlias);
        Assertions.assertNotNull(userId, "User alias not found: " + userAlias);
        Currency currency = Currency.getInstance(currencyStr);

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("userUuid", userId.toString());
        payload.put("cvrNumber", cvr);
        payload.put("organisationName", orgName);
        payload.put("displayName", createdUserFullNames.getOrDefault(userAlias, "Maja Lind"));
        payload.put("biography", "Autoriseret psykolog med speciale i traumebehandling");
        payload.put("workingHoursJson", "{\"mon\":\"09:00-17:00\",\"tue\":\"09:00-17:00\"}");
        payload.put("address", "Bredgade 42");
        payload.put("postalCode", "1260");
        payload.put("phoneNumber", "+45 20 30 40 50");
        payload.put("iban", iban);
        payload.put("currency", currency.getCurrencyCode());

        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + userId)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/clinic/therapist-onboarding");
        lastResponse.then().statusCode(200);
        Assertions.assertEquals(iban, lastResponse.jsonPath().getString("iban"));
        Assertions.assertEquals(currency.getCurrencyCode(), lastResponse.jsonPath().getString("currency"));
    }

    @When("therapist user {string} sets up a clinic service priced at {double} in java.util.Currency {string} supporting both {string} and {string}:")
    public void therapist_user_sets_up_a_clinic_service_priced_at_in_java_util_currency_supporting_both_and(
            String userAlias,
            double expectedPrice,
            String currencyStr,
            String mode1,
            String mode2,
            DataTable dataTable
    ) throws Exception {
        UUID therapistUuid = createdUserIds.get(userAlias);
        Assertions.assertNotNull(therapistUuid, "Therapist alias not found: " + userAlias);
        Currency currency = Currency.getInstance(currencyStr);
        Assertions.assertTrue(List.of(mode1, mode2).containsAll(List.of("ENGANGSBETALING", "KLIPPEKORT")));

        Map<String, String> rows = dataTable.asMap(String.class, String.class);
        ProviderProfile provider = providerProfileRepository.findByUserUuid(therapistUuid).orElseThrow();

        Map<String, Object> payload = new LinkedHashMap<>(rows);
        payload.put("therapistUuid", therapistUuid.toString());
        payload.put("providerProfileId", provider.getId().toString());
        payload.put("organisationId", provider.getOrganisation().getId().toString());
        payload.put("price", expectedPrice);
        payload.put("currency", currency.getCurrencyCode());

        lastResponse = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuid)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/clinic/services");
        lastResponse.then().statusCode(201);
        lastServiceId = UUID.fromString(lastResponse.jsonPath().getString("id"));
    }

    @Then("the created service for therapist {string} should be active with price {double} and java.util.Currency {string}")
    public void the_created_service_for_therapist_should_be_active_with_price_and_java_util_currency(
            String therapistAlias,
            double expectedPrice,
            String expectedCurrency
    ) {
        UUID therapistUuid = createdUserIds.get(therapistAlias);
        Assertions.assertNotNull(therapistUuid);
        Assertions.assertNotNull(lastServiceId);

        Service saved = serviceRepository.findById(lastServiceId).orElseThrow();
        Assertions.assertTrue(saved.getIsActive());
        Assertions.assertEquals(expectedPrice, saved.getPrice(), 0.01);
        Assertions.assertEquals(Currency.getInstance(expectedCurrency), saved.resolveJavaCurrency());
        Assertions.assertEquals(therapistUuid, saved.getProvider().getUserUuid());
    }

    @When("client user {string} books the therapist {string} service from {string} to {string} and pays {double} in java.util.Currency {string} via engangsbetaling")
    public void client_user_books_the_therapist_service_from_to_and_pays_in_java_util_currency_via_engangsbetaling(
            String clientAlias,
            String therapistAlias,
            String startIso,
            String endIso,
            double amount,
            String currencyStr
    ) throws Exception {
        UUID clientUuid = createdUserIds.get(clientAlias);
        UUID therapistUuid = createdUserIds.get(therapistAlias);
        Currency currency = Currency.getInstance(currencyStr);
        Service srv = serviceRepository.findById(lastServiceId).orElseThrow();
        Assertions.assertEquals(currency, srv.resolveJavaCurrency());
        Assertions.assertEquals(amount, srv.getPrice(), 0.01);

        Map<String, Object> bookingBody = new LinkedHashMap<>();
        bookingBody.put("clientUuid", clientUuid.toString());
        bookingBody.put("therapistUuid", therapistUuid.toString());
        bookingBody.put("serviceId", lastServiceId.toString());
        bookingBody.put("startTime", startIso);
        bookingBody.put("endTime", endIso);
        bookingBody.put("title", srv.getName());
        bookingBody.put("location", srv.getLocation());

        Response bookResp = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuid)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(bookingBody))
                .when()
                .post("/api/v1/appointments");
        bookResp.then().statusCode(201);
        String appointmentId = bookResp.jsonPath().getString("id");

        Map<String, Object> paymentBody = new LinkedHashMap<>();
        paymentBody.put("clientUuid", clientUuid.toString());
        paymentBody.put("amount", BigDecimal.valueOf(amount));
        paymentBody.put("currency", currency.getCurrencyCode());
        paymentBody.put("description", "Engangsbetaling for booking " + appointmentId + " hos " + createdUserFullNames.get(therapistAlias));
        paymentBody.put("appointmentUuid", appointmentId);
        paymentBody.put("payImmediately", true);

        Response payResp = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", clientUuid.toString())
                .header("X-Auth-Full-Name", createdUserFullNames.getOrDefault(clientAlias, "Anders Sand"))
                .header("X-Auth-Roles", "client")
                .body(objectMapper.writeValueAsString(paymentBody))
                .when()
                .post("/api/v1/finance/invoices/session");
        payResp.then().statusCode(201);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("clientAlias", clientAlias);
        record.put("therapistUuid", therapistUuid.toString());
        record.put("appointmentId", appointmentId);
        record.put("method", "ENGANGSBETALING");
        record.put("amount", BigDecimal.valueOf(amount));
        record.put("currency", Currency.getInstance(payResp.jsonPath().getString("currency")));
        record.put("status", payResp.jsonPath().getString("status"));
        record.put("gitCommitHash", payResp.jsonPath().getString("gitCommitHash"));
        bookingPayments.add(record);
    }

    @When("client user {string} books the therapist {string} service from {string} to {string} and pays {double} in java.util.Currency {string} via klippekort")
    public void client_user_books_the_therapist_service_from_to_and_pays_in_java_util_currency_via_klippekort(
            String clientAlias,
            String therapistAlias,
            String startIso,
            String endIso,
            double amount,
            String currencyStr
    ) throws Exception {
        UUID clientUuid = createdUserIds.get(clientAlias);
        UUID therapistUuid = createdUserIds.get(therapistAlias);
        Currency currency = Currency.getInstance(currencyStr);
        Service srv = serviceRepository.findById(lastServiceId).orElseThrow();
        Assertions.assertEquals(currency, srv.resolveJavaCurrency());
        Assertions.assertEquals(amount, srv.getPrice(), 0.01);

        Map<String, Object> bookingBody = new LinkedHashMap<>();
        bookingBody.put("clientUuid", clientUuid.toString());
        bookingBody.put("therapistUuid", therapistUuid.toString());
        bookingBody.put("serviceId", lastServiceId.toString());
        bookingBody.put("startTime", startIso);
        bookingBody.put("endTime", endIso);
        bookingBody.put("title", srv.getName());
        bookingBody.put("location", srv.getLocation());

        Response bookResp = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuid)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(bookingBody))
                .when()
                .post("/api/v1/appointments");
        bookResp.then().statusCode(201);
        String appointmentId = bookResp.jsonPath().getString("id");

        Map<String, Object> topupBody = new LinkedHashMap<>();
        topupBody.put("clientUuid", clientUuid.toString());
        topupBody.put("clips", 1);
        topupBody.put("amount", BigDecimal.valueOf(amount));
        topupBody.put("currency", currency.getCurrencyCode());

        Response topupResp = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", clientUuid.toString())
                .header("X-Auth-Full-Name", createdUserFullNames.getOrDefault(clientAlias, "Sofie Holm"))
                .header("X-Auth-Roles", "client")
                .body(objectMapper.writeValueAsString(topupBody))
                .when()
                .post("/api/v1/finance/prepaid/topup");
        topupResp.then().statusCode(201);

        Map<String, Object> consumeBody = new LinkedHashMap<>();
        consumeBody.put("clientUuid", clientUuid.toString());
        consumeBody.put("clips", 1);
        consumeBody.put("appointmentUuid", appointmentId);

        Response consumeResp = given()
                .contentType("application/json")
                .header("X-Auth-User-Uuid", clientUuid.toString())
                .header("X-Auth-Full-Name", createdUserFullNames.getOrDefault(clientAlias, "Sofie Holm"))
                .header("X-Auth-Roles", "client")
                .body(objectMapper.writeValueAsString(consumeBody))
                .when()
                .post("/api/v1/finance/prepaid/consume");
        consumeResp.then().statusCode(200);

        BigDecimal deductedAmount = new BigDecimal(consumeResp.jsonPath().getString("deductedAmount"));
        Assertions.assertEquals(0, BigDecimal.valueOf(amount).compareTo(deductedAmount));
        Assertions.assertEquals(0, consumeResp.jsonPath().getInt("remainingClips"));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("clientAlias", clientAlias);
        record.put("therapistUuid", therapistUuid.toString());
        record.put("appointmentId", appointmentId);
        record.put("method", "KLIPPEKORT");
        record.put("amount", deductedAmount);
        record.put("currency", Currency.getInstance(consumeResp.jsonPath().getString("currency")));
        record.put("status", "PAID");
        record.put("gitCommitHash", consumeResp.jsonPath().getString("gitCommitHash"));
        bookingPayments.add(record);
    }

    @Then("both bookings with therapist {string} should be confirmed and paid with {double} in java.util.Currency {string} each")
    public void both_bookings_with_therapist_should_be_confirmed_and_paid_with_in_java_util_currency_each(
            String therapistAlias,
            double expectedEachAmount,
            String expectedCurrencyStr
    ) {
        UUID therapistUuid = createdUserIds.get(therapistAlias);
        Currency expectedCurrency = Currency.getInstance(expectedCurrencyStr);
        Assertions.assertEquals(2, bookingPayments.size(), "Expected 2 completed client booking payments");

        for (Map<String, Object> payment : bookingPayments) {
            Assertions.assertEquals(therapistUuid.toString(), payment.get("therapistUuid"));
            Assertions.assertEquals("PAID", payment.get("status"));
            Assertions.assertEquals(expectedCurrency, payment.get("currency"));
            BigDecimal paidAmount = (BigDecimal) payment.get("amount");
            Assertions.assertEquals(0, BigDecimal.valueOf(expectedEachAmount).compareTo(paidAmount));
            Assertions.assertNotNull(payment.get("gitCommitHash"));

            UUID aptId = UUID.fromString(String.valueOf(payment.get("appointmentId")));
            Appointment apt = appointmentRepository.findAppointmentById(aptId).orElseThrow();
            Assertions.assertEquals(therapistUuid, apt.getTherapistUuid());
        }
    }

    @Then("therapist {string} should have received {double} in java.util.Currency {string} in consultation revenue on Fineract GL {string}")
    public void therapist_should_have_received_in_java_util_currency_in_consultation_revenue_on_fineract_gl(
            String therapistAlias,
            double expectedTotalRevenue,
            String expectedCurrencyStr,
            String glCode
    ) {
        UUID therapistUuid = createdUserIds.get(therapistAlias);
        ProviderProfile provider = providerProfileRepository.findByUserUuid(therapistUuid).orElseThrow();
        Assertions.assertEquals(Currency.getInstance(expectedCurrencyStr), provider.resolveJavaCurrency());
        Assertions.assertNotNull(provider.getIban());

        BigDecimal actualRevenue = fineractClient.getChartOfAccounts().stream()
                .filter(a -> glCode.equals(a.get("glCode")))
                .map(a -> (BigDecimal) a.get("balance"))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(0, BigDecimal.valueOf(expectedTotalRevenue).compareTo(actualRevenue));
    }

    @Then("Fineract GL {string} total bank liquidity should be {double} in java.util.Currency {string}")
    public void fineract_gl_total_bank_liquidity_should_be_in_java_util_currency(
            String glCode,
            double expectedBalance,
            String expectedCurrencyStr
    ) {
        Assertions.assertEquals("DKK", Currency.getInstance(expectedCurrencyStr).getCurrencyCode());
        BigDecimal actualBalance = fineractClient.getChartOfAccounts().stream()
                .filter(a -> glCode.equals(a.get("glCode")))
                .map(a -> (BigDecimal) a.get("balance"))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(0, BigDecimal.valueOf(expectedBalance).compareTo(actualBalance));
    }

    @When("client {string} grants an {string} handshake to therapist {string} with per-branch custom restrictions:")
    public void client_grants_an_handshake_to_therapist_with_per_branch_custom_restrictions(
            String clientUuidStr,
            String status,
            String therapistUuidStr,
            DataTable dataTable
    ) throws Exception {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        List<String> branches = new ArrayList<>();
        List<String> branchRestrictionPairs = new ArrayList<>();
        Set<String> allRestrictions = new LinkedHashSet<>();

        for (Map<String, String> row : rows) {
            String branch = row.get("branch").trim();
            String restrictions = row.get("restrictions").trim();
            branches.add(branch);
            branchRestrictionPairs.add(branch + ":" + restrictions);
            for (String r : restrictions.split(",")) {
                if (!r.isBlank()) {
                    allRestrictions.add(r.trim());
                }
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("clientUuid", clientUuidStr);
        body.put("therapistUuid", therapistUuidStr);
        body.put("status", status);
        body.put("allowedBranches", String.join(",", branches));
        body.put("customRestrictions", String.join(",", allRestrictions));
        body.put("branchRestrictions", String.join(";", branchRestrictionPairs));

        lastResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-" + clientUuidStr)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(body))
                .when()
                .post("/api/v1/clinic/handshakes");
        Assertions.assertTrue(lastResponse.getStatusCode() == 201 || lastResponse.getStatusCode() == 200);
        lastHandshakeId = UUID.fromString(lastResponse.jsonPath().getString("id"));
    }

    @Then("the handshake should permit JGit branches {string} with per-branch restrictions")
    public void the_handshake_should_permit_jgit_branches_with_per_branch_restrictions(String expectedBranchesCsv) {
        ClientTherapistHandshake hs = handshakeRepository.findById(lastHandshakeId).orElseThrow();
        List<String> expectedBranches = Arrays.asList(expectedBranchesCsv.split(","));
        for (String b : expectedBranches) {
            Assertions.assertTrue(hs.isBranchAllowed(b.trim()), "Expected branch to be allowed: " + b);
        }
        Assertions.assertFalse(hs.isBranchAllowed("branch-private-diary"));
        Map<String, List<String>> brMap = hs.getBranchRestrictionsMap();
        Assertions.assertEquals(expectedBranches.size(), brMap.size());
    }

    @Then("the therapist's view of branch {string} should have moodRating masked as null while branch {string} retains moodRating {int}")
    public void the_therapists_view_of_branch_should_have_mood_rating_masked_as_null_while_branch_retains_mood_rating(
            String maskedBranch,
            String unmaskedBranch,
            Integer expectedMoodRating
    ) {
        List<Map<String, Object>> entries = lastResponse.jsonPath().getList("entries");
        Map<String, Object> maskedEntry = entries.stream()
                .filter(e -> maskedBranch.equals(e.get("gitBranch")))
                .findFirst()
                .orElseThrow();
        Assertions.assertNull(maskedEntry.get("moodRating"), "Expected moodRating to be null on " + maskedBranch);

        Map<String, Object> unmaskedEntry = entries.stream()
                .filter(e -> unmaskedBranch.equals(e.get("gitBranch")))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(expectedMoodRating, ((Number) unmaskedEntry.get("moodRating")).intValue(),
                "Expected moodRating to be preserved on " + unmaskedBranch);
    }

    @When("{word} {string} views the service marketplace from their personal client profile at {string}")
    public void user_views_the_service_marketplace_from_their_personal_client_profile_at(
            String roleType,
            String userUuidStr,
            String endpoint
    ) {
        String tokenPrefix = "therapist".equalsIgnoreCase(roleType)
                ? "Bearer valid-therapist-token-for-"
                : "Bearer valid-client-token-for-";
        lastResponse = given()
                .header("Authorization", tokenPrefix + userUuidStr)
                .when()
                .get(endpoint);
        lastResponse.then().statusCode(200);
    }

    @Then("the personal client marketplace services should NOT contain {string}")
    public void the_personal_client_marketplace_services_should_not_contain(String forbiddenServiceName) {
        List<Map<String, Object>> services = lastResponse.jsonPath().getList("services");
        Assertions.assertFalse(
                services.stream().anyMatch(s -> forbiddenServiceName.equals(s.get("name"))),
                "Personal client marketplace should NOT contain therapist's own service: " + forbiddenServiceName
        );
    }

    @Then("the personal client marketplace services should contain {string}")
    public void the_personal_client_marketplace_services_should_contain(String expectedServiceName) {
        List<Map<String, Object>> services = lastResponse.jsonPath().getList("services");
        Assertions.assertTrue(
                services.stream().anyMatch(s -> expectedServiceName.equals(s.get("name"))),
                "Personal client marketplace should contain service: " + expectedServiceName
        );
    }
}
