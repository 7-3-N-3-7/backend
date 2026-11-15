package com.platform.core;

import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.Organisation;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.Service;
import com.platform.features.i18n.I18nFeature.I18nDictionary;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.TherapistClinicalNote;
import com.platform.features.journal.JournalFeature.TherapistNote;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.user.UserFeature.UserEntity;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GenericBaseArchitectureSteps {

    @Autowired
    private DomainScenarioState state;

    private Response lastInheritedResponse;

    private Class<?> resolveClassOrFail(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            Assertions.fail("Expected class to exist: " + className);
            return null;
        }
    }

    private Class<?> resolveDomainEntity(String simpleName) {
        Map<String, Class<?>> entities = Map.ofEntries(
                Map.entry("Appointment", Appointment.class),
                Map.entry("Organisation", Organisation.class),
                Map.entry("ClientProfile", ClientProfile.class),
                Map.entry("ProviderProfile", ProviderProfile.class),
                Map.entry("Service", Service.class),
                Map.entry("ClientTherapistHandshake", ClientTherapistHandshake.class),
                Map.entry("UserEntity", UserEntity.class),
                Map.entry("JournalEntry", JournalEntry.class),
                Map.entry("TherapistClinicalNote", TherapistClinicalNote.class),
                Map.entry("TherapistNote", TherapistNote.class),
                Map.entry("PsychotraumatologyTest", PsychotraumatologyTest.class),
                Map.entry("I18nDictionary", I18nDictionary.class)
        );
        Class<?> clazz = entities.get(simpleName);
        Assertions.assertNotNull(clazz, "Unknown domain entity: " + simpleName);
        return clazz;
    }

    private Class<?> resolveDomainRepository(String simpleName) {
        Map<String, String> repos = Map.ofEntries(
                Map.entry("UserRepository", "com.platform.features.user.UserFeature$UserRepository"),
                Map.entry("AppointmentRepository", "com.platform.features.appointment.AppointmentFeature$AppointmentRepository"),
                Map.entry("JournalEntryRepository", "com.platform.features.journal.JournalFeature$JournalEntryRepository"),
                Map.entry("TherapistClinicalNoteRepository", "com.platform.features.journal.JournalFeature$TherapistClinicalNoteRepository"),
                Map.entry("TherapistNoteRepository", "com.platform.features.journal.JournalFeature$TherapistNoteRepository"),
                Map.entry("PsychotraumatologyTestRepository", "com.platform.features.psychotraumatology.PsychotraumatologyFeature$PsychotraumatologyTestRepository"),
                Map.entry("ClientProfileRepository", "com.platform.features.clinic.ClinicPlatformFeature$ClientProfileRepository"),
                Map.entry("OrganisationRepository", "com.platform.features.clinic.ClinicPlatformFeature$OrganisationRepository"),
                Map.entry("ProviderProfileRepository", "com.platform.features.clinic.ClinicPlatformFeature$ProviderProfileRepository"),
                Map.entry("ServiceRepository", "com.platform.features.clinic.ClinicPlatformFeature$ServiceRepository"),
                Map.entry("HandshakeRepository", "com.platform.features.clinic.ClinicPlatformFeature$HandshakeRepository")
        );
        String fqcn = repos.get(simpleName);
        Assertions.assertNotNull(fqcn, "Unknown domain repository: " + simpleName);
        return resolveClassOrFail(fqcn);
    }

    private Class<?> resolveDomainService(String simpleName) {
        Map<String, String> services = Map.of(
                "AppointmentService", "com.platform.features.appointment.AppointmentFeature$AppointmentService",
                "JournalService", "com.platform.features.journal.JournalFeature$JournalService",
                "PsychotraumatologyService", "com.platform.features.psychotraumatology.PsychotraumatologyFeature$PsychotraumatologyService",
                "UserService", "com.platform.features.user.UserFeature$UserService",
                "ClinicPlatformService", "com.platform.features.clinic.ClinicPlatformFeature$ClinicPlatformService"
        );
        String fqcn = services.get(simpleName);
        Assertions.assertNotNull(fqcn, "Unknown domain service: " + simpleName);
        return resolveClassOrFail(fqcn);
    }

    private Class<?> resolveDomainController(String simpleName) {
        Map<String, String> controllers = Map.of(
                "AppointmentController", "com.platform.features.appointment.AppointmentFeature$AppointmentController",
                "JournalController", "com.platform.features.journal.JournalFeature$JournalController",
                "PsychotraumatologyController", "com.platform.features.psychotraumatology.PsychotraumatologyFeature$PsychotraumatologyController",
                "UserController", "com.platform.features.user.UserFeature$UserController",
                "ClinicPlatformController", "com.platform.features.clinic.ClinicPlatformFeature$ClinicPlatformController"
        );
        String fqcn = controllers.get(simpleName);
        Assertions.assertNotNull(fqcn, "Unknown domain controller: " + simpleName);
        return resolveClassOrFail(fqcn);
    }

    @Then("the following domain components should extend or implement the core base contracts:")
    public void verifyDomainComponentsExtendBaseContracts(DataTable dataTable) {
        for (Map<String, String> row : dataTable.asMaps(String.class, String.class)) {
            String componentType = row.get("componentType");
            String className = row.get("className");
            switch (componentType) {
                case "entity" -> {
                    Class<?> entityClass = resolveDomainEntity(className);
                    Assertions.assertTrue(
                            Identifiable.class.isAssignableFrom(entityClass),
                            className + " must implement Identifiable"
                    );
                    Assertions.assertTrue(
                            Modifier.isPublic(entityClass.getModifiers()),
                            className + " must be public"
                    );
                }
                case "repository" -> {
                    Class<?> repoClass = resolveDomainRepository(className);
                    Assertions.assertTrue(
                            BaseRepository.class.isAssignableFrom(repoClass),
                            className + " must extend BaseRepository"
                    );
                    Assertions.assertFalse(
                            Modifier.isPublic(repoClass.getModifiers()),
                            className + " must be package-private"
                    );
                }
                case "service" -> {
                    Class<?> serviceClass = resolveDomainService(className);
                    Assertions.assertTrue(
                            BaseService.class.isAssignableFrom(serviceClass),
                            className + " must extend BaseService"
                    );
                    Assertions.assertFalse(
                            Modifier.isPublic(serviceClass.getModifiers()),
                            className + " must be package-private"
                    );
                }
                case "controller" -> {
                    Class<?> controllerClass = resolveDomainController(className);
                    Assertions.assertTrue(
                            BaseController.class.isAssignableFrom(controllerClass),
                            className + " must extend BaseController"
                    );
                    Assertions.assertFalse(
                            Modifier.isPublic(controllerClass.getModifiers()),
                            className + " must be package-private"
                    );
                }
                default -> Assertions.fail("Unexpected componentType: " + componentType);
            }
        }
    }

    @When("a generic appointment is registered for client {string} at {string}")
    public void registerGenericAppointment(String clientName, String startTimeStr) {
        LocalDateTime start = LocalDateTime.parse(startTimeStr);
        Map<String, Object> body = Map.of(
                "clientName", clientName,
                "startTime", startTimeStr,
                "endTime", start.plusHours(1).toString(),
                "title", "Generic Domain Session"
        );
        lastInheritedResponse = state.authenticatedRequest()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/v1/appointments");
        state.setLastResponse(lastInheritedResponse);
        if (lastInheritedResponse.statusCode() < 300) {
            String id = lastInheritedResponse.jsonPath().getString("id");
            if (id != null) {
                state.setLastAppointmentId(UUID.fromString(id));
            }
        }
    }

    @Then("the appointment registration should succeed")
    public void assertAppointmentRegistrationSucceeded() {
        Response res = state.getLastResponse();
        Assertions.assertNotNull(res);
        int status = res.statusCode();
        Assertions.assertTrue(status == 200 || status == 201, "Expected 200/201 but got " + status + ": " + res.asString());
    }

    @And("retrieving that appointment individually should return client {string} at {string}")
    public void assertRetrieveIndividualAppointment(String expectedClientName, String expectedStartTime) {
        UUID id = state.getLastAppointmentId();
        Assertions.assertNotNull(id, "Expected lastAppointmentId to be recorded");
        Response res = state.authenticatedRequest()
                .when()
                .get("/api/v1/appointments/" + id);
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        Assertions.assertEquals(expectedClientName, res.jsonPath().getString("clientName"));
        Assertions.assertTrue(
                res.jsonPath().getString("startTime").startsWith(expectedStartTime),
                "Expected startTime " + expectedStartTime + " but got " + res.jsonPath().getString("startTime")
        );
    }

    @And("the generic appointment directory should include client {string}")
    public void assertGenericAppointmentDirectoryIncludes(String expectedClientName) {
        Response res = state.authenticatedRequest()
                .when()
                .get("/api/v1/appointments");
        Assertions.assertEquals(200, res.statusCode(), res.asString());
        List<String> clientNames = res.jsonPath().getList("clientName");
        Assertions.assertTrue(
                clientNames != null && clientNames.contains(expectedClientName),
                "Expected appointment directory to include " + expectedClientName + " but got " + clientNames
        );
    }
}
