package com.platform.crm.repository;

<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.PsychotraumatologyTest;
=======
import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.data.jpa.repository.JpaRepository;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.data.jpa.repository.JpaRepository;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
<<<<<<< HEAD
<<<<<<< HEAD
public interface PsychotraumatologyTestRepository extends BaseRepository<PsychotraumatologyTest, UUID> {
=======
public interface PsychotraumatologyTestRepository extends JpaRepository<PsychotraumatologyTest, UUID> {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
public interface PsychotraumatologyTestRepository extends JpaRepository<PsychotraumatologyTest, UUID> {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
}
