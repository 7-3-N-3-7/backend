package com.platform.controller;

import com.platform.core.controller.BaseController;
import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.dto.PsychotraumatologyTestDto;
import com.platform.service.PsychotraumatologyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/psychotraumatology/tests")
public class PsychotraumatologyController extends BaseController<PsychotraumatologyTest, PsychotraumatologyTestDto, UUID> {

    private final PsychotraumatologyService psychotraumatologyService;

    public PsychotraumatologyController(PsychotraumatologyTestRepository testRepository) {
        this(new PsychotraumatologyService(testRepository));
    }

    @Autowired
    public PsychotraumatologyController(PsychotraumatologyService psychotraumatologyService) {
        super(psychotraumatologyService);
        this.psychotraumatologyService = psychotraumatologyService;
    }

    @Override
    @PostMapping
    public ResponseEntity<PsychotraumatologyTest> create(@RequestBody PsychotraumatologyTestDto dto) {
        return submitTest(dto);
    }

    public ResponseEntity<PsychotraumatologyTest> submitTest(@RequestBody PsychotraumatologyTestDto dto) {
        return ResponseEntity.ok(psychotraumatologyService.submitTest(dto));
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<PsychotraumatologyTest>> getTestsByClient(
            @PathVariable UUID clientUuid,
            Authentication authentication) {
        return ResponseEntity.ok(psychotraumatologyService.getTestsByClient(clientUuid, authentication));
    }
}
