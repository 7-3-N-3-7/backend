package com.platform.core.entity;

import com.platform.core.dto.BaseDTO;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.util.UUID;

/**
 * [DATA LAYER]
 * Generic BaseEntity<ID> inspired by crud-app-sample.
 * Also implements BaseDTO<ID> so entities can seamlessly act as their own DTO in BaseService<E, E, ID>
 * when a separate DTO class is not needed.
 */
@MappedSuperclass
public abstract class BaseEntity<ID extends Serializable> implements BaseDTO<ID>, Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected ID id;

    @Override
    public ID getId() {
        return id;
    }

    @Override
    public void setId(ID id) {
        this.id = id;
    }

    /**
     * Convenience specialization for standard UUID-backed JPA entities.
     */
    @MappedSuperclass
    public static abstract class UuidEntity extends BaseEntity<UUID> {
        @Override
        public UUID getId() {
            return super.getId();
        }

        @Override
        public void setId(UUID id) {
            super.setId(id);
        }
    }
}
