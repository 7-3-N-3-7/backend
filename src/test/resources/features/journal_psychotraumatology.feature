Feature: Client Journaling and Psychotraumatology Test Management
  As a Client or Therapist on the INTEGRATE platform
  I want to submit and retrieve journal entries and test results stored in Business PostgreSQL
  So that treatment progress and assessments are securely tracked

  Background:
    Given Business PostgreSQL database is active
    And Hibernate ORM mappings use UUID primary keys

  Scenario: Client creates a new journal entry
    Given an authenticated client with UUID "550e8400-e29b-41d4-a716-446655440000"
    When the client posts a new journal titled "Daily Assessment" with body "Feeling calm and positive"
    Then the journal entry should be saved in Business PostgreSQL with a generated UUID
    And cached in Redis via Redisson for rapid retrieval

  Scenario: Therapist views client psychotraumatology test results
    Given an authenticated therapist with UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    And a client psychotraumatology test result referencing client UUID "550e8400-e29b-41d4-a716-446655440000" and therapist UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    When the therapist requests test results for client "550e8400-e29b-41d4-a716-446655440000"
    Then the response should include the detailed score and timestamp
