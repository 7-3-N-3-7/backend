package com.platform.features.psychotraumatology;

import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyController;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyService;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTestDto;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTestRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

public class PsychotraumatologyServiceTest {

    @Test
    public void testPsychotraumatologyServiceAndControllerAndEntity() {
        PsychotraumatologyTestRepository pclRepo = Mockito.mock(PsychotraumatologyTestRepository.class);
        Mockito.when(pclRepo.save(any(PsychotraumatologyTest.class))).thenAnswer(i -> {
            PsychotraumatologyTest t = i.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        PsychotraumatologyService pclService = new PsychotraumatologyService(pclRepo);
        PsychotraumatologyController pclController = new PsychotraumatologyController(pclService);

        UUID id = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // Entity coverage
        PsychotraumatologyTest pcl = new PsychotraumatologyTest();
        pcl.setId(id);
        pcl.setClientUuid(clientUuid);
        pcl.setTherapistUuid(therapistUuid);
        pcl.setTestType("PCL-5");
        pcl.setScore(40);
        pcl.setSeverity("High");
        pcl.setAnswersJson("{}");
        ReflectionTestUtils.invokeMethod(pcl, "onCreate");
        Assertions.assertNotNull(pcl.getTimestamp());
        ReflectionTestUtils.invokeMethod(pcl, "onCreate");
        pcl.setTimestamp(now);
        Assertions.assertEquals(now, pcl.getTimestamp());
        Assertions.assertEquals(40, pcl.getScore());
        Assertions.assertEquals("High", pcl.getSeverity());
        Assertions.assertEquals("{}", pcl.getAnswersJson());
        Assertions.assertEquals("PCL-5", pcl.getTestType());
        Assertions.assertEquals(id, pcl.getId());
        Assertions.assertEquals(clientUuid, pcl.getClientUuid());
        Assertions.assertEquals(therapistUuid, pcl.getTherapistUuid());

        // DTO & mapping coverage
        Assertions.assertNull(pclService.toDto(null));
        Assertions.assertNull(pclService.toEntity(null));
        PsychotraumatologyTestDto mappedDto = pclService.toDto(pcl);
        Assertions.assertEquals(id, mappedDto.getId());
        Assertions.assertEquals(clientUuid, mappedDto.getClientUuid());
        Assertions.assertEquals(therapistUuid, mappedDto.getTherapistUuid());
        Assertions.assertEquals("PCL-5", mappedDto.getTestType());
        Assertions.assertEquals(40, mappedDto.getScore());
        Assertions.assertEquals("High", mappedDto.getSeverity());
        Assertions.assertEquals("{}", mappedDto.getAnswersJson());
        Assertions.assertEquals(now, mappedDto.getTimestamp());

        PsychotraumatologyTestDto dto = new PsychotraumatologyTestDto(
                id, clientUuid, therapistUuid, "PCL-5", 35, "Moderate to High", "{\"q1\":2}"
        );
        ResponseEntity<PsychotraumatologyTestDto> savedTest = pclController.create(dto);
        Assertions.assertEquals(HttpStatus.OK, savedTest.getStatusCode());
        Assertions.assertEquals(35, savedTest.getBody().score());

        // Self-assessment submission without therapistUuid falls back to clientUuid / defaults
        PsychotraumatologyTestDto selfAssessmentDto = new PsychotraumatologyTestDto(
                null, clientUuid, null, null, null, null, null
        );
        ResponseEntity<PsychotraumatologyTestDto> selfSaved = pclController.create(selfAssessmentDto);
        Assertions.assertEquals(clientUuid, selfSaved.getBody().therapistUuid());
        Assertions.assertEquals("PCL-5", selfSaved.getBody().testType());

        PsychotraumatologyTestDto emptyDto = new PsychotraumatologyTestDto(
                null, null, null, null, null, null, null
        );
        ResponseEntity<PsychotraumatologyTestDto> emptySaved = pclController.create(emptyDto);
        Assertions.assertNotNull(emptySaved.getBody().therapistUuid());

        // Null auth & non-Jwt principal throw DomainException (401)
        Assertions.assertThrows(DomainException.class, () -> pclController.getTestsByClient(clientUuid, null));
        Authentication nonJwtAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonJwtAuth.getPrincipal()).thenReturn("anonymous");
        Assertions.assertThrows(DomainException.class, () -> pclController.getTestsByClient(clientUuid, nonJwtAuth));

        // Caller != clientUuid throws DomainException (403)
        Jwt otherJwt = Jwt.withTokenValue("other")
                .header("alg", "none")
                .claim("sub", UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication otherAuth = Mockito.mock(Authentication.class);
        Mockito.when(otherAuth.getPrincipal()).thenReturn(otherJwt);
        Assertions.assertThrows(DomainException.class, () -> pclController.getTestsByClient(clientUuid, otherAuth));

        // Matching callerUuid returns 200 OK
        Jwt ownerJwt = Jwt.withTokenValue("owner")
                .header("alg", "none")
                .claim("sub", clientUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication ownerAuth = Mockito.mock(Authentication.class);
        Mockito.when(ownerAuth.getPrincipal()).thenReturn(ownerJwt);
        Mockito.when(pclRepo.findByClientUuid(clientUuid)).thenReturn(List.of(pcl));
        Assertions.assertEquals(1, pclController.getTestsByClient(clientUuid, ownerAuth).getBody().size());
    }
}
