package com.platform.core;

import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.DomainException;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;
import com.platform.core.BaseWebCore.GlobalExceptionHandler;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentDto;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfileDto;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshakeDto;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakeRequestDto;
import com.platform.features.clinic.ClinicPlatformFeature.OnboardingRequestDto;
import com.platform.features.clinic.ClinicPlatformFeature.Organisation;
import com.platform.features.clinic.ClinicPlatformFeature.OrganisationDto;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfileDto;
import com.platform.features.clinic.ClinicPlatformFeature.Service;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceDto;
import com.platform.features.finance.FinanceFeature.FinancialVoucherDto;
import com.platform.features.finance.FinanceFeature.InvoiceDto;
import com.platform.features.finance.FinanceFeature.LedgerSummaryDto;
import com.platform.features.finance.FinanceFeature.PaymentDto;
import com.platform.features.finance.FinanceFeature.RefundDto;
import com.platform.features.finance.FinanceFeature.VippsAgreementDto;
import com.platform.features.i18n.I18nFeature.I18nDictionary;
import com.platform.features.i18n.I18nFeature.I18nDictionaryDto;
import com.platform.features.i18n.I18nFeature.TranslationUpdateRequest;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.JournalEntryDto;
import com.platform.features.journal.JournalFeature.TherapistClinicalNote;
import com.platform.features.journal.JournalFeature.TherapistClinicalNoteDto;
import com.platform.features.journal.JournalFeature.TherapistNote;
import com.platform.features.journal.JournalFeature.TherapistNoteDto;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTestDto;
import com.platform.features.user.UserFeature.UserDto;
import com.platform.features.user.UserFeature.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GenericBaseArchitectureTest {

    public static class DummyEntity implements Identifiable {
        private UUID id;
        private String title;

        public DummyEntity() {}

        public DummyEntity(UUID id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public record DummyDto(UUID id, String title) implements HasId {}

    static class DummyService extends BaseService<DummyEntity, DummyDto> {
        DummyService(BaseRepository<DummyEntity> repository) {
            super(repository);
        }

        @Override
        protected DummyDto toDto(DummyEntity entity) {
            return new DummyDto(entity.getId(), entity.getTitle());
        }

        @Override
        protected DummyEntity toEntity(DummyDto dto) {
            return new DummyEntity(dto.id(), dto.title());
        }
    }

    static class DummyController extends BaseController<DummyEntity, DummyDto> {
        DummyController(DummyService service) {
            super(service);
        }
    }

    @Test
    public void testCleanArchitectureDependencyRuleAndVerticalSliceEncapsulation() throws Exception {
        // 1. Verify BaseDomainCore has zero imports from org.springframework.web or jakarta.servlet
        Path baseDomainPath = Path.of("src/main/java/com/platform/core/BaseDomainCore.java");
        if (Files.exists(baseDomainPath)) {
            String source = Files.readString(baseDomainPath);
            Assertions.assertFalse(source.contains("import org.springframework.web"),
                    "BaseDomainCore must NEVER import org.springframework.web");
            Assertions.assertFalse(source.contains("import jakarta.servlet"),
                    "BaseDomainCore must NEVER import jakarta.servlet");
        }

        // 2. Verify all JPA/Mongo Entities implement Identifiable with UUID id
        List<Class<?>> entities = List.of(
                Appointment.class,
                Organisation.class,
                ClientProfile.class,
                ProviderProfile.class,
                Service.class,
                ClientTherapistHandshake.class,
                UserEntity.class,
                JournalEntry.class,
                TherapistClinicalNote.class,
                TherapistNote.class,
                PsychotraumatologyTest.class,
                I18nDictionary.class
        );
        for (Class<?> entityClass : entities) {
            Assertions.assertTrue(Identifiable.class.isAssignableFrom(entityClass),
                    entityClass.getSimpleName() + " must implement Identifiable");
            Assertions.assertEquals(UUID.class, entityClass.getMethod("getId").getReturnType(),
                    entityClass.getSimpleName() + ".getId() must return UUID");
            Assertions.assertTrue(Modifier.isPublic(entityClass.getModifiers()),
                    entityClass.getSimpleName() + " must be public");
        }

        // 3. Verify all Record DTOs implement HasId with UUID id and are Java records
        List<Class<?>> recordDtos = List.of(
                AppointmentDto.class,
                ClientProfileDto.class,
                OrganisationDto.class,
                ProviderProfileDto.class,
                ServiceDto.class,
                ClientTherapistHandshakeDto.class,
                OnboardingRequestDto.class,
                HandshakeRequestDto.class,
                JournalEntryDto.class,
                TherapistClinicalNoteDto.class,
                TherapistNoteDto.class,
                PsychotraumatologyTestDto.class,
                UserDto.class,
                I18nDictionaryDto.class,
                TranslationUpdateRequest.class,
                InvoiceDto.class,
                PaymentDto.class,
                RefundDto.class,
                FinancialVoucherDto.class,
                LedgerSummaryDto.class,
                VippsAgreementDto.class
        );
        for (Class<?> dtoClass : recordDtos) {
            Assertions.assertTrue(dtoClass.isRecord(), dtoClass.getSimpleName() + " must be a Java record");
            Assertions.assertTrue(HasId.class.isAssignableFrom(dtoClass),
                    dtoClass.getSimpleName() + " must implement HasId");
            Assertions.assertEquals(UUID.class, dtoClass.getMethod("id").getReturnType(),
                    dtoClass.getSimpleName() + ".id() must return UUID");
            Assertions.assertTrue(Modifier.isPublic(dtoClass.getModifiers()),
                    dtoClass.getSimpleName() + " must be public");
        }

        // 4. Verify all Repositories inherit from BaseRepository and are package-private
        List<String> repositories = List.of(
                "com.platform.features.appointment.AppointmentFeature$AppointmentRepository",
                "com.platform.features.clinic.ClinicPlatformFeature$ClientProfileRepository",
                "com.platform.features.clinic.ClinicPlatformFeature$OrganisationRepository",
                "com.platform.features.clinic.ClinicPlatformFeature$ProviderProfileRepository",
                "com.platform.features.clinic.ClinicPlatformFeature$ServiceRepository",
                "com.platform.features.clinic.ClinicPlatformFeature$HandshakeRepository",
                "com.platform.features.user.UserFeature$UserRepository",
                "com.platform.features.journal.JournalFeature$JournalEntryRepository",
                "com.platform.features.journal.JournalFeature$TherapistClinicalNoteRepository",
                "com.platform.features.journal.JournalFeature$TherapistNoteRepository",
                "com.platform.features.psychotraumatology.PsychotraumatologyFeature$PsychotraumatologyTestRepository",
                "com.platform.features.i18n.I18nFeature$I18nDictionaryRepository"
        );
        for (String fqcn : repositories) {
            Class<?> repoClass = Class.forName(fqcn);
            Assertions.assertFalse(Modifier.isPublic(repoClass.getModifiers()),
                    repoClass.getSimpleName() + " must be package-private");
        }

        // 5. Verify all JPA Services and Controllers inherit from BaseService/BaseController and are package-private
        List<String> services = List.of(
                "com.platform.features.appointment.AppointmentFeature$AppointmentService",
                "com.platform.features.clinic.ClinicPlatformFeature$ClinicPlatformService",
                "com.platform.features.user.UserFeature$UserService",
                "com.platform.features.journal.JournalFeature$JournalService",
                "com.platform.features.psychotraumatology.PsychotraumatologyFeature$PsychotraumatologyService"
        );
        for (String fqcn : services) {
            Class<?> serviceClass = Class.forName(fqcn);
            Assertions.assertTrue(BaseService.class.isAssignableFrom(serviceClass),
                    serviceClass.getSimpleName() + " must extend BaseService");
            Assertions.assertFalse(Modifier.isPublic(serviceClass.getModifiers()),
                    serviceClass.getSimpleName() + " must be package-private");
        }
        Assertions.assertFalse(Modifier.isPublic(
                Class.forName("com.platform.features.i18n.I18nFeature$I18nService").getModifiers()));
        Assertions.assertFalse(Modifier.isPublic(
                Class.forName("com.platform.features.finance.FinanceFeature$FinanceService").getModifiers()));

        List<String> controllers = List.of(
                "com.platform.features.appointment.AppointmentFeature$AppointmentController",
                "com.platform.features.clinic.ClinicPlatformFeature$ClinicPlatformController",
                "com.platform.features.user.UserFeature$UserController",
                "com.platform.features.journal.JournalFeature$JournalController",
                "com.platform.features.psychotraumatology.PsychotraumatologyFeature$PsychotraumatologyController"
        );
        for (String fqcn : controllers) {
            Class<?> ctrlClass = Class.forName(fqcn);
            Assertions.assertTrue(BaseController.class.isAssignableFrom(ctrlClass),
                    ctrlClass.getSimpleName() + " must extend BaseController");
            Assertions.assertFalse(Modifier.isPublic(ctrlClass.getModifiers()),
                    ctrlClass.getSimpleName() + " must be package-private");
        }
        Assertions.assertFalse(Modifier.isPublic(
                Class.forName("com.platform.features.i18n.I18nFeature$I18nController").getModifiers()));
        Assertions.assertFalse(Modifier.isPublic(
                Class.forName("com.platform.features.finance.FinanceFeature$FinanceController").getModifiers()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBaseServiceBaseControllerErrorCodeAndGlobalExceptionHandler() {
        BaseRepository<DummyEntity> repo = mock(BaseRepository.class);
        DummyService service = new DummyService(repo);
        DummyController controller = new DummyController(service);

        UUID id = UUID.randomUUID();
        DummyEntity entity = new DummyEntity(id, "Test Item");
        DummyDto dto = new DummyDto(id, "Test Item");

        // Identifiable default id() method
        Assertions.assertEquals(id, entity.id());

        when(repo.findAll()).thenReturn(List.of(entity));
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entity)));
        when(repo.findById(id)).thenReturn(Optional.of(entity));
        when(repo.existsById(id)).thenReturn(true);
        when(repo.count()).thenReturn(1L);
        when(repo.save(any(DummyEntity.class))).thenAnswer(inv -> {
            DummyEntity saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(id);
            }
            return saved;
        });

        // BaseService operations
        Assertions.assertEquals(1, service.findAll().size());
        Page<DummyDto> paged = service.findAllPaged(PageRequest.of(0, 10));
        Assertions.assertEquals(1, paged.getTotalElements());
        Assertions.assertTrue(service.findOptionalById(id).isPresent());
        Assertions.assertEquals("Test Item", service.findById(id).title());
        Assertions.assertEquals(id, service.findEntityById(id).getId());

        DummyDto created = service.create(new DummyDto(UUID.randomUUID(), "Created"));
        Assertions.assertEquals(id, created.id());
        Assertions.assertEquals("Created", created.title());

        DummyDto updated = service.update(id, new DummyDto(null, "Updated"));
        Assertions.assertEquals(id, updated.id());
        Assertions.assertEquals("Updated", updated.title());

        service.deleteById(id);
        verify(repo).deleteById(id);

        // Missing ID throws DomainException (NOT_FOUND)
        UUID missingId = UUID.randomUUID();
        when(repo.findById(missingId)).thenReturn(Optional.empty());
        when(repo.existsById(missingId)).thenReturn(false);

        DomainException notFoundEx = Assertions.assertThrows(DomainException.class, () -> service.findById(missingId));
        Assertions.assertEquals(ErrorCode.NOT_FOUND, notFoundEx.getErrorCode());
        Assertions.assertEquals(404, notFoundEx.getStatus());
        Assertions.assertEquals("Ressource ikke fundet", notFoundEx.getTitle());
        Assertions.assertNotNull(notFoundEx.getTimestamp());

        DomainException deleteNotFoundEx = Assertions.assertThrows(DomainException.class, () -> service.deleteById(missingId));
        Assertions.assertEquals(ErrorCode.NOT_FOUND, deleteNotFoundEx.getErrorCode());

        // ErrorCode functional methods
        ErrorCode.BAD_REQUEST.require(true, "Should not throw");
        DomainException guardEx = Assertions.assertThrows(DomainException.class,
                () -> ErrorCode.CONFLICT.require(false, "Slot taken"));
        Assertions.assertEquals(409, guardEx.getStatus());
        Assertions.assertEquals("Slot taken", guardEx.getMessage());

        for (ErrorCode code : ErrorCode.values()) {
            Assertions.assertTrue(code.httpStatus() >= 400);
            Assertions.assertNotNull(code.title());
            Assertions.assertEquals(code, code.toException("msg").getErrorCode());
            Assertions.assertEquals(code, code.toException("msg %s", 1).getErrorCode());
            Assertions.assertEquals(code, code.supply("msg").get().getErrorCode());
        }

        // BaseController endpoints
        MockHttpServletRequest servletReq = new MockHttpServletRequest("POST", "/api/dummy");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletReq));
        try {
            Assertions.assertEquals(HttpStatus.OK, controller.getAll().getStatusCode());
            Assertions.assertEquals(HttpStatus.OK, controller.getAllPaged(PageRequest.of(0, 5)).getStatusCode());
            Assertions.assertEquals(HttpStatus.OK, controller.getById(id).getStatusCode());
            Assertions.assertEquals(HttpStatus.CREATED, controller.create(dto).getStatusCode());
            Assertions.assertEquals(HttpStatus.OK, controller.update(id, dto).getStatusCode());
            ResponseEntity<Void> delResp = controller.delete(id);
            Assertions.assertEquals(HttpStatus.NO_CONTENT, delResp.getStatusCode());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        // GlobalExceptionHandler (RFC 7807 ProblemDetail)
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getRequestURI()).thenReturn("/api/v1/bookings");

        ResponseEntity<ProblemDetail> domainResp = handler.handleDomainException(guardEx, mockReq);
        Assertions.assertEquals(HttpStatus.CONFLICT, domainResp.getStatusCode());
        ProblemDetail domainPd = domainResp.getBody();
        Assertions.assertNotNull(domainPd);
        Assertions.assertEquals(409, domainPd.getStatus());
        Assertions.assertEquals("Ressource-konflikt", domainPd.getTitle());
        Assertions.assertEquals("Slot taken", domainPd.getDetail());
        Assertions.assertEquals("CONFLICT", domainPd.getProperties().get("errorCode"));

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("dto", "startTime", "må ikke være null"),
                new FieldError("dto", "endTime", "skal være i fremtiden"),
                new FieldError("dto", "endTime", "duplikeret fejl")
        ));
        MethodArgumentNotValidException valEx = mock(MethodArgumentNotValidException.class);
        when(valEx.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ProblemDetail> valResp = handler.handleValidationException(valEx, mockReq);
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, valResp.getStatusCode());
        ProblemDetail valPd = valResp.getBody();
        Assertions.assertNotNull(valPd);
        Assertions.assertEquals(400, valPd.getStatus());
        Assertions.assertEquals("BAD_REQUEST", valPd.getProperties().get("errorCode"));
        Assertions.assertNotNull(valPd.getProperties().get("fields"));

        // Additional DTO constructors/getters coverage
        AppointmentDto aptDto = new AppointmentDto(id, id, id, id, "T", "O", "L", null, null, "Title", "CONFIRMED", "hash", id);
        Assertions.assertEquals(id, aptDto.id());
        ClientProfileDto cpDto = new ClientProfileDto(id, id, "Name", "1990-01-01", "Addr", "2100", "123", "a@b.dk");
        Assertions.assertEquals(id, cpDto.id());
    }
}
