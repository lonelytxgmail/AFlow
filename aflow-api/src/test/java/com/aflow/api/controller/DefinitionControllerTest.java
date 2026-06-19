package com.aflow.api.controller;

import com.aflow.api.dto.CreateDefinitionRequest;
import com.aflow.common.exception.FlowNotFoundException;
import com.aflow.common.model.FlowDefinition;
import com.aflow.core.dsl.DslParser;
import com.aflow.core.engine.FlowDefinitionPersistenceService;
import com.aflow.core.engine.WorkflowEngine;
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

@WebMvcTest(DefinitionController.class)
class DefinitionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private FlowDefinitionPersistenceService definitionService;
    @MockBean private DslParser dslParser;
    @MockBean private WorkflowEngine engine;

    @Test
    void list_returnsAllDefinitions() throws Exception {
        FlowDefinition def = new FlowDefinition();
        def.setId("def-1");
        def.setName("Test");
        when(definitionService.findAll()).thenReturn(List.of(def));

        mockMvc.perform(get("/api/v1/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("def-1"));
    }

    @Test
    void get_existingDefinition_returnsIt() throws Exception {
        FlowDefinition def = new FlowDefinition();
        def.setId("def-1");
        def.setName("Test");
        when(definitionService.findById("def-1")).thenReturn(Optional.of(def));
        when(definitionService.findDslContentById("def-1")).thenReturn("{}");

        mockMvc.perform(get("/api/v1/definitions/def-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.definition.id").value("def-1"));
    }

    @Test
    void get_nonExistent_returnsNotFound() throws Exception {
        when(definitionService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/definitions/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_validRequest_returnsOk() throws Exception {
        FlowDefinition parsed = new FlowDefinition();
        when(dslParser.parse(anyString())).thenReturn(parsed);

        CreateDefinitionRequest request = new CreateDefinitionRequest("def-1", "Test", "{\"nodes\":[]}");
        mockMvc.perform(post("/api/v1/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(definitionService).save(eq("def-1"), eq("Test"), eq(1), eq("DRAFT"), anyString());
    }

    @Test
    void create_blankName_returnsValidationError() throws Exception {
        CreateDefinitionRequest request = new CreateDefinitionRequest("def-1", "", "{\"nodes\":[]}");
        mockMvc.perform(post("/api/v1/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void delete_callsDeleteService() throws Exception {
        mockMvc.perform(delete("/api/v1/definitions/def-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(definitionService).delete("def-1");
    }

    @Test
    void publish_updatesStatus() throws Exception {
        mockMvc.perform(post("/api/v1/definitions/def-1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(definitionService).updateStatus("def-1", "PUBLISHED");
    }
}
