package com.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/user")
public class ProfileController {

    @GetMapping("/profile")
    public ResponseEntity<Map<String, String>> getUserProfile(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Map<String, String> profile = new HashMap<>();
        profile.put("userUuid", jwt.getSubject());
        putClaim(profile, "email", jwt.getClaimAsString("email"));
        putClaim(profile, "name", jwt.getClaimAsString("name"));
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object rawRoles = realmAccess == null ? null : realmAccess.get("roles");
        String primaryRole = rawRoles instanceof List<?> roles
                ? roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .findFirst()
                    .map(role -> role.replaceFirst("(?i)^ROLE_", "").toUpperCase(Locale.ROOT))
                    .orElse("")
                : "";
        profile.put("primaryRole", primaryRole);
        return ResponseEntity.ok(profile);
    }

    private void putClaim(Map<String, String> profile, String key, String value) {
        if (value != null) {
            profile.put(key, value);
        }
    }
}
