package com.platform.features.i18n;

import com.platform.core.DomainScenarioState;
import com.platform.features.i18n.I18nFeature.I18nDictionary;
import com.platform.features.i18n.I18nFeature.I18nDictionaryRepository;
import com.platform.features.i18n.I18nFeature.I18nService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.List;
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
    private I18nDictionaryRepository i18nRepository;

    @Autowired
    private I18nService i18nService;

    @Autowired
    private DomainScenarioState state;

    @Before(order = 20700)
    public void resetI18nDictionaries() {
        i18nRepository.findByLocale("ja").ifPresent(i18nRepository::delete);
        for (String locale : List.of("da", "en", "fr", "es", "de")) {
            i18nService.replaceDictionaryTranslations(locale, baselineTranslations(locale));
        }
    }

    private Map<String, Object> baselineTranslations(String locale) {
        Map<String, Object> portal = new LinkedHashMap<>();
        portal.put("brandTitle", "da".equals(locale) ? "EduJournal Klinik" : "EduJournal Clinic");
        portal.put("welcome", switch (locale) {
            case "da" -> "Velkommen til EduJournal";
            case "fr" -> "Bienvenue sur EduJournal";
            case "es" -> "Bienvenido a EduJournal";
            case "de" -> "Willkommen bei EduJournal";
            default -> "Welcome to EduJournal";
        });
        portal.put("tabCalendar", switch (locale) {
            case "da" -> "Månedskalender & Dagsprogram";
            case "fr" -> "Calendrier Mensuel & Programme";
            case "es" -> "Calendario Mensual y Agenda";
            case "de" -> "Monatskalender & Tagesplan";
            default -> "Monthly Calendar & Daily Schedule";
        });

        Map<String, Object> homePage = new LinkedHashMap<>();
        homePage.put("heroTitle", switch (locale) {
            case "da" -> "Reflekter, Lær, og Voks";
            case "fr" -> "Réfléchissez, Apprenez et Grandissez";
            case "es" -> "Reflexiona, Aprende y Crece";
            case "de" -> "Reflektieren, Lernen und Wachsen";
            default -> "Reflect, Learn, and Grow";
        });

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("portal", portal);
        root.put("HomePage", homePage);
        return root;
    }

    @When("requesting the localization bundle for locale {string}")
    public void requestLocalizationBundle(String locale) {
        Response res = given().when().get("/api/v1/i18n/" + locale);
        state.setLastResponse(res);
    }

    @When("requesting the localization bundle for unsupported locale {string}")
    public void requestUnsupportedLocalizationBundle(String locale) {
        Response res = given().when().get("/api/v1/i18n/" + locale);
        state.setLastResponse(res);
    }

    @Then("the localization bundle should return translations for {string} without fallback")
    public void assertLocalizationBundleWithoutFallback(String locale) {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        Assertions.assertNull(
                res.getHeader("X-Locale-Fallback"),
                "Expected no X-Locale-Fallback header for supported locale " + locale
        );
        Assertions.assertNotNull(res.jsonPath().get("portal"), "Expected portal translations for " + locale);
    }

    @Then("the localization bundle should fall back to {string}")
    public void assertLocalizationFallback(String expectedFallbackLocale) {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        Assertions.assertEquals(expectedFallbackLocale, res.getHeader("X-Locale-Fallback"));
        Assertions.assertEquals("Welcome to EduJournal", res.jsonPath().getString("portal.welcome"));
    }

    @When("the administrator updates translation key {string} in locale {string} to {string}")
    public void adminUpdatesTranslationKey(String key, String locale, String newValue) {
        Response res = state.authenticatedRequest()
                .contentType("application/json")
                .body(Map.of("value", newValue))
                .when()
                .put("/api/v1/i18n/" + locale + "/" + key);
        state.setLastResponse(res);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
    }

    @Then("requesting the localization bundle for locale {string} should map {string} to {string}")
    public void assertLocalizationKeyMapsTo(String locale, String key, String expectedValue) {
        Response res = given().when().get("/api/v1/i18n/" + locale);
        state.setLastResponse(res);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        Assertions.assertEquals(expectedValue, res.jsonPath().getString(key));
    }
}
