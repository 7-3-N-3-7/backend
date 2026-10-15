@api @e2e
Feature: Clinic Service Catalog, Marketplace Directory, Consent Handshakes, and Full Clinical Lifecycle
  As a Client or Clinical Therapist on the EduJournal platform
  I want to manage clinic services, browse the marketplace directory, control client-therapist consent handshakes, reschedule appointments with conflict checks, and track PCL-5 & JGit timelines
  So that every capability of the clinic platform is verified end-to-end against PostgreSQL and JGit

  Background:
    Given Business PostgreSQL database is clean and accessible
    And the clinic marketplace tables are clean in PostgreSQL

  # =========================================================================
  # 1. SERVICE CATALOG & MARKETPLACE DIRECTORY (Bilag 5.1.3)
  # =========================================================================

  Scenario: Therapist creates a clinic service, lists it in the Marketplace Directory, and toggles its active status
    Given an onboarded therapist "4c421f46-836c-4897-8dae-ba9587473273" ("Bo Jensen") in organisation "Nordic Mind Clinic ApS" with CVR "39485712"
    When the therapist "4c421f46-836c-4897-8dae-ba9587473273" creates a clinic service with:
      | name            | Traume-fokuseret EMDR Session |
      | description     | 60 min klinisk traumeterapi   |
      | durationMinutes | 60                            |
      | price           | 1150.0                        |
      | location        | København K                   |
    Then the clinic service response status should be 201
    And the created service should be active in PostgreSQL with name "Traume-fokuseret EMDR Session" and price 1150.0
    When a client fetches the clinic marketplace directory at "/api/v1/clinic/directory"
    Then the marketplace directory should contain organisation "Nordic Mind Clinic ApS", provider "Bo Jensen", and service "Traume-fokuseret EMDR Session"
    When the therapist toggles the active status of the created service
    Then the service active status in PostgreSQL should be false
    When the therapist toggles the active status of the created service
    Then the service active status in PostgreSQL should be true

  # =========================================================================
  # 2. CONSENT HANDSHAKES & FULL CLINICAL LIFECYCLE (PCL-5, RESCHEDULE, TIMELINE)
  # =========================================================================

  Scenario: Client grants, revokes, and re-activates a Therapist Consent Handshake and Therapist views enriched roster with PCL-5 scores
    Given an onboarded therapist "4c421f46-836c-4897-8dae-ba9587473273" ("Bo Jensen") in organisation "Nordic Mind Clinic ApS" with CVR "39485712"
    And a registered client profile for "5e357ca1-4913-414f-91a6-81a7de9042ab" named "Anders Sand" with email "client@mail.com"
    When client "5e357ca1-4913-414f-91a6-81a7de9042ab" grants an "ACTIVE" consent handshake to therapist "4c421f46-836c-4897-8dae-ba9587473273"
    Then the handshake record in PostgreSQL should have status "ACTIVE"
    When the handshake status is updated to "REVOKED"
    Then the handshake record in PostgreSQL should have status "REVOKED"
    When the handshake status is updated to "ACTIVE"
    Then the handshake record in PostgreSQL should have status "ACTIVE"
    Given a completed assessment test for client UUID "5e357ca1-4913-414f-91a6-81a7de9042ab"
    When the assessment result is posted to "/api/v1/psychotraumatology/tests" with body:
      """
      {
        "clientUuid": "5e357ca1-4913-414f-91a6-81a7de9042ab",
        "therapistUuid": "4c421f46-836c-4897-8dae-ba9587473273",
        "testType": "PCL-5",
        "score": 38,
        "severity": "Moderat til alvorlig",
        "answersJson": "{\"q1\":3,\"q2\":4,\"q3\":3}"
      }
      """
    Then the HTTP status code should be 200 OK
    When client "5e357ca1-4913-414f-91a6-81a7de9042ab" fetches their own PCL-5 test history
    Then the PCL-5 test list should contain 1 assessment with score 38 and severity "Moderat til alvorlig"
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" requests their clinic overview
    Then the therapist overview client roster should include "5e357ca1-4913-414f-91a6-81a7de9042ab" with handshake "ACTIVE" and PCL-5 score 38

  Scenario: Rescheduling an appointment updates the slot when free and returns 409 Conflict when overlapping another booking
    Given an existing confirmed appointment for therapist "4c421f46-836c-4897-8dae-ba9587473273" from "2026-10-20T14:00:00" to "2026-10-20T15:00:00"
    And a scheduled appointment for client "5e357ca1-4913-414f-91a6-81a7de9042ab" with therapist "4c421f46-836c-4897-8dae-ba9587473273" from "2026-10-20T09:00:00" to "2026-10-20T10:00:00"
    When the client reschedules the appointment to "2026-10-20T11:00:00" through "2026-10-20T12:00:00"
    Then the rescheduled appointment response status should be 200
    And the appointment start time in PostgreSQL should be "2026-10-20T11:00:00"
    When the client reschedules the appointment to "2026-10-20T14:00:00" through "2026-10-20T15:00:00"
    Then the rescheduled appointment response status should be 409

  @rbac
  Scenario: JGit markdown timeline endpoint strips INTERNAL_MEDICAL_NOTES for clients while preserving them for therapists
    Given a valid therapist with UUID "4c421f46-836c-4897-8dae-ba9587473273"
    When the therapist creates a shared journal entry for client "5e357ca1-4913-414f-91a6-81a7de9042ab" with title "Fælles statusnotat" and content "Klienten arbejder godt med vejrtrækningsøvelser."
    Then the response status should be 200 OK
    When the therapist creates a confidential clinical note for client "5e357ca1-4913-414f-91a6-81a7de9042ab" with text "Fortrolig klinisk vurdering kun for terapeut."
    Then the response status should be 200 OK
    When client "5e357ca1-4913-414f-91a6-81a7de9042ab" requests their JGit markdown timeline at "/api/v1/journals/timeline/5e357ca1-4913-414f-91a6-81a7de9042ab"
    Then the timeline response should have status 200 and contain "Klienten arbejder godt med vejrtrækningsøvelser."
    And the timeline response should NOT contain "Fortrolig klinisk vurdering kun for terapeut."
    And the timeline response should NOT contain "INTERNAL_MEDICAL_NOTES"
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" requests the JGit markdown timeline at "/api/v1/journals/timeline/5e357ca1-4913-414f-91a6-81a7de9042ab"
    Then the timeline response should have status 200 and contain "Fortrolig klinisk vurdering kun for terapeut."
    And the timeline response should have status 200 and contain "INTERNAL_MEDICAL_NOTES"
