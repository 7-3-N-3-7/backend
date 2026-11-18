package com.platform.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: BaseDomainCore (Del 1 af 2-File Core Architecture)
 * ============================================================================
 * @Spejler: frontend/src/core/baseDomainCore.ts
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Denne klasse udgør fundamentet for alle 7 vertikale feature-slices i `com.platform.features.*`.
 * Ved at samle tværgående domæne-, persistens- og fejlhåndteringsabstraktioner i én fil
 * elimineres gentaget CRUD-, transaktions- og fejlhåndteringskode på tværs af applikationen
 * efter Layer Supertype-mønstret (Fowler).
 *
 * ARKITEKTONISK OVERBLIK (4 KERNEBYGGESTENE):
 *   1. [Interface: HasId] & [Interface: Identifiable]
 *      Forener uforanderlige Java 17 [Record/DTO] typer (#id()) og mutable JPA [Entity]
 *      klasser (#getId() / #setId(id)) under én fælles UUID-kontrakt.
 *   2. [Repository: BaseRepository<Entity>]
 *      Generisk Spring Data JPA- og Specification-kontrakt til databasekald.
 *   3. [Enum: ErrorCode] & [Class: DomainException]
 *      Functional Exception Factory med metodereferencer (DomainException::new) og
 *      #require(condition, details) guards, der mapper forretningsfejl direkte til
 *      HTTP-statuskoder uden kendskab til Servlet/Web-laget.
 *   4. [AbstractClass: BaseService<Entity, DTO>]
 *      Template Method-motor for transaktionel CRUD-orkestrering med udvidelsespunkterne
 *      #validateBeforeCreate(dto), #validateBeforeUpdate(id, dto) og #mergeEntity(existingEntity, dto).
 *
 * ARKITEKTONISK INVARIANT (Zero-Web Boundary):
 * Denne fil må ALDRIG importere `org.springframework.web.*` eller `jakarta.servlet.*`.
 * Det garanterer, at domænelogikken er 100% uafhængig af transportprotokollen og spejles
 * 1:1 af `frontend/src/core/baseDomainCore.ts`.
 * ============================================================================
 */
public final class BaseDomainCore {

    private BaseDomainCore() {}

    // =========================================================================
    // 1. IDENTIFIABLE CONTRACTS (Universal UUID for JPA Entities & Java Records)
    // =========================================================================

    /**
     * [Interface: HasId]
     * Universel læsekontrakt for både uforanderlige Java [Record/DTO] typer og JPA [Entity] klasser.
     */
    public interface HasId {
        UUID id();
    }

    /**
     * [Interface: Identifiable]
     * Kontrakt for persistente JPA- og MongoDB-entiteter med mutabel UUID-primærnøgle.
     * Implementerer automatisk [Interface: HasId] (#id()) via en default-metode.
     */
    public interface Identifiable extends HasId {
        UUID getId();
        void setId(UUID id);

        @Override
        default UUID id() {
            return getId();
        }
    }

    // =========================================================================
    // 2. BASE REPOSITORY (Generic Spring Data JPA + Specifications)
    // =========================================================================

    /**
     * [Repository: BaseRepository<Entity>]
     * Generisk repository-interface for alle JPA-entiteter.
     * Kombinerer standard CRUD ([Interface: JpaRepository]) med dynamiske kriterieforespørgsler
     * ([Interface: JpaSpecificationExecutor]).
     */
    @NoRepositoryBean
    public interface BaseRepository<Entity extends Identifiable>
            extends JpaRepository<Entity, UUID>, JpaSpecificationExecutor<Entity> {
    }

    // =========================================================================
    // 3. FUNCTIONAL ERROR CODE ENUM & DOMAIN EXCEPTION
    // =========================================================================

    /**
     * [Enum: ErrorCode]
     * Katalog over standardiserede domænefejlkoder (Functional Exception Factory).
     * Hver enum-konstant binder en `httpStatus`, en menneskelæsbar `title` og en
     * [Interface: BiFunction]-fabrik (`DomainException::new`).
     */
    public enum ErrorCode {
        NOT_FOUND(404, "Ressource ikke fundet", DomainException::new),
        CONFLICT(409, "Ressource-konflikt", DomainException::new),
        BAD_REQUEST(400, "Ugyldig forespørgsel", DomainException::new),
        UNAUTHORIZED(401, "Ikke autoriseret", DomainException::new),
        FORBIDDEN(403, "Adgang nægtet", DomainException::new),
        UNPROCESSABLE_ENTITY(422, "Valideringsfejl i domæneregel", DomainException::new),
        INTERNAL_ERROR(500, "Intern systemfejl", DomainException::new);

        private final int httpStatus;
        private final String title;
        private final BiFunction<ErrorCode, String, DomainException> exceptionFactory;

        ErrorCode(int httpStatus, String title, BiFunction<ErrorCode, String, DomainException> exceptionFactory) {
            this.httpStatus = httpStatus;
            this.title = title;
            this.exceptionFactory = exceptionFactory;
        }

        public int httpStatus() { return httpStatus; }
        public String title() { return title; }

        /** #toException(details) — Opretter en [Class: DomainException] med den angivne `details`-beskrivelse. */
        public DomainException toException(String details) {
            return exceptionFactory.apply(this, details);
        }

        /** #toException(format, args) — Opretter en formateret [Class: DomainException] via String#format. */
        public DomainException toException(String format, Object... args) {
            return exceptionFactory.apply(this, String.format(format, args));
        }

        /** #supply(details) — Lazy [Interface: Supplier] til brug i Optional#orElseThrow. */
        public Supplier<DomainException> supply(String details) {
            return () -> toException(details);
        }

        /**
         * #require(condition, details) — Funktionel domæne-guard (Design by Contract):
         * Kaster en [Class: DomainException], hvis `condition` er `false`.
         */
        public void require(boolean condition, String details) {
            if (!condition) {
                throw toException(details);
            }
        }
    }

    /**
     * [Class: DomainException]
     * Ukontrolleret domæne-exception, der bærer en struktureret [Enum: ErrorCode] og et `timestamp`.
     * Oversættes i præsentationslaget ([Class: BaseWebCore.GlobalExceptionHandler]) til RFC 7807 `ProblemDetail`.
     */
    public static class DomainException extends RuntimeException {
        private final ErrorCode errorCode;
        private final Instant timestamp;

        public DomainException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = Objects.requireNonNull(errorCode);
            this.timestamp = Instant.now();
        }

        public ErrorCode getErrorCode() { return errorCode; }
        public int getStatus() { return errorCode.httpStatus(); }
        public String getTitle() { return errorCode.title(); }
        public Instant getTimestamp() { return timestamp; }
    }

    // =========================================================================
    // 4. BASE SERVICE (Template Method Pattern & Transaction Management)
    // =========================================================================

    /**
     * [AbstractClass: BaseService<Entity, DTO>]
     * Abstrakt domæneservice (Layer Supertype), der implementerer standard transaktionelle
     * CRUD-operationer og udstiller Template Method hooks (#validateBeforeCreate(dto)
     * og #validateBeforeUpdate(id, dto)) til domænespecifikke forretningsregler i hver vertikal slice.
     *
     * @param <Entity> Intern JPA-entitetstype, der implementerer [Interface: Identifiable]
     * @param <DTO>    Ekstern datakontrakt, typisk en Java [Record/DTO], der implementerer [Interface: HasId]
     */
    @Transactional(readOnly = true)
    public static abstract class BaseService<Entity extends Identifiable, DTO extends HasId> {

        protected final BaseRepository<Entity> repository;

        protected BaseService(BaseRepository<Entity> repository) {
            this.repository = Objects.requireNonNull(repository);
        }

        /** #toDto(entity) — Konverterer en intern JPA [Entity] til en ekstern [Record/DTO] (Information Hiding). */
        protected abstract DTO toDto(Entity entity);

        /** #toEntity(dto) — Konverterer en ekstern [Record/DTO] til en ny JPA [Entity]. */
        protected abstract Entity toEntity(DTO dto);

        /** #mergeEntity(existingEntity, dto) — Fletter ændringer fra `dto` ind i `existingEntity` og bevarer dens `id`. */
        protected Entity mergeEntity(Entity existingEntity, DTO dto) {
            Entity updatedEntity = toEntity(dto);
            updatedEntity.setId(existingEntity.getId());
            return updatedEntity;
        }

        public List<DTO> findAll() {
            return repository.findAll().stream()
                    .map(this::toDto)
                    .toList();
        }

        public Page<DTO> findAllPaged(Pageable pageable) {
            return repository.findAll(pageable)
                    .map(this::toDto);
        }

        public Optional<DTO> findOptionalById(UUID id) {
            return repository.findById(id).map(this::toDto);
        }

        public DTO findById(UUID id) {
            return toDto(findEntityById(id));
        }

        public Entity findEntityById(UUID id) {
            return repository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Ressource med ID '" + id + "' blev ikke fundet."));
        }

        /**
         * #create(dto) — Template Method for transaktionel oprettelse:
         */
        @Transactional
        public DTO create(DTO dto) {
            // Trin 1: Kør domænespecifikke forretningsregler i subklassen via #validateBeforeCreate(dto)
            validateBeforeCreate(dto);

            // Trin 2: Konverter [Record/DTO] `dto` til [Entity] `entity` og nulstil `id` for at fremtvinge SQL INSERT
            Entity entity = toEntity(dto);
            entity.setId(null);

            // Trin 3: Persister via `repository` og returner resultatet mappet tilbage til [Record/DTO]
            return toDto(repository.save(entity));
        }

        /**
         * #update(id, dto) — Template Method for transaktionel opdatering:
         */
        @Transactional
        public DTO update(UUID id, DTO dto) {
            // Trin 1: Kør domænespecifikke opdateringsregler i subklassen via #validateBeforeUpdate(id, dto)
            validateBeforeUpdate(id, dto);

            // Trin 2: Hent eksisterende [Entity] fra `repository` (eller kast HTTP 404 NOT_FOUND)
            Entity existingEntity = findEntityById(id);

            // Trin 3: Flet ændringer ind i `existingEntity` og gem i databasen
            Entity mergedEntity = mergeEntity(existingEntity, dto);
            return toDto(repository.save(mergedEntity));
        }

        /**
         * #deleteById(id) — Sletter en ressource ud fra `id` eller kaster [Enum: ErrorCode] `NOT_FOUND`.
         */
        @Transactional
        public void deleteById(UUID id) {
            if (!repository.existsById(id)) {
                throw ErrorCode.NOT_FOUND.toException("Kan ikke slette: ID '%s' findes ikke.", id);
            }
            repository.deleteById(id);
        }

        /** #validateBeforeCreate(dto) — Hook til domænevalidering før #create(dto) (overskrives i vertikale slices). */
        protected void validateBeforeCreate(DTO dto) {}

        /** #validateBeforeUpdate(id, dto) — Hook til domænevalidering før #update(id, dto) (overskrives i vertikale slices). */
        protected void validateBeforeUpdate(UUID id, DTO dto) {}
    }
}
