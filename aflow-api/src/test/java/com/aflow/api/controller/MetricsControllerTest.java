package com.aflow.api.controller;

import com.aflow.api.dto.MetricsSummaryDto;
import com.aflow.api.dto.MetricsSummaryDto.AgentMetricsSummary;
import com.aflow.api.dto.MetricsSummaryDto.LlmMetricsSummary;
import com.aflow.api.dto.MetricsSummaryDto.NodeDurationSummary;
import com.aflow.api.dto.MetricsSummaryDto.ToolUsage;
import com.aflow.api.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private MetricsService metricsService;

    @Test
    void summary_returnsAggregatedMetrics() throws Exception {
        MetricsSummaryDto dto = new MetricsSummaryDto(
                5,
                42,
                0.95,
                2300.0,
                new AgentMetricsSummary(150000, 3.5, List.of(new ToolUsage("http", 20))),
                new LlmMetricsSummary(120, 800.0, 0.05),
                List.of(new NodeDurationSummary("agent", 5000.0, 12000.0, 30))
        );
        when(metricsService.buildSummary()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeFlows").value(5))
                .andExpect(jsonPath("$.data.todayExecutions").value(42))
                .andExpect(jsonPath("$.data.successRate").value(0.95))
                .andExpect(jsonPath("$.data.avgDurationMs").value(2300.0))
                .andExpect(jsonPath("$.data.agent.totalTokens").value(150000))
                .andExpect(jsonPath("$.data.agent.avgIterations").value(3.5))
                .andExpect(jsonPath("$.data.agent.topTools[0].tool").value("http"))
                .andExpect(jsonPath("$.data.agent.topTools[0].count").value(20))
                .andExpect(jsonPath("$.data.llm.totalCalls").value(120))
                .andExpect(jsonPath("$.data.llm.p50LatencyMs").value(800.0))
                .andExpect(jsonPath("$.data.llm.retryRate").value(0.05))
                .andExpect(jsonPath("$.data.nodeDurations[0].nodeType").value("agent"))
                .andExpect(jsonPath("$.data.nodeDurations[0].avgDurationMs").value(5000.0))
                .andExpect(jsonPath("$.data.nodeDurations[0].p95DurationMs").value(12000.0))
                .andExpect(jsonPath("$.data.nodeDurations[0].count").value(30));
    }

    @Test
    void summary_emptyMetrics_returnsDefaults() throws Exception {
        MetricsSummaryDto dto = new MetricsSummaryDto(
                0, 0, 1.0, 0.0,
                new AgentMetricsSummary(0, 0.0, List.of()),
                new LlmMetricsSummary(0, 0.0, 0.0),
                List.of()
        );
        when(metricsService.buildSummary()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeFlows").value(0))
                .andExpect(jsonPath("$.data.todayExecutions").value(0))
                .andExpect(jsonPath("$.data.successRate").value(1.0))
                .andExpect(jsonPath("$.data.nodeDurations").isEmpty());
    }
}
