package com.aflow.persistence.service;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;
import com.aflow.persistence.repository.FlowInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class JpaFlowInstancePersistenceServiceTest {

    @Autowired
    private FlowInstanceRepository repository;

    private JpaFlowInstancePersistenceService service() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new JpaFlowInstancePersistenceService(repository, mapper);
    }

    @Test
    void save_and_findById() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.RUNNING);
        ctx.putVariable("key", "value");
        ctx.recordNodeExecution("n1");
        ctx.getBreakpoints().add("n2");
        ctx.setDebugMode(true);

        service().save(ctx);

        Optional<FlowContext> result = service().findById("inst-1");
        assertTrue(result.isPresent());
        assertEquals("def-1", result.get().getFlowDefinitionId());
        assertEquals(FlowStatus.RUNNING, result.get().getStatus());
        assertEquals("value", result.get().getVariable("key"));
        assertTrue(result.get().getExecutionPath().contains("n1"));
        assertTrue(result.get().getBreakpoints().contains("n2"));
        assertTrue(result.get().isDebugMode());
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        assertTrue(service().findById("nonexistent").isEmpty());
    }

    @Test
    void findByDefinitionId_returnsMatching() {
        FlowContext ctx1 = new FlowContext("inst-1", "def-1");
        ctx1.setStatus(FlowStatus.COMPLETED);
        FlowContext ctx2 = new FlowContext("inst-2", "def-1");
        ctx2.setStatus(FlowStatus.RUNNING);
        FlowContext ctx3 = new FlowContext("inst-3", "def-2");
        ctx3.setStatus(FlowStatus.RUNNING);

        service().save(ctx1);
        service().save(ctx2);
        service().save(ctx3);

        List<FlowContext> results = service().findByDefinitionId("def-1");
        assertEquals(2, results.size());
    }

    @Test
    void findByStatus_returnsMatching() {
        FlowContext running = new FlowContext("inst-1", "def-1");
        running.setStatus(FlowStatus.RUNNING);
        FlowContext completed = new FlowContext("inst-2", "def-1");
        completed.setStatus(FlowStatus.COMPLETED);

        service().save(running);
        service().save(completed);

        List<FlowContext> runningList = service().findByStatus(FlowStatus.RUNNING);
        assertEquals(1, runningList.size());
        assertEquals("inst-1", runningList.get(0).getFlowInstanceId());
    }

    @Test
    void findAll_returnsAll() {
        service().save(new FlowContext("inst-1", "def-1"));
        service().save(new FlowContext("inst-2", "def-1"));

        List<FlowContext> all = service().findAll();
        assertEquals(2, all.size());
    }

    @Test
    void save_preservesMetadata() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.RUNNING);
        ctx.getMetadata().put("tenant", "acme");
        ctx.getMetadata().put("creator", "admin");

        service().save(ctx);

        FlowContext loaded = service().findById("inst-1").orElseThrow();
        assertEquals("acme", loaded.getMetadata().get("tenant"));
        assertEquals("admin", loaded.getMetadata().get("creator"));
    }

    @Test
    void save_updateExisting_updatesStatus() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.RUNNING);
        service().save(ctx);

        ctx.setStatus(FlowStatus.COMPLETED);
        service().save(ctx);

        FlowContext loaded = service().findById("inst-1").orElseThrow();
        assertEquals(FlowStatus.COMPLETED, loaded.getStatus());
    }

    @Test
    void save_preservesMultipleVariables() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.RUNNING);
        ctx.putVariable("string", "hello");
        ctx.putVariable("number", 42);
        ctx.putVariable("bool", true);
        ctx.putVariable("nested", Map.of("a", 1));

        service().save(ctx);

        FlowContext loaded = service().findById("inst-1").orElseThrow();
        assertEquals("hello", loaded.getVariable("string"));
        assertEquals(42, (int) loaded.getVariable("number"));
        assertEquals(true, loaded.getVariable("bool"));
        assertNotNull(loaded.getVariable("nested"));
    }
}
