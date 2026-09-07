Feature: Clinical Journaling, Psychotraumatology Assessment Tests, and Audit Logging
  As a Therapist or Client on the INTEGRATE platform
  I want client journal entries and psychotraumatology test results persisted with strict relational integrity
  So that medical record confidentiality is maintained and progress scores are tracked over time

  Background:
    Given Business PostgreSQL database is active
    And Hibernate prepared statements are enabled for SQL injection defense

  # =========================================================================
  # 1. CLIENT JOURNAL ENTRIES & CACHING SCENARIOS
  # =========================================================================

  Scenario: Client creates a private journal entry
    Given an authenticated client with UUID "550e8400-e29b-41d4-a716-446655440000"
    When the client posts a new journal entry to "/api/v1/journals" with body:
      """
      {
        "title": "Dagbog - Uge 37",
        "content": "Jeg har haft en god uge uden angst symptomer.",
        "moodRating": 8
      }
      """
    Then the response status should be 200 OK
    And the saved entity in PostgreSQL should have a valid generated UUID
    And the journal entry should be cached in Redisson Redis under key "journal:client:550e8400-e29b-41d4-a716-446655440000"

  # =========================================================================
  # 2. PSYCHOTRAUMATOLOGY TEST RESULTS & DUAL UUID REFERENCES
  # =========================================================================

  Scenario: Submit psychotraumatology assessment test linking Client and Therapist UUIDs
    Given a completed assessment test for client UUID "550e8400-e29b-41d4-a716-446655440000"
    And supervising therapist UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    When the assessment result is posted to "/api/v1/psychotraumatology/tests" with body:
      """
      {
        "clientUuid": "550e8400-e29b-41d4-a716-446655440000",
        "therapistUuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "testType": "PCL-5-PTSD-ASSESSMENT",
        "score": 24,
        "severity": "MODERATE",
        "answersJson": "{\"q1\": 2, \"q2\": 3, \"q3\": 1}"
      }
      """
    Then the HTTP status code should be 200 OK
    And the assessment record in PostgreSQL must reference client UUID "550e8400-e29b-41d4-a716-446655440000" and therapist UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

  Scenario: Prevent unauthorized client from viewing another client's psychotraumatology test scores
    Given client A with UUID "550e8400-e29b-41d4-a716-446655440000"
    And client B with UUID "11111111-2222-3333-4444-555555555555"
    When client A attempts to access GET "/api/v1/psychotraumatology/tests/client/11111111-2222-3333-4444-555555555555"
    Then the system should deny access with HTTP status 403 Forbidden
