@api @architecture
Feature: Generic Base Architecture Inheritance (BaseEntity, BaseRepository, BaseDAO, BaseDTO, BaseService, BaseController)
  As a Backend Developer on the EduJournal platform
  I want domain entities, repositories, DAOs, DTOs, services, and controllers to inherit from generic Base classes wired dynamically via Spring IoC
  So that standard CRUD, dynamic filtering, and lifecycle hooks flow cleanly through Controller -> Service -> Repository -> Entity / DTO swimlanes

  Background:
    Given Business PostgreSQL database is clean and accessible
    And the clinic marketplace tables are clean in PostgreSQL

  Scenario: Domain Entities, Repositories, Services, and Controllers inherit from Generic Base Architecture classes
    Then the domain entity "Appointment" should inherit from BaseEntity
    And the domain entity "Organisation" should inherit from BaseEntity
    And the domain entity "ClientProfile" should inherit from BaseEntity
    And the domain entity "ProviderProfile" should inherit from BaseEntity
    And the domain entity "Service" should inherit from BaseEntity
    And the domain entity "ClientTherapistHandshake" should inherit from BaseEntity
    And the domain entity "UserEntity" should inherit from BaseEntity
    And the domain entity "JournalEntry" should inherit from BaseEntity
    And the domain entity "PsychotraumatologyTest" should inherit from BaseEntity
    And the domain entity "TherapistClinicalNote" should inherit from BaseEntity
    And the domain repository "AppointmentRepository" should inherit from BaseRepository
    And the domain repository "OrganisationRepository" should inherit from BaseRepository
    And the domain repository "ServiceRepository" should inherit from BaseRepository
    And "AppointmentService" should inherit from BaseService
    And "JournalService" should inherit from BaseService
    And "PsychotraumatologyService" should inherit from BaseService
    And "UserService" should inherit from BaseService
    And "ClinicPlatformService" should inherit from BaseService
    And "AppointmentController" should inherit from BaseController
    And "JournalController" should inherit from BaseController
    And "PsychotraumatologyController" should inherit from BaseController
    And "UserController" should inherit from BaseController
    And "ClinicPlatformController" should inherit from BaseController

  Scenario: AppointmentController and AppointmentService inherit generic CRUD operations from BaseController and BaseService alongside custom endpoints
    Given a valid client with UUID "5e357ca1-4913-414f-91a6-81a7de9042ab"
    And a valid therapist with UUID "4c421f46-836c-4897-8dae-ba9587473273"
    Then "AppointmentService" should inherit from BaseService
    And "AppointmentController" should inherit from BaseController
    When the client posts a new appointment booking request with:
      | title         | Generisk Base Arkitektur Samtale |
      | startTime     | 2027-11-10T10:00:00              |
      | endTime       | 2027-11-10T11:00:00              |
      | therapistUuid | 4c421f46-836c-4897-8dae-ba9587473273 |
    Then the returned appointment object should contain a generated UUID
    When the client fetches the created appointment by ID via the inherited BaseController GET endpoint
    Then the inherited BaseController GET by ID response status should be 200 and title should be "Generisk Base Arkitektur Samtale"
    When the client fetches all appointments via the inherited BaseController GET all endpoint
    Then the inherited BaseController GET all response should contain at least 1 appointment
