package com.platform.crm.repository;

import com.platform.crm.model.TherapistClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TherapistClinicalNoteRepository extends JpaRepository<TherapistClinicalNote, UUID> {
    List<TherapistClinicalNote> findByClientUuidOrderByCreatedAtDesc(UUID clientUuid);
}
