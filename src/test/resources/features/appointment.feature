Feature: Appointment Booking Management
  As a user on the INTEGRATE platform
  I want to schedule and retrieve appointment bookings
  So that therapists and clients can manage their sessions seamlessly

  Scenario: Successfully schedule a new appointment booking
    Given a client with UUID "550e8400-e29b-41d4-a716-446655440000" and a therapist with UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    When an appointment is created for "2026-09-08T10:00:00" titled "Psykotraumatologi Session"
    Then the appointment should be saved with status "CONFIRMED"
