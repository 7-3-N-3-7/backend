package com.platform.features.management;

import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.management.ManagementFeature.ManagementController;
import com.platform.features.management.ManagementFeature.ManagementKpi;
import com.platform.features.management.ManagementFeature.ManagementKpiDto;
import com.platform.features.management.ManagementFeature.ManagementKpiRepository;
import com.platform.features.management.ManagementFeature.ManagementKpiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

public class ManagementServiceTest {

    private ManagementKpiRepository repository;
    private ManagementKpiService service;
    private ManagementController controller;

    @BeforeEach
    public void setUp() {
        repository = Mockito.mock(ManagementKpiRepository.class);
        service = new ManagementKpiService(repository);
        controller = new ManagementController(service);
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void setAuthWithRole(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "pw",
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }

    @Test
    public void testEntityDtoServiceAndControllerRbacAndCrud() {
        UUID kpiId = UUID.randomUUID();
        ManagementKpi entity = new ManagementKpi();
        entity.setId(kpiId);
        entity.setMetricName("Omsætning");
        entity.setMetricValue(50000.0);
        entity.setUnit("DKK");
        entity.setDepartment("Økonomi");

        Assertions.assertEquals(kpiId, entity.getId());
        Assertions.assertEquals("Omsætning", entity.getMetricName());
        Assertions.assertEquals(50000.0, entity.getMetricValue());
        Assertions.assertEquals("DKK", entity.getUnit());
        Assertions.assertEquals("Økonomi", entity.getDepartment());

        ManagementKpiDto dto = service.toDto(entity);
        Assertions.assertEquals(kpiId, dto.id());
        Assertions.assertEquals("Omsætning", dto.metricName());

        ManagementKpi mappedNullUnit = service.toEntity(
                new ManagementKpiDto(kpiId, "Belægning", 85.0, null, "Drift")
        );
        Assertions.assertEquals("DKK", mappedNullUnit.getUnit());

        // validateBeforeCreate edge cases
        Assertions.assertThrows(DomainException.class, () -> service.validateBeforeCreate(null));
        Assertions.assertThrows(DomainException.class, () ->
                service.validateBeforeCreate(new ManagementKpiDto(null, null, 10.0, "DKK", "Drift"))
        );
        Assertions.assertThrows(DomainException.class, () ->
                service.validateBeforeCreate(new ManagementKpiDto(null, "   ", 10.0, "DKK", "Drift"))
        );
        Assertions.assertThrows(DomainException.class, () ->
                service.validateBeforeCreate(new ManagementKpiDto(null, "Test", null, "DKK", "Drift"))
        );
        Assertions.assertThrows(DomainException.class, () ->
                service.validateBeforeCreate(new ManagementKpiDto(null, "Test", 0.0, "DKK", "Drift"))
        );
        Assertions.assertThrows(DomainException.class, () ->
                service.validateBeforeCreate(new ManagementKpiDto(null, "Test", 10.0, "DKK", null))
        );
        Assertions.assertThrows(DomainException.class, () ->
                service.validateBeforeCreate(new ManagementKpiDto(null, "Test", 10.0, "DKK", "  "))
        );

        // Seeding on count() == 0 vs non-empty count() > 0
        Mockito.when(repository.count()).thenReturn(0L).thenReturn(1L);
        Mockito.when(repository.findAll()).thenReturn(List.of(entity));
        List<ManagementKpiDto> seeded = service.findAll();
        Assertions.assertEquals(1, seeded.size());
        Mockito.verify(repository).saveAll(any());

        // Direct controller calls without auth in SecurityContextHolder
        List<ManagementKpiDto> allWithoutAuth = controller.getAll().getBody();
        Assertions.assertNotNull(allWithoutAuth);
        Assertions.assertEquals(1, allWithoutAuth.size());

        // ROLE_client denied (403)
        setAuthWithRole("ROLE_client");
        Assertions.assertThrows(DomainException.class, () -> controller.getAll());
        Assertions.assertThrows(DomainException.class, () -> controller.getAllPaged(PageRequest.of(0, 10)));
        Assertions.assertThrows(DomainException.class, () -> controller.getById(kpiId));
        Assertions.assertThrows(DomainException.class, () -> controller.create(dto));
        Assertions.assertThrows(DomainException.class, () -> controller.update(kpiId, dto));
        Assertions.assertThrows(DomainException.class, () -> controller.delete(kpiId));

        // ROLE_management allowed
        setAuthWithRole("ROLE_management");
        Assertions.assertEquals(HttpStatus.OK, controller.getAll().getStatusCode());

        // ROLE_admin allowed across all 6 endpoints
        setAuthWithRole("ROLE_admin");
        Mockito.when(repository.findAll(PageRequest.of(0, 10))).thenReturn(new PageImpl<>(List.of(entity)));
        Page<ManagementKpiDto> page = controller.getAllPaged(PageRequest.of(0, 10)).getBody();
        Assertions.assertNotNull(page);
        Assertions.assertEquals(1, page.getTotalElements());

        Mockito.when(repository.findById(kpiId)).thenReturn(Optional.of(entity));
        Assertions.assertEquals(kpiId, controller.getById(kpiId).getBody().id());

        Mockito.when(repository.save(any(ManagementKpi.class))).thenAnswer(i -> {
            ManagementKpi saved = i.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(kpiId);
            }
            return saved;
        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        ResponseEntity<ManagementKpiDto> created = controller.create(
                new ManagementKpiDto(null, "Ny KPI", 95.0, "%", "Klinik")
        );
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<ManagementKpiDto> updated = controller.update(
                kpiId,
                new ManagementKpiDto(kpiId, "Opdateret KPI", 99.0, "%", "Klinik")
        );
        Assertions.assertEquals(HttpStatus.OK, updated.getStatusCode());
        Assertions.assertEquals("Opdateret KPI", updated.getBody().metricName());

        Mockito.when(repository.existsById(kpiId)).thenReturn(true);
        Assertions.assertEquals(HttpStatus.NO_CONTENT, controller.delete(kpiId).getStatusCode());
    }
}
