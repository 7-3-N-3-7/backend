package com.platform.core.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * [INTERFACE LAYER]
 * Generic contract for DTOs (or Entities acting as their own DTO) with a typed identifier.
 */
public interface BaseDTO<ID extends Serializable> extends Serializable {
    default ID getId() {
        return null;
    }

    default void setId(ID id) {
    }
}
