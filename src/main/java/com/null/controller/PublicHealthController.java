package com.integrate.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@CrossOrigin(origins = "*")
public class PublicHealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "integrate-backend"));
    }
}
