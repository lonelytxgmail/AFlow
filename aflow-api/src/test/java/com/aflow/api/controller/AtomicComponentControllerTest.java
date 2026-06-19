package com.aflow.api.controller;

import com.aflow.api.dto.AtomicCallRequest;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.AtomicComponent;
import com.aflow.common.model.NodeResult;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import com.aflow.core.registry.NodeRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AtomicComponentController.class)
class AtomicComponentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AtomicComponentPersistenceService persistenceService;
    @MockBean private NodeRegistry nodeRegistry;
    @MockBean private NodeExecutor compositeExecutor;

    @Test
    void list_returnsAllComponents() throws Exception {
        AtomicComponent component = buildComponent("component-1", "HTTP Tool", "PUBLISHED");
        when(persistenceService.findAll()).thenReturn(List.of(component));

        mockMvc.perform(get("/api/v1/atomic/components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("component-1"))
                .andExpect(jsonPath("$.data[0].nodeType").value("http"));
    }

    @Test
    void list_withKeyword_prefersSearchByName() throws Exception {
        when(persistenceService.searchByName("tool")).thenReturn(List.of(buildComponent("component-1", "HTTP Tool", "PUBLISHED")));

        mockMvc.perform(get("/api/v1/atomic/components").param("keyword", "tool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(persistenceService).searchByName("tool");
    }

    @Test
    void get_existingComponent_returnsIt() throws Exception {
        when(persistenceService.findById("component-1")).thenReturn(Optional.of(buildComponent("component-1", "HTTP Tool", "PUBLISHED")));

        mockMvc.perform(get("/api/v1/atomic/components/component-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("component-1"));
    }

    @Test
    void get_missingComponent_returnsBusinessError() throws Exception {
        when(persistenceService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/atomic/components/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ATOMIC_COMPONENT_NOT_FOUND"));
    }

    @Test
    void create_persistsComponent() throws Exception {
        AtomicComponent request = buildComponent(null, "HTTP Tool", "DRAFT");
        AtomicComponent saved = buildComponent("component-1", "HTTP Tool", "DRAFT");
        when(persistenceService.save(any(AtomicComponent.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/atomic/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("component-1"));
    }

    @Test
    void update_existingComponent_savesWithPathId() throws Exception {
        AtomicComponent request = buildComponent("ignored-id", "Updated Tool", "PUBLISHED");
        AtomicComponent saved = buildComponent("component-1", "Updated Tool", "PUBLISHED");
        when(persistenceService.findById("component-1")).thenReturn(Optional.of(buildComponent("component-1", "HTTP Tool", "PUBLISHED")));
        when(persistenceService.save(any(AtomicComponent.class))).thenReturn(saved);

        mockMvc.perform(put("/api/v1/atomic/components/component-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("component-1"))
                .andExpect(jsonPath("$.data.name").value("Updated Tool"));
    }

    @Test
    void delete_missingComponent_returnsBusinessError() throws Exception {
        when(persistenceService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/atomic/components/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ATOMIC_COMPONENT_NOT_FOUND"));
    }

    @Test
    void delete_existingComponent_deletesIt() throws Exception {
        when(persistenceService.findById("component-1")).thenReturn(Optional.of(buildComponent("component-1", "HTTP Tool", "PUBLISHED")));

        mockMvc.perform(delete("/api/v1/atomic/components/component-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(persistenceService).deleteById("component-1");
    }

    @Test
    void registry_returnsPublishedProjection() throws Exception {
        AtomicComponent component = buildComponent("component-1", "HTTP Tool", "PUBLISHED");
        when(persistenceService.findByStatus("PUBLISHED")).thenReturn(List.of(component));

        mockMvc.perform(get("/api/v1/atomic/component-registry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("component-1"))
                .andExpect(jsonPath("$.data[0].name").value("HTTP Tool"))
                .andExpect(jsonPath("$.data[0].description").value("desc"))
                .andExpect(jsonPath("$.data[0].inputSchema").value("{\"required\":[\"url\"]}"));
    }

    @Test
    void invoke_publishedComponent_delegatesToCompositeExecutor() throws Exception {
        AtomicComponent component = buildComponent("component-1", "HTTP Tool", "PUBLISHED");
        when(persistenceService.findById("component-1")).thenReturn(Optional.of(component));
        when(nodeRegistry.getExecutor("composite")).thenReturn(compositeExecutor);
        when(compositeExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of("ok", true)));

        AtomicCallRequest request = new AtomicCallRequest(Map.of("timeout", 3000), Map.of("url", "https://example.com"));
        mockMvc.perform(post("/api/v1/atomic/components/component-1/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.outputs.ok").value(true));

        verify(nodeRegistry).getExecutor("composite");
    }

    @Test
    void invoke_unpublishedComponent_returnsBadRequest() throws Exception {
        AtomicComponent component = buildComponent("component-1", "HTTP Tool", "DRAFT");
        when(persistenceService.findById("component-1")).thenReturn(Optional.of(component));

        AtomicCallRequest request = new AtomicCallRequest(Map.of(), Map.of());
        mockMvc.perform(post("/api/v1/atomic/components/component-1/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    private AtomicComponent buildComponent(String id, String name, String status) {
        AtomicComponent component = new AtomicComponent();
        component.setId(id);
        component.setName(name);
        component.setDescription("desc");
        component.setCategory("tool");
        component.setNodeType("http");
        component.setStatus(status);
        component.setConfigTemplate("{\"method\":\"GET\"}");
        component.setInputSchema("{\"required\":[\"url\"]}");
        component.setOutputSchema("{\"type\":\"object\"}");
        return component;
    }
}
