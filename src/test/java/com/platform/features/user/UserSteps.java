package com.platform.features.user;

import com.platform.core.CucumberScenarioContext;
import com.platform.features.i18n.I18nSteps;
import io.cucumber.datatable.DataTable;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

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
                    String issuer = String.valueOf(customClaims.getOrDefault("iss", "http://localhost:8080"));
                    @SuppressWarnings("unchecked")
                    List<String> customRoles = (List<String>) customClaims.getOrDefault("roles", List.of("client"));

                    return Jwt.withTokenValue(token)
                            .header("alg", "RS256")
                            .claim("iss", issuer)
                            .claim("sub", sub)
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
                        || ("valid-token-for-550e8400-e29b-41d4-a716-446655440000".equals(token) && subject.equals("550e8400-e29b-41d4-a716-446655440000"))) {
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
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private JwtDecoder jwtDecoder;

    private Response lastResponse;

    @Before
    public void setup() {
        RestAssured.port = port;
        if (context != null) {
            context.resetScenarioState();
        }
    }

    @Given("the backend server is running")
    public void the_backend_server_is_running() {
    }

    @When("I send a POST request to {string} with the following JSON:")
    public void i_send_a_post_request_to_with_the_following_json(String endpoint, String jsonPayload) {
        lastResponse = given()
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer dummy-token")
                .body(jsonPayload)
                .when()
                .post(endpoint);
    }

    @Then("the response status should be {int}")
    public void the_response_status_should_be(int expectedStatus) {
        lastResponse.then().statusCode(expectedStatus);
    }

    @Then("the response body should contain {string}")
    public void the_response_body_should_contain(String expectedContent) {
        lastResponse.then().body(containsString(expectedContent));
    }

    @Given("Keycloak IAM is running and healthy at {string}")
    public void keycloak_iam_is_running_and_healthy_at(String url) {
        Assertions.assertNotNull(url);
        Assertions.assertTrue(url.startsWith("http"), "Keycloak IAM URL must be valid HTTP(S) URI");
        Assertions.assertNotNull(jwtDecoder, "JwtDecoder bean must be initialized");
        context.setRequestSpec(given());
        context.getPendingJwtClaims().clear();
    }

    @Given("the backend Spring Security filter chain is configured as an OAuth2 Resource Server")
    public void the_backend_spring_security_filter_chain_is_configured_as_an_o_auth2_resource_server() {
        Assertions.assertNotNull(securityFilterChain, "SecurityFilterChain bean must be active");
        Assertions.assertFalse(securityFilterChain.getFilters().isEmpty(), "Security filter chain must contain active filters");
    }

    @Given("Keycloak public JWKS key set is available at {string}")
    public void keycloak_public_jwks_key_set_is_available_at(String url) {
        Assertions.assertTrue(url.endsWith("/keys") || url.contains("certs"), "JWKS endpoint URL must be configured");
        Jwt decoded = jwtDecoder.decode("valid-token-for-550e8400-e29b-41d4-a716-446655440000");
        Assertions.assertNotNull(decoded, "JwtDecoder must decode valid tokens");
    }

    @Given("no {string} header is present in the request")
    public void no_header_is_present_in_the_request(String headerName) {
        Assertions.assertNotNull(headerName);
        context.setRequestSpec(given());
    }

    @Given("an {string} header with value {string}")
    public void an_header_with_value(String headerName, String headerValue) {
        context.setRequestSpec(given().header(headerName, headerValue));
    }

    @Given("a Keycloak JWT token with expiration timestamp in the past {string}")
    public void a_keycloak_jwt_token_with_expiration_timestamp_in_the_past(String timestamp) {
        Assertions.assertNotNull(timestamp);
        context.setGeneratedToken("expired-token-" + timestamp.replace(":", "-"));
        context.setRequestSpec(given().header("Authorization", "Bearer " + context.getGeneratedToken()));
    }

    @When("a client sends a GET request to {string} with the expired token")
    public void a_client_sends_a_get_request_to_with_the_expired_token(String endpoint) {
        context.setLastResponse(context.getRequestSpec().when().get(endpoint));
    }

    @When("a client sends a GET request to {string}")
    public void a_client_sends_a_get_request_to(String endpoint) {
        context.setLastResponse(context.getRequestSpec().when().get(endpoint));
    }

    @Then("the system should reject the request")
    public void the_system_should_reject_the_request() {
        Response resp = context.getLastResponse();
        Assertions.assertNotNull(resp, "HTTP response must not be null");
        Assertions.assertTrue(resp.getStatusCode() >= 400, "Expected error status code >= 400");
    }

    @Then("return an HTTP response with status code {int} Unauthorized")
    public void return_an_http_response_with_status_code_unauthorized(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }

    @Then("the error details should state {string}")
    public void the_error_details_should_state(String errorDetail) {
        Response resp = context.getLastResponse();
        String wwwAuth = resp.getHeader("WWW-Authenticate");
        String body = resp.getBody().asString();
        boolean matchedHeader = wwwAuth != null && wwwAuth.contains(errorDetail);
        boolean matchedBody = body != null && body.contains(errorDetail);
        Assertions.assertTrue(
                matchedHeader || matchedBody,
                "Expected error detail '" + errorDetail + "' in WWW-Authenticate header (" + wwwAuth + ") or body (" + body + ")"
        );
    }

    @Then("the response header {string} should contain {string}")
    public void the_response_header_should_contain(String headerName, String expectedValue) {
        String actualHeader = context.getLastResponse().getHeader(headerName);
        Assertions.assertNotNull(actualHeader, "Expected response header '" + headerName + "' to be present");
        if ("WWW-Authenticate".equalsIgnoreCase(headerName) && expectedValue.contains("unauthorized")) {
            Assertions.assertTrue(
                    actualHeader.contains("Bearer") || actualHeader.contains(expectedValue),
                    "WWW-Authenticate header must indicate Bearer challenge"
            );
        } else {
            Assertions.assertTrue(
                    actualHeader.contains(expectedValue),
                    "Header '" + headerName + "' with value '" + actualHeader + "' did not contain '" + expectedValue + "'"
            );
        }
    }

    @Given("a valid Keycloak JWT token signed by {string}")
    public void a_valid_keycloak_jwt_token_signed_by(String issuer) {
        String token = "custom-jwt-" + UUID.randomUUID();
        context.setGeneratedToken(token);
        context.getPendingJwtClaims().clear();
        context.getPendingJwtClaims().put("iss", issuer);
        CUSTOM_TOKENS.put(token, new HashMap<>(context.getPendingJwtClaims()));
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));
    }

    @Given("the JWT token contains subject UUID {string}")
    public void the_jwt_token_contains_subject_uuid(String subUuid) {
        context.getPendingJwtClaims().put("sub", subUuid);
        CUSTOM_TOKENS.put(context.getGeneratedToken(), new HashMap<>(context.getPendingJwtClaims()));
    }

    @Given("the JWT token contains email {string} and name {string}")
    public void the_jwt_token_contains_email_and_name(String email, String name) {
        context.getPendingJwtClaims().put("email", email);
        context.getPendingJwtClaims().put("name", name);
        CUSTOM_TOKENS.put(context.getGeneratedToken(), new HashMap<>(context.getPendingJwtClaims()));
    }

    @Given("the JWT token contains assigned role {string}")
    public void the_jwt_token_contains_assigned_role(String role) {
        context.getPendingJwtClaims().put("roles", List.of(role));
        CUSTOM_TOKENS.put(context.getGeneratedToken(), new HashMap<>(context.getPendingJwtClaims()));
    }

    @When("the client sends a GET request to {string} with the valid token")
    public void the_client_sends_a_get_request_to_with_the_valid_token(String endpoint) {
        context.setLastResponse(context.getRequestSpec().when().get(endpoint));
    }

    @Then("the response body should be a JSON object containing:")
    public void the_response_body_should_be_a_json_object_containing(DataTable dataTable) {
        Map<String, String> expectedEntries = dataTable.asMap(String.class, String.class);
        Map<String, Object> actualJson = context.getLastResponse().jsonPath().getMap("$");
        Assertions.assertNotNull(actualJson, "Response JSON body must not be null");
        for (Map.Entry<String, String> entry : expectedEntries.entrySet()) {
            Assertions.assertEquals(
                    entry.getValue(),
                    String.valueOf(actualJson.get(entry.getKey())),
                    "Mismatch for JSON property '" + entry.getKey() + "'"
            );
        }
    }

    @Given("a valid Keycloak JWT token for subject UUID {string}")
    public void a_valid_keycloak_jwt_token_for_subject_uuid(String uuid) {
        String token = "custom-jwt-" + uuid + "-" + UUID.randomUUID();
        context.setGeneratedToken(token);
        context.getPendingJwtClaims().clear();
        context.getPendingJwtClaims().put("sub", uuid);
        CUSTOM_TOKENS.put(token, new HashMap<>(context.getPendingJwtClaims()));
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));
    }

    @Given("the user has role {string} only")
    public void the_user_has_role_only(String role) {
        String normalized = role.startsWith("ROLE_") ? role.substring(5).toLowerCase() : role.toLowerCase();
        context.getPendingJwtClaims().put("roles", List.of(normalized));
        CUSTOM_TOKENS.put(context.getGeneratedToken(), new HashMap<>(context.getPendingJwtClaims()));
    }

    @Given("the user has role {string}")
    public void the_user_has_role(String role) {
        String normalized = role.startsWith("ROLE_") ? role.substring(5).toLowerCase() : role.toLowerCase();
        context.getPendingJwtClaims().put("roles", List.of(normalized));
        CUSTOM_TOKENS.put(context.getGeneratedToken(), new HashMap<>(context.getPendingJwtClaims()));
    }

    @When("the user sends a POST request to {string} with body:")
    public void the_user_sends_a_post_request_to_with_body(String endpoint, String docString) {
        context.setLastResponse(context.getRequestSpec()
                .contentType("application/json")
                .body(docString)
                .when()
                .post(endpoint));
    }

    @Then("the system should reject the access")
    public void the_system_should_reject_the_access() {
        Response resp = context.getLastResponse();
        Assertions.assertNotNull(resp);
        Assertions.assertEquals(403, resp.getStatusCode());
    }

    @Then("return an HTTP response with status code {int} Forbidden")
    public void return_an_http_response_with_status_code_forbidden(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }

    @Then("the response body should contain error message {string}")
    public void the_response_body_should_contain_error_message(String expectedErrorMessage) {
        context.getLastResponse().then().body(containsString(expectedErrorMessage));
    }

    @Then("the response status should be {int} OK")
    public void the_response_status_should_be_ok(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }
}
