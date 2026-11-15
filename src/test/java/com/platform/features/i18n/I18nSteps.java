package com.platform.features.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.CucumberScenarioContext;
import com.platform.features.i18n.I18nFeature.I18nDictionary;
import com.platform.features.i18n.I18nFeature.I18nDictionaryRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;

public class I18nSteps {

    @TestConfiguration
    public static class I18nMockConfig {
        @Bean
        @Primary
        I18nDictionaryRepository i18nRepository() {
            I18nDictionaryRepository mock = Mockito.mock(I18nDictionaryRepository.class);
            Map<String, I18nDictionary> db = new ConcurrentHashMap<>();

            Mockito.when(mock.save(ArgumentMatchers.any(I18nDictionary.class))).thenAnswer(invocation -> {
                I18nDictionary dict = invocation.getArgument(0);
                db.put(dict.getLocale(), dict);
                return dict;
            });

            Mockito.when(mock.findByLocale(ArgumentMatchers.anyString())).thenAnswer(invocation -> {
                String loc = invocation.getArgument(0);
                return Optional.ofNullable(db.get(loc));
            });

            Mockito.doAnswer(invocation -> {
                I18nDictionary dict = invocation.getArgument(0);
                db.remove(dict.getLocale());
                return null;
            }).when(mock).delete(ArgumentMatchers.any(I18nDictionary.class));

            return mock;
        }
    }

    @Autowired
    private CucumberScenarioContext context;

    @Autowired
    private I18nDictionaryRepository i18nRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Given("MongoDB is running and connected on port {int}")
    public void mongo_db_is_running_and_connected_on_port(Integer port) {
        Assertions.assertEquals(27017, port);
        Assertions.assertNotNull(i18nRepository, "I18nDictionaryRepository must be initialized");
    }

    @Given("the database {string} has collection {string}")
    public void the_database_has_collection(String dbName, String collectionName) {
        Assertions.assertEquals("integrate_mongo", dbName);
        Assertions.assertEquals("i18n_dictionaries", collectionName);
    }

    @Given("a MongoDB document in {string} for locale {string}:")
    @SuppressWarnings("unchecked")
    public void a_mongo_db_document_in_for_locale(String collection, String locale, String docString) throws Exception {
        Assertions.assertEquals("i18n_dictionaries", collection);
        Map<String, Object> doc = objectMapper.readValue(docString, Map.class);
        Map<String, Object> translations = (Map<String, Object>) doc.get("translations");
        I18nDictionary dict = new I18nDictionary(locale, new HashMap<>(translations));
        i18nRepository.save(dict);
    }

    @When("the frontend sends a GET request to {string}")
    public void the_frontend_sends_a_get_request_to(String endpoint) {
        context.setLastResponse(given().when().get(endpoint));
    }

    @Then("the HTTP status code should be {int} OK")
    public void the_http_status_code_should_be_ok(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }

    @Then("the response body should contain JSON dictionary mapping {string} to {string}")
    public void the_response_body_should_contain_json_dictionary_mapping_to(String key, String expectedValue) {
        context.getLastResponse().then().body(key, Matchers.equalTo(expectedValue));
    }

    @Given("MongoDB contains locale {string} dictionary but does not contain locale {string}")
    public void mongo_db_contains_locale_dictionary_but_does_not_contain_locale(String presentLocale, String missingLocale) {
        Map<String, Object> translations = new HashMap<>();
        translations.put("fallback_key", "fallback_value");
        I18nDictionary dict = new I18nDictionary(presentLocale, translations);
        i18nRepository.save(dict);
        i18nRepository.findByLocale(missingLocale).ifPresent(i18nRepository::delete);
    }

    @Then("the system should fall back to English locale {string}")
    public void the_system_should_fall_back_to_english_locale(String expectedFallbackLocale) {
        String fallbackHeader = context.getLastResponse().getHeader("X-Locale-Fallback");
        Assertions.assertEquals(expectedFallbackLocale, fallbackHeader, "X-Locale-Fallback header must indicate fallback locale");
    }

    @Then("return HTTP {int} OK with the English translation dictionary")
    public void return_http_ok_with_the_english_translation_dictionary(Integer expectedStatus) {
        Response lastResponse = context.getLastResponse();
        lastResponse.then().statusCode(expectedStatus);
        lastResponse.then().body("fallback_key", Matchers.equalTo("fallback_value"));
    }

    @Then("the response header {string} should be {string}")
    public void the_response_header_should_be(String headerName, String expectedValue) {
        context.getLastResponse().then().header(headerName, Matchers.equalTo(expectedValue));
    }

    @Given("an existing Danish dictionary in MongoDB where {string} is {string}")
    public void an_existing_danish_dictionary_in_mongo_db_where_is(String key, String initialValue) {
        context.setRequestSpec(given().header("Authorization", "Bearer valid-token-for-admin"));
        Map<String, Object> translations = new HashMap<>();
        translations.put(key, initialValue);
        I18nDictionary dict = new I18nDictionary("da", translations);
        i18nRepository.save(dict);
    }

    @When("an admin sends a PUT request to {string} with body:")
    public void an_admin_sends_a_put_request_to_with_body(String endpoint, String docString) {
        context.setLastResponse(context.getRequestSpec()
                .contentType("application/json")
                .body(docString)
                .when()
                .put(endpoint));
    }

    @Then("subsequent GET requests to {string} should immediately return {string} for {string}")
    public void subsequent_get_requests_to_should_immediately_return_for(String endpoint, String expectedValue, String key) {
        Response lastResponse = context.getRequestSpec().when().get(endpoint);
        context.setLastResponse(lastResponse);
        lastResponse.then().statusCode(200);
        lastResponse.then().body(key, Matchers.equalTo(expectedValue));
    }
}
