package com.platform.crm.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PsychotraumatologyTestRepository extends BaseRepository<PsychotraumatologyTest, UUID> {
    List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
}
