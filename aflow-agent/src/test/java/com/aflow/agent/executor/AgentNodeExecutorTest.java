package com.aflow.agent.executor;

import com.aflow.agent.llm.LlmMessage;
import com.aflow.agent.llm.LlmResponse;
import com.aflow.agent.llm.LlmService;
import com.aflow.agent.tool.NodeExecutorToolAdapter;
import com.aflow.agent.tool.ToolRegistry;
import com.aflow.agent.tool.ToolSelector;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentNodeExecutorTest {

    @Test
    void execute_usesAllowedToolsAndPublishesStructuredEvents() {
        LlmService llmService = mock(LlmService.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ToolRegistry> toolRegistryProvider = mock(ObjectProvider.class);

        NodeExecutorToolAdapter selectedTool = mock(NodeExecutorToolAdapter.class);
        when(selectedTool.getName()).thenReturn("atomic_component_1");
        when(selectedTool.execute(eq("{\"name\":\"lee\"}"), any(FlowContext.class))).thenReturn("{\"ok\":true}");

        when(toolRegistryProvider.getObject()).thenReturn(toolRegistry);
        when(toolRegistry.getToolsByNames(List.of("atomic_component_1"))).thenReturn(List.of(selectedTool));
        when(toolRegistry.buildToolDescriptions(any())).thenReturn("Available tools:\n- atomic_component_1: demo");
        when(toolRegistry.getTool("atomic_component_1")).thenReturn(selectedTool);

        LlmResponse first = new LlmResponse();
        first.setContent("call tool");
        first.setToolCalls(List.of(new LlmMessage.ToolCall("call-1", "atomic_component_1", "{\"name\":\"lee\"}")));
        LlmResponse second = new LlmResponse();
        second.setContent("final answer");
        when(llmService.chatWithTools(any()))
                .thenReturn(first)
                .thenReturn(second);

        ToolSelector toolSelector = mock(ToolSelector.class);
        when(toolSelector.selectTools(any(), any(FlowContext.class), any(ToolRegistry.class)))
                .thenReturn(List.of(selectedTool));

        AgentNodeExecutor executor = new AgentNodeExecutor(
                llmService,
                toolRegistryProvider,
                eventPersistenceService,
                eventBus,
                new ObjectMapper(),
                toolSelector
        );

        FlowContext context = new FlowContext("inst-1", "def-1");
        NodeConfig config = new NodeConfig("agent", Map.of(
                "userPrompt", "hello",
                "tools", List.of("atomic_component_1"),
                "outputVariable", "answer",
                "maxIterations", 3
        ), null);

        var result = executor.execute(config, context);

        assertEquals("final answer", context.getVariable("answer"));
        assertEquals("final answer", result.outputs().get("answer"));
        verify(toolSelector).selectTools(any(), eq(context), eq(toolRegistry));
        verify(selectedTool).execute(eq("{\"name\":\"lee\"}"), eq(context));
        verify(eventPersistenceService, times(2)).saveEvent(eq("inst-1"), eq(null), eq(FlowEventType.AGENT_THINK), any(), eq(0L));
        verify(eventPersistenceService).saveEvent(eq("inst-1"), eq(null), eq(FlowEventType.AGENT_ACT), any(), eq(0L));
        verify(eventPersistenceService).saveEvent(eq("inst-1"), eq(null), eq(FlowEventType.AGENT_OBSERVE), any(), eq(0L));
        verify(eventPersistenceService).saveEvent(eq("inst-1"), eq(null), eq(FlowEventType.AGENT_DONE), any(), eq(0L));
        verify(eventBus, times(5)).publish(eq("inst-1"), any(), any());
    }

    @Test
    void execute_toolTimeoutContinuesLoop_doesNotAbortAgent() {
        // Setup mocks
        LlmService llmService = mock(LlmService.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ToolRegistry> toolRegistryProvider = mock(ObjectProvider.class);

        // The tool that will sleep longer than the configured timeout
        NodeExecutorToolAdapter slowTool = mock(NodeExecutorToolAdapter.class);
        when(slowTool.getName()).thenReturn("slow_tool");
        when(slowTool.execute(any(), any(FlowContext.class))).thenAnswer(invocation -> {
            Thread.sleep(200); // sleeps 200ms, exceeding the 50ms timeout
            return "should not be seen";
        });

        when(toolRegistryProvider.getObject()).thenReturn(toolRegistry);
        when(toolRegistry.getToolsByNames(List.of("slow_tool"))).thenReturn(List.of(slowTool));
        when(toolRegistry.buildToolDescriptions(any())).thenReturn("Available tools:\n- slow_tool: a slow tool");
        when(toolRegistry.getTool("slow_tool")).thenReturn(slowTool);

        ToolSelector toolSelector = mock(ToolSelector.class);
        when(toolSelector.selectTools(any(), any(FlowContext.class), any(ToolRegistry.class)))
                .thenReturn(List.of(slowTool));

        // First LLM response: call the slow tool
        LlmResponse toolCallResponse = new LlmResponse();
        toolCallResponse.setContent("calling slow tool");
        toolCallResponse.setToolCalls(List.of(new LlmMessage.ToolCall("call-1", "slow_tool", "{}")));

        // Second LLM response: final answer (after receiving timeout error)
        LlmResponse finalResponse = new LlmResponse();
        finalResponse.setContent("done after timeout");

        when(llmService.chatWithTools(any()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        AgentNodeExecutor executor = new AgentNodeExecutor(
                llmService, toolRegistryProvider, eventPersistenceService, eventBus, new ObjectMapper(), toolSelector
        );

        FlowContext context = new FlowContext("inst-timeout", "def-1");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("userPrompt", "test timeout");
        cfg.put("tools", List.of("slow_tool"));
        cfg.put("outputVariable", "result");
        cfg.put("maxIterations", 5);
        cfg.put("toolTimeout", "50"); // 50ms timeout
        NodeConfig config = new NodeConfig("agent", cfg, null);

        var result = executor.execute(config, context);

        // Agent should produce a successful result (loop continued after timeout)
        assertEquals("done after timeout", context.getVariable("result"));
        assertEquals("done after timeout", result.outputs().get("result"));

        // AGENT_TOOL_TIMEOUT event should have been published
        verify(eventBus).publish(eq("inst-timeout"), eq(FlowEventType.AGENT_TOOL_TIMEOUT.name()), any());

        // LLM was called twice: once for tool call, once for final answer after receiving timeout error
        verify(llmService, times(2)).chatWithTools(any());
    }

    @Test
    void execute_truncationAppliedOnlyToLlmMessages_observeEventHasFullResult() {
        // Setup mocks
        LlmService llmService = mock(LlmService.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ToolRegistry> toolRegistryProvider = mock(ObjectProvider.class);

        // A tool that returns a very long result (longer than the configured max length)
        String longResult = "x".repeat(300); // 300 chars, exceeding toolResultMaxLength of 100
        NodeExecutorToolAdapter verboseTool = mock(NodeExecutorToolAdapter.class);
        when(verboseTool.getName()).thenReturn("verbose_tool");
        when(verboseTool.execute(any(), any(FlowContext.class))).thenReturn(longResult);

        when(toolRegistryProvider.getObject()).thenReturn(toolRegistry);
        when(toolRegistry.getToolsByNames(List.of("verbose_tool"))).thenReturn(List.of(verboseTool));
        when(toolRegistry.buildToolDescriptions(any())).thenReturn("Available tools:\n- verbose_tool: verbose");
        when(toolRegistry.getTool("verbose_tool")).thenReturn(verboseTool);

        ToolSelector toolSelector = mock(ToolSelector.class);
        when(toolSelector.selectTools(any(), any(FlowContext.class), any(ToolRegistry.class)))
                .thenReturn(List.of(verboseTool));

        // First LLM call: tool call
        LlmResponse toolCallResponse = new LlmResponse();
        toolCallResponse.setContent("calling verbose tool");
        toolCallResponse.setToolCalls(List.of(new LlmMessage.ToolCall("call-1", "verbose_tool", "{}")));

        // Second LLM call: final answer
        LlmResponse finalResponse = new LlmResponse();
        finalResponse.setContent("final");

        // Capture messages sent to the LLM to verify truncation
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(llmService.chatWithTools(messagesCaptor.capture()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        AgentNodeExecutor executor = new AgentNodeExecutor(
                llmService, toolRegistryProvider, eventPersistenceService, eventBus, new ObjectMapper(), toolSelector
        );

        FlowContext context = new FlowContext("inst-trunc", "def-1");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("userPrompt", "test truncation");
        cfg.put("tools", List.of("verbose_tool"));
        cfg.put("outputVariable", "result");
        cfg.put("maxIterations", 5);
        cfg.put("toolResultMaxLength", "100"); // Max 100 chars
        NodeConfig config = new NodeConfig("agent", cfg, null);

        executor.execute(config, context);

        // Verify that the messages sent to LLM on the second call contain truncated tool result
        List<List<LlmMessage>> allCaptured = messagesCaptor.getAllValues();
        assertTrue(allCaptured.size() >= 2, "LLM should have been called at least twice");

        List<LlmMessage> secondCallMessages = allCaptured.get(1);
        // Find the tool result message
        LlmMessage toolResultMsg = secondCallMessages.stream()
                .filter(m -> "tool".equals(m.getRole()))
                .findFirst()
                .orElse(null);
        assertNotNull(toolResultMsg, "Tool result message should exist in LLM call");
        // The truncated content should be shorter than the original 300 chars
        assertTrue(toolResultMsg.getContent().length() < longResult.length(),
                "Tool result in LLM messages should be truncated");
        assertTrue(toolResultMsg.getContent().contains("[...truncated"),
                "Truncated result should contain the truncation marker");

        // Verify the AGENT_OBSERVE event was published (with a preview of the full result)
        verify(eventBus).publish(eq("inst-trunc"), eq(FlowEventType.AGENT_OBSERVE.name()), any());
    }

    @Test
    void execute_tokenBudgetPruningTriggersEvent() {
        // Setup mocks
        LlmService llmService = mock(LlmService.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        EventPersistenceService eventPersistenceService = mock(EventPersistenceService.class);
        FlowEventBus eventBus = mock(FlowEventBus.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ToolRegistry> toolRegistryProvider = mock(ObjectProvider.class);

        // A tool that returns medium-length results to accumulate tokens
        NodeExecutorToolAdapter tool = mock(NodeExecutorToolAdapter.class);
        when(tool.getName()).thenReturn("filler_tool");
        when(tool.execute(any(), any(FlowContext.class))).thenReturn("a".repeat(200));

        when(toolRegistryProvider.getObject()).thenReturn(toolRegistry);
        when(toolRegistry.getToolsByNames(List.of("filler_tool"))).thenReturn(List.of(tool));
        when(toolRegistry.buildToolDescriptions(any())).thenReturn("Available tools:\n- filler_tool: filler");
        when(toolRegistry.getTool("filler_tool")).thenReturn(tool);

        ToolSelector toolSelector = mock(ToolSelector.class);
        when(toolSelector.selectTools(any(), any(FlowContext.class), any(ToolRegistry.class)))
                .thenReturn(List.of(tool));

        // The LLM keeps calling the tool 3 times, then gives a final answer.
        // Each tool call adds messages (assistant + tool result), building up token usage.
        LlmResponse toolCallResp1 = new LlmResponse();
        toolCallResp1.setContent("thinking 1");
        toolCallResp1.setToolCalls(List.of(new LlmMessage.ToolCall("c1", "filler_tool", "{}")));

        LlmResponse toolCallResp2 = new LlmResponse();
        toolCallResp2.setContent("thinking 2");
        toolCallResp2.setToolCalls(List.of(new LlmMessage.ToolCall("c2", "filler_tool", "{}")));

        LlmResponse toolCallResp3 = new LlmResponse();
        toolCallResp3.setContent("thinking 3");
        toolCallResp3.setToolCalls(List.of(new LlmMessage.ToolCall("c3", "filler_tool", "{}")));

        LlmResponse finalResponse = new LlmResponse();
        finalResponse.setContent("done");

        when(llmService.chatWithTools(any()))
                .thenReturn(toolCallResp1)
                .thenReturn(toolCallResp2)
                .thenReturn(toolCallResp3)
                .thenReturn(finalResponse);

        AgentNodeExecutor executor = new AgentNodeExecutor(
                llmService, toolRegistryProvider, eventPersistenceService, eventBus, new ObjectMapper(), toolSelector
        );

        FlowContext context = new FlowContext("inst-budget", "def-1");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("userPrompt", "test budget");
        cfg.put("tools", List.of("filler_tool"));
        cfg.put("outputVariable", "result");
        cfg.put("maxIterations", 10);
        // Set a very small token budget so pruning triggers after accumulating messages
        // The system prompt + tool descriptions alone will use tokens, and adding
        // multiple tool results of 200 chars each will quickly exceed the budget.
        cfg.put("maxTokenBudget", "50"); // very small budget to force pruning
        NodeConfig config = new NodeConfig("agent", cfg, null);

        var result = executor.execute(config, context);

        // Agent should still produce a final result
        assertEquals("done", context.getVariable("result"));

        // AGENT_TOKEN_PRUNE event should have been published at least once
        verify(eventBus, atLeastOnce()).publish(eq("inst-budget"), eq(FlowEventType.AGENT_TOKEN_PRUNE.name()), any());
    }
}
