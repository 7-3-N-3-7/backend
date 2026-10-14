package com.platform.repository;

import com.platform.entity.ClientTherapistHandshake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HandshakeRepository extends JpaRepository<ClientTherapistHandshake, UUID> {
    List<ClientTherapistHandshake> findByClientUuid(UUID clientUuid);
    List<ClientTherapistHandshake> findByTherapistUuid(UUID therapistUuid);
    Optional<ClientTherapistHandshake> findByClientUuidAndTherapistUuid(UUID clientUuid, UUID therapistUuid);
}
