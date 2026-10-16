package com.platform.crm.repository;

<<<<<<< HEAD
import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.PsychotraumatologyTest;
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.PsychotraumatologyTest;
=======
import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.data.jpa.repository.JpaRepository;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.data.jpa.repository.JpaRepository;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
<<<<<<< HEAD
public interface PsychotraumatologyTestRepository extends BaseRepository<PsychotraumatologyTest, UUID> {
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
public interface PsychotraumatologyTestRepository extends BaseRepository<PsychotraumatologyTest, UUID> {
=======
public interface PsychotraumatologyTestRepository extends JpaRepository<PsychotraumatologyTest, UUID> {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
public interface PsychotraumatologyTestRepository extends JpaRepository<PsychotraumatologyTest, UUID> {
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
    List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
}
