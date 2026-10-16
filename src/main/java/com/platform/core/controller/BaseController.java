package com.platform.core.controller;

import com.platform.core.dto.BaseDTO;
import com.platform.core.entity.BaseEntity;
import com.platform.core.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.Serializable;
import java.util.List;

/**
 * [INTERFACE LAYER]
 * Generic BaseController<E, D, ID> inspired by crud-app-sample.
 * Spring's IoC Dependency Injection container automatically resolves and wires the matching
 * BaseService<E, D, ID> -> BaseRepository<E, ID> -> Hibernate JPA Entity E.
 */
public abstract class BaseController<E extends BaseEntity<ID>, D extends BaseDTO<ID>, ID extends Serializable> {

    @Autowired(required = false)
    protected BaseService<E, D, ID> service;

    protected BaseController() {
    }

    protected BaseController(BaseService<E, D, ID> service) {
        this.service = service;
    }

    public BaseService<E, D, ID> getService() {
        return service;
    }

    public void setService(BaseService<E, D, ID> service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<D>> getAll() {
        return ResponseEntity.ok(getService().findAllDtos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<D> getById(@PathVariable ID id) {
        return getService().findDtoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody D dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(getService().createFromDto(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable ID id, @RequestBody D dto) {
        return ResponseEntity.ok(getService().updateFromDto(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable ID id) {
        getService().deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
