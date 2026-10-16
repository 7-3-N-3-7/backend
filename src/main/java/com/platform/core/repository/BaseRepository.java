package com.platform.core.repository;

import com.platform.core.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * [DATA LAYER]
 * Generic BaseRepository<E, ID> extending Spring Data's JpaRepository and JpaSpecificationExecutor
 * so Spring IoC automatically resolves and injects the matching Hibernate repository into BaseService<E, D, ID>.
 */
@NoRepositoryBean
public interface BaseRepository<E extends BaseEntity<ID>, ID extends Serializable>
        extends JpaRepository<E, ID>, JpaSpecificationExecutor<E> {
}
