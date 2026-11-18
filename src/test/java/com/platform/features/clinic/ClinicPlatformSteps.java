package com.platform.features.clinic;

import com.platform.core.DomainScenarioState;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfileRepository;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakeRepository;
import com.platform.features.clinic.ClinicPlatformFeature.OrganisationRepository;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfileRepository;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class ClinicPlatformSteps {

    private final HandshakeRepository handshakeRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final OrganisationRepository organisationRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final DomainScenarioState state;

    public ClinicPlatformSteps(
            HandshakeRepository handshakeRepository,
            ServiceRepository serviceRepository,
            ProviderProfileRepository providerProfileRepository,
            OrganisationRepository organisationRepository,
            ClientProfileRepository clientProfileRepository,
            DomainScenarioState state
    ) {
        this.handshakeRepository = handshakeRepository;
        this.serviceRepository = serviceRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.organisationRepository = organisationRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.state = state;
    }

    @Before(order = 20200)
    public void cleanClinicData() {
        handshakeRepository.deleteAll();
        serviceRepository.deleteAll();
        providerProfileRepository.deleteAll();
        organisationRepository.deleteAll();
        clientProfileRepository.deleteAll();
    }

    private UUID ensureRegisteredUser(String username, String fullName, String email, String role) {
        UUID existing = state.getUserIdsByUsername().get(username);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("username", username);
        userPayload.put("fullName", fullName);
        userPayload.put("email", email);
        userPayload.put("password", "SecurePassword123!");
        userPayload.put("role", role);

        Response response = state.requestAs("clinic_seed_admin", "ADMIN")
                .contentType(ContentType.JSON)
                .body(userPayload)
                .post("/api/users");
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        UUID uuid = UUID.fromString(response.jsonPath().getString("id"));
        state.getUserIdsByUsername().put(username, uuid);
        state.getUserRolesByUsername().put(username, role.toUpperCase(Locale.ROOT));
        state.getUserFullNamesByUsername().put(username, fullName);
        state.getUserEmailsByUsername().put(username, email);
        return uuid;
    }

    private Response onboardTherapist(String username, String iban, String currency) {
        String fullName = state.getUserFullNamesByUsername().getOrDefault(username, "Dr. " + username);
        UUID therapistUuid = ensureRegisteredUser(username, fullName, username + "@klinik.dk", "THERAPIST");

        Map<String, Object> onboardingPayload = new LinkedHashMap<>();
        onboardingPayload.put("userUuid", therapistUuid.toString());
        onboardingPayload.put("displayName", fullName);
        onboardingPayload.put("cvrNumber", String.format("%08d", Math.abs(username.hashCode()) % 90000000 + 10000000));
        onboardingPayload.put("organisationName", "Klinik " + fullName);
        onboardingPayload.put("iban", iban);
        onboardingPayload.put("currency", currency);
        onboardingPayload.put("address", "Bredgade 42");
        onboardingPayload.put("postalCode", "1260");
        onboardingPayload.put("phoneNumber", "+45 33 14 25 36");
        onboardingPayload.put("biography", "Autoriseret psykolog");
        onboardingPayload.put("workingHoursJson", "09:00-17:00");

        Response response = state.requestAs(username, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(onboardingPayload)
                .post("/api/v1/clinic/therapist-onboarding");
        state.setLastResponse(response);

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            String providerId = response.jsonPath().getString("id");
            if (providerId == null) {
                providerId = response.jsonPath().getString("providerProfile.id");
            }
            if (providerId == null) {
                providerId = response.jsonPath().getString("providerProfileId");
            }
            if (providerId != null) {
                state.getProviderProfileIdsByUsername().put(username, UUID.fromString(providerId));
            }
            String orgId = response.jsonPath().getString("organisation.id");
            if (orgId == null) {
                orgId = response.jsonPath().getString("organisationId");
            }
            if (orgId != null) {
                state.getOrganisationIdsByUsername().put(username, UUID.fromString(orgId));
            }
        }

        // Populate ClientProfile contact fields too so personal profile completeness never masks clinic IBAN checks.
        Map<String, Object> profilePayload = new LinkedHashMap<>();
        profilePayload.put("userId", therapistUuid);
        profilePayload.put("fullName", fullName);
        profilePayload.put("dateOfBirth", "1985-05-15");
        profilePayload.put("homeAddress", "Bredgade 42");
        profilePayload.put("postalCode", "1260");
        profilePayload.put("phoneNumber", "+45 33 14 25 36");
        state.requestAs(username, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(profilePayload)
                .post("/api/v1/public/client-profile");

        return response;
    }

    private Response createService(
            String therapistUsername,
            String serviceName,
            int price,
            String currency,
            int durationMinutes,
            String branch
    ) {
        UUID providerProfileId = state.getProviderProfileIdsByUsername().get(therapistUsername);
        UUID organisationId = state.getOrganisationIdsByUsername().get(therapistUsername);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", serviceName);
        payload.put("description", serviceName + " i speciale " + branch);
        payload.put("durationMinutes", durationMinutes);
        payload.put("price", price);
        payload.put("location", branch);
        payload.put("currency", currency);
        if (providerProfileId != null) {
            payload.put("providerProfileId", providerProfileId);
        }
        if (organisationId != null) {
            payload.put("organisationId", organisationId);
        }

        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/clinic/services");
        state.setLastResponse(response);

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            String serviceId = response.jsonPath().getString("id");
            if (serviceId != null && !serviceId.isBlank()) {
                UUID uuid = UUID.fromString(serviceId);
                state.getServiceIdsByName().put(serviceName, uuid);
                state.getServiceCurrencyByName().put(serviceName, currency);
                state.setLastServiceId(uuid);
            }
        }
        return response;
    }

    @Given("an onboarded therapist {string} with IBAN {string}, currency {string}, and service {string} priced at {int} {word} for {int} minutes in branch {string}")
    public void onboardedTherapistWithService(
            String therapistUsername,
            String iban,
            String currency,
            String serviceName,
            int price,
            String priceCurrency,
            int durationMinutes,
            String branch
    ) {
        Response onboardResp = onboardTherapist(therapistUsername, iban, currency);
        assertThat(onboardResp.statusCode(), anyOf(is(200), is(201)));
        Response serviceResp = createService(therapistUsername, serviceName, price, priceCurrency, durationMinutes, branch);
        assertThat(serviceResp.statusCode(), anyOf(is(200), is(201)));
    }

    @Given("a registered client {string} with an active handshake with therapist {string}")
    public void registeredClientWithActiveHandshake(String clientUsername, String therapistUsername) {
        String clientFullName = state.getUserFullNamesByUsername().getOrDefault(clientUsername, clientUsername);
        UUID clientUuid = ensureRegisteredUser(clientUsername, clientFullName, clientUsername + "@example.dk", "CLIENT");
        UUID therapistUuid = ensureRegisteredUser(
                therapistUsername,
                state.getUserFullNamesByUsername().getOrDefault(therapistUsername, therapistUsername),
                therapistUsername + "@klinik.dk",
                "THERAPIST"
        );

        Map<String, Object> handshakePayload = new LinkedHashMap<>();
        handshakePayload.put("clientUuid", clientUuid);
        handshakePayload.put("clientName", clientFullName);
        handshakePayload.put("therapistUuid", therapistUuid);
        handshakePayload.put("therapistName", state.getUserFullNamesByUsername().getOrDefault(therapistUsername, therapistUsername));
        handshakePayload.put("organisationName", "Klinik " + therapistUsername);
        handshakePayload.put("status", "ACTIVE");

        Response response = state.requestAs(clientUsername, "CLIENT")
                .contentType(ContentType.JSON)
                .body(handshakePayload)
                .post("/api/v1/clinic/handshakes");
        assertThat(response.statusCode(), anyOf(is(200), is(201)));

        String handshakeId = response.jsonPath().getString("id");
        UUID handshakeUuid = UUID.fromString(handshakeId);
        state.setLastHandshakeId(handshakeUuid);

        if (!"ACTIVE".equals(response.jsonPath().getString("status"))) {
            Response approveResp = state.requestAs(therapistUsername, "THERAPIST")
                    .contentType(ContentType.JSON)
                    .body(Map.of("status", "ACTIVE"))
                    .patch("/api/v1/clinic/handshakes/{id}/status", handshakeUuid);
            assertThat(approveResp.statusCode(), is(200));
            assertThat(approveResp.jsonPath().getString("status"), is("ACTIVE"));
        }
    }

    @When("checking onboarding readiness for therapist {string}")
    public void checkingOnboardingReadiness(String therapistUsername) {
        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .get("/api/v1/clinic/me");
        state.setLastResponse(response);
    }

    @Then("service creation should be blocked and missing profile fields should include {string}")
    public void serviceCreationBlockedWithMissingField(String expectedField) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));
        assertThat(response.jsonPath().getBoolean("canCreateServices"), is(false));
        List<String> missingFields = response.jsonPath().getList("missingProfileFields", String.class);
        assertThat(missingFields, notNullValue());
        boolean matched = missingFields.stream()
                .anyMatch(field -> field != null && field.toLowerCase(Locale.ROOT).contains(expectedField.toLowerCase(Locale.ROOT)));
        assertThat("Expected missingProfileFields " + missingFields + " to mention " + expectedField, matched, is(true));
    }

    @When("therapist {string} attempts to create service {string} priced at {int} {string} for {int} minutes in branch {string}")
    public void therapistAttemptsCreateService(
            String therapistUsername,
            String serviceName,
            int price,
            String currency,
            int durationMinutes,
            String branch
    ) {
        createService(therapistUsername, serviceName, price, currency, durationMinutes, branch);
    }

    @When("therapist {string} creates service {string} priced at {int} {string} for {int} minutes in branch {string}")
    public void therapistCreatesService(
            String therapistUsername,
            String serviceName,
            int price,
            String currency,
            int durationMinutes,
            String branch
    ) {
        createService(therapistUsername, serviceName, price, currency, durationMinutes, branch);
    }

    @When("therapist {string} configures bank details with IBAN {string} and preferred currency {string}")
    public void therapistConfiguresBankDetails(String therapistUsername, String iban, String currency) {
        onboardTherapist(therapistUsername, iban, currency);
    }

    @Then("service creation should be enabled for therapist {string} with no missing profile fields")
    public void serviceCreationEnabled(String therapistUsername) {
        Response meResponse = state.requestAs(therapistUsername, "THERAPIST")
                .get("/api/v1/clinic/me");
        assertThat(meResponse.statusCode(), is(200));
        assertThat(meResponse.jsonPath().getBoolean("canCreateServices"), is(true));
        List<String> missingFields = meResponse.jsonPath().getList("missingProfileFields", String.class);
        assertThat(missingFields, empty());
    }

    @Then("the service {string} should be created as active with currency {string}")
    public void serviceCreatedAsActiveWithCurrency(String serviceName, String expectedCurrency) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(response.jsonPath().getString("name"), is(serviceName));
        assertThat(response.jsonPath().getString("currency"), is(expectedCurrency));
        Boolean isActive = response.jsonPath().get("isActive");
        Boolean active = response.jsonPath().get("active");
        assertThat(Boolean.TRUE.equals(isActive) || Boolean.TRUE.equals(active), is(true));
    }

    @When("therapist {string} toggles active status on service {string}")
    public void therapistTogglesServiceActiveStatus(String therapistUsername, String serviceName) {
        UUID serviceId = state.getServiceIdsByName().get(serviceName);
        assertThat("Expected service ID for " + serviceName, serviceId, notNullValue());
        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .patch("/api/v1/clinic/services/{id}/toggle", serviceId);
        state.setLastResponse(response);
    }

    @Then("the service {string} should become inactive")
    public void serviceBecomesInactive(String serviceName) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));
        assertThat(response.jsonPath().getString("name"), is(serviceName));
        Boolean isActive = response.jsonPath().get("isActive");
        Boolean active = response.jsonPath().get("active");
        assertThat(Boolean.FALSE.equals(isActive) || Boolean.FALSE.equals(active), is(true));
    }

    @When("{string} browses the clinic marketplace directory")
    public void browsesMarketplaceDirectory(String viewerUsername) {
        UUID viewerUuid = state.getUserIdsByUsername().get(viewerUsername);
        String role = state.getUserRolesByUsername().getOrDefault(viewerUsername, "THERAPIST");
        Response response = state.requestAs(viewerUsername, role)
                .get("/api/v1/clinic/client/{uuid}/overview", viewerUuid);
        state.setLastResponse(response);
    }

    @Then("the marketplace directory should include provider {string} and exclude provider {string}")
    public void marketplaceDirectoryIncludesAndExcludes(String includedUsername, String excludedUsername) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), is(200));

        UUID includedProviderId = state.getProviderProfileIdsByUsername().get(includedUsername);
        UUID excludedProviderId = state.getProviderProfileIdsByUsername().get(excludedUsername);
        assertThat("Expected onboarded providerProfileId for " + includedUsername, includedProviderId, notNullValue());
        assertThat("Expected onboarded providerProfileId for " + excludedUsername, excludedProviderId, notNullValue());

        List<String> serviceProviderIds = response.jsonPath().getList("services.provider.id", String.class);
        assertThat(serviceProviderIds, hasItem(includedProviderId.toString()));
        assertThat(serviceProviderIds, not(hasItem(excludedProviderId.toString())));
    }

    @When("client {string} requests a handshake with therapist {string}")
    public void clientRequestsHandshake(String clientUsername, String therapistUsername) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientUuid", clientUuid);
        payload.put("clientName", state.getUserFullNamesByUsername().getOrDefault(clientUsername, clientUsername));
        payload.put("therapistUuid", therapistUuid);
        payload.put("therapistName", state.getUserFullNamesByUsername().getOrDefault(therapistUsername, therapistUsername));
        payload.put("organisationName", "Klinik " + therapistUsername);
        payload.put("status", "REQUESTED");

        Response response = state.requestAs(clientUsername, "CLIENT")
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/api/v1/clinic/handshakes");
        state.setLastResponse(response);
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            state.setLastHandshakeId(UUID.fromString(response.jsonPath().getString("id")));
        }
    }

    @Then("the handshake status should be {string}")
    public void handshakeStatusShouldBe(String expectedStatus) {
        Response response = state.getLastResponse();
        assertThat(response.statusCode(), anyOf(is(200), is(201)));
        assertThat(response.jsonPath().getString("status"), is(expectedStatus));
    }

    @When("therapist {string} updates the handshake status to {string}")
    public void therapistUpdatesHandshakeStatus(String therapistUsername, String status) {
        UUID handshakeId = state.getLastHandshakeId();
        assertThat("Expected handshake ID", handshakeId, notNullValue());
        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .contentType(ContentType.JSON)
                .body(Map.of("status", status))
                .patch("/api/v1/clinic/handshakes/{id}/status", handshakeId);
        state.setLastResponse(response);
    }

    @Then("the therapist overview for {string} should list client {string} with handshake status {string} and latest PCL-5 score {int}")
    public void therapistOverviewListsClientWithHandshakeAndScore(
            String therapistUsername,
            String clientUsername,
            String expectedStatus,
            int expectedScore
    ) {
        UUID therapistUuid = state.getUserIdsByUsername().get(therapistUsername);
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);

        Response response = state.requestAs(therapistUsername, "THERAPIST")
                .get("/api/v1/clinic/therapist/{uuid}/overview", therapistUuid);
        assertThat(response.statusCode(), is(200));

        List<Map<String, Object>> clients = response.jsonPath().getList("clients");
        Map<String, Object> matchedClient = clients.stream()
                .filter(c -> clientUuid.toString().equals(String.valueOf(c.get("clientUuid")))
                        || clientUuid.toString().equals(String.valueOf(c.get("uuid"))))
                .findFirst()
                .orElse(null);

        assertThat("Expected client " + clientUsername + " in therapist overview: " + clients, matchedClient, notNullValue());
        assertThat(String.valueOf(matchedClient.get("handshakeStatus")), is(expectedStatus));
        assertThat(((Number) matchedClient.get("latestPcl5Score")).intValue(), is(expectedScore));
    }

    @Then("the client overview for {string} should reflect the booking for {string} without exposing {string}")
    public void clientOverviewReflectsBookingWithoutExposingPeer(
            String clientUsername,
            String serviceName,
            String excludedUsername
    ) {
        UUID clientUuid = state.getUserIdsByUsername().get(clientUsername);
        Response response = state.requestAs(clientUsername, "CLIENT")
                .get("/api/v1/clinic/client/{uuid}/overview", clientUuid);
        assertThat(response.statusCode(), is(200));

        String rawBody = response.getBody().asString();
        assertThat(rawBody, containsString(serviceName));
        assertThat(rawBody, not(containsString(excludedUsername)));

        UUID excludedUuid = state.getUserIdsByUsername().get(excludedUsername);
        if (excludedUuid != null) {
            assertThat(rawBody, not(containsString(excludedUuid.toString())));
        }
        String excludedFullName = state.getUserFullNamesByUsername().get(excludedUsername);
        if (excludedFullName != null) {
            assertThat(rawBody, not(containsString(excludedFullName)));
        }
    }
}
