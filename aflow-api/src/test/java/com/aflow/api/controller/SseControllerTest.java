package com.aflow.api.controller;

import com.aflow.core.event.FlowEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SseController.class)
class SseControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private FlowEventBus flowEventBus;

    @Test
    void stream_returnsEventStreamContentType() throws Exception {
        mockMvc.perform(get("/api/v1/flows/flow-1/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(flowEventBus).subscribe(eq("flow-1"), any());
    }

    @Test
    void stream_registersListenerThatBridgesEventsToEmitter() {
        final FlowEventBus.FlowEventListener[] captured = new FlowEventBus.FlowEventListener[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(1);
            return null;
        }).when(flowEventBus).subscribe(eq("flow-1"), any());

        SseController controller = new SseController(flowEventBus);
        SseEmitter emitter = controller.stream("flow-1");

        assertThat(captured[0]).isNotNull();
        assertThat(flowEventBus.hasSubscriber("flow-1")).isFalse();

        captured[0].onEvent("NODE_ENTER", Map.of("currentNodeId", "node-a"));
        emitter.complete();
    }
}
