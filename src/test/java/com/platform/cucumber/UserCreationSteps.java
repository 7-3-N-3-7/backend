package com.platform.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class UserCreationSteps {

    @LocalServerPort
    private int port;

    private HttpResponse<String> lastResponse;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    // We will use the admin credentials seeded by DataSeeder for the API calls
    private final String authHeader = "Basic " + Base64.getEncoder().encodeToString("therapist:password".getBytes());

    @Given("the backend server is running")
    public void the_backend_server_is_running() {
        assertTrue(port > 0, "Server should be running on a random port");
    }

    @When("I send a POST request to {string} with the following JSON:")
    public void i_send_a_post_request_to_with_the_following_json(String endpoint, String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + endpoint))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        lastResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Then("the response status should be {int}")
    public void the_response_status_should_be(int expectedStatus) {
        assertEquals(expectedStatus, lastResponse.statusCode(), 
            "Expected status " + expectedStatus + " but got " + lastResponse.statusCode() + ". Body: " + lastResponse.body());
    }

    @Then("the response body should contain {string}")
    public void the_response_body_should_contain(String expectedContent) {
        assertTrue(lastResponse.body().contains(expectedContent), 
            "Response body did not contain expected content. Body: " + lastResponse.body());
    }
}
