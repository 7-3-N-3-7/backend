package com.platform.cucumber;

import com.platform.controller.AppointmentController;
import com.platform.controller.ClinicPlatformController;
import com.platform.controller.JournalController;
import com.platform.controller.PsychotraumatologyController;
import com.platform.controller.UserController;
import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.model.TherapistClinicalNote;
import com.platform.entity.Appointment;
import com.platform.entity.ClientProfile;
import com.platform.entity.ClientTherapistHandshake;
import com.platform.entity.Organisation;
import com.platform.entity.ProviderProfile;
import com.platform.entity.Service;
import com.platform.entity.UserEntity;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.OrganisationRepository;
import com.platform.repository.ServiceRepository;
import com.platform.service.AppointmentService;
import com.platform.service.ClinicPlatformService;
import com.platform.service.JournalService;
import com.platform.service.PsychotraumatologyService;
import com.platform.service.UserService;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

public class GenericBaseArchitectureSteps {

    @Autowired
    private AppointmentRepository appointmentRepository;

    private Response lastInheritedResponse;

    private Class<?> resolveClassOrFail(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            Assertions.fail("Expected generic base class to exist: " + className);
            return null;
        }
    }

    private Class<?> resolveDomainEntity(String simpleName) {
        Map<String, Class<?>> entities = Map.of(
                "Appointment", Appointment.class,
                "Organisation", Organisation.class,
                "ClientProfile", ClientProfile.class,
                "ProviderProfile", ProviderProfile.class,
                "Service", Service.class,
                "ClientTherapistHandshake", ClientTherapistHandshake.class,
                "UserEntity", UserEntity.class,
                "JournalEntry", JournalEntry.class,
                "PsychotraumatologyTest", PsychotraumatologyTest.class,
                "TherapistClinicalNote", TherapistClinicalNote.class
        );
        Class<?> clazz = entities.get(simpleName);
        Assertions.assertNotNull(clazz, "Unknown domain entity: " + simpleName);
        return clazz;
    }

    private Class<?> resolveDomainRepository(String simpleName) {
        Map<String, Class<?>> repos = Map.of(
                "AppointmentRepository", AppointmentRepository.class,
                "OrganisationRepository", OrganisationRepository.class,
                "ServiceRepository", ServiceRepository.class
        );
        Class<?> clazz = repos.get(simpleName);
        Assertions.assertNotNull(clazz, "Unknown domain repository: " + simpleName);
        return clazz;
    }

    private Class<?> resolveDomainService(String simpleName) {
        Map<String, Class<?>> services = Map.of(
                "AppointmentService", AppointmentService.class,
                "JournalService", JournalService.class,
                "PsychotraumatologyService", PsychotraumatologyService.class,
                "UserService", UserService.class,
                "ClinicPlatformService", ClinicPlatformService.class
        );
        Class<?> clazz = services.get(simpleName);
        Assertions.assertNotNull(clazz, "Unknown domain service: " + simpleName);
        return clazz;
    }

    private Class<?> resolveDomainController(String simpleName) {
        Map<String, Class<?>> controllers = Map.of(
                "AppointmentController", AppointmentController.class,
                "JournalController", JournalController.class,
                "PsychotraumatologyController", PsychotraumatologyController.class,
                "UserController", UserController.class,
                "ClinicPlatformController", ClinicPlatformController.class
        );
        Class<?> clazz = controllers.get(simpleName);
        Assertions.assertNotNull(clazz, "Unknown domain controller: " + simpleName);
        return clazz;
    }

    @Then("the domain entity {string} should inherit from BaseEntity")
    public void the_domain_entity_should_inherit_from_base_entity(String entitySimpleName) {
        Class<?> baseEntityClass = resolveClassOrFail("com.platform.core.entity.BaseEntity");
        Class<?> entityClass = resolveDomainEntity(entitySimpleName);
        Assertions.assertTrue(
                baseEntityClass.isAssignableFrom(entityClass),
                entitySimpleName + " must extend com.platform.core.entity.BaseEntity"
        );
    }

    @Then("the domain repository {string} should inherit from BaseRepository")
    public void the_domain_repository_should_inherit_from_base_repository(String repoSimpleName) {
        Class<?> baseRepoClass = resolveClassOrFail("com.platform.core.repository.BaseRepository");
        Class<?> repoClass = resolveDomainRepository(repoSimpleName);
        Assertions.assertTrue(
                baseRepoClass.isAssignableFrom(repoClass),
                repoSimpleName + " must extend com.platform.core.repository.BaseRepository"
        );
    }

    @Then("{string} should inherit from BaseService")
    public void should_inherit_from_base_service(String serviceSimpleName) {
        Class<?> baseServiceClass = resolveClassOrFail("com.platform.core.service.BaseService");
        Class<?> serviceClass = resolveDomainService(serviceSimpleName);
        Assertions.assertTrue(
                baseServiceClass.isAssignableFrom(serviceClass),
                serviceSimpleName + " must extend com.platform.core.service.BaseService"
        );
    }

    @Then("{string} should inherit from BaseController")
    public void should_inherit_from_base_controller(String controllerSimpleName) {
        Class<?> baseControllerClass = resolveClassOrFail("com.platform.core.controller.BaseController");
        Class<?> controllerClass = resolveDomainController(controllerSimpleName);
        Assertions.assertTrue(
                baseControllerClass.isAssignableFrom(controllerClass),
                controllerSimpleName + " must extend com.platform.core.controller.BaseController"
        );
    }

    @When("the client fetches the created appointment by ID via the inherited BaseController GET endpoint")
    public void the_client_fetches_the_created_appointment_by_id_via_the_inherited_base_controller_get_endpoint() {
        List<Appointment> all = appointmentRepository.findAll();
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
