package com.aflow.api.controller;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;
import com.aflow.core.engine.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebugController.class)
class DebugControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private WorkflowEngine engine;

    @Test
    void addBreakpoint_callsEngine() throws Exception {
        mockMvc.perform(post("/api/v1/debug/flow-1/breakpoint/node-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Breakpoint added"));

        verify(engine).addBreakpoint("flow-1", "node-a");
    }

    @Test
    void removeBreakpoint_callsEngine() throws Exception {
        mockMvc.perform(delete("/api/v1/debug/flow-1/breakpoint/node-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Breakpoint removed"));

        verify(engine).removeBreakpoint("flow-1", "node-a");
    }

    @Test
    void getBreakpoints_returnsCurrentSet() throws Exception {
        when(engine.getBreakpoints("flow-1")).thenReturn(Set.of("node-a", "node-b"));

        mockMvc.perform(get("/api/v1/debug/flow-1/breakpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void step_returnsUpdatedContext() throws Exception {
        FlowContext context = new FlowContext("flow-1", "def-1");
        context.setStatus(FlowStatus.SUSPENDED);
        context.setCurrentNodeId("node-b");
        when(engine.step("flow-1")).thenReturn(context);

        mockMvc.perform(post("/api/v1/debug/flow-1/step"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.flowInstanceId").value("flow-1"))
                .andExpect(jsonPath("$.data.currentNodeId").value("node-b"));
    }

    @Test
    void updateContext_forwardsVariables() throws Exception {
        FlowContext context = new FlowContext("flow-1", "def-1");
        context.setStatus(FlowStatus.SUSPENDED);
        context.setVariables(Map.of("debugFlag", true));
        when(engine.updateContext(eq("flow-1"), eq(Map.of("debugFlag", true)))).thenReturn(context);

        mockMvc.perform(put("/api/v1/debug/flow-1/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("debugFlag", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.variables.debugFlag").value(true));
    }
}
