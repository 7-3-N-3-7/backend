package com.platform.crm.repository;

import com.platform.crm.model.TherapistNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TherapistNoteRepository extends JpaRepository<TherapistNote, UUID> {
    List<TherapistNote> findByTherapistUuid(UUID therapistUuid);
}
