package com.platform.repository;

import com.platform.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByClientUuid(UUID clientUuid);
    List<Appointment> findByTherapistUuid(UUID therapistUuid);
    boolean existsByClientUuidAndTherapistUuid(UUID clientUuid, UUID therapistUuid);
}
