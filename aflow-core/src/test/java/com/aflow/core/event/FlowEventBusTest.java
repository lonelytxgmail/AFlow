package com.aflow.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowEventBusTest {

    @Test
    void subscribePublishAndUnsubscribe_works() {
        FlowEventBus bus = new FlowEventBus();
        List<String> received = new ArrayList<>();

        FlowEventBus.FlowEventListener listener = (eventName, data) ->
                received.add(eventName + ":" + ((Map<?, ?>) data).get("status"));

        bus.subscribe("inst-1", listener);
        assertTrue(bus.hasSubscriber("inst-1"));

        bus.publish("inst-1", "NODE_ENTER", Map.of("status", "RUNNING"));
        assertEquals(List.of("NODE_ENTER:RUNNING"), received);

        bus.unsubscribe("inst-1", listener);
        assertFalse(bus.hasSubscriber("inst-1"));
    }

    @Test
    void complete_removesAllListeners() {
        FlowEventBus bus = new FlowEventBus();
        bus.subscribe("inst-1", (eventName, data) -> {});
        bus.subscribe("inst-1", (eventName, data) -> {});

        assertTrue(bus.hasSubscriber("inst-1"));
        bus.complete("inst-1");
        assertFalse(bus.hasSubscriber("inst-1"));
    }
}
