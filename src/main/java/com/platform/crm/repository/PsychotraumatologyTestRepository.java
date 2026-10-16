package com.platform.crm.repository;

<<<<<<< HEAD
import com.platform.core.repository.BaseRepository;
import com.platform.crm.model.PsychotraumatologyTest;
=======
import com.platform.crm.model.PsychotraumatologyTest;
import org.springframework.data.jpa.repository.JpaRepository;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
<<<<<<< HEAD
public interface PsychotraumatologyTestRepository extends BaseRepository<PsychotraumatologyTest, UUID> {
=======
public interface PsychotraumatologyTestRepository extends JpaRepository<PsychotraumatologyTest, UUID> {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
}
