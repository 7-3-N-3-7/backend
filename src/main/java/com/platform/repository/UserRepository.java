package com.platform.repository;

import com.platform.core.repository.BaseRepository;
import com.platform.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends BaseRepository<UserEntity, UUID> {
    boolean existsByUsername(String username);
}
