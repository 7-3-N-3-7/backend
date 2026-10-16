package com.platform.core.dao;

import com.platform.core.entity.BaseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * [DATA LAYER]
 * Generic BaseDAO<T, ID> inspired by CrudRepository in crud-app-sample.
 * Provides EntityManager-backed CRUD and dynamic Criteria API filtering (_like, _gt, _lt, _gte, _lte)
 * and sorting, which subclasses can inherit and extend with custom queries.
 */
@Transactional
public abstract class BaseDAO<T extends BaseEntity<ID>, ID extends Serializable> {

    @PersistenceContext
    protected EntityManager entityManager;

    protected Class<T> entityClass;

    @SuppressWarnings("unchecked")
    protected BaseDAO() {
        Class<?>[] types = GenericTypeResolver.resolveTypeArguments(getClass(), BaseDAO.class);
        if (types != null && types.length > 0) {
            this.entityClass = (Class<T>) types[0];
        }
    }

    public BaseDAO(Class<T> entityClass, EntityManager entityManager) {
        this.entityClass = entityClass;
        this.entityManager = entityManager;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<T> findAll() {
        return entityManager.createQuery("SELECT e FROM " + entityClass.getSimpleName() + " e", entityClass)
                .getResultList();
    }

    public Optional<T> findById(ID id) {
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    public T save(T entity) {
        if (entity.getId() == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    public void deleteById(ID id) {
        findById(id).ifPresent(entity -> entityManager.remove(entity));
    }

    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    public long count() {
        return entityManager.createQuery("SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e", Long.class)
                .getSingleResult();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<T> findAllDynamic(int offset, int limit, Map<String, String> filters, String sortParam) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(entityClass);
        Root<T> root = cq.from(entityClass);

        List<Predicate> predicates = new ArrayList<>();
        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null || value.isBlank() || key.equals("page") || key.equals("size") || key.equals("sort")) {
                    continue;
                }
                if (key.endsWith("_like")) {
                    String field = key.substring(0, key.length() - 5);
                    predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
                } else if (key.endsWith("_gt")) {
                    String field = key.substring(0, key.length() - 3);
                    predicates.add(cb.greaterThan(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                } else if (key.endsWith("_lt")) {
                    String field = key.substring(0, key.length() - 3);
                    predicates.add(cb.lessThan(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                } else if (key.endsWith("_gte")) {
                    String field = key.substring(0, key.length() - 4);
                    predicates.add(cb.greaterThanOrEqualTo(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                } else if (key.endsWith("_lte")) {
                    String field = key.substring(0, key.length() - 4);
                    predicates.add(cb.lessThanOrEqualTo(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                } else {
                    predicates.add(cb.equal(root.get(key), convertValue(value, root.get(key).getJavaType())));
                }
            }
        }

        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",");
            String sortField = parts[0].trim();
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
            cq.orderBy(desc ? cb.desc(root.get(sortField)) : cb.asc(root.get(sortField)));
        }

        TypedQuery<T> query = entityManager.createQuery(cq);
        query.setFirstResult(Math.max(0, offset));
        if (limit > 0) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    protected Object convertValue(String val, Class<?> targetType) {
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
