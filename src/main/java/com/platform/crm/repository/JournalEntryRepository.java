package com.platform.crm.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.JournalEntry;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
<<<<<<< HEAD
<<<<<<< HEAD
public interface JournalEntryRepository extends BaseRepository<JournalEntry, java.util.UUID> {
=======
public interface JournalEntryRepository extends JpaRepository<JournalEntry, java.util.UUID> {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
public interface JournalEntryRepository extends JpaRepository<JournalEntry, java.util.UUID> {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    List<JournalEntry> findByClientUuidOrderByTimestampDesc(java.util.UUID clientUuid);
}
