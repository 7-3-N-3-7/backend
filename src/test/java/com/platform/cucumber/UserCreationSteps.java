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
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claim("sub", "mock-user")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("therapist")))
                        .build();
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
