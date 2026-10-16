package com.platform.repository;

<<<<<<< HEAD
import com.platform.core.repository.BaseRepository;
import com.platform.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends BaseRepository<UserEntity, UUID> {
=======
import com.platform.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, java.util.UUID> {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    boolean existsByUsername(String username);
}
