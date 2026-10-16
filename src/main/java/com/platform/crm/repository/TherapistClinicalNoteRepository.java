package com.platform.crm.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.TherapistClinicalNote;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TherapistClinicalNoteRepository extends BaseRepository<TherapistClinicalNote, UUID> {
    List<TherapistClinicalNote> findByClientUuidOrderByCreatedAtDesc(UUID clientUuid);
}
