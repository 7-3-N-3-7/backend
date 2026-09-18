Feature: Appointment Scheduling, Conflict Prevention, and Calendar Management
  As a Therapist or Client on the INTEGRATE platform
  I want to schedule, query, reschedule, and cancel appointment bookings
  So that treatment schedules are accurately maintained without overlapping conflicts

  Background:
    Given Business PostgreSQL database is clean and accessible
    And Hibernate ORM is initialized with prepared statements and UUID generation
    And Redisson Redis cache is connected

  # =========================================================================
  # 1. APPOINTMENT CREATION & VALIDATION SCENARIOS
  # =========================================================================

  Scenario: Successfully schedule a valid 1-hour therapy appointment
    Given a valid client with UUID "550e8400-e29b-41d4-a716-446655440000"
    And a valid therapist with UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    When the client posts a new appointment booking request with:
      | title          | Psykotraumatologi Initial Session  |
      | startTime      | 2026-09-08T10:00:00                |
      | endTime        | 2026-09-08T11:00:00                |
      | therapistUuid  | 6ba7b810-9dad-11d1-80b4-00c04fd430c8 |
    Then the response status should be 200 OK
    And the returned appointment object should contain a generated UUID
    And the appointment status should be "CONFIRMED"
    And a Kafka event should be published to topic "appointment-events" with action "CREATED"

  Scenario: Prevent appointment creation when end time is before start time
    Given a valid client UUID "550e8400-e29b-41d4-a716-446655440000"
    When the client attempts to book an appointment with startTime "2026-09-08T11:00:00" and endTime "2026-09-08T10:00:00"
    Then the system should reject the creation with HTTP 400 Bad Request
    And the response error message should state "Invalid time range: End time must be after start time"

  Scenario: Prevent double-booking / overlapping appointments for the same therapist
    Given an existing confirmed appointment for therapist "6ba7b810-9dad-11d1-80b4-00c04fd430c8" from "2026-09-08T10:00:00" to "2026-09-08T11:00:00"
    When another client attempts to book therapist "6ba7b810-9dad-11d1-80b4-00c04fd430c8" from "2026-09-08T10:30:00" to "2026-09-08T11:30:00"
    Then the system should reject the request with HTTP 409 Conflict
    And the response error message should state "Therapist is already booked during the requested slot"

  # =========================================================================
  # 2. CALENDAR QUERYING & REDIS CACHING SCENARIOS
  # =========================================================================

  Scenario: Query weekly calendar appointments for a client with Redis caching
    Given client "550e8400-e29b-41d4-a716-446655440000" has 2 scheduled appointments in week 37 (2026-09-07 to 2026-09-13)
    When the client requests appointments for range "2026-09-07" to "2026-09-13"
    Then the backend should return 2 appointment items in the JSON array
    And subsequent queries for the same range should be served directly from Redisson Redis cache

  Scenario: Cancel an existing appointment booking
    Given an active appointment with UUID "a1b2c3d4-e5f6-7890-abcd-ef1234567890" for client "550e8400-e29b-41d4-a716-446655440000"
    When the client sends a DELETE request to "/api/v1/appointments/a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    Then the response status should be 200 OK
    And the appointment status in PostgreSQL should be updated to "CANCELLED"
    And the corresponding Redisson cache key should be evicted
