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

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class UserCreationSteps {

    @TestConfiguration
    static class MockSecurityConfig {
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
