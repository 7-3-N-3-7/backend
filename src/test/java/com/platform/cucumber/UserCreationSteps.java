package com.platform.cucumber;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
<<<<<<< HEAD
<<<<<<< HEAD
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.security.oauth2.jwt.BadJwtException;
=======

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
<<<<<<< HEAD
<<<<<<< HEAD
@EnableAutoConfiguration(exclude = {
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class,
    MongoRepositoriesAutoConfiguration.class,
    RedissonAutoConfigurationV2.class
})
public class UserCreationSteps {

    public static final java.util.Map<String, java.util.Map<String, Object>> CUSTOM_TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

=======
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class})
public class UserCreationSteps {

>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class})
public class UserCreationSteps {

>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    @TestConfiguration
    static class MockSecurityConfig {
        @Bean
        @Primary
        public com.platform.repository.I18nDictionaryRepository i18nRepository() {
            com.platform.repository.I18nDictionaryRepository mock = org.mockito.Mockito.mock(com.platform.repository.I18nDictionaryRepository.class);
            java.util.Map<String, com.platform.entity.I18nDictionary> db = new java.util.concurrent.ConcurrentHashMap<>();

            org.mockito.Mockito.when(mock.save(org.mockito.ArgumentMatchers.any(com.platform.entity.I18nDictionary.class))).thenAnswer(invocation -> {
                com.platform.entity.I18nDictionary dict = invocation.getArgument(0);
                db.put(dict.getLocale(), dict);
                return dict;
            });

            org.mockito.Mockito.when(mock.findByLocale(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
                String loc = invocation.getArgument(0);
                return java.util.Optional.ofNullable(db.get(loc));
            });
            
            org.mockito.Mockito.doAnswer(invocation -> {
                com.platform.entity.I18nDictionary dict = invocation.getArgument(0);
                db.remove(dict.getLocale());
                return null;
            }).when(mock).delete(org.mockito.ArgumentMatchers.any(com.platform.entity.I18nDictionary.class));

            return mock;
        }

        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            return token -> {
                if (token.contains("expired") || token.contains("invalid")) {
<<<<<<< HEAD
<<<<<<< HEAD
                    throw new BadJwtException("Jwt expired");
                }
<<<<<<< HEAD
                java.util.Map<String, Object> customClaims = CUSTOM_TOKENS.get(token);
                if (customClaims != null) {
                    String sub = String.valueOf(customClaims.getOrDefault("sub", "550e8400-e29b-41d4-a716-446655440000"));
                    String email = String.valueOf(customClaims.getOrDefault("email", "jesper@example.com"));
                    String name = String.valueOf(customClaims.getOrDefault("name", "Jesper Kock"));
                    String issuer = String.valueOf(customClaims.getOrDefault("iss", "http://localhost:8080"));
                    @SuppressWarnings("unchecked")
                    java.util.List<String> customRoles = (java.util.List<String>) customClaims.getOrDefault("roles", java.util.List.of("client"));

                    return Jwt.withTokenValue(token)
                            .header("alg", "RS256")
                            .claim("iss", issuer)
                            .claim("sub", sub)
                            .claim("email", email)
                            .claim("name", name)
                            .claim("realm_access", java.util.Map.of("roles", customRoles))
                            .issuedAt(java.time.Instant.now().minusSeconds(60))
                            .expiresAt(java.time.Instant.now().plusSeconds(3600))
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

                java.util.List<String> roles = java.util.List.of("therapist");
                if (token.startsWith("valid-client-token-for-")
                        || ("valid-token-for-550e8400-e29b-41d4-a716-446655440000".equals(token) && subject.equals("550e8400-e29b-41d4-a716-446655440000"))) {
=======
=======
                    throw new JwtException("Jwt expired");
                }
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
                    throw new JwtException("Jwt expired");
                }
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
                String[] tokenParts = token.split("\\|");
                String tokenIdentity = tokenParts[0];
                String subject = "550e8400-e29b-41d4-a716-446655440000";
                if (tokenIdentity.startsWith("valid-token-for-")) {
                    subject = tokenIdentity.substring("valid-token-for-".length());
                } else if (tokenIdentity.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                    subject = tokenIdentity;
                }

                String email = null;
                String name = null;
                String assignedRole = null;
                for (int i = 1; i < tokenParts.length; i++) {
                    if (tokenParts[i].startsWith("email=")) {
                        email = tokenParts[i].substring("email=".length());
                    } else if (tokenParts[i].startsWith("name=")) {
                        name = tokenParts[i].substring("name=".length());
                    } else if (tokenParts[i].startsWith("role=")) {
                        assignedRole = tokenParts[i].substring("role=".length());
                    }
                }

                java.util.List<String> roles = java.util.List.of("therapist");
                if ("valid-token-for-550e8400-e29b-41d4-a716-446655440000".equals(tokenIdentity)
                        && subject.equals("550e8400-e29b-41d4-a716-446655440000")) {
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
                    roles = java.util.List.of("client");
                }
                if (assignedRole != null) {
                    roles = java.util.List.of(assignedRole.replaceFirst("(?i)^ROLE_", "")
                            .toLowerCase(java.util.Locale.ROOT));
                }

                Jwt.Builder jwt = Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claim("sub", subject)
                        .claim("realm_access", java.util.Map.of("roles", roles))
                        .issuedAt(java.time.Instant.now())
                        .expiresAt(java.time.Instant.now().plusSeconds(3600));
                if (email != null) {
                    jwt.claim("email", email);
                }
                if (name != null) {
                    jwt.claim("name", name);
                }
                return jwt.build();
            };
        }
    }

    @LocalServerPort
    private int port;

    private Response lastResponse;

    @Before
    public void setup() {
        RestAssured.port = port;
    }

    @Given("the backend server is running")
    public void the_backend_server_is_running() {
        // RestAssured is configured with the random port
    }

    @When("I send a POST request to {string} with the following JSON:")
    public void i_send_a_post_request_to_with_the_following_json(String endpoint, String jsonPayload) {
        lastResponse = 
        given()
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
}
