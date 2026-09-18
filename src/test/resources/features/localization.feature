Feature: Dynamic Multilingual UI Localization and Dictionary Management via MongoDB
  As a platform administrator or developer
  I want UI strings, navigation menus, tooltips, and error messages stored as JSON documents in MongoDB
  So that text corrections, new translations, or new languages can be deployed instantly without rebuilding or restarting the application

  Background:
    Given MongoDB is running and connected on port 27017
    And the database "integrate_mongo" has collection "i18n_dictionaries"

  # =========================================================================
  # 1. DICTIONARY FETCHING & FALLBACK SCENARIOS
  # =========================================================================

  Scenario: Fetch Danish locale dictionary containing UI key-value pairs
    Given a MongoDB document in "i18n_dictionaries" for locale "da":
      """
      {
        "locale": "da",
        "translations": {
          "nav_overview": "Overblik",
          "nav_booking": "Booking",
          "nav_cases": "Sagsbehandling",
          "greeting_banner": "GOD AFTEN, JESPER",
          "action_outcome": "Outcome",
          "action_tasks": "Opgaver",
          "action_new_journal": "Ny journal",
          "action_messages": "Beskeder",
          "upcoming_appointments_empty": "Ingen planlagte aftaler i de næste 7 dage"
        }
      }
      """
    When the frontend sends a GET request to "/api/v1/i18n/da"
    Then the HTTP status code should be 200 OK
    And the response body should contain JSON dictionary mapping "nav_overview" to "Overblik"
    And the response body should contain JSON dictionary mapping "greeting_banner" to "GOD AFTEN, JESPER"

  Scenario: Fallback to English when requested locale is not found
    Given MongoDB contains locale "en" dictionary but does not contain locale "de"
    When the frontend sends a GET request to "/api/v1/i18n/de"
    Then the system should fall back to English locale "en"
    And return HTTP 200 OK with the English translation dictionary
    And the response header "X-Locale-Fallback" should be "en"

  # =========================================================================
  # 2. LIVE DICTIONARY UPDATE SCENARIOS
  # =========================================================================

  Scenario: Admin updates UI translation without app deployment
    Given an existing Danish dictionary in MongoDB where "greeting_banner" is "GOD AFTEN, JESPER"
    When an admin sends a PUT request to "/api/v1/i18n/da/greeting_banner" with body:
      """
      {
        "value": "VELKOMMEN TILBAGE, JESPER"
      }
      """
    Then the response status should be 200 OK
    And subsequent GET requests to "/api/v1/i18n/da" should immediately return "VELKOMMEN TILBAGE, JESPER" for "greeting_banner"
