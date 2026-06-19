package com.aflow.components.callback;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FlowNode(type = "callback", name = "Callback Wait", description = "Suspend and wait for external callback")
public class CallbackNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CallbackNodeExecutor.class);

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        String callbackKey = config.getConfig().get("callbackKey") != null
                ? String.valueOf(config.getConfig().get("callbackKey"))
                : context.getFlowInstanceId();
        log.info("Suspending flow for callback, key={}", callbackKey);
        return NodeResult.suspended();
    }
}
