package com.platform.service;

import com.platform.core.service.BaseService;
import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.dto.PsychotraumatologyTestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PsychotraumatologyService extends BaseService<PsychotraumatologyTest, PsychotraumatologyTestDto, UUID> {

    private final PsychotraumatologyTestRepository testRepository;

    @Autowired
    public PsychotraumatologyService(PsychotraumatologyTestRepository testRepository) {
        super(testRepository);
        this.testRepository = testRepository;
    }

    @Override
    public PsychotraumatologyTest toEntity(PsychotraumatologyTestDto dto) {
        if (dto == null) {
            return null;
        }
        PsychotraumatologyTest test = new PsychotraumatologyTest();
        test.setId(dto.getId());
        test.setClientUuid(dto.getClientUuid());
        test.setTherapistUuid(
                dto.getTherapistUuid() != null
                        ? dto.getTherapistUuid()
                        : (dto.getClientUuid() != null
                                ? dto.getClientUuid()
                                : UUID.fromString("00000000-0000-0000-0000-000000000000"))
        );
        test.setTestType(dto.getTestType() != null ? dto.getTestType() : "PCL-5");
        test.setScore(dto.getScore() != null ? dto.getScore() : 0);
        test.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : "Unknown");
        test.setAnswersJson(dto.getAnswersJson() != null ? dto.getAnswersJson() : "[]");
        return test;
    }

    public PsychotraumatologyTest submitTest(PsychotraumatologyTestDto dto) {
        PsychotraumatologyTest test = toEntity(dto);
        return create(test);
    }

    public List<PsychotraumatologyTest> getTestsByClient(UUID clientUuid, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        UUID callerUuid = UUID.fromString(jwt.getSubject());
        if (!callerUuid.equals(clientUuid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access to other client's records");
        }

        return testRepository.findByClientUuid(clientUuid);
    }
}
