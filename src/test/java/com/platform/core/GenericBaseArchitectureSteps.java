package com.platform.core;

import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentPort;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.Organisation;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.Service;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.TherapistClinicalNote;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.user.UserFeature.UserEntity;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

public class GenericBaseArchitectureSteps {

    @Autowired
    private AppointmentPort appointmentPort;

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
                Map.entry("PsychotraumatologyTest", PsychotraumatologyTest.class),
                Map.entry("TherapistClinicalNote", TherapistClinicalNote.class)
        );
        Class<?> clazz = entities.get(simpleName);
        Assertions.assertNotNull(clazz, "Unknown domain entity: " + simpleName);
        return clazz;
    }

    private Class<?> resolveDomainRepository(String simpleName) {
        Map<String, String> repos = Map.of(
                "AppointmentRepository", "com.platform.features.appointment.AppointmentFeature$AppointmentRepository",
                "OrganisationRepository", "com.platform.features.clinic.ClinicPlatformFeature$OrganisationRepository",
                "ServiceRepository", "com.platform.features.clinic.ClinicPlatformFeature$ServiceRepository"
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

    @Then("the domain entity {string} should inherit from BaseEntity")
    public void the_domain_entity_should_inherit_from_base_entity(String entitySimpleName) {
        Class<?> entityClass = resolveDomainEntity(entitySimpleName);
        Assertions.assertTrue(
                Identifiable.class.isAssignableFrom(entityClass),
                entitySimpleName + " must implement BaseDomainCore.Identifiable"
        );
        Assertions.assertTrue(
                Modifier.isPublic(entityClass.getModifiers()),
                entitySimpleName + " must be public"
        );
    }

    @Then("the domain repository {string} should inherit from BaseRepository")
    public void the_domain_repository_should_inherit_from_base_repository(String repoSimpleName) {
        Class<?> repoClass = resolveDomainRepository(repoSimpleName);
        Assertions.assertTrue(
                BaseRepository.class.isAssignableFrom(repoClass),
                repoSimpleName + " must extend BaseDomainCore.BaseRepository"
        );
        Assertions.assertFalse(
                Modifier.isPublic(repoClass.getModifiers()),
                repoSimpleName + " must be package-private inside its Feature class"
        );
    }

    @Then("{string} should inherit from BaseService")
    public void should_inherit_from_base_service(String serviceSimpleName) {
        Class<?> serviceClass = resolveDomainService(serviceSimpleName);
        Assertions.assertTrue(
                BaseService.class.isAssignableFrom(serviceClass),
                serviceSimpleName + " must extend BaseDomainCore.BaseService"
        );
        Assertions.assertFalse(
                Modifier.isPublic(serviceClass.getModifiers()),
                serviceSimpleName + " must be package-private inside its Feature class"
        );
    }

    @Then("{string} should inherit from BaseController")
    public void should_inherit_from_base_controller(String controllerSimpleName) {
        Class<?> controllerClass = resolveDomainController(controllerSimpleName);
        Assertions.assertTrue(
                BaseController.class.isAssignableFrom(controllerClass),
                controllerSimpleName + " must extend BaseWebCore.BaseController"
        );
        Assertions.assertFalse(
                Modifier.isPublic(controllerClass.getModifiers()),
                controllerSimpleName + " must be package-private inside its Feature class"
        );
    }

    @When("the client fetches the created appointment by ID via the inherited BaseController GET endpoint")
    public void the_client_fetches_the_created_appointment_by_id_via_the_inherited_base_controller_get_endpoint() {
        List<Appointment> all = appointmentPort.findAll();
        Assertions.assertFalse(all.isEmpty(), "Expected at least one appointment in DB");
        UUID id = all.get(0).getId();
        lastInheritedResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-5e357ca1-4913-414f-91a6-81a7de9042ab")
                .when()
                .get("/api/v1/appointments/" + id);
    }

    @Then("the inherited BaseController GET by ID response status should be {int} and title should be {string}")
    public void the_inherited_base_controller_get_by_id_response_status_should_be_and_title_should_be(
            Integer expectedStatus,
            String expectedTitle
    ) {
        Assertions.assertNotNull(lastInheritedResponse);
        lastInheritedResponse.then().statusCode(expectedStatus);
        Assertions.assertEquals(expectedTitle, lastInheritedResponse.jsonPath().getString("title"));
    }

    @When("the client fetches all appointments via the inherited BaseController GET all endpoint")
    public void the_client_fetches_all_appointments_via_the_inherited_base_controller_get_all_endpoint() {
        lastInheritedResponse = given()
                .header("Authorization", "Bearer valid-client-token-for-5e357ca1-4913-414f-91a6-81a7de9042ab")
                .when()
                .get("/api/v1/appointments");
    }

    @Then("the inherited BaseController GET all response should contain at least {int} appointment")
    public void the_inherited_base_controller_get_all_response_should_contain_at_least_appointment(Integer minCount) {
        Assertions.assertNotNull(lastInheritedResponse);
        lastInheritedResponse.then().statusCode(200);
        List<?> list = lastInheritedResponse.jsonPath().getList("$");
        Assertions.assertTrue(list.size() >= minCount, "Expected at least " + minCount + " appointment(s)");
    }
}
