package com.aflow.api.controller;

import com.aflow.core.event.FlowEventBus;
import com.aflow.core.event.FlowEventBus.FlowEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE (Server-Sent Events) 控制器。
 * <p>
 * 提供流程执行状态的实时推送接口，前端通过 EventSource 连接此接口，
 * 即可实时接收节点执行进度、状态变更、错误等事件。
 * <p>
 * <b>前后端交互示例：</b>
 * <pre>{@code
 * // 前端 JavaScript
 * const eventSource = new EventSource('/api/v1/flows/' + flowId + '/stream');
 * eventSource.addEventListener('NODE_ENTER', (e) => {
 *   const data = JSON.parse(e.data);
 *   console.log('节点开始执行:', data.currentNodeId);
 * });
 * eventSource.addEventListener('FLOW_COMPLETED', (e) => {
 *   eventSource.close();
 * });
 * }</pre>
 * <p>
 * <b>实现说明：</b>
 * SSE 连接由 {@link SseEmitter} 驱动，通过 {@link FlowEventBus} 注册回调监听器。
 * 当引擎推送事件时，监听器将事件转发给 SseEmitter，实现服务端到客户端的实时推送。
 * 流程结束时自动关闭 SSE 连接并清理监听器。
 */
@RestController
@RequestMapping("/api/v1/flows")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    /** SSE 超时时间：30 分钟（覆盖长时间运行的 Agent 流程） */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final FlowEventBus flowEventBus;

    public SseController(FlowEventBus flowEventBus) {
        this.flowEventBus = flowEventBus;
    }

    /**
     * 订阅指定流程实例的实时执行事件。
     * <p>
     * 返回 SSE 流，客户端通过 EventSource 接收以下事件：
     * <ul>
     *   <li><b>FLOW_STARTED</b> — 流程已启动</li>
     *   <li><b>NODE_ENTER</b> — 节点开始执行（含 currentNodeId、variables）</li>
     *   <li><b>NODE_EXIT</b> — 节点执行完成（含 duration、variables）</li>
     *   <li><b>NODE_ERROR</b> — 节点执行失败</li>
     *   <li><b>FLOW_COMPLETED</b> — 流程执行完成（连接将自动关闭）</li>
     *   <li><b>FLOW_FAILED</b> — 流程执行失败（连接将自动关闭）</li>
     * </ul>
     *
     * @param id 流程实例 ID
     * @return SseEmitter SSE 事件流
     */
    @GetMapping(value = "/{id}/stream", produces = "text/event-stream")
    public SseEmitter stream(@PathVariable String id) {
        log.info("SSE 连接请求: flowId={}", id);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 创建事件监听器，桥接 FlowEventBus → SseEmitter
        FlowEventListener listener = (eventName, data) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data)
                        .id(String.valueOf(System.currentTimeMillis())));
            } catch (IOException e) {
                log.debug("SSE 推送失败（客户端可能已断开）: flowId={}, event={}", id, eventName);
            }
        };

        // 注册监听器到事件总线
        flowEventBus.subscribe(id, listener);

        // SSE 完成/超时/错误时清理监听器
        emitter.onCompletion(() -> {
            flowEventBus.unsubscribe(id, listener);
            log.debug("SSE 连接完成: flowId={}", id);
        });
        emitter.onTimeout(() -> {
            flowEventBus.unsubscribe(id, listener);
            log.debug("SSE 连接超时: flowId={}", id);
        });
        emitter.onError(e -> {
            flowEventBus.unsubscribe(id, listener);
            log.debug("SSE 连接错误: flowId={}, error={}", id, e.getMessage());
        });

        return emitter;
    }
}
