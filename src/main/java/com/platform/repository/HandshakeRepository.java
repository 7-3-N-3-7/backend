package com.platform.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.entity.ClientTherapistHandshake;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HandshakeRepository extends BaseRepository<ClientTherapistHandshake, UUID> {
    List<ClientTherapistHandshake> findByClientUuid(UUID clientUuid);
    List<ClientTherapistHandshake> findByTherapistUuid(UUID therapistUuid);
    Optional<ClientTherapistHandshake> findByClientUuidAndTherapistUuid(UUID clientUuid, UUID therapistUuid);
}
