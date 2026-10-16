package com.platform.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.entity.ProviderProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderProfileRepository extends BaseRepository<ProviderProfile, UUID> {
    Optional<ProviderProfile> findByUserUuid(UUID userUuid);
}
