package com.platform.crm.repository;

import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PsychotraumatologyTestRepository extends JpaRepository<PsychotraumatologyTest, UUID> {
    List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
}
