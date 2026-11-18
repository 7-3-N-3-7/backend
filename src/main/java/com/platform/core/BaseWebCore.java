package com.platform.core;

import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.DomainException;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: BaseWebCore (Del 2 af 2-File Core Architecture)
 * ============================================================================
 * @Spejler: frontend/src/core/baseUiCore.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Denne klasse indkapsler al HTTP/REST-protokolhåndtering og fejlformatering
 * for hele platformen. Ved at isolere Spring Web og Jakarta Servlet her holdes
 * [Class: BaseDomainCore] 100% fri for transportafhængigheder.
 *
 * ARKITEKTONISK OVERBLIK (2 KERNEBYGGESTENE):
 *   1. [AbstractClass: BaseController<Entity, DTO>]
 *      Generisk REST-controller (Layer Supertype), som automatisk udstiller
 *      standardiserede `GET`, `GET /paged`, `GET /{id}`, `POST` (med HTTP 201 Created
 *      og `Location`-header), `PUT /{id}` og `DELETE /{id}` endpoints for enhver slice.
 *   2. [Class: GlobalExceptionHandler]
 *      Central `@RestControllerAdvice`, der oversætter både [Class: DomainException]
 *      og Jakarta Bean Validation (`MethodArgumentNotValidException`) til IETF
 *      RFC 7807 `ProblemDetail` JSON-svar med maskinlæsbar `errorCode`, `timestamp`
 *      og `fields`.
 * ============================================================================
 */
public final class BaseWebCore {

    private BaseWebCore() {}

    // =========================================================================
    // 1. BASE CONTROLLER (Standard REST CRUD Endpoints)
    // =========================================================================

    /**
     * [AbstractClass: BaseController<Entity, DTO>]
     * Abstrakt REST-controller der delegerer standard CRUD-kald direkte til
     * [AbstractClass: BaseService<Entity, DTO>]. Vertikale feature-controllere kan
     * nedarve herfra og nøjes med at tilføje domænespecifikke endpoints.
     *
     * @param <Entity> Intern JPA-entitetstype, der implementerer [Interface: Identifiable]
     * @param <DTO>    Ekstern datakontrakt, typisk en Java [Record/DTO], der implementerer [Interface: HasId]
     */
    public static abstract class BaseController<Entity extends Identifiable, DTO extends HasId> {

        protected final BaseService<Entity, DTO> service;

        protected BaseController(BaseService<Entity, DTO> service) {
            this.service = Objects.requireNonNull(service);
        }

        /** #getAll() — Henter alle ressourcer i slicen som en uforanderlig [Record/DTO]-liste. */
        @GetMapping
        public ResponseEntity<List<DTO>> getAll() {
            return ResponseEntity.ok(service.findAll());
        }

        /** #getAllPaged(pageable) — Henter en pagineret side af ressourcer (`?page=0&size=20&sort=...`). */
        @GetMapping("/paged")
        public ResponseEntity<Page<DTO>> getAllPaged(Pageable pageable) {
            return ResponseEntity.ok(service.findAllPaged(pageable));
        }

        /** #getById(id) — Henter én ressource ud fra `id` (eller returnerer RFC 7807 HTTP 404 via [Class: GlobalExceptionHandler]). */
        @GetMapping("/{id}")
        public ResponseEntity<DTO> getById(@PathVariable UUID id) {
            return ResponseEntity.ok(service.findById(id));
        }

        /** #create(dto) — Opretter en ny ressource og returnerer HTTP 201 Created med RFC-konform `Location`-header. */
        @PostMapping
        public ResponseEntity<DTO> create(@Valid @RequestBody DTO dto) {
            // Trin 1: Deleger oprettelse og domænevalidering til `service`
            DTO createdDto = service.create(dto);

            // Trin 2: Konstruer RFC-konform `Location`-URI for den nyoprettede ressource
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(createdDto.id())
                    .toUri();

            // Trin 3: Returner HTTP 201 Created med `Location`-header og `createdDto` som body
            return ResponseEntity.created(location).body(createdDto);
        }

        /** #update(id, dto) — Opdaterer en eksisterende ressource fuldstændigt ud fra `id`. */
        @PutMapping("/{id}")
        public ResponseEntity<DTO> update(@PathVariable UUID id, @Valid @RequestBody DTO dto) {
            return ResponseEntity.ok(service.update(id, dto));
        }

        /** #delete(id) — Sletter en ressource ud fra `id` og returnerer HTTP 204 No Content. */
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(@PathVariable UUID id) {
            service.deleteById(id);
            return ResponseEntity.noContent().build();
        }
    }

    // =========================================================================
    // 2. GLOBAL EXCEPTION HANDLER (RFC 7807 ProblemDetail)
    // =========================================================================

    /**
     * [Class: GlobalExceptionHandler]
     * Global fejlhåndtering der sikrer, at alle fejl fra backend præsenteres som
     * RFC 7807 `ProblemDetail` (`application/problem+json`).
     * Dette gør det muligt for frontendens [AbstractClass: BaseApiService] og
     * [Component: <ProblemAlert />] at vise præcise fejlbeskeder uden specialkode pr. feature.
     */
    @RestControllerAdvice
    public static class GlobalExceptionHandler {

        /** #handleDomainException(ex, request) — Oversætter [Class: DomainException] til RFC 7807 `ProblemDetail`. */
        @ExceptionHandler(DomainException.class)
        public ResponseEntity<ProblemDetail> handleDomainException(
                DomainException ex, HttpServletRequest request) {

            // Trin 1: Udled HTTP-statuskode og opret RFC 7807 `ProblemDetail` med fejlbesked
            HttpStatus status = HttpStatus.valueOf(ex.getStatus());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());

            // Trin 2: Berig med menneskelæsbar `title`, request-URI (`instance`), `errorCode` og `timestamp`
            problem.setTitle(ex.getTitle());
            problem.setInstance(URI.create(request.getRequestURI()));
            problem.setProperty("errorCode", ex.getErrorCode().name());
            problem.setProperty("timestamp", ex.getTimestamp());

            // Trin 3: Returner struktureret JSON-fejlsvar til klienten/BFF
            return ResponseEntity.status(status).body(problem);
        }

        /** #handleValidationException(ex, request) — Oversætter Jakarta `@Valid` fejl til RFC 7807 med `fields`-map. */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ProblemDetail> handleValidationException(
                MethodArgumentNotValidException ex, HttpServletRequest request) {

            // Trin 1: Saml alle valideringsfejl pr. feltnavn i `fieldErrors`
            Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                    .collect(Collectors.toMap(
                            FieldError::getField,
                            err -> Objects.requireNonNullElse(err.getDefaultMessage(), "Ugyldig værdi"),
                            (first, second) -> first
                    ));

            // Trin 2: Konstruer RFC 7807 `ProblemDetail` med `BAD_REQUEST` og vedhæft `fields`-mappet
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "En eller flere felter i forespørgslen er ugyldige.");
            problem.setTitle(ErrorCode.BAD_REQUEST.title());
            problem.setInstance(URI.create(request.getRequestURI()));
            problem.setProperty("errorCode", ErrorCode.BAD_REQUEST.name());
            problem.setProperty("timestamp", Instant.now());
            problem.setProperty("fields", fieldErrors);

            // Trin 3: Returner HTTP 400 Bad Request
            return ResponseEntity.badRequest().body(problem);
        }
    }
}
