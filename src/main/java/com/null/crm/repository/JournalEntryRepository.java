package com.integrate.crm.repository;

import com.integrate.crm.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByClientIdOrderByTimestampDesc(Long clientId);
}
