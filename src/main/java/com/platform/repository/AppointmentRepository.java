package com.platform.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.entity.Appointment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends BaseRepository<Appointment, UUID> {
    List<Appointment> findByClientUuid(UUID clientUuid);
    List<Appointment> findByTherapistUuid(UUID therapistUuid);
}
