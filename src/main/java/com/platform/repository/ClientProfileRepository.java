package com.platform.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.entity.ClientProfile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientProfileRepository extends BaseRepository<ClientProfile, UUID> {
    Optional<ClientProfile> findByUserId(UUID userId);
}
