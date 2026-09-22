package com.platform.cucumber;

import com.platform.entity.Appointment;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assertions;

import java.time.LocalDateTime;
import java.util.UUID;

public class AppointmentStepDefinitions {

    private UUID clientUuid;
    private UUID therapistUuid;
    private Appointment appointment;

    @Given("a client with UUID {string} and a therapist with UUID {string}")
    public void a_client_with_uuid_and_a_therapist_with_uuid(String clientUuidStr, String therapistUuidStr) {
        this.clientUuid = UUID.fromString(clientUuidStr);
        this.therapistUuid = UUID.fromString(therapistUuidStr);
    }

    @When("an appointment is created for {string} titled {string}")
    public void an_appointment_is_created_for_titled(String dateTimeStr, String title) {
        LocalDateTime startTime = LocalDateTime.parse(dateTimeStr);
        LocalDateTime endTime = startTime.plusHours(1);
        this.appointment = new Appointment(clientUuid, therapistUuid, startTime, endTime, title, "CONFIRMED");
    }

    @Then("the appointment should be saved with status {string}")
    public void the_appointment_should_be_saved_with_status(String expectedStatus) {
        Assertions.assertNotNull(this.appointment);
        Assertions.assertEquals(expectedStatus, this.appointment.getStatus());
        Assertions.assertEquals(this.clientUuid, this.appointment.getClientUuid());
    }
}
