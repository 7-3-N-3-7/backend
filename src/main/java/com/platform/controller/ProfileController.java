package com.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class ProfileController {

    @GetMapping("/profile")
    public ResponseEntity<Map<String, String>> getUserProfile(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("userUuid", jwt.getSubject());

        String email = jwt.getClaimAsString("email");
        profile.put("email", (email != null && !email.isBlank()) ? email : "jesper@example.com");

        String name = jwt.getClaimAsString("name");
        profile.put("name", (name != null && !name.isBlank()) ? name : "Jesper Kock");

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

