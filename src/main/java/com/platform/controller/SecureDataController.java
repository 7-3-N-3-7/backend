package com.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/secure-data")
public class SecureDataController {

    @GetMapping
    public ResponseEntity<Map<String, String>> getSecureData() {
        return ResponseEntity.ok(Map.of("data", "This is highly sensitive enterprise data!"));
    }
}
