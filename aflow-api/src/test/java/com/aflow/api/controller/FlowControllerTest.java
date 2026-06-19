package com.aflow.api.controller;

import com.aflow.api.dto.StartFlowRequest;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;
import com.aflow.core.engine.FlowInstancePersistenceService;
import com.aflow.core.engine.WorkflowEngine;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.snapshot.SnapshotPersistenceService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FlowController.class)
class FlowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private WorkflowEngine engine;
    @MockBean private FlowInstancePersistenceService instanceService;
    @MockBean private SnapshotPersistenceService snapshotService;
    @MockBean private EventPersistenceService eventService;

    @Test
    void start_validRequest_returnsContext() throws Exception {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.COMPLETED);
        when(engine.start(eq("def-1"), any())).thenReturn(ctx);

        StartFlowRequest request = new StartFlowRequest("def-1", Map.of("key", "value"));
        mockMvc.perform(post("/api/v1/flows/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.flowDefinitionId").value("def-1"));
    }

    @Test
    void start_blankDefinitionId_returnsValidationError() throws Exception {
        StartFlowRequest request = new StartFlowRequest("", null);
        mockMvc.perform(post("/api/v1/flows/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returnsAllInstances() throws Exception {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.RUNNING);
        when(instanceService.findAll()).thenReturn(List.of(ctx));

        mockMvc.perform(get("/api/v1/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].flowInstanceId").value("inst-1"));
    }

    @Test
    void list_withStatusFilter_filtersByStatus() throws Exception {
        when(instanceService.findByStatus(FlowStatus.RUNNING)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/flows").param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(instanceService).findByStatus(FlowStatus.RUNNING);
    }

    @Test
    void get_existingInstance_returnsIt() throws Exception {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        when(instanceService.findById("inst-1")).thenReturn(Optional.of(ctx));

        mockMvc.perform(get("/api/v1/flows/inst-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void get_nonExistent_returnsError() throws Exception {
        when(instanceService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/flows/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void cancel_callsEngine() throws Exception {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.CANCELLED);
        when(engine.cancel("inst-1")).thenReturn(ctx);

        mockMvc.perform(post("/api/v1/flows/inst-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(engine).cancel("inst-1");
    }

    @Test
    void snapshots_returnsList() throws Exception {
        when(snapshotService.findByFlowInstanceId("inst-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/flows/inst-1/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void events_returnsList() throws Exception {
        when(eventService.findByFlowInstanceId("inst-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/flows/inst-1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
