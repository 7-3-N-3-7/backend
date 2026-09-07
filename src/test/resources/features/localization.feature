Feature: Dynamic Multilingual UI Localization via MongoDB
  As a platform frontend client
  I want to fetch structured translation dictionaries from MongoDB
  So that UI text, tooltips, and navigation labels can be instantly updated without changing app code

  Background:
    Given MongoDB is connected to the backend
    And the localization collection contains dictionary documents for "da" and "en"

  Scenario: Fetch Danish UI dictionary
    Given the Danish locale dictionary exists in MongoDB with key "welcome_greeting" and value "GOD AFTEN, JESPER"
    When the frontend sends a GET request to "/api/v1/i18n/da"
    Then the response status should be 200 OK
    And the response dictionary should map "welcome_greeting" to "GOD AFTEN, JESPER"

  Scenario: Fetch English fallback locale dictionary
    Given the English locale dictionary exists in MongoDB with key "welcome_greeting" and value "GOOD EVENING, JESPER"
    When the frontend sends a GET request to "/api/v1/i18n/en"
    Then the response status should be 200 OK
    And the response dictionary should map "welcome_greeting" to "GOOD EVENING, JESPER"
