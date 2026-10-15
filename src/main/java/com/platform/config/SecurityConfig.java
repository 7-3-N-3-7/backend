package com.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**", "/actuator/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/i18n/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> 
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            ))
            .addFilterAfter(bffSessionHeaderFilter(), BearerTokenAuthenticationFilter.class);
        
        return http.build();
    }

    private OncePerRequestFilter bffSessionHeaderFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilterErrorDispatch() {
                return false;
            }

            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    String userUuid = request.getHeader("X-Auth-User-Uuid");
                    if (userUuid != null && !userUuid.isBlank()) {
                        String username = request.getHeader("X-Auth-Username");
                        String fullName = request.getHeader("X-Auth-Full-Name");
                        String email = request.getHeader("X-Auth-Email");
                        String rolesHeader = request.getHeader("X-Auth-Roles");

                        List<String> roles = (rolesHeader != null && !rolesHeader.isBlank())
                                ? Arrays.stream(rolesHeader.split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .collect(Collectors.toList())
                                : List.of("client");

                        Map<String, Object> claims = new HashMap<>();
                        claims.put("sub", userUuid.trim());
                        claims.put("preferred_username", username != null ? username : "client");
                        claims.put("name", fullName != null ? fullName : (username != null ? username : "Client"));
                        claims.put("email", email != null ? email : "client@mail.com");
                        claims.put("realm_access", Map.of("roles", roles));

                        Jwt syntheticJwt = new Jwt(
                                "bff-session-token",
                                Instant.now().minusSeconds(5),
                                Instant.now().plusSeconds(3600),
                                Map.of("alg", "none"),
                                claims
                        );

                        AbstractAuthenticationToken authToken = jwtAuthenticationConverter().convert(syntheticJwt);
                        if (authToken != null) {
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtAuthenticationConverter() {
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter converter = 
            new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?>)) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(roleName -> roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

