package com.aflow.components.delay;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

@FlowNode(type = "delay", name = "Delay", description = "Wait for specified duration")
public class DelayNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DelayNodeExecutor.class);

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        String durationStr = String.valueOf(config.getConfig().getOrDefault("duration", "PT1S"));
        try {
            Duration duration = Duration.parse(durationStr);
            log.info("Delaying for {}", duration);
            Thread.sleep(duration.toMillis());
            return NodeResult.success(Map.of("delayed", durationStr));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeResult.failed("Delay interrupted");
        } catch (Exception e) {
            return NodeResult.failed("Invalid duration format: " + durationStr);
        }
    }
}
