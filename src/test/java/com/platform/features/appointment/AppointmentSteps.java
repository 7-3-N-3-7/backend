package com.platform.features.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.PlatformCacheService;
import com.platform.config.PlatformEventService;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.CucumberScenarioContext;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentDto;
import com.platform.features.appointment.AppointmentFeature.AppointmentRepository;
import com.platform.features.appointment.AppointmentFeature.AppointmentService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class AppointmentSteps {

    @Autowired
    private CucumberScenarioContext context;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private PlatformCacheService cacheService;

    @Autowired
    private PlatformEventService eventService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    private UUID unitClientUuid;
    private UUID unitTherapistUuid;
    private Appointment unitAppointment;

    @Given("a client with UUID {string} and a therapist with UUID {string}")
    public void a_client_with_uuid_and_a_therapist_with_uuid(String clientUuidStr, String therapistUuidStr) {
        this.unitClientUuid = UUID.fromString(clientUuidStr);
        this.unitTherapistUuid = UUID.fromString(therapistUuidStr);
    }

    @When("an appointment is created for {string} titled {string}")
    public void an_appointment_is_created_for_titled(String dateTimeStr, String title) {
        LocalDateTime startTime = LocalDateTime.parse(dateTimeStr);
        LocalDateTime endTime = startTime.plusHours(1);
        this.unitAppointment = new Appointment(unitClientUuid, unitTherapistUuid, startTime, endTime, title, "CONFIRMED");
    }

    @Then("the appointment should be saved with status {string}")
    public void the_appointment_should_be_saved_with_status(String expectedStatus) {
        Assertions.assertNotNull(this.unitAppointment);
        Assertions.assertEquals(expectedStatus, this.unitAppointment.getStatus());
        Assertions.assertEquals(this.unitClientUuid, this.unitAppointment.getClientUuid());
    }

    @Given("Business PostgreSQL database is clean and accessible")
    public void business_postgre_sql_database_is_clean_and_accessible() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Assertions.assertTrue(connection.isValid(2), "Database connection must be valid");
        }
        appointmentRepository.deleteAll();
        cacheService.clearAll();
        eventService.clearEvents();
        Assertions.assertEquals(0, appointmentRepository.count(), "Appointments table must be clean before scenario");
    }

    @Given("Hibernate ORM is initialized with prepared statements and UUID generation")
    public void hibernate_orm_is_initialized_with_prepared_statements_and_uuid_generation() {
        Assertions.assertNotNull(entityManager, "JPA EntityManager must be initialized");
        Assertions.assertTrue(entityManager.isOpen(), "JPA EntityManager must be open");
        var appointmentType = entityManager.getMetamodel().entity(Appointment.class);
        Assertions.assertNotNull(appointmentType, "Appointment entity must be registered in Hibernate metamodel");
        Class<?> resolvedIdType = GenericTypeResolver.resolveTypeArgument(Appointment.class, Identifiable.class);
        Assertions.assertEquals(UUID.class, resolvedIdType != null ? resolvedIdType : appointmentType.getIdType().getJavaType(), "Appointment ID type must be UUID");
    }

    @Given("Redisson Redis cache is connected")
    public void redisson_redis_cache_is_connected() {
        Assertions.assertNotNull(cacheService, "PlatformCacheService must be initialized");
        Assertions.assertTrue(cacheService.isConnected(), "Redis cache service must report connected");
    }

    @Given("a valid client with UUID {string}")
    public void a_valid_client_with_uuid(String clientUuid) {
        context.setCurrentClientUuid(clientUuid);
        String token = "valid-client-token-for-" + clientUuid;
        context.setGeneratedToken(token);
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));
    }

    @Given("a valid therapist with UUID {string}")
    public void a_valid_therapist_with_uuid(String therapistUuid) {
        context.setCurrentTherapistUuid(therapistUuid);
        String token = "valid-therapist-token-for-" + therapistUuid;
        context.setGeneratedToken(token);
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));
    }

    @When("the client posts a new appointment booking request with:")
    public void the_client_posts_a_new_appointment_booking_request_with(DataTable dataTable) throws Exception {
        Map<String, String> data = dataTable.asMap(String.class, String.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", data.get("title"));
        payload.put("startTime", data.get("startTime"));
        payload.put("endTime", data.get("endTime"));
        payload.put("therapistUuid", data.get("therapistUuid"));
        payload.put("clientUuid", context.getCurrentClientUuid());

        context.setLastResponse(context.getRequestSpec()
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/appointments"));
    }

    @Then("the returned appointment object should contain a generated UUID")
    public void the_returned_appointment_object_should_contain_a_generated_uuid() {
        String idStr = context.getLastResponse().jsonPath().getString("id");
        Assertions.assertNotNull(idStr, "Returned appointment must have a non-null 'id'");
        UUID parsedId = UUID.fromString(idStr);
        Assertions.assertTrue(appointmentRepository.findById(parsedId).isPresent(), "Appointment must exist in PostgreSQL");
    }

    @Then("the appointment status should be {string}")
    public void the_appointment_status_should_be(String expectedStatus) {
        Response lastResponse = context.getLastResponse();
        String actualStatus = lastResponse.jsonPath().getString("status");
        Assertions.assertEquals(expectedStatus, actualStatus, "Returned appointment status must match");
        UUID parsedId = UUID.fromString(lastResponse.jsonPath().getString("id"));
        Appointment persisted = appointmentRepository.findById(parsedId).orElseThrow();
        Assertions.assertEquals(expectedStatus, persisted.getStatus(), "Database appointment status must match");
    }

    @Then("a Kafka event should be published to topic {string} with action {string}")
    public void a_kafka_event_should_be_published_to_topic_with_action(String expectedTopic, String expectedAction) {
        Assertions.assertTrue(
                eventService.hasEvent(expectedTopic, expectedAction),
                "Expected Kafka event on topic '" + expectedTopic + "' with action '" + expectedAction + "'"
        );
    }

    @Given("a valid client UUID {string}")
    public void a_valid_client_uuid(String clientUuid) {
        context.setCurrentClientUuid(clientUuid);
        String token = "valid-client-token-for-" + clientUuid;
        context.setGeneratedToken(token);
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));
    }

    @When("the client attempts to book an appointment with startTime {string} and endTime {string}")
    public void the_client_attempts_to_book_an_appointment_with_start_time_and_end_time(String startTime, String endTime) throws Exception {
        Map<String, Object> payload = Map.of(
                "startTime", startTime,
                "endTime", endTime,
                "clientUuid", context.getCurrentClientUuid(),
                "therapistUuid", "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        );
        context.setLastResponse(context.getRequestSpec()
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/appointments"));
    }

    @Then("the system should reject the creation with HTTP {int} Bad Request")
    public void the_system_should_reject_the_creation_with_http_bad_request(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }

    @Then("the response error message should state {string}")
    public void the_response_error_message_should_state(String expectedError) {
        context.getLastResponse().then().body(containsString(expectedError));
    }

    @Given("an existing confirmed appointment for therapist {string} from {string} to {string}")
    public void an_existing_confirmed_appointment_for_therapist_from_to(String therapistUuid, String startTime, String endTime) throws Exception {
        Map<String, Object> payload = Map.of(
                "therapistUuid", therapistUuid,
                "startTime", startTime,
                "endTime", endTime,
                "clientUuid", "00000000-0000-0000-0000-000000000001",
                "title", "Existing Confirmed Session"
        );
        Response seedResp = given()
                .header("Authorization", "Bearer valid-therapist-token-for-" + therapistUuid)
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/appointments");
        seedResp.then().statusCode(200);
    }

    @When("another client attempts to book therapist {string} from {string} to {string}")
    public void another_client_attempts_to_book_therapist_from_to(String therapistUuid, String startTime, String endTime) throws Exception {
        Map<String, Object> payload = Map.of(
                "therapistUuid", therapistUuid,
                "startTime", startTime,
                "endTime", endTime,
                "clientUuid", "00000000-0000-0000-0000-000000000002",
                "title", "Overlapping Session Attempt"
        );
        context.setLastResponse(given()
                .header("Authorization", "Bearer valid-client-token-for-00000000-0000-0000-0000-000000000002")
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(payload))
                .when()
                .post("/api/v1/appointments"));
    }

    @Then("the system should reject the request with HTTP {int} Conflict")
    public void the_system_should_reject_the_request_with_http_conflict(Integer expectedStatus) {
        context.getLastResponse().then().statusCode(expectedStatus);
    }

    @Given("client {string} has {int} scheduled appointments in week {int} \\({word}-{word}-{word} to {word}-{word}-{word})")
    public void client_has_scheduled_appointments_in_week_to(
            String clientUuidStr,
            Integer count,
            Integer weekNumber,
            String y1, String m1, String d1,
            String y2, String m2, String d2
    ) {
        Assertions.assertTrue(weekNumber > 0);
        context.setCurrentClientUuid(clientUuidStr);
        String token = "valid-client-token-for-" + clientUuidStr;
        context.setGeneratedToken(token);
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));

        LocalDate startDate = LocalDate.parse(y1 + "-" + m1 + "-" + d1);
        LocalDate endDate = LocalDate.parse(y2 + "-" + m2 + "-" + d2);
        Assertions.assertFalse(endDate.isBefore(startDate));

        UUID clientUuid = UUID.fromString(clientUuidStr);
        UUID therapistUuid = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        for (int i = 0; i < count; i++) {
            LocalDateTime slotStart = startDate.plusDays(i).atTime(10 + i, 0);
            LocalDateTime slotEnd = slotStart.plusHours(1);
            Appointment apt = new Appointment(
                    clientUuid,
                    therapistUuid,
                    slotStart,
                    slotEnd,
                    "Week " + weekNumber + " Session " + (i + 1),
                    "CONFIRMED"
            );
            appointmentRepository.save(apt);
        }
    }

    @When("the client requests appointments for range {string} to {string}")
    public void the_client_requests_appointments_for_range_to(String fromStr, String toStr) {
        context.setLastQueriedRangeFrom(fromStr);
        context.setLastQueriedRangeTo(toStr);
        Response response = given()
                .header("Authorization", "Bearer " + context.getGeneratedToken())
                .queryParam("from", fromStr)
                .queryParam("to", toStr)
                .when()
                .get("/api/v1/appointments/client/" + context.getCurrentClientUuid());
        response.then().statusCode(200);
        context.setLastResponse(response);
    }

    @Then("the backend should return {int} appointment items in the JSON array")
    public void the_backend_should_return_appointment_items_in_the_json_array(Integer expectedCount) {
        List<?> items = context.getLastResponse().jsonPath().getList("$");
        Assertions.assertNotNull(items, "Response body must be a JSON array");
        Assertions.assertEquals(expectedCount, items.size(), "Returned appointment count must match");
    }

    @Then("subsequent queries for the same range should be served directly from Redisson Redis cache")
    public void subsequent_queries_for_the_same_range_should_be_served_directly_from_redisson_redis_cache() {
        String expectedCacheKey = appointmentService.buildClientCacheKey(
                UUID.fromString(context.getCurrentClientUuid()),
                LocalDate.parse(context.getLastQueriedRangeFrom()),
                LocalDate.parse(context.getLastQueriedRangeTo())
        );
        Assertions.assertTrue(cacheService.containsKey(expectedCacheKey), "Cache must contain key: " + expectedCacheKey);

        int hitsBefore = cacheService.getCacheHitCount();
        Response cachedResponse = given()
                .header("Authorization", "Bearer " + context.getGeneratedToken())
                .queryParam("from", context.getLastQueriedRangeFrom())
                .queryParam("to", context.getLastQueriedRangeTo())
                .when()
                .get("/api/v1/appointments/client/" + context.getCurrentClientUuid());
        cachedResponse.then().statusCode(200);
        int hitsAfter = cacheService.getCacheHitCount();
        Assertions.assertTrue(hitsAfter > hitsBefore, "Subsequent query must be served from cache (cache hit count must increase)");
    }

    @Given("an active appointment with UUID {string} for client {string}")
    public void an_active_appointment_with_uuid_for_client(String appointmentUuidStr, String clientUuidStr) {
        context.setCurrentClientUuid(clientUuidStr);
        context.setLastCancelledAppointmentId(appointmentUuidStr);
        String token = "valid-client-token-for-" + clientUuidStr;
        context.setGeneratedToken(token);
        context.setRequestSpec(given().header("Authorization", "Bearer " + token));

        UUID aptUuid = UUID.fromString(appointmentUuidStr);
        UUID clientUuid = UUID.fromString(clientUuidStr);
        UUID therapistUuid = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        LocalDateTime futureStart = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        jdbcTemplate.update(
                "INSERT INTO appointments (id, client_uuid, therapist_uuid, start_time, end_time, title, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                aptUuid,
                clientUuid,
                therapistUuid,
                futureStart,
                futureStart.plusHours(1),
                "To Cancel",
                "CONFIRMED"
        );
        cacheService.put("appointment:" + aptUuid, "cached-appointment");
        cacheService.put("appointments:client:" + clientUuid + ":all", List.of());
    }

    @When("the client sends a DELETE request to {string}")
    public void the_client_sends_a_delete_request_to(String endpoint) {
        context.setLastResponse(context.getRequestSpec().when().delete(endpoint));
    }

    @Then("the appointment status in PostgreSQL should be updated to {string}")
    public void the_appointment_status_in_postgre_sql_should_be_updated_to(String expectedStatus) {
        UUID aptUuid = UUID.fromString(context.getLastCancelledAppointmentId());
        Appointment updated = appointmentRepository.findById(aptUuid)
                .orElseThrow(() -> new AssertionError("Cancelled appointment record must still exist in PostgreSQL"));
        Assertions.assertEquals(expectedStatus, updated.getStatus(), "Appointment status in PostgreSQL must be updated");
    }

    @Then("the corresponding Redisson cache key should be evicted")
    public void the_corresponding_redisson_cache_key_should_be_evicted() {
        Assertions.assertFalse(
                cacheService.containsKey("appointment:" + context.getLastCancelledAppointmentId()),
                "Appointment cache key must be evicted upon cancellation"
        );
        Assertions.assertFalse(
                cacheService.containsKey("appointments:client:" + context.getCurrentClientUuid() + ":all"),
                "Client appointments list cache key must be evicted upon cancellation"
        );
    }

    @Given("der eksisterer en booking på tidspunktet {string}")
    public void derEksistererEnBookingPaaTidspunktet(String startTimeIso) throws Exception {
        LocalDateTime startTime = LocalDateTime.parse(startTimeIso);
        AppointmentDto existing = new AppointmentDto(null, "Eksisterende Klient", startTime);
        Response response = given()
                .header("Authorization", "Bearer valid-cucumber-token")
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(existing))
                .when()
                .post("/api/v1/appointments");
        Assertions.assertEquals(201, response.getStatusCode());
    }

    @When("en klient forsøger at oprette en booking på tidspunktet {string}")
    public void enKlientForsoegerAtOpretteEnBookingPaaTidspunktet(String startTimeIso) throws Exception {
        LocalDateTime startTime = LocalDateTime.parse(startTimeIso);
        AppointmentDto attempt = new AppointmentDto(null, "Ny Klient", startTime);
        context.setLastResponse(given()
                .header("Authorization", "Bearer valid-cucumber-token")
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(attempt))
                .when()
                .post("/api/v1/appointments"));
    }

    @Then("svarer API'et med HTTP status {int}")
    public void svarerApietMedHttpStatus(int expectedStatus) {
        Assertions.assertNotNull(context.getLastResponse());
        Assertions.assertEquals(expectedStatus, context.getLastResponse().getStatusCode());
    }

    @And("fejlsvaret indeholder errorCode {string}")
    public void fejlsvaretIndeholderErrorCode(String expectedErrorCode) {
        Assertions.assertNotNull(context.getLastResponse());
        Assertions.assertEquals(expectedErrorCode, context.getLastResponse().jsonPath().getString("errorCode"));
    }
}
