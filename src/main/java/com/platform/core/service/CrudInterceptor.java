package com.platform.core.service;

import com.platform.core.entity.BaseEntity;

import java.io.Serializable;

/**
 * [DOMAIN LAYER]
 * Lifecycle interceptor hook inspired by CrudInterceptor in crud-app-sample.
 * Automatically discovered and wired by Spring IoC into the matching BaseService<E, D, ID>.
 */
public interface CrudInterceptor<E extends BaseEntity<ID>, ID extends Serializable> {
    default void beforeCreate(E entity) {}
    default void afterCreate(E entity) {}
    default void beforeUpdate(E entity) {}
    default void afterUpdate(E entity) {}
    default void beforeDelete(ID id) {}
    default void afterDelete(ID id) {}
}
