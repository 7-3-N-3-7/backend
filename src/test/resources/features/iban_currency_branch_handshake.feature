@api @e2e @finance @jgit @rbac
Feature: Therapist Mandatory IBAN Setup, Java.util.Currency Support, Service Booking, and Single-Branch JGit Handshake Restrictions
  As a Clinical Therapist and Client on the EduJournal platform
  I want mandatory IBAN and java.util.Currency configuration in clinic setup before creating services, and granular JGit single-branch and custom permission restrictions on client-therapist handshakes
  So that therapists always have a valid payout IBAN & ISO 4217 currency configured and clients can strictly limit a therapist to seeing only 1 of their JGit branches

  Background:
    Given Business PostgreSQL database is clean and accessible
    And the clinic marketplace tables are clean in PostgreSQL

  Scenario: Therapist is blocked from creating a service until IBAN is configured in required clinic setup settings
    Given a therapist "4c421f46-836c-4897-8dae-ba9587473273" ("Bo Jensen") in organisation "Nordic Mind Clinic ApS" with CVR "39485712" WITHOUT an IBAN configured
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" requests their clinic overview
    Then the therapist overview should report canCreateServices as false and missingProfileFields containing "IBAN"
    When the therapist "4c421f46-836c-4897-8dae-ba9587473273" creates a clinic service with:
      | name            | EMDR Traumeterapi |
      | description     | 60 min session    |
      | durationMinutes | 60                |
      | price           | 1200.0            |
      | currency        | DKK               |
      | location        | København K       |
    Then the clinic service response status should be 400
    And the clinic service error message should mention "IBAN"

  Scenario: Therapist configures IBAN & java.util.Currency, creates a service which a client books, and the client grants permission to see only 1 of the client's JGit branches
    Given a therapist "4c421f46-836c-4897-8dae-ba9587473273" ("Bo Jensen") in organisation "Nordic Mind Clinic ApS" with CVR "39485712" WITHOUT an IBAN configured
    And a registered client profile for "5e357ca1-4913-414f-91a6-81a7de9042ab" named "Anders Sand" with email "client@mail.com"
    When the therapist "4c421f46-836c-4897-8dae-ba9587473273" completes clinic setup with IBAN "DK5000400440116243" and currency "EUR"
    Then the therapist profile should have IBAN "DK5000400440116243" and java.util.Currency "EUR"
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" requests their clinic overview
    Then the therapist overview should report canCreateServices as true and missingProfileFields should be empty
    When the therapist "4c421f46-836c-4897-8dae-ba9587473273" creates a clinic service with:
      | name            | Specialiseret Traumesamtale |
      | description     | 60 min klinisk session      |
      | durationMinutes | 60                          |
      | price           | 160.0                       |
      | currency        | EUR                         |
      | location        | København K                 |
    Then the clinic service response status should be 201
    And the created service should have java.util.Currency "EUR" and price 160.0
    When client "5e357ca1-4913-414f-91a6-81a7de9042ab" commits a journal entry on JGit branch "branch-trauma-therapy" with title "Traume Session Refleksion" and content "Vi arbejdede med EMDR fokuspunkter."
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" commits a journal entry on JGit branch "branch-private-diary" with title "Privat Dagbog" and content "Hemmelige personlige tanker kun på privat branch."
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" books the created service with therapist "4c421f46-836c-4897-8dae-ba9587473273" from "2027-11-10T10:00:00" to "2027-11-10T11:00:00"
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" grants an "ACTIVE" handshake to therapist "4c421f46-836c-4897-8dae-ba9587473273" restricted to only JGit branch "branch-trauma-therapy" and custom restrictions "NO_MOOD_SCORES,NO_CLINICAL_NOTES_EXPORT"
    Then the handshake should only permit JGit branch "branch-trauma-therapy" and have custom restrictions "NO_MOOD_SCORES,NO_CLINICAL_NOTES_EXPORT"
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" fetches the client journal bundle and JGit history for "5e357ca1-4913-414f-91a6-81a7de9042ab"
    Then the therapist should see the JGit commit and entry from branch "branch-trauma-therapy" containing "Vi arbejdede med EMDR fokuspunkter."
    And the therapist should NOT see any JGit commit or entry from branch "branch-private-diary" containing "Hemmelige personlige tanker kun på privat branch."
    And the mood rating on the therapist's view of the entry should be masked as null due to "NO_MOOD_SCORES"
    When client "5e357ca1-4913-414f-91a6-81a7de9042ab" fetches their own journal bundle and JGit history
    Then the client should see both JGit branches "branch-trauma-therapy" and "branch-private-diary"

  Scenario: Client grants therapist access to multiple JGit branches with distinct custom restrictions per branch line [branch, restriction(s)]
    Given a therapist "4c421f46-836c-4897-8dae-ba9587473273" ("Bo Jensen") in organisation "Nordic Mind Clinic ApS" with CVR "39485712" WITHOUT an IBAN configured
    And a registered client profile for "5e357ca1-4913-414f-91a6-81a7de9042ab" named "Anders Sand" with email "client@mail.com"
    When the therapist "4c421f46-836c-4897-8dae-ba9587473273" completes clinic setup with IBAN "DK5000400440116243" and currency "DKK"
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" commits a journal entry on JGit branch "branch-trauma-therapy" with title "Traume Session" and content "Traume indhold med skjult mood."
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" commits a journal entry on JGit branch "branch-anxiety-log" with title "Angst Logbog" and content "Angst eksponering med synlig mood."
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" commits a journal entry on JGit branch "branch-private-diary" with title "Privat Dagbog" and content "Privat gren som terapeuten ikke må se."
    And client "5e357ca1-4913-414f-91a6-81a7de9042ab" grants an "ACTIVE" handshake to therapist "4c421f46-836c-4897-8dae-ba9587473273" with per-branch custom restrictions:
      | branch                | restrictions                         |
      | branch-trauma-therapy | NO_MOOD_SCORES,READ_ONLY_TIMELINE    |
      | branch-anxiety-log    | NO_CLINICAL_NOTES_EXPORT             |
    Then the handshake should permit JGit branches "branch-trauma-therapy,branch-anxiety-log" with per-branch restrictions
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" fetches the client journal bundle and JGit history for "5e357ca1-4913-414f-91a6-81a7de9042ab"
    Then the therapist should see the JGit commit and entry from branch "branch-trauma-therapy" containing "Traume indhold med skjult mood."
    And the therapist should see the JGit commit and entry from branch "branch-anxiety-log" containing "Angst eksponering med synlig mood."
    And the therapist should NOT see any JGit commit or entry from branch "branch-private-diary" containing "Privat gren som terapeuten ikke må se."
    And the therapist's view of branch "branch-trauma-therapy" should have moodRating masked as null while branch "branch-anxiety-log" retains moodRating 8

  Scenario: Therapist cannot see their own clinic services when browsing the Service Marketplace from their personal client profile
    Given a therapist "4c421f46-836c-4897-8dae-ba9587473273" ("Bo Jensen") in organisation "Nordic Mind Clinic ApS" with CVR "39485712" WITHOUT an IBAN configured
    And a therapist "9b111111-2222-3333-4444-555555555555" ("Dr. Maja Lind") in organisation "Maja Lind Terapi ApS" with CVR "88776655" WITHOUT an IBAN configured
    And a registered client profile for "5e357ca1-4913-414f-91a6-81a7de9042ab" named "Anders Sand" with email "client@mail.com"
    When the therapist "4c421f46-836c-4897-8dae-ba9587473273" completes clinic setup with IBAN "DK5000400440116243" and currency "DKK"
    And the therapist "9b111111-2222-3333-4444-555555555555" completes clinic setup with IBAN "DK9900400440998877" and currency "DKK"
    And the therapist "4c421f46-836c-4897-8dae-ba9587473273" creates a clinic service with:
      | name            | Bo Jensens Egne Samtaler |
      | description     | Bo Jensens egen ydelse   |
      | durationMinutes | 60                       |
      | price           | 950.0                    |
      | currency        | DKK                      |
      | location        | København K              |
    And the therapist "9b111111-2222-3333-4444-555555555555" creates a clinic service with:
      | name            | Maja Linds Kognitive Terapi |
      | description     | Maja Linds ydelse           |
      | durationMinutes | 60                          |
      | price           | 850.0                       |
      | currency        | DKK                         |
      | location        | Aarhus C                    |
    When therapist "4c421f46-836c-4897-8dae-ba9587473273" views the service marketplace from their personal client profile at "/api/v1/clinic/client/4c421f46-836c-4897-8dae-ba9587473273/overview"
    Then the personal client marketplace services should NOT contain "Bo Jensens Egne Samtaler"
    And the personal client marketplace services should contain "Maja Linds Kognitive Terapi"
    When client "5e357ca1-4913-414f-91a6-81a7de9042ab" views the service marketplace from their personal client profile at "/api/v1/clinic/client/5e357ca1-4913-414f-91a6-81a7de9042ab/overview"
    Then the personal client marketplace services should contain "Bo Jensens Egne Samtaler"
    And the personal client marketplace services should contain "Maja Linds Kognitive Terapi"
