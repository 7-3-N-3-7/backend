package com.platform.features.management;

import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: ManagementFeature (Vertical Feature Slice)
 * ============================================================================
 * @Spejler: frontend/src/features/management/ManagementFeature.tsx
 *
 * FORMÅL:
 * Udstiller KPI-styring og klinikledelsesnøgletal (`/api/v1/management/kpis`)
 * bag Zero-Trust RBAC (kun `ADMIN` / `MANAGEMENT`).
 * ============================================================================
 */
public final class ManagementFeature {

    private ManagementFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Record implementing HasId)
    // =========================================================================

    public record ManagementKpiDto(
            UUID id,
            String metricName,
            Double metricValue,
            String unit,
            String department
    ) implements HasId {}

    // =========================================================================
    // 2. JPA ENTITY (Implements Identifiable)
    // =========================================================================

    @Entity(name = "ManagementKpi")
    @Table(name = "management_kpis")
    public static class ManagementKpi implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "metric_name", nullable = false)
        private String metricName;

        @Column(name = "metric_value", nullable = false)
        private Double metricValue;

        @Column(nullable = false)
        private String unit = "DKK";

        @Column(nullable = false)
        private String department;

        public ManagementKpi() {}

        public ManagementKpi(UUID id, String metricName, Double metricValue, String unit, String department) {
            this.id = id;
            this.metricName = metricName;
            this.metricValue = metricValue;
            this.unit = unit;
            this.department = department;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public void setId(UUID id) {
            this.id = id;
        }

        public String getMetricName() {
            return metricName;
        }

        public void setMetricName(String metricName) {
            this.metricName = metricName;
        }

        public Double getMetricValue() {
            return metricValue;
        }

        public void setMetricValue(Double metricValue) {
            this.metricValue = metricValue;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }
    }

    // =========================================================================
    // 3. REPOSITORY (Package-Private)
    // =========================================================================

    @Repository
    interface ManagementKpiRepository extends BaseRepository<ManagementKpi> {}

    // =========================================================================
    // 4. DOMAIN SERVICE (Package-Private)
    // =========================================================================

    @Service
    static class ManagementKpiService extends BaseService<ManagementKpi, ManagementKpiDto> {

        ManagementKpiService(ManagementKpiRepository repository) {
            super(repository);
        }

        @Override
        protected ManagementKpiDto toDto(ManagementKpi entity) {
            return new ManagementKpiDto(
                    entity.getId(),
                    entity.getMetricName(),
                    entity.getMetricValue(),
                    entity.getUnit(),
                    entity.getDepartment()
            );
        }

        @Override
        protected ManagementKpi toEntity(ManagementKpiDto dto) {
            return new ManagementKpi(
                    dto.id(),
                    dto.metricName(),
                    dto.metricValue(),
                    Objects.requireNonNullElse(dto.unit(), "DKK"),
                    dto.department()
            );
        }

        @Override
        protected void validateBeforeCreate(ManagementKpiDto dto) {
            ErrorCode.BAD_REQUEST.require(
                    dto != null && dto.metricName() != null && !dto.metricName().isBlank(),
                    "KPI Metric name is required"
            );
            ErrorCode.BAD_REQUEST.require(
                    dto.metricValue() != null && dto.metricValue() > 0,
                    "KPI Metric value must be positive"
            );
            ErrorCode.BAD_REQUEST.require(
                    dto.department() != null && !dto.department().isBlank(),
                    "Department is required"
            );
        }

        @Override
        @Transactional
        public List<ManagementKpiDto> findAll() {
            if (repository.count() == 0) {
                repository.saveAll(List.of(
                        new ManagementKpi(null, "Gennemsnitlig sessionspris", 1200.0, "DKK", "Psykotraumatologi"),
                        new ManagementKpi(null, "Månedlig kapacitetsudnyttelse", 84.0, "%", "Klinikdrift"),
                        new ManagementKpi(null, "Klientfastholdelse (3 mdr.)", 92.0, "%", "Kvalitet & Audit")
                ));
            }
            return super.findAll();
        }
    }

    // =========================================================================
    // 5. REST CONTROLLER (Package-Private)
    // =========================================================================

    @RestController
    @RequestMapping("/api/v1/management/kpis")
    @CrossOrigin(origins = "*")
    static class ManagementController extends BaseController<ManagementKpi, ManagementKpiDto> {

        ManagementController(ManagementKpiService service) {
            super(service);
        }

        private void requireManagementAccess() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return;
            }
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String role = authority.getAuthority();
                if ("ROLE_admin".equalsIgnoreCase(role) || "ROLE_management".equalsIgnoreCase(role)) {
                    return;
                }
            }
            throw ErrorCode.FORBIDDEN.toException("Management or admin role is required");
        }

        @Override
        @GetMapping
        public ResponseEntity<List<ManagementKpiDto>> getAll() {
            requireManagementAccess();
            return super.getAll();
        }

        @Override
        @GetMapping("/paged")
        public ResponseEntity<Page<ManagementKpiDto>> getAllPaged(Pageable pageable) {
            requireManagementAccess();
            return super.getAllPaged(pageable);
        }

        @Override
        @GetMapping("/{id}")
        public ResponseEntity<ManagementKpiDto> getById(@PathVariable UUID id) {
            requireManagementAccess();
            return super.getById(id);
        }

        @Override
        @PostMapping
        public ResponseEntity<ManagementKpiDto> create(@Valid @RequestBody ManagementKpiDto dto) {
            requireManagementAccess();
            return super.create(dto);
        }

        @Override
        @PutMapping("/{id}")
        public ResponseEntity<ManagementKpiDto> update(@PathVariable UUID id, @Valid @RequestBody ManagementKpiDto dto) {
            requireManagementAccess();
            return super.update(id, dto);
        }

        @Override
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(@PathVariable UUID id) {
            requireManagementAccess();
            return super.delete(id);
        }
    }
}
