package com.platform.features.user;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: UserFeature (Vertical Feature Slice)
 * ============================================================================
 * @Spejler: frontend/src/features/user/UserFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Samler alle lag for brugerstyring, JWT-profiludtræk og sundhedstjek i én
 * selvbærende fil efter 5-trins Vertical Slice-strukturen:
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: UserDto]
 *      Java 17 `record` med `WRITE_ONLY` adgangskode, så `password` aldrig
 *      serialiseres tilbage i HTTP-svar.
 *   2. [Entity: UserEntity]
 *      JPA-entitet i tabellen `app_users`, der implementerer [Interface: Identifiable].
 *   3. [Repository: UserRepository]
 *      Package-private repository der arver fra [Repository: BaseRepository<UserEntity>].
 *   4. [Service: UserService]
 *      Package-private domæneservice der arver fra [AbstractClass: BaseService<UserEntity, UserDto>]
 *      og validerer unikke brugernavne samt tilladte roller via #validateBeforeCreate(userDto).
 *   5. [Controller: UserController], [Controller: ProfileController],
 *      [Controller: PublicHealthController] & [Controller: SecureDataController]
 *      Package-private REST-controllere under `/api/users` og `/api/v1/user/profile`.
 * ============================================================================
 */
public final class UserFeature {

    public static final String ALLOWED_USER_ROLES_REGEX = "^(CLIENT|ADMIN|THERAPIST)$";

