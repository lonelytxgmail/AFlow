package com.aflow.persistence.service;

import com.aflow.common.model.FlowDefinition;
import com.aflow.persistence.entity.FlowDefinitionEntity;
import com.aflow.persistence.repository.FlowDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class JpaFlowDefinitionPersistenceServiceTest {

    @Autowired
    private FlowDefinitionRepository repository;

    private JpaFlowDefinitionPersistenceService service() {
        return new JpaFlowDefinitionPersistenceService(repository);
    }

    @Test
    void save_and_findById() {
        service().save("def-1", "Test Flow", 1, "DRAFT", "{\"nodes\":[]}");

        Optional<FlowDefinition> result = service().findById("def-1");
        assertTrue(result.isPresent());
        assertEquals("Test Flow", result.get().getName());
        assertEquals(1, result.get().getVersion());
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        Optional<FlowDefinition> result = service().findById("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void findDslContentById_returnsContent() {
        service().save("def-1", "Test", 1, "DRAFT", "{\"nodes\":[],\"edges\":[]}");

        String dsl = service().findDslContentById("def-1");
        assertNotNull(dsl);
        assertTrue(dsl.contains("nodes"));
    }

    @Test
    void findDslContentById_nonExistent_returnsNull() {
        assertNull(service().findDslContentById("nonexistent"));
    }

    @Test
    void findAll_returnsAll() {
        service().save("def-1", "Flow A", 1, "DRAFT", "{}");
        service().save("def-2", "Flow B", 1, "PUBLISHED", "{}");

        List<FlowDefinition> all = service().findAll();
        assertEquals(2, all.size());
    }

    @Test
    void updateDsl_updatesContent() {
        service().save("def-1", "Original", 1, "DRAFT", "{\"v\":1}");

        service().updateDsl("def-1", "Updated", "{\"v\":2}");

        String dsl = service().findDslContentById("def-1");
        assertEquals("{\"v\":2}", dsl);

        FlowDefinition def = service().findById("def-1").orElseThrow();
        assertEquals("Updated", def.getName());
    }

    @Test
    void updateDsl_nonExistent_throws() {
        assertThrows(RuntimeException.class, () ->
                service().updateDsl("nonexistent", "name", "dsl"));
    }

    @Test
    void updateStatus_changesStatus() {
        service().save("def-1", "Test", 1, "DRAFT", "{}");

        service().updateStatus("def-1", "PUBLISHED");

        List<FlowDefinitionEntity> published = repository.findByStatus("PUBLISHED");
        assertEquals(1, published.size());
        assertEquals("PUBLISHED", published.get(0).getStatus());
    }

    @Test
    void delete_removesEntity() {
        service().save("def-1", "Test", 1, "DRAFT", "{}");
        service().delete("def-1");

        assertTrue(service().findById("def-1").isEmpty());
    }

    @Test
    void save_updateExisting_updatesVersion() {
        service().save("def-1", "V1", 1, "DRAFT", "{}");
        service().save("def-1", "V2", 2, "PUBLISHED", "{\"v\":2}");

        FlowDefinition def = service().findById("def-1").orElseThrow();
        assertEquals("V2", def.getName());
        assertEquals(2, def.getVersion());
    }
}
