package com.aflow.core.engine;

import com.aflow.common.exception.FlowExecutionException;
import com.aflow.common.exception.FlowNotFoundException;
import com.aflow.common.model.*;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.util.JsonUtil;
import com.aflow.core.dsl.DslParser;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventType;
import com.aflow.core.expression.ExpressionEvaluator;
import com.aflow.core.registry.NodeRegistry;
import com.aflow.core.snapshot.SnapshotManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 工作流引擎默认实现。
 * <p>
 * 驱动 DAG 节点执行，管理状态流转、断点、快照、事件和持久化。
 * <p>
 * 执行方式：
 * <ul>
 *   <li><b>异步执行</b>（默认）：{@link #start} 创建上下文后立即返回，流程在虚拟线程中异步执行</li>
 *   <li><b>同步执行</b>：关闭 {@code aflow.engine.async} 后在当前线程中同步执行，主要用于测试场景</li>
 * </ul>
 * <p>
 * SSE 事件推送：引擎在每个节点执行的关键节点通过 {@link FlowEventBus} 推送实时状态，
 * 前端可通过 SSE 连接获取执行进度。
 */
@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowEngine.class);

    private final NodeRegistry nodeRegistry;
    private final DslParser dslParser;
    private final ExpressionEvaluator expressionEvaluator;
    private final SnapshotManager snapshotManager;
    private final EventPersistenceService eventPersistenceService;
    private final FlowDefinitionPersistenceService definitionPersistence;
    private final FlowInstancePersistenceService instancePersistence;
    private final FlowEventBus eventBus;
    private final com.aflow.core.metrics.EngineMetrics engineMetrics;
    /** 虚拟线程执行器，用于异步执行流程（Java 21 虚拟线程，轻量级、高并发） */
    private final Executor virtualThreadExecutor;

    /** Parallel wave executor for DAG-based parallel execution. */
    private final ParallelWaveExecutor parallelWaveExecutor;

    /** 异步执行开关：true（默认）= 异步执行，false = 同步执行（用于测试） */
    private final boolean asyncEnabled;

    /** Default node execution timeout in milliseconds, configurable via aflow.engine.node-timeout-ms */
    private final long defaultNodeTimeoutMs;

    /** In-memory cache of active flow contexts for fast access during execution. */
    private final Cache<String, FlowContext> activeContexts;

    /** In-memory store of parsed definitions cache. */
    private final Map<String, FlowDefinition> definitionCache = new ConcurrentHashMap<>();

    public DefaultWorkflowEngine(NodeRegistry nodeRegistry,
                                   DslParser dslParser,
                                   ExpressionEvaluator expressionEvaluator,
                                   SnapshotManager snapshotManager,
                                   EventPersistenceService eventPersistenceService,
                                   FlowDefinitionPersistenceService definitionPersistence,
                                   FlowInstancePersistenceService instancePersistence,
                                   FlowEventBus eventBus,
                                   com.aflow.core.metrics.EngineMetrics engineMetrics,
                                   @Value("${aflow.engine.context-cache.max-size:10000}") long contextCacheMaxSize,
                                   @Value("${aflow.engine.context-cache.ttl-minutes:30}") long contextCacheTtlMinutes,
                                   @Value("${aflow.engine.async:true}") boolean asyncEnabled,
                                   @Value("${aflow.engine.node-timeout-ms:300000}") long defaultNodeTimeoutMs) {
        this.nodeRegistry = nodeRegistry;
        this.dslParser = dslParser;
        this.expressionEvaluator = expressionEvaluator;
        this.snapshotManager = snapshotManager;
        this.eventPersistenceService = eventPersistenceService;
        this.definitionPersistence = definitionPersistence;
        this.instancePersistence = instancePersistence;
        this.eventBus = eventBus;
        this.engineMetrics = engineMetrics;
        this.asyncEnabled = asyncEnabled;
        this.defaultNodeTimeoutMs = defaultNodeTimeoutMs;
        // 虚拟线程执行器：每个任务启动一个新的虚拟线程，适合 I/O 密集型任务
        this.virtualThreadExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        this.parallelWaveExecutor = new ParallelWaveExecutor(this.virtualThreadExecutor, eventBus, eventPersistenceService);
        this.activeContexts = Caffeine.newBuilder()
                .maximumSize(contextCacheMaxSize)
                .expireAfterAccess(Duration.ofMinutes(contextCacheTtlMinutes))
                .build();
    }

    @Override
    public FlowContext start(String flowDefinitionId, Map<String, Object> inputs) {
        log.info("启动流程（异步）: definitionId={}", flowDefinitionId);

        // 1. 加载并解析流程定义
        FlowDefinition definition = loadDefinition(flowDefinitionId);

        // 2. 创建流程上下文
        String instanceId = UUID.randomUUID().toString();
        FlowContext context = new FlowContext(instanceId, flowDefinitionId);
        context.setStatus(FlowStatus.RUNNING);
        // 注入环境变量（从 FlowDefinition.environment 加载）
        if (definition.getEnvironment() != null) {
            context.setEnvironment(new HashMap<>(definition.getEnvironment()));
        }
        if (inputs != null) {
            context.mergeOutputs(inputs);
        }

        // 3. 持久化初始状态（在返回前确保数据已落库）
        instancePersistence.save(context);
        activeContexts.put(instanceId, context);

        // 4. 发布 FLOW_STARTED 事件（持久化 + SSE 推送）
        eventPersistenceService.saveEvent(instanceId, null, FlowEventType.FLOW_STARTED,
                JsonUtil.toJson(Map.of("definitionId", flowDefinitionId, "inputs", inputs != null ? inputs : Map.of())), 0);
        eventBus.publishNodeEvent(context, FlowEventType.FLOW_STARTED.name(), null, 0);
        engineMetrics.flowStarted();

        // 5. 执行流程（异步或同步，由 aflow.engine.async 配置控制）
        if (asyncEnabled) {
            String asyncInstanceId = instanceId;
            CompletableFuture.runAsync(() -> startAsyncExecution(asyncInstanceId, definition), virtualThreadExecutor);
        } else {
            startAsyncExecution(instanceId, definition);
        }

        return context;
    }

    /**
     * 异步执行流程的内部方法。
     * 在虚拟线程中运行，不阻塞 HTTP 请求线程。执行过程中的异常会被捕获并记录，流程状态自动设为 FAILED。
     *
     * @param instanceId 流程实例 ID
     * @param definition 流程定义
     */
    public void startAsyncExecution(String instanceId, FlowDefinition definition) {
        try {
            FlowContext context = activeContexts.getIfPresent(instanceId);
            if (context == null) {
                log.error("异步执行时找不到上下文: instanceId={}", instanceId);
                return;
            }
            executeFlow(context, definition, false);
        } catch (Exception e) {
            log.error("异步执行流程异常: instanceId={}, error={}", instanceId, e.getMessage(), e);
            FlowContext context = activeContexts.getIfPresent(instanceId);
            if (context != null && context.getStatus() == FlowStatus.RUNNING) {
                failFlow(context, "异步执行异常: " + e.getMessage());
            }
        }
    }

    @Override
    public FlowContext resume(String flowInstanceId, Map<String, Object> additionalInputs) {
        log.info("Resuming flow: instanceId={}", flowInstanceId);

        FlowContext context = getActiveContext(flowInstanceId);
        if (context.getStatus() != FlowStatus.SUSPENDED) {
            throw new FlowExecutionException("Cannot resume flow in status: " + context.getStatus());
        }

        // Merge additional inputs
        if (additionalInputs != null) {
            context.mergeOutputs(additionalInputs);
        }

        context.setStatus(FlowStatus.RUNNING);
        FlowDefinition definition = loadDefinition(context.getFlowDefinitionId());

        instancePersistence.save(context);

        // currentNodeId always means "the next node to execute" while suspended.
        // Skip the breakpoint check once so resuming from a breakpoint does not
        // immediately suspend at the same node again.
        executeFlow(context, definition, false, true);

        return context;
    }

    @Override
    public FlowContext retry(String flowInstanceId, String fromNodeId) {
        log.info("Retrying flow: instanceId={}, fromNodeId={}", flowInstanceId, fromNodeId);

        FlowContext context = getActiveContext(flowInstanceId);
        if (context.getStatus() != FlowStatus.FAILED && context.getStatus() != FlowStatus.SUSPENDED) {
            throw new FlowExecutionException("Cannot retry flow in status: " + context.getStatus());
        }

        context.setStatus(FlowStatus.RUNNING);
        context.setCurrentNodeId(fromNodeId);
        FlowDefinition definition = loadDefinition(context.getFlowDefinitionId());

        eventPersistenceService.saveEvent(flowInstanceId, fromNodeId, FlowEventType.NODE_RETRY, null, 0);

        executeFlow(context, definition, false, true);
        return context;
    }

    @Override
    public FlowContext suspend(String flowInstanceId) {
        log.info("Suspending flow: instanceId={}", flowInstanceId);
        FlowContext context = getActiveContext(flowInstanceId);
        context.setStatus(FlowStatus.SUSPENDED);
        instancePersistence.save(context);
        eventPersistenceService.saveEvent(flowInstanceId, context.getCurrentNodeId(),
                FlowEventType.NODE_SUSPENDED, "{\"reason\":\"manual_suspend\"}", 0);
        eventBus.publishNodeEvent(context, FlowEventType.NODE_SUSPENDED.name(), context.getCurrentNodeId(), 0);
        return context;
    }

    @Override
    public FlowContext cancel(String flowInstanceId) {
        log.info("Cancelling flow: instanceId={}", flowInstanceId);
        FlowContext context = getActiveContext(flowInstanceId);
        context.setStatus(FlowStatus.CANCELLED);
        instancePersistence.save(context);
        activeContexts.invalidate(flowInstanceId);

        eventPersistenceService.saveEvent(flowInstanceId, null, FlowEventType.FLOW_CANCELLED, null, 0);
        eventBus.publishNodeEvent(context, FlowEventType.FLOW_CANCELLED.name(), null, 0);
        eventBus.complete(flowInstanceId);
        engineMetrics.flowEnded();
        return context;
    }

    @Override
    public FlowContext step(String flowInstanceId) {
        log.info("Stepping flow: instanceId={}", flowInstanceId);

        FlowContext context = getActiveContext(flowInstanceId);
        if (context.getStatus() != FlowStatus.SUSPENDED && context.getStatus() != FlowStatus.RUNNING) {
            throw new FlowExecutionException("Cannot step flow in status: " + context.getStatus());
        }

        context.setStatus(FlowStatus.RUNNING);
        context.setDebugMode(true);
        FlowDefinition definition = loadDefinition(context.getFlowDefinitionId());

        // If context has no current node yet (first step after start), find start node
        if (context.getCurrentNodeId() == null) {
            String startNode = findStartNode(definition);
            context.setCurrentNodeId(startNode);
        }

        // Execute exactly one node, then suspend
        executeFlow(context, definition, true, true);
        return context;
    }

    @Override
    public void addBreakpoint(String flowInstanceId, String nodeId) {
        FlowContext context = getActiveContext(flowInstanceId);
        context.getBreakpoints().add(nodeId);
        log.debug("Breakpoint added: flow={}, node={}", flowInstanceId, nodeId);
    }

    @Override
    public void removeBreakpoint(String flowInstanceId, String nodeId) {
        FlowContext context = getActiveContext(flowInstanceId);
        context.getBreakpoints().remove(nodeId);
        log.debug("Breakpoint removed: flow={}, node={}", flowInstanceId, nodeId);
    }

    @Override
    public Set<String> getBreakpoints(String flowInstanceId) {
        FlowContext context = getActiveContext(flowInstanceId);
        return Collections.unmodifiableSet(context.getBreakpoints());
    }

    @Override
    public FlowContext updateContext(String flowInstanceId, Map<String, Object> variables) {
        FlowContext context = getActiveContext(flowInstanceId);
        if (context.getStatus() != FlowStatus.SUSPENDED) {
            throw new FlowExecutionException("Can only update context when flow is SUSPENDED");
        }
        context.mergeOutputs(variables);
        instancePersistence.save(context);
        log.info("Context updated for flow={}, keys={}", flowInstanceId, variables.keySet());
        return context;
    }

    // ─── Core Execution Logic ─────────────────────────────────────────

    /**
     * Execute the flow DAG from the current node until completion, suspension, or failure.
     *
     * @param context     the flow context
     * @param definition  the flow definition
     * @param singleStep  if true, execute exactly one node then suspend
     */
    private void executeFlow(FlowContext context, FlowDefinition definition, boolean singleStep) {
        executeFlow(context, definition, singleStep, false);
    }

    private void executeFlow(FlowContext context, FlowDefinition definition, boolean singleStep,
                             boolean ignoreBreakpointAtCurrentNode) {
        String currentNodeId = context.getCurrentNodeId();

        // If no current node, find the start node
        if (currentNodeId == null) {
            currentNodeId = findStartNode(definition);
            context.setCurrentNodeId(currentNodeId);
        }

        // For single-step or resuming from a specific node, use sequential execution
        if (singleStep || ignoreBreakpointAtCurrentNode) {
            executeFlowSequential(context, definition, singleStep, ignoreBreakpointAtCurrentNode);
            return;
        }

        // Wave-based parallel execution for full DAG traversal
        executeFlowWithWaves(context, definition);
    }

    /**
     * Execute the flow using wave-based parallel decomposition.
     * Nodes in the same wave are executed in parallel; waves are executed sequentially.
     */
    private void executeFlowWithWaves(FlowContext context, FlowDefinition definition) {
        List<Set<String>> waves = parallelWaveExecutor.computeWaves(definition);
        ParallelStrategy strategy = definition.getParallelStrategy();
        String currentNodeId = context.getCurrentNodeId();

        // Find the wave index that contains the current node (for resume scenarios)
        int startWaveIndex = 0;
        if (currentNodeId != null) {
            for (int i = 0; i < waves.size(); i++) {
                if (waves.get(i).contains(currentNodeId)) {
                    startWaveIndex = i;
                    break;
                }
            }
        }

        for (int waveIdx = startWaveIndex; waveIdx < waves.size(); waveIdx++) {
            Set<String> wave = waves.get(waveIdx);

            // Filter out already-executed nodes (for resume scenario)
            Set<String> toExecute = wave.stream()
                    .filter(nodeId -> !context.getExecutionPath().contains(nodeId))
                    .collect(Collectors.toSet());

            if (toExecute.isEmpty()) {
                continue;
            }

            if (toExecute.size() == 1) {
                // Single node in wave — use sequential execution with full features (breakpoints, error edges, etc.)
                String nodeId = toExecute.iterator().next();
                NodeDefinition nodeDef = findNodeDefinition(nodeId, definition);
                if (nodeDef == null) {
                    failFlow(context, "Node not found in definition: " + nodeId);
                    return;
                }

                // Check breakpoint
                if (context.hasBreakpoint(nodeId) || nodeDef.isBreakpoint()) {
                    log.info("Breakpoint hit: flow={}, node={}", context.getFlowInstanceId(), nodeId);
                    context.setStatus(FlowStatus.SUSPENDED);
                    context.setCurrentNodeId(nodeId);
                    instancePersistence.save(context);
                    eventPersistenceService.saveEvent(context.getFlowInstanceId(), nodeId,
                            FlowEventType.NODE_SUSPENDED, "{\"reason\":\"breakpoint\"}", 0);
                    eventBus.publishNodeEvent(context, FlowEventType.NODE_SUSPENDED.name(), nodeId, 0);
                    return;
                }

                NodeResult result = executeNode(context, nodeDef);
                if (result == null) {
                    failFlow(context, "Node executor returned null for node: " + nodeId);
                    return;
                }

                switch (result.status()) {
                    case SUCCESS -> {
                        if (result.outputs() != null) {
                            if (nodeDef.getOutput() != null && !nodeDef.getOutput().isBlank()) {
                                context.putVariable(nodeDef.getOutput(), result.outputs());
                            }
                            context.mergeOutputs(result.outputs());
                        }
                        context.setCurrentNodeId(null);
                        instancePersistence.save(context);
                    }
                    case SUSPENDED -> {
                        context.setStatus(FlowStatus.SUSPENDED);
                        context.setCurrentNodeId(nodeId);
                        instancePersistence.save(context);
                        eventPersistenceService.saveEvent(context.getFlowInstanceId(), nodeId,
                                FlowEventType.NODE_SUSPENDED, "{\"reason\":\"node_suspended\"}", 0);
                        eventBus.publishNodeEvent(context, FlowEventType.NODE_SUSPENDED.name(), nodeId, 0);
                        return;
                    }
                    case FAILED -> {
                        String errorMessage = result.errorMessage() != null ? result.errorMessage() : "Node execution failed: " + nodeId;
                        String errorNextNode = determineErrorNextNode(nodeId, definition, context, errorMessage);
                        if (errorNextNode != null) {
                            log.info("Error edge taken: flow={}, failedNode={}, errorTarget={}",
                                    context.getFlowInstanceId(), nodeId, errorNextNode);
                            context.putVariable("_lastError", errorMessage);
                            context.putVariable("_lastErrorNodeId", nodeId);
                            // Continue from error node using sequential fallback
                            context.setCurrentNodeId(errorNextNode);
                            instancePersistence.save(context);
                            executeFlowSequential(context, definition, false, true);
                            return;
                        } else {
                            failFlow(context, errorMessage);
                            return;
                        }
                    }
                }
            } else {
                // Multiple nodes in wave — execute in parallel
                ParallelWaveExecutor.WaveExecutionResult waveResult = parallelWaveExecutor.executeWaveParallel(
                        toExecute, context, definition, strategy,
                        (ctx, nodeDef) -> executeNode(ctx, nodeDef)
                );

                // Merge successful results into context
                for (Map.Entry<String, NodeResult> entry : waveResult.results().entrySet()) {
                    String nodeId = entry.getKey();
                    NodeResult result = entry.getValue();
                    if (result.status() == ResultStatus.SUCCESS && result.outputs() != null) {
                        NodeDefinition nodeDef = findNodeDefinition(nodeId, definition);
                        if (nodeDef != null && nodeDef.getOutput() != null && !nodeDef.getOutput().isBlank()) {
                            context.putVariable(nodeDef.getOutput(), result.outputs());
                        }
                        context.mergeOutputs(result.outputs());
                    }
                }

                instancePersistence.save(context);

                // Handle wave failure
                if (!waveResult.success()) {
                    failFlow(context, waveResult.errorMessage());
                    return;
                }
            }
        }

        // All waves completed — flow done
        completeFlow(context);
    }

    /**
     * Sequential execution logic — used for single-step, breakpoint resume, and error-edge fallback.
     * This preserves the original sequential node-by-node traversal behavior.
     */
    private void executeFlowSequential(FlowContext context, FlowDefinition definition, boolean singleStep,
                                        boolean ignoreBreakpointAtCurrentNode) {
        String currentNodeId = context.getCurrentNodeId();

        // If no current node, find the start node
        if (currentNodeId == null) {
            currentNodeId = findStartNode(definition);
            context.setCurrentNodeId(currentNodeId);
        }

        String initialNodeId = currentNodeId;

        while (currentNodeId != null) {
            NodeDefinition nodeDef = findNodeDefinition(currentNodeId, definition);
            if (nodeDef == null) {
                failFlow(context, "Node not found in definition: " + currentNodeId);
                return;
            }

            // Check breakpoint (only if not the first node being stepped into)
            boolean isBreakpoint = context.hasBreakpoint(currentNodeId) || nodeDef.isBreakpoint();
            boolean skipBreakpoint = ignoreBreakpointAtCurrentNode && Objects.equals(currentNodeId, initialNodeId);
            if (isBreakpoint && !skipBreakpoint) {
                log.info("Breakpoint hit: flow={}, node={}", context.getFlowInstanceId(), currentNodeId);
                context.setStatus(FlowStatus.SUSPENDED);
                context.setCurrentNodeId(currentNodeId);
                instancePersistence.save(context);
                eventPersistenceService.saveEvent(context.getFlowInstanceId(), currentNodeId,
                        FlowEventType.NODE_SUSPENDED, "{\"reason\":\"breakpoint\"}", 0);
                eventBus.publishNodeEvent(context, FlowEventType.NODE_SUSPENDED.name(), currentNodeId, 0);
                return;
            }

            // Execute the node
            NodeResult result = executeNode(context, nodeDef);

            if (result == null) {
                failFlow(context, "Node executor returned null for node: " + currentNodeId);
                return;
            }

            switch (result.status()) {
                case SUCCESS -> {
                    // Merge outputs
                    if (result.outputs() != null) {
                        // If node has an output variable name, store under that key
                        if (nodeDef.getOutput() != null && !nodeDef.getOutput().isBlank()) {
                            context.putVariable(nodeDef.getOutput(), result.outputs());
                        }
                        context.mergeOutputs(result.outputs());
                    }

                    // Determine next node
                    String nextNodeId;
                    if (result.nextNodeId() != null) {
                        // Explicit routing (from condition nodes)
                        nextNodeId = result.nextNodeId();
                    } else {
                        nextNodeId = determineNextNode(currentNodeId, definition, context);
                    }

                    context.setCurrentNodeId(nextNodeId);
                    instancePersistence.save(context);

                    if (singleStep) {
                        // Single step mode: suspend after one node
                        if (nextNodeId != null) {
                            context.setStatus(FlowStatus.SUSPENDED);
                            instancePersistence.save(context);
                        } else {
                            completeFlow(context);
                        }
                        return;
                    }

                    currentNodeId = nextNodeId;
                }
                case SUSPENDED -> {
                    context.setStatus(FlowStatus.SUSPENDED);
                    context.setCurrentNodeId(currentNodeId);
                    instancePersistence.save(context);
                    eventPersistenceService.saveEvent(context.getFlowInstanceId(), currentNodeId,
                            FlowEventType.NODE_SUSPENDED, "{\"reason\":\"node_suspended\"}", 0);
                    eventBus.publishNodeEvent(context, FlowEventType.NODE_SUSPENDED.name(), currentNodeId, 0);
                    return;
                }
                case FAILED -> {
                    // Try-Catch semantics: check for error edges before failing the flow
                    String errorMessage = result.errorMessage() != null ? result.errorMessage() : "Node execution failed: " + currentNodeId;
                    String errorNextNode = determineErrorNextNode(currentNodeId, definition, context, errorMessage);

                    if (errorNextNode != null) {
                        // Error edge found — write error info to context and continue on the error path
                        log.info("Error edge taken: flow={}, failedNode={}, errorTarget={}",
                                context.getFlowInstanceId(), currentNodeId, errorNextNode);
                        context.putVariable("_lastError", errorMessage);
                        context.putVariable("_lastErrorNodeId", currentNodeId);
                        context.setCurrentNodeId(errorNextNode);
                        instancePersistence.save(context);

                        if (singleStep) {
                            context.setStatus(FlowStatus.SUSPENDED);
                            instancePersistence.save(context);
                            return;
                        }

                        currentNodeId = errorNextNode;
                    } else {
                        // No error edge — fail the flow as before
                        failFlow(context, errorMessage);
                        return;
                    }
                }
            }
        }

        // No more nodes — flow completed
        completeFlow(context);
    }

    /**
     * Execute a single node: snapshot before, execute with timeout and retry, snapshot after, publish events.
     */
    private NodeResult executeNode(FlowContext context, NodeDefinition nodeDef) {
        String flowInstanceId = context.getFlowInstanceId();
        String nodeId = nodeDef.getId();
        long startTime = System.currentTimeMillis();

        log.info("Executing node: flow={}, node={}, type={}", flowInstanceId, nodeId, nodeDef.getType());

        // Record execution path
        context.recordNodeExecution(nodeId);

        // Snapshot BEFORE
        snapshotManager.saveSnapshot(flowInstanceId, nodeId, SnapshotPhase.BEFORE, context);

        // Publish NODE_ENTER event
        eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_ENTER,
                JsonUtil.toJson(Map.of("type", nodeDef.getType())), 0);
        // SSE 推送：节点开始执行
        eventBus.publishNodeEvent(context, FlowEventType.NODE_ENTER.name(), nodeId, 0);

        try {
            // Get executor
            NodeExecutor executor = nodeRegistry.getExecutor(nodeDef.getType());

            // Build NodeConfig
            NodeConfig config = new NodeConfig();
            config.setType(nodeDef.getType());
            config.setConfig(nodeDef.getConfig() != null ? nodeDef.getConfig() : Map.of());
            config.setOutputVariable(nodeDef.getOutput());

            // Resolve timeout and retry policies from node definition
            NodeTimeoutPolicy timeoutPolicy = NodeTimeoutPolicy.fromNodeDef(nodeDef, defaultNodeTimeoutMs);
            RetryPolicy retryPolicy = RetryPolicy.fromNodeDef(nodeDef);
            long timeoutMs = timeoutPolicy.timeoutMs();

            // Execute with retry loop
            NodeResult result = null;
            for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
                if (attempt > 1) {
                    // Compute backoff and sleep before retry
                    long backoffMs = retryPolicy.computeBackoff(attempt - 1);
                    log.info("Retrying node: flow={}, node={}, attempt={}/{}, backoff={}ms",
                            flowInstanceId, nodeId, attempt, retryPolicy.maxAttempts(), backoffMs);

                    // Publish NODE_RETRY event
                    eventBus.publish(flowInstanceId, FlowEventType.NODE_RETRY.name(), Map.of(
                            "nodeId", nodeId,
                            "attempt", attempt,
                            "maxAttempts", retryPolicy.maxAttempts(),
                            "backoffMs", backoffMs
                    ));

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        result = NodeResult.failed("Node retry interrupted");
                        break;
                    }

                    // Take snapshot before retry attempt
                    snapshotManager.saveSnapshot(flowInstanceId, nodeId, SnapshotPhase.BEFORE, context);
                }

                try {
                    result = CompletableFuture.supplyAsync(
                            () -> executor.execute(config, context), virtualThreadExecutor
                    ).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
                } catch (CompletionException e) {
                    if (e.getCause() instanceof TimeoutException) {
                        long elapsedMs = System.currentTimeMillis() - startTime;
                        log.warn("Node execution timed out: flow={}, node={}, timeout={}ms, elapsed={}ms",
                                flowInstanceId, nodeId, timeoutMs, elapsedMs);

                        // Publish NODE_TIMEOUT event
                        eventBus.publish(flowInstanceId, FlowEventType.NODE_TIMEOUT.name(), Map.of(
                                "nodeId", nodeId,
                                "timeoutMs", timeoutMs,
                                "elapsedMs", elapsedMs
                        ));

                        result = NodeResult.failed("Node execution timed out after " + timeoutMs + "ms");
                    } else {
                        result = NodeResult.failed("Node execution exception: " + e.getCause().getMessage());
                    }
                }

                // Break on non-FAILED result or if this was the last attempt
                if (result.status() != ResultStatus.FAILED || attempt == retryPolicy.maxAttempts()) {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            // Snapshot AFTER
            snapshotManager.saveSnapshot(flowInstanceId, nodeId, SnapshotPhase.AFTER, context);

            // Publish NODE_EXIT or NODE_ERROR event
            if (result.status() == ResultStatus.SUCCESS) {
                eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_EXIT,
                        JsonUtil.toJson(Map.of("outputKeys", result.outputs() != null ? result.outputs().keySet() : Set.of())),
                        duration);
                // SSE 推送：节点执行完成
                eventBus.publishNodeEvent(context, FlowEventType.NODE_EXIT.name(), nodeId, duration);
            } else if (result.status() == ResultStatus.FAILED) {
                eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_ERROR,
                        JsonUtil.toJson(Map.of("error", result.errorMessage() != null ? result.errorMessage() : "unknown")),
                        duration);
                // SSE 推送：节点执行错误
                eventBus.publishNodeEvent(context, FlowEventType.NODE_ERROR.name(), nodeId, duration);
            }

            log.info("Node completed: flow={}, node={}, status={}, duration={}ms",
                    flowInstanceId, nodeId, result.status(), duration);

            // Record metrics
            engineMetrics.recordNodeExecution(nodeDef.getType(), result.status().name(), duration);

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Node execution failed: flow={}, node={}, error={}", flowInstanceId, nodeId, e.getMessage(), e);

            eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_ERROR,
                    JsonUtil.toJson(Map.of("error", e.getMessage())), duration);

            return NodeResult.failed("Node execution exception: " + e.getMessage());
        }
    }

    // ─── DAG Navigation ───────────────────────────────────────────────

    /**
     * Find the start node (node with no incoming edges).
     */
    private String findStartNode(FlowDefinition definition) {
        Set<String> nodesWithIncoming = new HashSet<>();
        if (definition.getEdges() != null) {
            definition.getEdges().forEach(e -> nodesWithIncoming.add(e.getTo()));
        }

        return definition.getNodes().stream()
                .map(NodeDefinition::getId)
                .filter(id -> !nodesWithIncoming.contains(id))
                .findFirst()
                .orElseThrow(() -> new FlowExecutionException("No start node found in flow definition"));
    }

    /**
     * Determine the next node to execute after the current node.
     * Handles conditional edges by evaluating SpEL expressions.
     * Only considers normal edges (not error edges).
     */
    private String determineNextNode(String currentNodeId, FlowDefinition definition, FlowContext context) {
        if (definition.getEdges() == null) {
            return null;
        }

        List<EdgeDefinition> outgoingEdges = definition.getEdges().stream()
                .filter(e -> e.getFrom().equals(currentNodeId))
                .filter(EdgeDefinition::isNormalEdge)
                .toList();

        if (outgoingEdges.isEmpty()) {
            return null; // Terminal node
        }

        // If only one edge with no condition, follow it
        if (outgoingEdges.size() == 1 && (outgoingEdges.getFirst().getCondition() == null
                || outgoingEdges.getFirst().getCondition().isBlank())) {
            return outgoingEdges.getFirst().getTo();
        }

        // Multiple edges or conditional edges — evaluate conditions
        for (EdgeDefinition edge : outgoingEdges) {
            if (edge.getCondition() == null || edge.getCondition().isBlank()) {
                // Default edge (no condition) — used as fallback
                continue;
            }
            try {
                Boolean result = expressionEvaluator.evaluate(edge.getCondition(), context, Boolean.class);
                if (Boolean.TRUE.equals(result)) {
                    return edge.getTo();
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate edge condition '{}': {}", edge.getCondition(), e.getMessage());
            }
        }

        // Fallback: return the first edge without a condition (default path)
        return outgoingEdges.stream()
                .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                .map(EdgeDefinition::getTo)
                .findFirst()
                .orElse(null);
    }

    /**
     * Determine the next node via error edges when a node fails.
     * Supports conditional error edges (e.g., route based on error type).
     * The error message is available as #_lastError in SpEL conditions.
     *
     * @return the target node ID if an error edge matches, or null if no error handling path exists
     */
    private String determineErrorNextNode(String currentNodeId, FlowDefinition definition,
                                          FlowContext context, String errorMessage) {
        if (definition.getEdges() == null) {
            return null;
        }

        List<EdgeDefinition> errorEdges = definition.getEdges().stream()
                .filter(e -> e.getFrom().equals(currentNodeId))
                .filter(EdgeDefinition::isErrorEdge)
                .toList();

        if (errorEdges.isEmpty()) {
            return null;
        }

        // Temporarily expose error info for condition evaluation
        context.putVariable("_lastError", errorMessage);

        // If only one error edge with no condition, follow it
        if (errorEdges.size() == 1 && (errorEdges.getFirst().getCondition() == null
                || errorEdges.getFirst().getCondition().isBlank())) {
            return errorEdges.getFirst().getTo();
        }

        // Multiple error edges or conditional — evaluate conditions
        for (EdgeDefinition edge : errorEdges) {
            if (edge.getCondition() == null || edge.getCondition().isBlank()) {
                continue;
            }
            try {
                Boolean result = expressionEvaluator.evaluate(edge.getCondition(), context, Boolean.class);
                if (Boolean.TRUE.equals(result)) {
                    return edge.getTo();
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate error edge condition '{}': {}", edge.getCondition(), e.getMessage());
            }
        }

        // Fallback: return the first error edge without a condition
        return errorEdges.stream()
                .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                .map(EdgeDefinition::getTo)
                .findFirst()
                .orElse(null);
    }

    private NodeDefinition findNodeDefinition(String nodeId, FlowDefinition definition) {
        return definition.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    // ─── Flow Lifecycle Helpers ────────────────────────────────────────

    private void completeFlow(FlowContext context) {
        context.setStatus(FlowStatus.COMPLETED);
        context.setCurrentNodeId(null);
        instancePersistence.save(context);
        activeContexts.invalidate(context.getFlowInstanceId());

        eventPersistenceService.saveEvent(context.getFlowInstanceId(), null,
                FlowEventType.FLOW_COMPLETED, null, 0);
        // SSE 推送：流程执行完成，并关闭 SSE 连接
        eventBus.publishNodeEvent(context, FlowEventType.FLOW_COMPLETED.name(), null, 0);
        eventBus.complete(context.getFlowInstanceId());
        engineMetrics.flowEnded();

        log.info("流程执行完成: instanceId={}", context.getFlowInstanceId());
    }

    private void failFlow(FlowContext context, String errorMessage) {
        context.setStatus(FlowStatus.FAILED);
        instancePersistence.save(context);
        activeContexts.invalidate(context.getFlowInstanceId());

        eventPersistenceService.saveEvent(context.getFlowInstanceId(), context.getCurrentNodeId(),
                FlowEventType.FLOW_FAILED, JsonUtil.toJson(Map.of("error", errorMessage)), 0);
        // SSE 推送：流程执行失败，并关闭 SSE 连接
        eventBus.publishNodeEvent(context, FlowEventType.FLOW_FAILED.name(), context.getCurrentNodeId(), 0);
        eventBus.complete(context.getFlowInstanceId());
        engineMetrics.flowEnded();

        log.error("流程执行失败: instanceId={}, error={}", context.getFlowInstanceId(), errorMessage);
    }

    private FlowContext getActiveContext(String flowInstanceId) {
        FlowContext context = activeContexts.getIfPresent(flowInstanceId);
        if (context == null) {
            // Try loading from database
            context = instancePersistence.findById(flowInstanceId)
                    .orElseThrow(() -> new FlowNotFoundException("Flow instance not found: " + flowInstanceId));
            activeContexts.put(flowInstanceId, context);
        }
        return context;
    }

    private FlowDefinition loadDefinition(String flowDefinitionId) {
        return definitionCache.computeIfAbsent(flowDefinitionId, id -> {
            String dslContent = definitionPersistence.findDslContentById(id);
            if (dslContent == null) {
                throw new FlowNotFoundException("Flow definition not found: " + id);
            }
            FlowDefinition def = dslParser.parse(dslContent);
            def.setId(id);
            return def;
        });
    }

    @Override
    public void invalidateDefinitionCache(String definitionId) {
        definitionCache.remove(definitionId);
        log.info("Definition cache invalidated: definitionId={}", definitionId);
    }
}
