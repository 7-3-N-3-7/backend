package com.platform.core;

import com.platform.controller.AppointmentController;
import com.platform.controller.ClinicPlatformController;
import com.platform.controller.JournalController;
import com.platform.controller.PsychotraumatologyController;
import com.platform.controller.UserController;
import com.platform.core.controller.BaseController;
import com.platform.core.dao.BaseDAO;
import com.platform.core.dto.BaseDTO;
import com.platform.core.entity.BaseEntity;
import com.platform.core.repository.BaseRepository;
import com.platform.core.service.BaseService;
import com.platform.core.service.CrudInterceptor;
import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.model.TherapistClinicalNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.crm.repository.TherapistClinicalNoteRepository;
import com.platform.dto.JournalEntryDto;
import com.platform.dto.PsychotraumatologyTestDto;
import com.platform.dto.UserRequestDto;
import com.platform.dto.UserResponseDto;
import com.platform.entity.Appointment;
import com.platform.entity.ClientProfile;
import com.platform.entity.ClientTherapistHandshake;
import com.platform.entity.Organisation;
import com.platform.entity.ProviderProfile;
import com.platform.entity.Service;
import com.platform.entity.UserEntity;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.ClientProfileRepository;
import com.platform.repository.HandshakeRepository;
import com.platform.repository.OrganisationRepository;
import com.platform.repository.ProviderProfileRepository;
import com.platform.repository.ServiceRepository;
import com.platform.repository.UserRepository;
import com.platform.service.AppointmentService;
import com.platform.service.ClinicPlatformService;
import com.platform.service.JournalService;
import com.platform.service.PsychotraumatologyService;
import com.platform.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GenericBaseArchitectureTest {

    public static class DummyEntity extends BaseEntity<UUID> {
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public static class DummyDTO implements BaseDTO<UUID> {
        private UUID id;
        private String title;

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public static class DefaultIdDTO implements BaseDTO<UUID> {
    }

    public static class NoArgBlockedEntity extends BaseEntity<UUID> {
        public NoArgBlockedEntity(String requiredArg) {
        }
    }

    public static class NoArgBlockedDTO implements BaseDTO<UUID> {
        public NoArgBlockedDTO(String requiredArg) {
        }
    }

    static class DummyServiceWithSeparateDto extends BaseService<DummyEntity, DummyDTO, UUID> {
        public DummyServiceWithSeparateDto() {
            super();
        }

        public DummyServiceWithSeparateDto(BaseRepository<DummyEntity, UUID> repo) {
            super(repo);
        }
    }

    static class BlockedTypeService extends BaseService<NoArgBlockedEntity, NoArgBlockedDTO, UUID> {
    }

    static class DummyDAO extends BaseDAO<DummyEntity, UUID> {
        public DummyDAO() {
            super();
        }

        public DummyDAO(EntityManager em) {
            super(DummyEntity.class, em);
        }

        public Object exposeConvert(String val, Class<?> type) {
            return convertValue(val, type);
        }
    }

    @SuppressWarnings("rawtypes")
    static class RawDAO extends BaseDAO {
        public RawDAO() {
            super();
        }
    }

    static class DummyController extends BaseController<DummyEntity, DummyEntity, UUID> {
        public DummyController() {
            super();
        }

        public DummyController(BaseService<DummyEntity, DummyEntity, UUID> service) {
            super(service);
        }
    }

    @Test
    public void testBaseClassesExistAndAreInheritedByDomain() throws Exception {
        Class<?> baseEntityClass = Class.forName("com.platform.core.entity.BaseEntity");
        Class<?> baseRepoClass = Class.forName("com.platform.core.repository.BaseRepository");
        Class<?> baseDaoClass = Class.forName("com.platform.core.dao.BaseDAO");
        Class<?> baseDtoClass = Class.forName("com.platform.core.dto.BaseDTO");
        Class<?> baseServiceClass = Class.forName("com.platform.core.service.BaseService");
        Class<?> baseControllerClass = Class.forName("com.platform.core.controller.BaseController");
        Class<?> crudInterceptorClass = Class.forName("com.platform.core.service.CrudInterceptor");

        Assertions.assertNotNull(baseDaoClass);
        Assertions.assertNotNull(crudInterceptorClass);

        // Verify all JPA Entities inherit from BaseEntity
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(Appointment.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(Organisation.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(ClientProfile.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(ProviderProfile.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(Service.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(ClientTherapistHandshake.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(UserEntity.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(JournalEntry.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(PsychotraumatologyTest.class));
        Assertions.assertTrue(baseEntityClass.isAssignableFrom(TherapistClinicalNote.class));

        // Verify all JPA Repositories inherit from BaseRepository
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(AppointmentRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(OrganisationRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(ClientProfileRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(ProviderProfileRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(ServiceRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(HandshakeRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(UserRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(JournalEntryRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(PsychotraumatologyTestRepository.class));
        Assertions.assertTrue(baseRepoClass.isAssignableFrom(TherapistClinicalNoteRepository.class));

        // Verify DTOs implement BaseDTO
        Assertions.assertTrue(baseDtoClass.isAssignableFrom(JournalEntryDto.class));
        Assertions.assertTrue(baseDtoClass.isAssignableFrom(PsychotraumatologyTestDto.class));
        Assertions.assertTrue(baseDtoClass.isAssignableFrom(UserRequestDto.class));
        Assertions.assertTrue(baseDtoClass.isAssignableFrom(UserResponseDto.class));

        // Verify ALL domain Services and Controllers inherit from BaseService and BaseController
        Assertions.assertTrue(baseServiceClass.isAssignableFrom(AppointmentService.class));
        Assertions.assertTrue(baseServiceClass.isAssignableFrom(JournalService.class));
        Assertions.assertTrue(baseServiceClass.isAssignableFrom(PsychotraumatologyService.class));
        Assertions.assertTrue(baseServiceClass.isAssignableFrom(UserService.class));
        Assertions.assertTrue(baseServiceClass.isAssignableFrom(ClinicPlatformService.class));

        Assertions.assertTrue(baseControllerClass.isAssignableFrom(AppointmentController.class));
        Assertions.assertTrue(baseControllerClass.isAssignableFrom(JournalController.class));
        Assertions.assertTrue(baseControllerClass.isAssignableFrom(PsychotraumatologyController.class));
        Assertions.assertTrue(baseControllerClass.isAssignableFrom(UserController.class));
        Assertions.assertTrue(baseControllerClass.isAssignableFrom(ClinicPlatformController.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testBaseServiceAndInterceptorsAndControllerAndSpecifications() {
        BaseRepository<DummyEntity, UUID> repo = mock(BaseRepository.class);
        BaseService<DummyEntity, DummyEntity, UUID> service = new BaseService<>() {};
        service.setRepository(repo);
        Assertions.assertSame(repo, service.getRepository());
        Assertions.assertEquals(DummyEntity.class, service.getEntityClass());
        Assertions.assertEquals(DummyEntity.class, service.getDtoClass());

        BaseService<DummyEntity, DummyEntity, UUID> constructedService = new BaseService<>(repo) {};
        Assertions.assertSame(repo, constructedService.getRepository());

        // Test raw BaseService without resolved generic type parameters
        BaseService rawService = new BaseService();
        Assertions.assertNull(rawService.getEntityClass());
        Assertions.assertNull(rawService.getDtoClass());
        DummyEntity rawEntity = new DummyEntity();
        Assertions.assertThrows(IllegalStateException.class, () -> rawService.toEntity(rawEntity));
        Assertions.assertThrows(IllegalStateException.class, () -> rawService.toDto(rawEntity));

        // Test separate DTO <-> Entity mapping via BeanUtils in BaseService
        DummyServiceWithSeparateDto dtoService = new DummyServiceWithSeparateDto(repo);
        Assertions.assertEquals(DummyEntity.class, dtoService.getEntityClass());
        Assertions.assertEquals(DummyDTO.class, dtoService.getDtoClass());
        Assertions.assertNull(dtoService.toEntity(null));
        Assertions.assertNull(dtoService.toDto(null));

        UUID dtoId = UUID.randomUUID();
        DummyDTO inputDto = new DummyDTO();
        inputDto.setId(dtoId);
        inputDto.setTitle("Mapped Title");

        DummyEntity mappedEntity = dtoService.toEntity(inputDto);
        Assertions.assertEquals(dtoId, mappedEntity.getId());
        Assertions.assertEquals("Mapped Title", mappedEntity.getTitle());

        DummyDTO mappedBackDto = dtoService.toDto(mappedEntity);
        Assertions.assertEquals(dtoId, mappedBackDto.getId());
        Assertions.assertEquals("Mapped Title", mappedBackDto.getTitle());

        when(repo.save(any(DummyEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findAll()).thenReturn(List.of(mappedEntity));
        when(repo.findById(dtoId)).thenReturn(Optional.of(mappedEntity));
        when(repo.existsById(dtoId)).thenReturn(true);

        Assertions.assertEquals(1, dtoService.findAllDtos().size());
        Assertions.assertTrue(dtoService.findDtoById(dtoId).isPresent());
        Assertions.assertEquals("Mapped Title", dtoService.createFromDto(inputDto).getTitle());
        Assertions.assertEquals("Mapped Title", dtoService.updateFromDto(dtoId, inputDto).getTitle());

        // Test reflection error branches on BlockedTypeService
        BlockedTypeService blockedService = new BlockedTypeService();
        NoArgBlockedDTO blockedDto = new NoArgBlockedDTO("x");
        NoArgBlockedEntity blockedEntity = new NoArgBlockedEntity("x");
        Assertions.assertThrows(org.springframework.beans.BeanInstantiationException.class, () -> blockedService.toEntity(blockedDto));
        Assertions.assertThrows(org.springframework.beans.BeanInstantiationException.class, () -> blockedService.toDto(blockedEntity));

        // Test default CrudInterceptor methods
        CrudInterceptor<DummyEntity, UUID> defaultInterceptor = new CrudInterceptor<>() {};
        DummyEntity e = new DummyEntity();
        UUID id = UUID.randomUUID();
        defaultInterceptor.beforeCreate(e);
        defaultInterceptor.afterCreate(e);
        defaultInterceptor.beforeUpdate(e);
        defaultInterceptor.afterUpdate(e);
        defaultInterceptor.beforeDelete(id);
        defaultInterceptor.afterDelete(id);

        List<String> events = new ArrayList<>();
        service.setInterceptors(null);
        service.setInterceptors(List.of(defaultInterceptor));
        Assertions.assertEquals(1, service.getInterceptors().size());
        service.setInterceptors(new ArrayList<>());

        service.addInterceptor(null); // null branch
        service.addInterceptor(new CrudInterceptor<>() {
            @Override
            public void beforeCreate(DummyEntity entity) {
                events.add("beforeCreate");
            }

            @Override
            public void afterCreate(DummyEntity entity) {
                events.add("afterCreate");
            }

            @Override
            public void beforeUpdate(DummyEntity entity) {
                events.add("beforeUpdate");
            }

            @Override
            public void afterUpdate(DummyEntity entity) {
                events.add("afterUpdate");
            }

            @Override
            public void beforeDelete(UUID id) {
                events.add("beforeDelete");
            }

            @Override
            public void afterDelete(UUID id) {
                events.add("afterDelete");
            }
        });

        when(repo.findAll()).thenReturn(List.of(e));
        when(repo.findById(id)).thenReturn(Optional.of(e));
        when(repo.existsById(id)).thenReturn(true);
        when(repo.count()).thenReturn(1L);

        DummyEntity created = service.create(e);
        Assertions.assertSame(e, created);
        Assertions.assertEquals(List.of("beforeCreate", "afterCreate"), events);

        Assertions.assertSame(e, service.save(e));
        Assertions.assertEquals(1, service.findAll().size());
        Assertions.assertTrue(service.findById(id).isPresent());
        Assertions.assertSame(e, service.getByIdOrThrow(id));
        Assertions.assertTrue(service.existsById(id));
        Assertions.assertEquals(1L, service.count());

        DummyEntity updated = service.update(id, e);
        Assertions.assertEquals(id, updated.getId());
        Assertions.assertTrue(events.contains("beforeUpdate"));
        Assertions.assertTrue(events.contains("afterUpdate"));

        service.deleteById(id);
        Assertions.assertTrue(events.contains("beforeDelete"));
        Assertions.assertTrue(events.contains("afterDelete"));

        UUID missingId = UUID.randomUUID();
        when(repo.findById(missingId)).thenReturn(Optional.empty());
        when(repo.existsById(missingId)).thenReturn(false);

        Assertions.assertThrows(ResponseStatusException.class, () -> service.getByIdOrThrow(missingId));
        Assertions.assertThrows(ResponseStatusException.class, () -> service.update(missingId, e));

        // Test BaseDTO default getId and setId
        DefaultIdDTO defaultDto = new DefaultIdDTO();
        Assertions.assertNull(defaultDto.getId());
        defaultDto.setId(id);

        // TestDTO getters/setters on JournalEntryDto, PsychotraumatologyTestDto, UserRequestDto
        JournalEntryDto jDto = new JournalEntryDto();
        jDto.setId(id);
        Assertions.assertEquals(id, jDto.getId());

        PsychotraumatologyTestDto pDto = new PsychotraumatologyTestDto();
        pDto.setId(id);
        Assertions.assertEquals(id, pDto.getId());

        UserRequestDto uDto = new UserRequestDto();
        uDto.setId(id);
        Assertions.assertEquals(id, uDto.getId());

        // Test BaseController
        DummyController controller = new DummyController();
        controller.setService(service);
        Assertions.assertSame(service, controller.getService());

        DummyController controller2 = new DummyController(service);
        Assertions.assertEquals(HttpStatus.OK, controller2.getAll().getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, controller2.getById(id).getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND, controller2.getById(missingId).getStatusCode());
        Assertions.assertEquals(HttpStatus.CREATED, controller2.create(e).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, controller2.update(id, e).getStatusCode());
        ResponseEntity<Void> deleteResp = controller2.delete(id);
        Assertions.assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // Test BaseService JPA Specification dynamic filtering & sorting
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(repo.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(e));

        Map<String, String> specFilters = new HashMap<>();
        specFilters.put("title_like", "abc");
        specFilters.put("age_gt", "18");
        specFilters.put("age_lt", "65");
        specFilters.put("score_gte", "10");
        specFilters.put("score_lte", "100");
        specFilters.put("exactField", "42");
        specFilters.put("blankField", "   ");
        specFilters.put("nullField", null);
        specFilters.put("page", "0");
        specFilters.put("size", "10");
        specFilters.put("sort", "title,desc");

        Assertions.assertEquals(1, service.findAllDynamic(0, 10, specFilters, "title,desc").size());
        Assertions.assertEquals(1, service.findAllDynamic(-1, 0, null, "title,asc").size());
        Assertions.assertEquals(1, service.findAllDynamic(0, 0, Map.of(), "title").size());
        Assertions.assertEquals(1, service.findAllDynamic(0, 0, Map.of(), "   ").size());
        Assertions.assertEquals(1, service.findAllDynamic(0, 0, Map.of(), null).size());

        // Execute the built Specification lambda against mocked Criteria Root/Query/Builder
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<DummyEntity> cq = mock(CriteriaQuery.class);
        Root<DummyEntity> root = mock(Root.class);
        Path path = mock(Path.class);
        Expression lowerExpr = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get(anyString())).thenReturn(path);
        when(path.getJavaType()).thenReturn((Class) Integer.class);
        when(cb.lower(any())).thenReturn(lowerExpr);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.greaterThan(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.lessThan(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<DummyEntity> spec = service.buildSpecification(specFilters);
        Assertions.assertNotNull(spec.toPredicate(root, cq, cb));

        Specification<DummyEntity> nullSpec = service.buildSpecification(null);
        Assertions.assertNotNull(nullSpec.toPredicate(root, cq, cb));

        // Test convertFilterValue all type branches in BaseService
        Assertions.assertEquals(id, service.convertFilterValue(id.toString(), UUID.class));
        Assertions.assertEquals(42L, service.convertFilterValue("42", Long.class));
        Assertions.assertEquals(42L, service.convertFilterValue("42", long.class));
        Assertions.assertEquals(10, service.convertFilterValue("10", Integer.class));
        Assertions.assertEquals(10, service.convertFilterValue("10", int.class));
        Assertions.assertEquals(3.14, service.convertFilterValue("3.14", Double.class));
        Assertions.assertEquals(3.14, service.convertFilterValue("3.14", double.class));
        Assertions.assertEquals(true, service.convertFilterValue("true", Boolean.class));
        Assertions.assertEquals(false, service.convertFilterValue("false", boolean.class));
        Assertions.assertEquals("hello", service.convertFilterValue("hello", String.class));

        // Also test JournalController and PsychotraumatologyController inherited BaseController methods
        JournalEntryRepository jRepo = mock(JournalEntryRepository.class);
        when(jRepo.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        JournalController jCtrl = new JournalController(jRepo, null);
        JournalEntryDto createDto = new JournalEntryDto();
        createDto.setTitle("Inherited Create");
        createDto.setContent("Body");
        Assertions.assertEquals(HttpStatus.OK, jCtrl.create(createDto).getStatusCode());
        Assertions.assertNull(new JournalService().toEntity(null));

        PsychotraumatologyTestRepository pRepo = mock(PsychotraumatologyTestRepository.class);
        when(pRepo.save(any(PsychotraumatologyTest.class))).thenAnswer(inv -> inv.getArgument(0));
        PsychotraumatologyController pCtrl = new PsychotraumatologyController(pRepo);
        PsychotraumatologyTestDto pclDto = new PsychotraumatologyTestDto();
        pclDto.setScore(15);
        Assertions.assertEquals(HttpStatus.OK, pCtrl.create(pclDto).getStatusCode());
        Assertions.assertNull(new PsychotraumatologyService(pRepo).toEntity(null));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testBaseDAOCrudAndDynamicFiltering() {
        EntityManager em = mock(EntityManager.class);
        DummyDAO dao = new DummyDAO();
        Assertions.assertEquals(DummyEntity.class, dao.getEntityClass());
        dao.setEntityManager(em);
        dao.setEntityClass(DummyEntity.class);
        Assertions.assertSame(em, dao.getEntityManager());

        RawDAO rawDao = new RawDAO();
        Assertions.assertNull(rawDao.getEntityClass());

        DummyDAO daoWithArgs = new DummyDAO(em);
        Assertions.assertSame(em, daoWithArgs.getEntityManager());

        TypedQuery<DummyEntity> listQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(DummyEntity.class))).thenReturn(listQuery);
        DummyEntity entity = new DummyEntity();
        entity.setTitle("Test");
        when(listQuery.getResultList()).thenReturn(List.of(entity));

        Assertions.assertEquals(1, dao.findAll().size());

        // Save new vs merge existing
        DummyEntity savedNew = dao.save(entity);
        verify(em).persist(entity);
        Assertions.assertSame(entity, savedNew);

        UUID id = UUID.randomUUID();
        entity.setId(id);
        when(em.merge(entity)).thenReturn(entity);
        DummyEntity savedMerged = dao.save(entity);
        verify(em).merge(entity);
        Assertions.assertSame(entity, savedMerged);

        // findById, existsById, deleteById, count
        when(em.find(DummyEntity.class, id)).thenReturn(entity);
        Assertions.assertTrue(dao.findById(id).isPresent());
        Assertions.assertTrue(dao.existsById(id));
        dao.deleteById(id);
        verify(em).remove(entity);

        UUID missingId = UUID.randomUUID();
        when(em.find(DummyEntity.class, missingId)).thenReturn(null);
        Assertions.assertFalse(dao.existsById(missingId));
        dao.deleteById(missingId);

        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(5L);
        Assertions.assertEquals(5L, dao.count());

        // convertValue branches
        Assertions.assertEquals(id, dao.exposeConvert(id.toString(), UUID.class));
        Assertions.assertEquals(42L, dao.exposeConvert("42", Long.class));
        Assertions.assertEquals(42L, dao.exposeConvert("42", long.class));
        Assertions.assertEquals(10, dao.exposeConvert("10", Integer.class));
        Assertions.assertEquals(10, dao.exposeConvert("10", int.class));
        Assertions.assertEquals(3.14, dao.exposeConvert("3.14", Double.class));
        Assertions.assertEquals(3.14, dao.exposeConvert("3.14", double.class));
        Assertions.assertEquals(true, dao.exposeConvert("true", Boolean.class));
        Assertions.assertEquals(false, dao.exposeConvert("false", boolean.class));
        Assertions.assertEquals("hello", dao.exposeConvert("hello", String.class));

        // findAllDynamic with filters & sorting
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<DummyEntity> cq = mock(CriteriaQuery.class);
        Root<DummyEntity> root = mock(Root.class);
        Path path = mock(Path.class);
        Expression lowerExpr = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);
        Order order = mock(Order.class);
        TypedQuery<DummyEntity> criteriaTypedQuery = mock(TypedQuery.class);

        when(em.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(DummyEntity.class)).thenReturn(cq);
        when(cq.from(DummyEntity.class)).thenReturn(root);
        when(root.get(anyString())).thenReturn(path);
        when(path.getJavaType()).thenReturn((Class) Integer.class);
        when(cb.lower(any())).thenReturn(lowerExpr);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.greaterThan(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.lessThan(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(), any(Comparable.class))).thenReturn(predicate);
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        when(cb.asc(any())).thenReturn(order);
        when(cb.desc(any())).thenReturn(order);
        when(em.createQuery(cq)).thenReturn(criteriaTypedQuery);
        when(criteriaTypedQuery.setFirstResult(anyInt())).thenReturn(criteriaTypedQuery);
        when(criteriaTypedQuery.setMaxResults(anyInt())).thenReturn(criteriaTypedQuery);
        when(criteriaTypedQuery.getResultList()).thenReturn(List.of(entity));

        Map<String, String> filters = new HashMap<>();
        filters.put("title_like", "abc");
        filters.put("age_gt", "18");
        filters.put("age_lt", "65");
        filters.put("score_gte", "10");
        filters.put("score_lte", "100");
        filters.put("exactField", "42");
        filters.put("blankField", "   ");
        filters.put("nullField", null);
        filters.put("page", "0");
        filters.put("size", "10");
        filters.put("sort", "title,desc");

        List<DummyEntity> dynamicDesc = dao.findAllDynamic(0, 10, filters, "title,desc");
        Assertions.assertEquals(1, dynamicDesc.size());

        List<DummyEntity> dynamicAsc = dao.findAllDynamic(-1, 0, null, "title,asc");
        Assertions.assertEquals(1, dynamicAsc.size());

        List<DummyEntity> dynamicSingleSort = dao.findAllDynamic(0, 5, Map.of(), "title");
        Assertions.assertEquals(1, dynamicSingleSort.size());

        List<DummyEntity> dynamicBlankSort = dao.findAllDynamic(0, 5, Map.of(), "   ");
        Assertions.assertEquals(1, dynamicBlankSort.size());

        List<DummyEntity> dynamicNullSort = dao.findAllDynamic(0, 5, Map.of(), null);
        Assertions.assertEquals(1, dynamicNullSort.size());
    }
}
