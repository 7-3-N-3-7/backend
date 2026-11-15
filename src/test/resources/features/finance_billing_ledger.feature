Feature: Kill Bill Billing, Apache Fineract Double-Entry Ledger, Prepaid Savings, Loans & JGit Audit Trail
  As a clinic platform with Keycloak IAM and Eclipse JGit
  I want all billing in Kill Bill and double-entry accounting in Apache Fineract to be orchestrated via our REST API
  So that every invoice, prepaid clip, subscription, installment loan, and credit note is double-entry booked and immutably committed to JGit

  Background:
    Given the finance billing and ledger state is reset

  Scenario: Single-session billing and cancellation credit note with Fineract GL and JGit cross-reference
    When therapist "11111111-2222-3333-4444-555555555555" ("Dr. Maja Lind") bills a paid session of 1200.00 DKK for client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" linked to appointment "apt-101" and clinical commit "c0ffee12"
    Then the finance response status should be 201
    And the invoice status should be "PAID" with a valid JGit commit hash
    And the Fineract General Ledger account "1100" balance should be 1200.00 DKK
    And the Fineract General Ledger account "4100" balance should be 1200.00 DKK
    When therapist "11111111-2222-3333-4444-555555555555" refunds the last invoice with reason "Aflyst pga. sygdom"
    Then the finance response status should be 200
    And the refunded invoice status should be "REFUNDED" referencing the original invoice JGit commit hash
    And the Fineract General Ledger account "1100" balance should be 0.00 DKK
    And the Fineract General Ledger account "4100" balance should be 0.00 DKK

  Scenario: Prepaid Klippekort top-up and session clip consumption via Fineract Savings and GL
    When client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" tops up a prepaid Klippekort with 5 clips for 4500.00 DKK
    Then the finance response status should be 201
    And the client Fineract savings account should have 5 remaining clips and balance 4500.00 DKK
    And the Fineract General Ledger account "2100" balance should be 4500.00 DKK
    When therapist "11111111-2222-3333-4444-555555555555" consumes 1 prepaid clip for client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" on appointment "apt-202"
    Then the finance response status should be 200
    And the client Fineract savings account should have 4 remaining clips and balance 3600.00 DKK
    And the Fineract General Ledger account "2100" balance should be 3600.00 DKK
    And the Fineract General Ledger account "4100" balance should be 900.00 DKK

  Scenario: Recurring subscription and installment loan repayment via Kill Bill, Fineract and JGit
    When client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" subscribes to plan "nordic-trauma-care-monthly" at 1800.00 DKK per month
    Then the finance response status should be 201
    And the Fineract General Ledger account "4200" balance should be 1800.00 DKK
    When therapist "11111111-2222-3333-4444-555555555555" creates a 3-installment Fineract loan plan of 6000.00 DKK for client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    Then the finance response status should be 201
    And the Fineract General Ledger account "1200" balance should be 6000.00 DKK
    When client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" repays one installment on the active loan plan
    Then the finance response status should be 200
    And the loan remaining principal should be 4000.00 DKK with 1 paid installment
    And the Fineract General Ledger account "1200" balance should be 4000.00 DKK

  Scenario: Keycloak RBAC isolates client financial records and restricts general ledger to therapists
    Given client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" tops up a prepaid Klippekort with 5 clips for 4500.00 DKK
    When client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" requests their own finance overview
    Then the finance response status should be 200
    And the client finance overview should contain 1 JGit voucher commit
    When client "99999999-8888-7777-6666-555555555555" attempts to request the finance overview of client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    Then the finance response status should be 403
    When client "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" attempts to request the clinic general ledger summary
    Then the finance response status should be 403
    When therapist "11111111-2222-3333-4444-555555555555" requests the clinic general ledger summary
    Then the finance response status should be 200
