package com.platform.core;

import com.platform.features.user.UserSteps;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@Component
public class DomainScenarioState {

    private final Map<String, UUID> userIdsByUsername = new LinkedHashMap<>();
    private final Map<String, String> userRolesByUsername = new LinkedHashMap<>();
    private final Map<String, String> userFullNamesByUsername = new LinkedHashMap<>();
    private final Map<String, String> userEmailsByUsername = new LinkedHashMap<>();
    private final Map<String, UUID> providerProfileIdsByUsername = new LinkedHashMap<>();
    private final Map<String, UUID> organisationIdsByUsername = new LinkedHashMap<>();
    private final Map<String, UUID> serviceIdsByName = new LinkedHashMap<>();
    private final Map<String, String> serviceCurrencyByName = new LinkedHashMap<>();
    private final Map<String, BigDecimal> baselineGlBalances = new LinkedHashMap<>();

    private UUID lastAppointmentId;
    private UUID lastHandshakeId;
    private UUID lastServiceId;
    private String lastInvoiceId;
    private String lastCommitHash;
    private String lastLoanId;

    private Response lastResponse;
    private Response secondaryResponse;
    private Response tertiaryResponse;
    private String activeBearerToken;

    public void reset() {
        userIdsByUsername.clear();
        userRolesByUsername.clear();
        userFullNamesByUsername.clear();
        userEmailsByUsername.clear();
        providerProfileIdsByUsername.clear();
        organisationIdsByUsername.clear();
        serviceIdsByName.clear();
        serviceCurrencyByName.clear();
        baselineGlBalances.clear();
        lastAppointmentId = null;
        lastHandshakeId = null;
        lastServiceId = null;
        lastInvoiceId = null;
        lastCommitHash = null;
        lastLoanId = null;
        lastResponse = null;
        secondaryResponse = null;
        tertiaryResponse = null;
        activeBearerToken = null;
        UserSteps.CUSTOM_TOKENS.clear();
    }

    public String issueToken(
            UUID userUuid,
            String username,
            String fullName,
            String email,
            List<String> roles,
            String issuer
    ) {
        if (issuer != null && !"http://127.0.0.1:8085".equals(issuer)) {
            return "invalid-untrusted-issuer-token-" + UUID.randomUUID();
        }
        String token = "custom-token-" + username + "-" + UUID.randomUUID();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userUuid.toString());
        claims.put("preferred_username", username);
        claims.put("name", fullName != null ? fullName : username);
        claims.put("email", email != null ? email : username + "@example.dk");
        claims.put("iss", "http://127.0.0.1:8085");
        claims.put("roles", roles);
        UserSteps.CUSTOM_TOKENS.put(token, claims);
        return token;
    }

    public String issueTokenForUser(String username, String roleOverride) {
        UUID uuid = userIdsByUsername.computeIfAbsent(username, k -> UUID.randomUUID());
        String role = roleOverride != null
                ? roleOverride
                : userRolesByUsername.getOrDefault(username, "CLIENT");
        String fullName = userFullNamesByUsername.getOrDefault(username, username);
        String email = userEmailsByUsername.getOrDefault(username, username + "@example.dk");
        return issueToken(uuid, username, fullName, email, List.of(role.toLowerCase()), "http://127.0.0.1:8085");
    }

    public RequestSpecification authenticatedRequest() {
        RequestSpecification spec = given();
        if (activeBearerToken != null) {
            spec.header("Authorization", "Bearer " + activeBearerToken);
        }
        return spec;
    }

    public RequestSpecification requestAs(String username, String role) {
        return given().header("Authorization", "Bearer " + issueTokenForUser(username, role));
    }

    public Map<String, UUID> getUserIdsByUsername() {
        return userIdsByUsername;
    }

    public Map<String, String> getUserRolesByUsername() {
        return userRolesByUsername;
    }

    public Map<String, String> getUserFullNamesByUsername() {
        return userFullNamesByUsername;
    }

    public Map<String, String> getUserEmailsByUsername() {
        return userEmailsByUsername;
    }

    public Map<String, UUID> getProviderProfileIdsByUsername() {
        return providerProfileIdsByUsername;
    }

    public Map<String, UUID> getOrganisationIdsByUsername() {
        return organisationIdsByUsername;
    }

    public Map<String, UUID> getServiceIdsByName() {
        return serviceIdsByName;
    }

    public Map<String, String> getServiceCurrencyByName() {
        return serviceCurrencyByName;
    }

    public Map<String, BigDecimal> getBaselineGlBalances() {
        return baselineGlBalances;
    }

    public UUID getLastAppointmentId() {
        return lastAppointmentId;
    }

    public void setLastAppointmentId(UUID lastAppointmentId) {
        this.lastAppointmentId = lastAppointmentId;
    }

    public UUID getLastHandshakeId() {
        return lastHandshakeId;
    }

    public void setLastHandshakeId(UUID lastHandshakeId) {
        this.lastHandshakeId = lastHandshakeId;
    }

    public UUID getLastServiceId() {
        return lastServiceId;
    }

    public void setLastServiceId(UUID lastServiceId) {
        this.lastServiceId = lastServiceId;
    }

    public String getLastInvoiceId() {
        return lastInvoiceId;
    }

    public void setLastInvoiceId(String lastInvoiceId) {
        this.lastInvoiceId = lastInvoiceId;
    }

    public String getLastCommitHash() {
        return lastCommitHash;
    }

    public void setLastCommitHash(String lastCommitHash) {
        this.lastCommitHash = lastCommitHash;
    }

    public String getLastLoanId() {
        return lastLoanId;
    }

    public void setLastLoanId(String lastLoanId) {
        this.lastLoanId = lastLoanId;
    }

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public Response getSecondaryResponse() {
        return secondaryResponse;
    }

    public void setSecondaryResponse(Response secondaryResponse) {
        this.secondaryResponse = secondaryResponse;
    }

    public Response getTertiaryResponse() {
        return tertiaryResponse;
    }

    public void setTertiaryResponse(Response tertiaryResponse) {
        this.tertiaryResponse = tertiaryResponse;
    }

    public String getActiveBearerToken() {
        return activeBearerToken;
    }

    public void setActiveBearerToken(String activeBearerToken) {
        this.activeBearerToken = activeBearerToken;
    }
}
