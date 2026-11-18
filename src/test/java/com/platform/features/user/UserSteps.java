package com.platform.features.user;

import com.platform.config.PlatformCacheService;
import com.platform.config.PlatformEventService;
import com.platform.core.CucumberScenarioContext;
import com.platform.core.DomainScenarioState;
import com.platform.features.i18n.I18nSteps;
import com.platform.features.user.UserFeature.UserEntity;
import com.platform.features.user.UserFeature.UserRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(I18nSteps.I18nMockConfig.class)
@EnableAutoConfiguration(exclude = {
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class,
    MongoRepositoriesAutoConfiguration.class,
    RedissonAutoConfigurationV2.class
})
public class UserSteps {

    public static final Map<String, Map<String, Object>> CUSTOM_TOKENS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class MockSecurityConfig {
        @Bean
        public CucumberScenarioContext cucumberScenarioContext() {
            return new CucumberScenarioContext();
        }

        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            return token -> {
                if (token.contains("expired") || token.contains("invalid")) {
                    throw new BadJwtException("Jwt expired");
                }
                Map<String, Object> customClaims = CUSTOM_TOKENS.get(token);
                if (customClaims != null) {
                    String sub = String.valueOf(customClaims.getOrDefault("sub", "550e8400-e29b-41d4-a716-446655440000"));
                    String email = String.valueOf(customClaims.getOrDefault("email", "jesper@example.com"));
                    String name = String.valueOf(customClaims.getOrDefault("name", "Jesper Kock"));
                    String preferredUsername = String.valueOf(customClaims.getOrDefault("preferred_username", name));
                    String issuer = String.valueOf(customClaims.getOrDefault("iss", "http://127.0.0.1:8085"));
                    @SuppressWarnings("unchecked")
                    List<String> customRoles = (List<String>) customClaims.getOrDefault("roles", List.of("client"));

                    return Jwt.withTokenValue(token)
                            .header("alg", "RS256")
                            .claim("iss", issuer)
                            .claim("sub", sub)
                            .claim("preferred_username", preferredUsername)
                            .claim("email", email)
                            .claim("name", name)
                            .claim("realm_access", Map.of("roles", customRoles))
                            .issuedAt(Instant.now().minusSeconds(60))
                            .expiresAt(Instant.now().plusSeconds(3600))
                            .build();
                }

                String subject = "550e8400-e29b-41d4-a716-446655440000";
                if (token.startsWith("valid-therapist-token-for-")) {
                    subject = token.substring("valid-therapist-token-for-".length());
                } else if (token.startsWith("valid-client-token-for-")) {
                    subject = token.substring("valid-client-token-for-".length());
                } else if (token.startsWith("valid-token-for-")) {
                    subject = token.substring("valid-token-for-".length());
                } else if (token.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                    subject = token;
                }

                List<String> roles = List.of("therapist");
                if (token.startsWith("valid-client-token-for-")
                        || ("valid-token-for-550e8400-e29b-41d4-a716-446655440000".equals(token)
                                && subject.equals("550e8400-e29b-41d4-a716-446655440000"))) {
                    roles = List.of("client");
                }

                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claim("sub", subject)
                        .claim("realm_access", Map.of("roles", roles))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CucumberScenarioContext context;

    @Autowired
    private DomainScenarioState state;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformCacheService cacheService;

    @Autowired
    private PlatformEventService eventService;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Before(order = 10000)
    public void setup() {
        RestAssured.port = port;
        Assertions.assertNotNull(securityFilterChain);
        Assertions.assertNotNull(jwtDecoder);
        if (context != null) {
            context.resetScenarioState();
        }
        state.reset();
        cacheService.clearAll();
        eventService.clearEvents();
    }

    @Before(order = 20500)
    public void cleanUsers() {
        userRepository.deleteAll();
    }

    public UUID ensureRegisteredUser(String username, String fullName, String email, String role) {
        UUID existing = state.getUserIdsByUsername().get(username);
        if (existing != null) {
            return existing;
        }
        UserEntity entity = userRepository.findAll().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst()
                .orElseGet(() -> userRepository.save(new UserEntity(
                        null,
                        username,
                        passwordEncoder.encode("SecurePassword123!"),
                        role.toUpperCase()
                )));
        UUID uuid = entity.getId();
        state.getUserIdsByUsername().put(username, uuid);
        state.getUserRolesByUsername().put(username, role.toUpperCase());
        state.getUserFullNamesByUsername().put(username, fullName);
        state.getUserEmailsByUsername().put(username, email);

        Map<String, Object> profilePayload = new LinkedHashMap<>();
        profilePayload.put("userId", uuid.toString());
        profilePayload.put("fullName", fullName);
        profilePayload.put("dateOfBirth", "1990-01-01");
        profilePayload.put("homeAddress", "Vestergade 10");
        profilePayload.put("postalCode", "1456");
        profilePayload.put("phoneNumber", "+45 20 30 40 50");
        profilePayload.put("email", email);
        io.restassured.RestAssured.given()
                .contentType("application/json")
                .body(profilePayload)
                .when()
                .post("/api/v1/public/client-profile");

        return uuid;
    }

    @Given("an authenticated administrator session")
    public void authenticatedAdministratorSession() {
        UUID adminUuid = ensureRegisteredUser("platform_admin", "Platform Admin", "admin@klinik.dk", "ADMIN");
        state.setActiveBearerToken(
                state.issueToken(adminUuid, "platform_admin", "Platform Admin", "admin@klinik.dk", List.of("admin"), "http://127.0.0.1:8085")
        );
    }

    @Given("an authenticated therapist session for {string}")
    public void authenticatedTherapistSessionFor(String username) {
        String fullName = state.getUserFullNamesByUsername().getOrDefault(username, username);
        String email = state.getUserEmailsByUsername().getOrDefault(username, username + "@klinik.dk");
        UUID uuid = ensureRegisteredUser(username, fullName, email, "THERAPIST");
        state.setActiveBearerToken(
                state.issueToken(uuid, username, fullName, email, List.of("therapist"), "http://127.0.0.1:8085")
        );
    }

    @Given("an authenticated client session for {string}")
    public void authenticatedClientSessionFor(String username) {
        String fullName = state.getUserFullNamesByUsername().getOrDefault(username, username);
        String email = state.getUserEmailsByUsername().getOrDefault(username, username + "@example.dk");
        UUID uuid = ensureRegisteredUser(username, fullName, email, "CLIENT");
        state.setActiveBearerToken(
                state.issueToken(uuid, username, fullName, email, List.of("client"), "http://127.0.0.1:8085")
        );
    }

    @Given("an unauthenticated visitor session")
    public void unauthenticatedVisitorSession() {
        state.setActiveBearerToken(null);
    }

    @Given("a registered user account exists with username {string}, full name {string}, email {string}, and role {string}")
    public void registeredUserAccountExists(String username, String fullName, String email, String role) {
        ensureRegisteredUser(username, fullName, email, role);
    }

    @When("registering a new user account with username {string}, full name {string}, email {string}, password {string}, and role {string}")
    public void registerNewUserAccount(String username, String fullName, String email, String password, String role) {
        Map<String, Object> body = Map.of(
                "username", username,
                "password", password,
                "role", role
        );
        Response res = state.authenticatedRequest()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/v1/users");
        state.setLastResponse(res);
        if (res.statusCode() == 200 || res.statusCode() == 201) {
            String id = res.jsonPath().getString("id");
            if (id != null) {
                state.getUserIdsByUsername().put(username, UUID.fromString(id));
            }
            state.getUserRolesByUsername().put(username, role.toUpperCase());
            state.getUserFullNamesByUsername().put(username, fullName);
            state.getUserEmailsByUsername().put(username, email);
        }
    }

    @Then("the user account registration should succeed")
    public void assertUserAccountRegistrationSucceeded() {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertTrue(
                res.statusCode() == 200 || res.statusCode() == 201,
                "Expected 200/201 but got " + res.statusCode() + ": " + res.asString()
        );
    }

    @Then("the user directory should include username {string} with role {string}")
    public void assertUserDirectoryIncludes(String username, String expectedRole) {
        Response res = state.authenticatedRequest()
                .when()
                .get("/api/users");
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        List<Map<String, Object>> users = res.jsonPath().getList("$");
        boolean found = users.stream().anyMatch(u ->
                username.equals(u.get("username")) && expectedRole.equalsIgnoreCase(String.valueOf(u.get("role")))
        );
        Assertions.assertTrue(found, "Expected directory to include " + username + " (" + expectedRole + ") in " + users);
    }

    @Then("no password hash or secret credential should be exposed in the account record")
    public void assertNoPasswordExposed() {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        String body = res.asString();
        Assertions.assertFalse(body.contains("\"password\""), "Password field must not be exposed: " + body);
        Assertions.assertFalse(body.contains("SecurePassword123!"), "Raw password must not be exposed: " + body);
    }

    @When("the user account {string} is deleted")
    public void deleteUserAccount(String username) {
        UUID userId = state.getUserIdsByUsername().get(username);
        Assertions.assertNotNull(userId, "Unknown user to delete: " + username);
        Response res = state.authenticatedRequest()
                .when()
                .delete("/api/v1/users/" + userId);
        state.setLastResponse(res);
    }

    @Then("the account removal should complete successfully")
    public void assertAccountRemovalSucceeded() {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertTrue(
                res.statusCode() == 200 || res.statusCode() == 204,
                "Expected 200/204 but got " + res.statusCode() + ": " + res.asString()
        );
    }

    @Then("the user directory should not include username {string}")
    public void assertUserDirectoryExcludes(String username) {
        Response res = state.authenticatedRequest()
                .when()
                .get("/api/v1/users");
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        List<Map<String, Object>> users = res.jsonPath().getList("$");
        boolean found = users.stream().anyMatch(u -> username.equals(u.get("username")));
        Assertions.assertFalse(found, "Expected directory NOT to include " + username);
    }

    @When("attempting to view the user directory")
    public void attemptViewUserDirectory() {
        state.setLastResponse(state.authenticatedRequest().when().get("/api/v1/users"));
    }

    @When("attempting to view appointments")
    public void attemptViewAppointments() {
        state.setLastResponse(state.authenticatedRequest().when().get("/api/v1/appointments"));
    }

    @When("attempting to view client journal entries for {string}")
    public void attemptViewClientJournalEntries(String username) {
        UUID clientUuid = state.getUserIdsByUsername().getOrDefault(username, UUID.randomUUID());
        state.setLastResponse(state.authenticatedRequest().when().get("/api/v1/journals/client/" + clientUuid));
    }

    @Then("access should be denied as unauthenticated")
    public void assertDeniedAsUnauthenticated() {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertEquals(401, res.statusCode(), "Expected 401 Unauthorized but got " + res.statusCode() + ": " + res.asString());
    }

    @Then("access should be denied due to insufficient permissions")
    public void assertDeniedInsufficientPermissions() {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertEquals(403, res.statusCode(), "Expected 403 Forbidden but got " + res.statusCode() + ": " + res.asString());
    }

    @Then("the operation should be rejected with a validation error mentioning {string}")
    public void assertRejectedWithValidationError(String expectedText) {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertEquals(400, res.statusCode(), "Expected 400 Bad Request but got " + res.statusCode() + ": " + res.asString());
        Assertions.assertTrue(
                res.asString().toLowerCase().contains(expectedText.toLowerCase()),
                "Expected error body to mention '" + expectedText + "' but got: " + res.asString()
        );
    }

    @When("the signed-in user views their personal profile")
    public void viewSignedInPersonalProfile() {
        state.setLastResponse(state.authenticatedRequest().when().get("/api/v1/user/profile"));
    }

    @Then("the personal profile should belong to {string} with role {string}")
    public void assertPersonalProfileBelongsTo(String username, String expectedRole) {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        String expectedFullName = state.getUserFullNamesByUsername().getOrDefault(username, username);
        String actualName = res.jsonPath().getString("name");
        Assertions.assertTrue(
                expectedFullName.equals(actualName) || username.equals(actualName),
                "Expected profile name " + expectedFullName + " or " + username + " but got " + actualName
        );
        Assertions.assertEquals(expectedRole.toUpperCase(), res.jsonPath().getString("primaryRole"));
    }

    @When("the signed-in user updates their personal profile full name to {string} and phone to {string}")
    public void updateSignedInPersonalProfile(String newFullName, String newPhone) {
        Response meRes = state.authenticatedRequest().when().get("/api/v1/clinic/me");
        Assertions.assertEquals(200, meRes.statusCode(), meRes.asString());
        String userUuid = meRes.jsonPath().getString("userUuid");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userUuid);
        payload.put("fullName", newFullName);
        payload.put("dateOfBirth", "1990-01-01");
        payload.put("homeAddress", "Vestergade 10");
        payload.put("postalCode", "1456");
        payload.put("phoneNumber", newPhone);
        payload.put("email", meRes.jsonPath().getString("email"));

        Response res = state.authenticatedRequest()
                .contentType("application/json")
                .body(payload)
                .when()
                .put("/api/v1/clinic/client-profile");
        state.setLastResponse(res);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
    }

    @Then("the personal profile full name should be {string}")
    public void assertPersonalProfileFullName(String expectedFullName) {
        Response meRes = state.authenticatedRequest().when().get("/api/v1/clinic/me");
        Assertions.assertEquals(200, meRes.statusCode(), meRes.asString());
        String userUuid = meRes.jsonPath().getString("userUuid");
        Response profileRes = state.authenticatedRequest().when().get("/api/v1/clinic/client-profile/" + userUuid);
        state.setLastResponse(profileRes);
        Assertions.assertEquals(200, profileRes.statusCode(), profileRes.asString());
        Assertions.assertEquals(expectedFullName, profileRes.jsonPath().getString("fullName"));
    }

    @Given("an identity token with subject {string}, username {string}, and roles {string} issued by an untrusted authority")
    public void identityTokenUntrustedAuthority(String subject, String username, String roles) {
        String token = "invalid-issuer-token-" + subject;
        state.setActiveBearerToken(token);
    }

    @Given("an identity token with subject {string}, username {string}, and roles {string} issued by the trusted authority")
    public void identityTokenTrustedAuthority(String subject, String username, String roles) {
        String token = "custom-jwt-" + subject + "-" + UUID.randomUUID();
        String fullName = state.getUserFullNamesByUsername().getOrDefault(username, username);
        String email = state.getUserEmailsByUsername().getOrDefault(username, username + "@example.dk");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);
        claims.put("preferred_username", username);
        claims.put("name", fullName);
        claims.put("email", email);
        claims.put("iss", "http://127.0.0.1:8085");
        claims.put("roles", List.of(roles.split(",")));
        CUSTOM_TOKENS.put(token, claims);
        state.setActiveBearerToken(token);
    }

    @When("checking the current user profile with that identity token")
    public void checkCurrentUserProfileWithIdentityToken() {
        state.setLastResponse(state.authenticatedRequest().when().get("/api/v1/user/profile"));
    }
}