    private UserFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    /**
     * [Record/DTO: UserDto]
     * Ekstern datakontrakt for brugeroprettelse og -visning.
     * Bemærk `@JsonProperty(access = WRITE_ONLY)` på `password`, som sikrer
     * Information Hiding ved udgående JSON-serialisering.
     */
    public record UserDto(
            UUID id,
            @NotBlank(message = "Username is required") String username,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotBlank(message = "Password is required") String password,
            @NotBlank(message = "Role is required")
            @Pattern(regexp = ALLOWED_USER_ROLES_REGEX, message = "invalid role") String role
    ) implements HasId {

        public UserDto(String username, String password, String role) {
            this(null, username, password, role);
        }

        public UserDto(UUID id, String username, String role) {
            this(id, username, null, role);
        }

        public UUID getId() { return id; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getRole() { return role; }
    }

    // =========================================================================
    // 2. JPA / MONGO ENTITIES (Implements Identifiable)
    // =========================================================================

    /**
     * [Entity: UserEntity]
     * Persistent brugerentitet i `app_users`-tabellen.
     */
    @Entity(name = "UserEntity")
    @Table(name = "app_users")
    public static class UserEntity implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(unique = true, nullable = false)
        private String username;

        @Column(nullable = false)
        private String password;

        @Column(nullable = false)
        private String role;

        public UserEntity() {}

        public UserEntity(UUID id, String username, String password, String role) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.role = role;
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    // =========================================================================
    // 3. REPOSITORIES & PORTS (Package-Private)
    // =========================================================================

    /**
     * [Repository: UserRepository]
     * Package-private Spring Data repository for [Entity: UserEntity].
     */
    @Repository
    interface UserRepository extends BaseRepository<UserEntity> {
        boolean existsByUsername(String username);
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    /**
     * [Service: UserService]
     * Domæneservice for brugeroprettelse og rollevalidering.
     */
    @Service
    static class UserService extends BaseService<UserEntity, UserDto> {

        private final UserRepository userRepository;

        UserService(UserRepository userRepository) {
            super(userRepository);
            this.userRepository = userRepository;
        }

        @Override
        protected UserDto toDto(UserEntity userEntity) {
            return new UserDto(
                    userEntity.getId(),
                    userEntity.getUsername(),
                    userEntity.getPassword(),
                    userEntity.getRole()
            );
        }

        @Override
        protected UserEntity toEntity(UserDto userDto) {
            return new UserEntity(
                    userDto.id(),
                    userDto.username(),
                    userDto.password(),
                    userDto.role()
            );
        }

        /**
         * #validateBeforeCreate(userDto) — Håndhæver gyldig rolle og unikt `username`.
         */
        @Override
        protected void validateBeforeCreate(UserDto userDto) {
            // Trin 1: Verificer at `role` matcher en af de tilladte domæneroller (CLIENT, ADMIN, THERAPIST)
            ErrorCode.BAD_REQUEST.require(
                    userDto.role() != null && userDto.role().matches(ALLOWED_USER_ROLES_REGEX),
                    "invalid role"
            );

            // Trin 2: Afvis oprettelse hvis `username` allerede er registreret i `app_users`
            ErrorCode.BAD_REQUEST.require(
                    !userRepository.existsByUsername(userDto.username()),
                    "username already exists"
            );
        }

        /**
         * #createUser(userDto) — Opretter bruger via #create(userDto) og renser `password` fra returværdien.
         */
        @Transactional
        public UserDto createUser(UserDto userDto) {
            UserDto createdUser = create(userDto);
            return new UserDto(createdUser.id(), createdUser.username(), createdUser.role());
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    /**
     * [Controller: UserController]
     * Standard REST CRUD-controller for `/api/users`.
     */
    @RestController
    @RequestMapping({"/api/users", "/api/v1/users"})
    static class UserController extends BaseController<UserEntity, UserDto> {

        UserController(UserService userService) {
            super(userService);
        }
    }

    /**
     * [Controller: ProfileController]
     * Udtrækker brugerprofil (`userUuid`, `email`, `name`, `primaryRole`) direkte fra Keycloak JWT-claims.
     */
    @RestController
    @RequestMapping("/api/v1/user")
    static class ProfileController {

        @GetMapping("/profile")
        public ResponseEntity<Map<String, String>> getUserProfile(Authentication authentication) {
            // Trin 1: Verificer at kaldet indeholder et gyldigt Keycloak [Class: Jwt] principal
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw ErrorCode.UNAUTHORIZED.toException("Unauthorized");
            }

            // Trin 2: Udtræk bruger-identitet og claims med sikre defaults til udviklingsmiljøet
            Map<String, String> profile = new LinkedHashMap<>();
            profile.put("userUuid", jwt.getSubject());

            String email = jwt.getClaimAsString("email");
            profile.put("email", (email != null && !email.isBlank()) ? email : "jesper@example.com");

            String name = jwt.getClaimAsString("name");
            profile.put("name", (name != null && !name.isBlank()) ? name : "Jesper Kock");

            // Trin 3: Normaliser brugerens primære RBAC-rolle fra `realm_access.roles`
            String primaryRole = extractPrimaryRole(jwt);
            profile.put("primaryRole", primaryRole);
            return ResponseEntity.ok(profile);
        }

        private String extractPrimaryRole(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles && !roles.isEmpty()) {
                String rawRole = String.valueOf(roles.get(0));
                if (rawRole.toUpperCase().startsWith("ROLE_")) {
                    rawRole = rawRole.substring(5);
                }
                return rawRole.toUpperCase();
            }
            return "CLIENT";
        }
    }

    /**
     * [Controller: PublicHealthController]
     * Offentligt sundhedsendpoint (`/api/v1/public/health`) til Docker/Traefik liveness-probes.
     */
    @RestController
    @RequestMapping("/api/v1/public")
    @CrossOrigin(origins = "*")
    static class PublicHealthController {

        @GetMapping("/health")
        public ResponseEntity<Map<String, String>> healthCheck() {
            return ResponseEntity.ok(Map.of("status", "UP", "service", "integrate-backend"));
        }
    }

    /**
     * [Controller: SecureDataController]
     * Beskyttet verifikationsendpoint (`/api/v1/secure-data`) til E2E-test af Zero-Trust JWT-adgang.
     */
    @RestController
    @RequestMapping("/api/v1/secure-data")
    static class SecureDataController {

        @GetMapping
        public ResponseEntity<Map<String, String>> getSecureData() {
            return ResponseEntity.ok(Map.of("data", "This is highly sensitive enterprise data!"));
        }
    }
}
