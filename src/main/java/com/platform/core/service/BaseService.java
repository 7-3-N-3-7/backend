package com.platform.core.service;

import com.platform.core.dto.BaseDTO;
import com.platform.core.entity.BaseEntity;
import com.platform.core.repository.BaseRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * [LOGIC LAYER]
 * Generic BaseService<E, D, ID> automatically wired by Spring's IoC Dependency Injection container
 * to the matching BaseRepository<E, ID> and List<CrudInterceptor<E, ID>>.
 * Provides automatic Entity <-> DTO conversion via GenericTypeResolver + BeanUtils,
 * dynamic JPA Specification filtering (_like, _gt, _lt, _gte, _lte), sorting, and lifecycle hooks.
 */
@Transactional
public class BaseService<E extends BaseEntity<ID>, D extends BaseDTO<ID>, ID extends Serializable> {

    @Autowired(required = false)
    protected BaseRepository<E, ID> repository;

    protected final List<CrudInterceptor<E, ID>> interceptors = new ArrayList<>();

    protected Class<E> entityClass;
    protected Class<D> dtoClass;

    @SuppressWarnings("unchecked")
    public BaseService() {
        Class<?>[] resolved = GenericTypeResolver.resolveTypeArguments(getClass(), BaseService.class);
        if (resolved != null && resolved.length >= 2) {
            this.entityClass = (Class<E>) resolved[0];
            this.dtoClass = (Class<D>) resolved[1];
        }
    }

    public BaseService(BaseRepository<E, ID> repository) {
        this();
        this.repository = repository;
    }

    public BaseRepository<E, ID> getRepository() {
        return repository;
    }

    public void setRepository(BaseRepository<E, ID> repository) {
        this.repository = repository;
    }

    public Class<E> getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(Class<E> entityClass) {
        this.entityClass = entityClass;
    }

    public Class<D> getDtoClass() {
        return dtoClass;
    }

    public void setDtoClass(Class<D> dtoClass) {
        this.dtoClass = dtoClass;
    }

    @Autowired(required = false)
    public void setInterceptors(List<CrudInterceptor<E, ID>> discoveredInterceptors) {
        if (discoveredInterceptors != null) {
            for (CrudInterceptor<E, ID> interceptor : discoveredInterceptors) {
                addInterceptor(interceptor);
            }
        }
    }

    public void addInterceptor(CrudInterceptor<E, ID> interceptor) {
        if (interceptor != null && !this.interceptors.contains(interceptor)) {
            this.interceptors.add(interceptor);
        }
    }

    public List<CrudInterceptor<E, ID>> getInterceptors() {
        return interceptors;
    }

    @SuppressWarnings("unchecked")
    public E toEntity(D dto) {
        if (dto == null) {
            return null;
        }
        if (entityClass != null && entityClass.isInstance(dto)) {
            return (E) dto;
        }
        if (entityClass == null) {
            throw new IllegalStateException("Entity class could not be resolved for " + getClass().getName());
        }
        E entity = BeanUtils.instantiateClass(entityClass);
        BeanUtils.copyProperties(dto, entity);
        entity.setId(dto.getId());
        return entity;
    }

