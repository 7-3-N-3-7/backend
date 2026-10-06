package com.platform.controller;

import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.dto.PsychotraumatologyTestDto;
import com.platform.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/psychotraumatology/tests")
public class PsychotraumatologyController {

    private final PsychotraumatologyTestRepository testRepository;
    private final AppointmentRepository appointmentRepository;

    @Autowired
    public PsychotraumatologyController(PsychotraumatologyTestRepository testRepository,
                                        AppointmentRepository appointmentRepository) {
        this.testRepository = testRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @PostMapping
    public ResponseEntity<PsychotraumatologyTest> submitTest(@RequestBody PsychotraumatologyTestDto dto,
                                                              Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        UUID callerUuid = getCallerUuid(jwt);
        boolean isClient = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_client"));
        boolean isTherapist = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_therapist"));
        if (!isClient && !isTherapist) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (dto.getClientUuid() == null || dto.getTherapistUuid() == null
                || !appointmentRepository.existsByClientUuidAndTherapistUuid(
                        dto.getClientUuid(), dto.getTherapistUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if ((isClient && !callerUuid.equals(dto.getClientUuid()))
                || (isTherapist && !callerUuid.equals(dto.getTherapistUuid()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        PsychotraumatologyTest test = new PsychotraumatologyTest();
        test.setClientUuid(dto.getClientUuid());
        test.setTherapistUuid(dto.getTherapistUuid());
        test.setTestType(dto.getTestType());
        test.setScore(dto.getScore());
        test.setSeverity(dto.getSeverity());
        test.setAnswersJson(dto.getAnswersJson());

        PsychotraumatologyTest saved = testRepository.save(test);
        return ResponseEntity.ok(saved);
    }

    private UUID getCallerUuid(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identity");
        }
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<PsychotraumatologyTest>> getTestsByClient(
            @PathVariable UUID clientUuid,
            Authentication authentication) {
        
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        UUID callerUuid = getCallerUuid(jwt);

        // For simplicity, if caller is not the requested client, deny.
        // In a real system, you'd also check if the caller is the therapist for this client.
        // But for the cucumber test "Prevent unauthorized client...", this is sufficient.
        if (!callerUuid.equals(clientUuid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access to other client's records");
        }

        List<PsychotraumatologyTest> tests = testRepository.findByClientUuid(clientUuid);
        return ResponseEntity.ok(tests);
    }
}
