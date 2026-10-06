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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class})
public class UserCreationSteps {

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
                    throw new JwtException("Jwt expired");
                }
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