    @SuppressWarnings("unchecked")
    public D toDto(E entity) {
        if (entity == null) {
            return null;
        }
        if (dtoClass != null && dtoClass.isInstance(entity)) {
            return (D) entity;
        }
        if (dtoClass == null) {
            throw new IllegalStateException("DTO class could not be resolved for " + getClass().getName());
        }
        D dto = BeanUtils.instantiateClass(dtoClass);
        BeanUtils.copyProperties(entity, dto);
        dto.setId(entity.getId());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<E> findAll() {
        return getRepository().findAll();
    }

    @Transactional(readOnly = true)
    public List<D> findAllDtos() {
        return findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<E> findAllDynamic(int offset, int limit, Map<String, String> filters, String sortParam) {
        Specification<E> spec = buildSpecification(filters);
        Sort sort = buildSort(sortParam);
        if (limit > 0) {
            int page = Math.max(0, offset) / limit;
            return getRepository().findAll(spec, PageRequest.of(page, limit, sort)).getContent();
        }
        return getRepository().findAll(spec, sort);
    }

    @Transactional(readOnly = true)
    public Optional<E> findById(ID id) {
        return getRepository().findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<D> findDtoById(ID id) {
        return findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public E getByIdOrThrow(ID id) {
        return findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found with id: " + id));
    }

    public E create(E entity) {
        for (CrudInterceptor<E, ID> interceptor : interceptors) {
            interceptor.beforeCreate(entity);
        }
        E saved = getRepository().save(entity);
        for (CrudInterceptor<E, ID> interceptor : interceptors) {
            interceptor.afterCreate(saved);
        }
        return saved;
    }

    public D createFromDto(D dto) {
        E created = create(toEntity(dto));
        return toDto(created);
    }

    public E save(E entity) {
        return getRepository().save(entity);
    }

    public E update(ID id, E entity) {
        if (!getRepository().existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found with id: " + id);
        }
        entity.setId(id);
        for (CrudInterceptor<E, ID> interceptor : interceptors) {
            interceptor.beforeUpdate(entity);
        }
        E saved = getRepository().save(entity);
        for (CrudInterceptor<E, ID> interceptor : interceptors) {
            interceptor.afterUpdate(saved);
        }
        return saved;
    }

    public D updateFromDto(ID id, D dto) {
        E updated = update(id, toEntity(dto));
        return toDto(updated);
    }

    public void deleteById(ID id) {
        for (CrudInterceptor<E, ID> interceptor : interceptors) {
            interceptor.beforeDelete(id);
        }
        getRepository().deleteById(id);
        for (CrudInterceptor<E, ID> interceptor : interceptors) {
            interceptor.afterDelete(id);
        }
    }

    @Transactional(readOnly = true)
    public boolean existsById(ID id) {
        return getRepository().existsById(id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return getRepository().count();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Specification<E> buildSpecification(Map<String, String> filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filters != null) {
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value == null || value.isBlank()
                            || key.equals("page") || key.equals("size")
                            || key.equals("offset") || key.equals("limit")
                            || key.equals("sort")) {
                        continue;
                    }
                    if (key.endsWith("_like")) {
                        String field = key.substring(0, key.length() - 5);
                        predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
                    } else if (key.endsWith("_gt")) {
                        String field = key.substring(0, key.length() - 3);
                        predicates.add(cb.greaterThan(root.get(field), (Comparable) convertFilterValue(value, root.get(field).getJavaType())));
                    } else if (key.endsWith("_lt")) {
                        String field = key.substring(0, key.length() - 3);
                        predicates.add(cb.lessThan(root.get(field), (Comparable) convertFilterValue(value, root.get(field).getJavaType())));
                    } else if (key.endsWith("_gte")) {
                        String field = key.substring(0, key.length() - 4);
                        predicates.add(cb.greaterThanOrEqualTo(root.get(field), (Comparable) convertFilterValue(value, root.get(field).getJavaType())));
                    } else if (key.endsWith("_lte")) {
                        String field = key.substring(0, key.length() - 4);
                        predicates.add(cb.lessThanOrEqualTo(root.get(field), (Comparable) convertFilterValue(value, root.get(field).getJavaType())));
                    } else {
                        predicates.add(cb.equal(root.get(key), convertFilterValue(value, root.get(key).getJavaType())));
                    }
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Sort buildSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sortParam.split(",");
        String sortField = parts[0].trim();
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return desc ? Sort.by(Sort.Direction.DESC, sortField) : Sort.by(Sort.Direction.ASC, sortField);
    }

    public Object convertFilterValue(String val, Class<?> targetType) {
        if (targetType.equals(UUID.class)) {
            return UUID.fromString(val);
        }
        if (targetType.equals(Long.class) || targetType.equals(long.class)) {
            return Long.parseLong(val);
        }
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
            return Integer.parseInt(val);
        }
        if (targetType.equals(Double.class) || targetType.equals(double.class)) {
            return Double.parseDouble(val);
        }
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
            return Boolean.parseBoolean(val);
        }
        return val;
    }
}
