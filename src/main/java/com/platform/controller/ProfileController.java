package com.platform.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

<<<<<<< HEAD
<<<<<<< HEAD
import java.util.LinkedHashMap;
=======
import java.util.HashMap;
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
import java.util.HashMap;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
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
<<<<<<< HEAD
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("userUuid", jwt.getSubject());
<<<<<<< HEAD

        String email = jwt.getClaimAsString("email");
        profile.put("email", (email != null && !email.isBlank()) ? email : "jesper@example.com");

        String name = jwt.getClaimAsString("name");
        profile.put("name", (name != null && !name.isBlank()) ? name : "Jesper Kock");

        String primaryRole = extractPrimaryRole(jwt);
=======
=======
        Map<String, String> profile = new HashMap<>();
        profile.put("userUuid", jwt.getSubject());
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
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
<<<<<<< HEAD
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
        profile.put("primaryRole", primaryRole);
        return ResponseEntity.ok(profile);
    }

<<<<<<< HEAD
<<<<<<< HEAD
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
=======
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    private void putClaim(Map<String, String> profile, String key, String value) {
        if (value != null) {
            profile.put(key, value);
        }
<<<<<<< HEAD
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
    }
}

=======
    }
}
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
