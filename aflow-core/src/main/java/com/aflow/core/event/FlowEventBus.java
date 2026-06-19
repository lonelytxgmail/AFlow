package com.aflow.core.event;

import com.aflow.common.model.FlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 流程执行事件总线。
 * <p>
 * 引擎在执行过程中通过此总线推送实时事件，SSE 控制器等外部监听者可通过回调接口订阅。
 * <p>
 * <b>设计说明：</b> 此类位于 aflow-core 模块，不依赖 spring-webmvc（SseEmitter），
 * 使用通用的 {@link BiConsumer} 回调模式。SSE 相关的桥接逻辑在 aflow-api 的 SseController 中实现。
 * <p>
 * <b>事件类型：</b>
 * <ul>
 *   <li>NODE_ENTER — 节点开始执行</li>
 *   <li>NODE_EXIT — 节点执行完成</li>
 *   <li>NODE_ERROR — 节点执行失败</li>
 *   <li>FLOW_STARTED — 流程启动</li>
 *   <li>FLOW_COMPLETED — 流程完成</li>
 *   <li>FLOW_FAILED — 流程失败</li>
 * </ul>
 */
@Component
public class FlowEventBus {

    private static final Logger log = LoggerFactory.getLogger(FlowEventBus.class);

    /**
     * 流程实例事件监听器：回调参数为 (eventName, eventData)
     */
    @FunctionalInterface
    public interface FlowEventListener {
        void onEvent(String eventName, Object data);
    }

    /** 每个流程实例 ID 对应一组监听器 */
    private final Map<String, CopyOnWriteArrayList<FlowEventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 为指定流程实例注册事件监听器。
     *
     * @param flowInstanceId 流程实例 ID
     * @param listener       事件监听器
     */
    public void subscribe(String flowInstanceId, FlowEventListener listener) {
        listeners.computeIfAbsent(flowInstanceId, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("事件监听器注册: flowId={}, listenerCount={}", flowInstanceId, listeners.get(flowInstanceId).size());
    }

    /**
     * 移除指定流程实例的事件监听器。
     *
     * @param flowInstanceId 流程实例 ID
     * @param listener       要移除的监听器
     */
    public void unsubscribe(String flowInstanceId, FlowEventListener listener) {
        CopyOnWriteArrayList<FlowEventListener> list = listeners.get(flowInstanceId);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(flowInstanceId);
            }
        }
    }

    /**
     * 向指定流程实例的所有监听器推送事件。
     *
     * @param flowInstanceId 流程实例 ID
     * @param eventName      事件名称
     * @param data           事件数据
     */
    public void publish(String flowInstanceId, String eventName, Object data) {
        CopyOnWriteArrayList<FlowEventListener> list = listeners.get(flowInstanceId);
        if (list == null || list.isEmpty()) return;

        for (FlowEventListener listener : list) {
            try {
                listener.onEvent(eventName, data);
            } catch (Exception e) {
                log.debug("事件监听器回调异常: flowId={}, event={}", flowInstanceId, eventName, e);
            }
        }
    }

    /**
     * 推送节点执行状态更新事件。
     * 事件数据包含当前上下文的关键信息（状态、当前节点、执行路径、变量等）。
     *
     * @param context   当前流程上下文
     * @param eventName 事件名称
     * @param nodeId    当前节点 ID（可为 null）
     * @param duration  执行耗时（毫秒，可为 0）
     */
    public void publishNodeEvent(FlowContext context, String eventName, String nodeId, long duration) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("flowInstanceId", context.getFlowInstanceId());
        eventData.put("status", context.getStatus().name());
        eventData.put("currentNodeId", nodeId != null ? nodeId : "");
        eventData.put("executionPath", context.getExecutionPath());
        eventData.put("duration", duration);
        eventData.put("variables", context.getVariables());
        // 如果 metadata 中有 traceId，将其传递到事件中供前端关联
        Object traceId = context.getMetadata().get("traceId");
        if (traceId != null) {
            eventData.put("traceId", traceId);
        }
        publish(context.getFlowInstanceId(), eventName, eventData);
    }

    /**
     * 完成指定流程实例的事件推送（关闭所有监听器）。
     * 流程结束（完成/失败/取消）时调用，通知所有监听者流程已终止。
     *
     * @param flowInstanceId 流程实例 ID
     */
    public void complete(String flowInstanceId) {
        CopyOnWriteArrayList<FlowEventListener> list = listeners.remove(flowInstanceId);
        if (list != null) {
            log.debug("事件监听器清理完成: flowId={}, count={}", flowInstanceId, list.size());
        }
    }

    /**
     * 检查指定流程实例是否有活跃的事件监听器。
     *
     * @param flowInstanceId 流程实例 ID
     * @return 是否有监听器
     */
    public boolean hasSubscriber(String flowInstanceId) {
        CopyOnWriteArrayList<FlowEventListener> list = listeners.get(flowInstanceId);
        return list != null && !list.isEmpty();
    }
}
