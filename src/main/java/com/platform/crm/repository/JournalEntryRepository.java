package com.platform.crm.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.JournalEntry;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryRepository extends BaseRepository<JournalEntry, java.util.UUID> {
    List<JournalEntry> findByClientUuidOrderByTimestampDesc(java.util.UUID clientUuid);
}
