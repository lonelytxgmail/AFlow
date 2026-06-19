package com.aflow.core.registry;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.exception.UnknownNodeTypeException;
import com.aflow.common.executor.NodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for all available node executors.
 * <p>
 * On application startup, discovers all beans annotated with {@link FlowNode}
 * that implement {@link NodeExecutor} and indexes them by type.
 * The workflow engine uses this registry to resolve the correct executor
 * for each node during flow execution.
 */
@Component
public class NodeRegistry implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistry.class);

    private final Map<String, NodeExecutor> executors = new ConcurrentHashMap<>();
    private final Map<String, FlowNode> metadata = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> beans = ctx.getBeansWithAnnotation(FlowNode.class);
        beans.forEach((beanName, bean) -> {
            if (bean instanceof NodeExecutor executor) {
                FlowNode annotation = bean.getClass().getAnnotation(FlowNode.class);
                if (annotation != null) {
                    String type = annotation.type();
                    if (executors.containsKey(type)) {
                        log.warn("Duplicate node executor registration for type '{}'. " +
                                "Bean '{}' will overwrite the previous registration.", type, beanName);
                    }
                    executors.put(type, executor);
                    metadata.put(type, annotation);
                    log.debug("Registered node executor: type='{}', bean='{}'", type, beanName);
                }
            } else {
                log.warn("Bean '{}' is annotated with @FlowNode but does not implement NodeExecutor. Skipping.", beanName);
            }
        });
        log.info("Registered {} node executors: {}", executors.size(), executors.keySet());
    }

    /**
     * Get the executor for the given node type.
     *
     * @param type the node type identifier
     * @return the registered executor
     * @throws UnknownNodeTypeException if no executor is registered for the type
     */
    public NodeExecutor getExecutor(String type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new UnknownNodeTypeException(type);
        }
        return executor;
    }

    /**
     * Get metadata for all registered node types.
     */
    public Map<String, FlowNode> getAllMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Check whether an executor is registered for the given type.
     */
    public boolean hasExecutor(String type) {
        return executors.containsKey(type);
    }

    /**
     * Get the number of registered executors.
     */
    public int size() {
        return executors.size();
    }
}
