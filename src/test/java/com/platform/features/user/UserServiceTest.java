package com.platform.features.user;

import com.platform.config.SecurityConfig;
import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.user.UserFeature.ProfileController;
import com.platform.features.user.UserFeature.PublicHealthController;
import com.platform.features.user.UserFeature.SecureDataController;
import com.platform.features.user.UserFeature.UserController;
import com.platform.features.user.UserFeature.UserDto;
import com.platform.features.user.UserFeature.UserEntity;
import com.platform.features.user.UserFeature.UserRepository;
import com.platform.features.user.UserFeature.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;
    private UserController userController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
        userController = new UserController(userService);
    }

    @Test
    void createUser_ShouldReturnCreatedUser_WhenUsernameIsUniqueAndRoleIsValid() {
        UUID id = UUID.randomUUID();
        UserDto request = new UserDto("newclient", "securepassword", "CLIENT");

        when(userRepository.existsByUsername("newclient")).thenReturn(false);

        UserEntity savedEntity = new UserEntity(id, "newclient", "securepassword", "CLIENT");
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

        UserDto response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("newclient", response.getUsername());
        assertEquals("CLIENT", response.getRole());
        verify(userRepository).save(any(UserEntity.class));

        // Controller create endpoint
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/api/users")));
        try {
            ResponseEntity<UserDto> ctrlResp = userController.create(request);
            assertEquals(HttpStatus.CREATED, ctrlResp.getStatusCode());
            assertEquals("newclient", ctrlResp.getBody().getUsername());
            assertEquals("securepassword", ctrlResp.getBody().getPassword());
            assertEquals("CLIENT", ctrlResp.getBody().getRole());
            assertEquals(id, ctrlResp.getBody().getId());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void createUser_ShouldThrowDomainException_WhenUsernameAlreadyExists() {
        UserDto request = new UserDto("existinguser", "securepassword", "CLIENT");
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        DomainException exception = assertThrows(DomainException.class, () -> userService.createUser(request));
        assertEquals(400, exception.getStatus());
        assertEquals("username already exists", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void createUser_ShouldThrowDomainException_WhenRoleIsInvalidOrNull() {
        UserDto invalidRoleReq = new UserDto("user1", "secure", "INVALID_ROLE");
        DomainException exception = assertThrows(DomainException.class, () -> userService.createUser(invalidRoleReq));
        assertEquals(400, exception.getStatus());
        assertEquals("invalid role", exception.getMessage());

        UserDto nullRoleReq = new UserDto("user1", "secure", null);
        assertThrows(DomainException.class, () -> userService.createUser(nullRoleReq));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void testUserEntityAndControllersAndSecurityConfigBranches() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setUsername("u1");
        entity.setPassword("p1");
        entity.setRole("ADMIN");
        assertEquals(userId, entity.getId());
        assertEquals("u1", entity.getUsername());
        assertEquals("p1", entity.getPassword());
        assertEquals("ADMIN", entity.getRole());

        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(userRepository.findAll()).thenReturn(List.of(entity));
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(HttpStatus.OK, userController.getById(userId).getStatusCode());
        assertEquals(HttpStatus.OK, userController.getAll().getStatusCode());
        assertEquals(HttpStatus.OK, userController.update(userId, new UserDto(userId, "u2", "p2", "THERAPIST")).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, userController.delete(userId).getStatusCode());

        // ProfileController, PublicHealthController, SecureDataController
        ProfileController profileCtrl = new ProfileController();
        assertThrows(DomainException.class, () -> profileCtrl.getUserProfile(null));
        Authentication nonJwtAuth = mock(Authentication.class);
        when(nonJwtAuth.getPrincipal()).thenReturn("anonymous");
        assertThrows(DomainException.class, () -> profileCtrl.getUserProfile(nonJwtAuth));

        Jwt fullJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("email", "real@example.com")
                .claim("name", "Real Name")
                .claim("realm_access", Map.of("roles", List.of("ROLE_therapist")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication fullAuth = mock(Authentication.class);
        when(fullAuth.getPrincipal()).thenReturn(fullJwt);
        Map<String, String> prof1 = profileCtrl.getUserProfile(fullAuth).getBody();
        assertEquals("THERAPIST", prof1.get("primaryRole"));
        assertEquals("real@example.com", prof1.get("email"));
        assertEquals("Real Name", prof1.get("name"));

        Jwt plainRoleJwt = Jwt.withTokenValue("tok-plain")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("email", "   ")
                .claim("name", "   ")
                .claim("realm_access", Map.of("roles", List.of("therapist")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication plainRoleAuth = mock(Authentication.class);
        when(plainRoleAuth.getPrincipal()).thenReturn(plainRoleJwt);
        Map<String, String> prof2 = profileCtrl.getUserProfile(plainRoleAuth).getBody();
        assertEquals("THERAPIST", prof2.get("primaryRole"));
        assertEquals("jesper@example.com", prof2.get("email"));
        assertEquals("Jesper Kock", prof2.get("name"));

        Jwt emptyRolesJwt = Jwt.withTokenValue("tok-empty")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("realm_access", Map.of("roles", List.of()))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication emptyRolesAuth = mock(Authentication.class);
        when(emptyRolesAuth.getPrincipal()).thenReturn(emptyRolesJwt);
        assertEquals("CLIENT", profileCtrl.getUserProfile(emptyRolesAuth).getBody().get("primaryRole"));

        Jwt nonListRolesJwt = Jwt.withTokenValue("tok-nonlist")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("realm_access", Map.of("roles", "invalid"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication nonListRolesAuth = mock(Authentication.class);
        when(nonListRolesAuth.getPrincipal()).thenReturn(nonListRolesJwt);
        assertEquals("CLIENT", profileCtrl.getUserProfile(nonListRolesAuth).getBody().get("primaryRole"));

        Jwt nullRealmJwt = Jwt.withTokenValue("tok-nullrealm")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication nullRealmAuth = mock(Authentication.class);
        when(nullRealmAuth.getPrincipal()).thenReturn(nullRealmJwt);
        assertEquals("CLIENT", profileCtrl.getUserProfile(nullRealmAuth).getBody().get("primaryRole"));

        PublicHealthController healthCtrl = new PublicHealthController();
        assertEquals("UP", healthCtrl.healthCheck().getBody().get("status"));

        SecureDataController secureCtrl = new SecureDataController();
        assertNotNull(secureCtrl.getSecureData().getBody().get("data"));

        // SecurityConfig direct unit coverage
        SecurityConfig secCfg = new SecurityConfig();
        assertNotNull(secCfg.passwordEncoder());
        ResourceHandlerRegistry resRegistry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration resReg = mock(ResourceHandlerRegistration.class);
        when(resRegistry.addResourceHandler(anyString())).thenReturn(resReg);
        secCfg.addResourceHandlers(resRegistry);

        ViewControllerRegistry viewRegistry = mock(ViewControllerRegistry.class);
        secCfg.addViewControllers(viewRegistry);

        Assertions.assertTrue(secCfg.jwtAuthenticationConverter().convert(nullRealmJwt).getAuthorities().isEmpty());
        Assertions.assertTrue(secCfg.jwtAuthenticationConverter().convert(nonListRolesJwt).getAuthorities().isEmpty());
        Assertions.assertFalse(secCfg.jwtAuthenticationConverter().convert(fullJwt).getAuthorities().isEmpty());
        Assertions.assertFalse(secCfg.jwtAuthenticationConverter().convert(plainRoleJwt).getAuthorities().isEmpty());

        OncePerRequestFilter bffFilter = ReflectionTestUtils.invokeMethod(secCfg, "bffSessionHeaderFilter");
        Boolean shouldNotFilterErr = ReflectionTestUtils.invokeMethod(bffFilter, "shouldNotFilterErrorDispatch");
        Assertions.assertFalse(shouldNotFilterErr);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        HttpServletResponse mockResp = mock(HttpServletResponse.class);
        FilterChain mockChain = mock(FilterChain.class);

        // 1. Existing auth in context
        SecurityContextHolder.getContext().setAuthentication(fullAuth);
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        // 2. Null header
        SecurityContextHolder.clearContext();
        when(mockReq.getHeader("X-Auth-User-Uuid")).thenReturn(null);
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        // 3. Blank header
        when(mockReq.getHeader("X-Auth-User-Uuid")).thenReturn("   ");
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        // 4. Full headers
        SecurityContextHolder.clearContext();
        when(mockReq.getHeader("X-Auth-User-Uuid")).thenReturn(userId.toString());
        when(mockReq.getHeader("X-Auth-Username")).thenReturn("anders");
        when(mockReq.getHeader("X-Auth-Full-Name")).thenReturn("Anders Sand");
        when(mockReq.getHeader("X-Auth-Email")).thenReturn("anders@mail.com");
        when(mockReq.getHeader("X-Auth-Roles")).thenReturn("client, ,therapist");
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        // 5. Minimal headers (null username, fullName, email, roles)
        SecurityContextHolder.clearContext();
        reset(mockReq);
        when(mockReq.getHeader("X-Auth-User-Uuid")).thenReturn(userId.toString());
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        // 6. Username present, fullName null, blank roles
        SecurityContextHolder.clearContext();
        reset(mockReq);
        when(mockReq.getHeader("X-Auth-User-Uuid")).thenReturn(userId.toString());
        when(mockReq.getHeader("X-Auth-Username")).thenReturn("anders");
        when(mockReq.getHeader("X-Auth-Roles")).thenReturn("   ");
        bffFilter.doFilter(mockReq, mockResp, mockChain);
        SecurityContextHolder.clearContext();
    }
}
