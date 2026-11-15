@api @e2e @finance @jgit @rbac
Feature: End-to-End 3-User Flow - 1 User Pays to Become Therapist & Sets Up Service, 2 Users Pay 50 DKK Each (Engangsbetaling & Klippekort)
  As an EduJournal platform supporting Kill Bill billing, Apache Fineract double-entry accounting, and java.util.Currency
  I want an end-to-end flow where 3 users are created, 1 user pays to upgrade to a therapist and configures a 50 DKK service supporting both one-time payment (engangsbetaling) and prepaid clip card (klippekort), and the remaining 2 users each book and pay 50 DKK in java.util.Currency to the therapist user
  So that both payment methods (engangsbetaling & klippekort) and therapist revenue accounting in DKK are verified end-to-end

  Background:
    Given Business PostgreSQL database is clean and accessible
    And the clinic marketplace tables are clean in PostgreSQL
    And the finance billing and ledger state is reset

  Scenario: 3 users are created, User 1 pays to become a therapist and creates a 50 DKK service, and User 2 (engangsbetaling) and User 3 (klippekort) each book and pay 50 DKK in java.util.Currency to the therapist
    Given 3 platform users are created via "/api/users":
      | alias | username         | role   | fullName          | email                 |
      | user1 | maja_terapeut    | CLIENT | Maja Lind         | maja@terapi.dk        |
      | user2 | anders_klient    | CLIENT | Anders Sand       | anders@klient.dk      |
      | user3 | sofie_klient     | CLIENT | Sofie Holm        | sofie@klient.dk       |
    When user "user1" pays 250.00 in java.util.Currency "DKK" for subscription plan "therapist-clinic-license" to become a therapist
    Then the therapist upgrade payment should be "ACTIVE" in java.util.Currency "DKK" and Fineract GL "4200" should have balance 250.00 DKK
    When user "user1" completes mandatory therapist clinic setup with CVR "39485712", organisation "Maja Lind Psykoterapi ApS", IBAN "DK5000400440116243", and java.util.Currency "DKK"
    And therapist user "user1" sets up a clinic service priced at 50.00 in java.util.Currency "DKK" supporting both "ENGANGSBETALING" and "KLIPPEKORT":
      | name            | Individuel Samtale (Engangsbetaling eller Klippekort) |
      | description     | Kan betales med engangsbetaling (50 DKK) eller klippekort (5 klip = 200 DKK) |
      | durationMinutes | 60                                                    |
      | price           | 50.0                                                  |
      | currency        | DKK                                                   |
      | location        | København K                                           |
    Then the created service for therapist "user1" should be active with price 50.00 and java.util.Currency "DKK"
    When client user "user2" books the therapist "user1" service from "2027-11-15T10:00:00" to "2027-11-15T11:00:00" and pays 50.00 in java.util.Currency "DKK" via engangsbetaling
    And client user "user3" books the therapist "user1" service from "2027-11-15T12:00:00" to "2027-11-15T13:00:00" and pays 50.00 in java.util.Currency "DKK" via klippekort
    Then both bookings with therapist "user1" should be confirmed and paid with 50.00 in java.util.Currency "DKK" each
    And therapist "user1" should have received 100.00 in java.util.Currency "DKK" in consultation revenue on Fineract GL "4100"
    And Fineract GL "1100" total bank liquidity should be 350.00 in java.util.Currency "DKK"
