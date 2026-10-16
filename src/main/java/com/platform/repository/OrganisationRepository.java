package com.platform.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.entity.Organisation;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganisationRepository extends BaseRepository<Organisation, UUID> {
    Optional<Organisation> findByCvrNumber(String cvrNumber);
}
