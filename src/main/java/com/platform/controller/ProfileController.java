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
import java.util.Map;

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
        profile.put("email", "jesper@example.com");
        profile.put("name", "Jesper Kock");
        return ResponseEntity.ok(profile);
    }
}
