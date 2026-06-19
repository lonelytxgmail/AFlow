# AFlow API 参考文档

基础路径: `/api/v1`

所有响应统一包装为：
```json
{
  "success": true,
  "message": "success",
  "errorCode": null,
  "data": { ... },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

---

## 目录

1. [定义管理 (Definitions)](#定义管理-definitions)
2. [流程执行 (Flows)](#流程执行-flows)
3. [SSE 实时事件 (Stream)](#sse-实时事件-stream)
4. [调试 (Debug)](#调试-debug)
5. [审批 (Approvals)](#审批-approvals)
6. [监控指标 (Metrics)](#监控指标-metrics)
7. [原子能力 (Atomic Components)](#原子能力-atomic-components)
8. [错误码说明](#错误码说明)

---

## 定义管理 (Definitions)

### POST /api/v1/definitions

创建新的流程定义。后端会对 DSL 做校验，如果校验失败返回结构化错误列表。

**请求体：**
```json
{
  "id": "my-flow-001",
  "name": "示例工作流",
  "dslContent": "{\"nodes\":[...],\"edges\":[...]}"
}
```

**成功响应：**
```json
{ "data": { "id": "my-flow-001", "name": "示例工作流", "status": "DRAFT" } }
```

**校验失败响应（success 仍为 true，但 data 中含 valid: false）：**
```json
{
  "data": {
    "valid": false,
    "errors": [
      { "field": "node:node-3", "code": "ORPHAN_NODE", "message": "...", "nodeId": "node-3" }
    ]
  }
}
```

---

### GET /api/v1/definitions

获取所有流程定义列表。

**响应：**
```json
{ "data": [ { "id": "...", "name": "...", "version": 1 } ] }
```

---

### GET /api/v1/definitions/{id}

获取单个定义详情（含 DSL 内容）。

**响应：**
```json
{ "data": { "definition": { ... }, "dslContent": "{...}" } }
```

---

### PUT /api/v1/definitions/{id}

更新流程定义。同样会做 DSL 校验。

**请求体：**
```json
{ "name": "更新后的名称", "dslContent": "{...}" }
```

---

### DELETE /api/v1/definitions/{id}

删除流程定义。

---

### POST /api/v1/definitions/{id}/publish

发布定义（状态变为 PUBLISHED），同时创建版本快照。

**响应：**
```json
{ "data": { "message": "Published", "version": 2 } }
```

---

### POST /api/v1/definitions/validate

仅校验 DSL，不保存。用于前端实时预校验。

**请求体：**
```json
{ "dslContent": "{...}" }
```

**响应：**
```json
{
  "data": {
    "valid": true,
    "errors": []
  }
}
```

---

### GET /api/v1/definitions/{id}/versions

获取定义的版本历史列表。

**响应：**
```json
{ "data": [ { "versionNumber": 1, "createdAt": "2024-01-01T00:00:00" } ] }
```

---

### GET /api/v1/definitions/{id}/versions/{versionNumber}

获取指定版本内容。

**响应：**
```json
{ "data": { "versionNumber": 1, "snapshotJson": "{...}", "createdAt": "..." } }
```

---

### POST /api/v1/definitions/{id}/versions/{versionNumber}/rollback

回滚到指定版本（创建一个新版本，内容等于指定历史版本）。

**响应：**
```json
{ "data": { "message": "Rolled back successfully", "newVersion": 3 } }
```

---

## 流程执行 (Flows)

### POST /api/v1/flows/start

启动流程实例。

**请求体：**
```json
{
  "flowDefinitionId": "my-flow-001",
  "inputs": { "orderId": "12345", "userId": "user-001" }
}
```

**响应：** FlowContext 对象
```json
{
  "data": {
    "flowInstanceId": "inst-001",
    "flowDefinitionId": "my-flow-001",
    "status": "RUNNING",
    "variables": { ... },
    "executionPath": ["start", "node-1"]
  }
}
```

---

### GET /api/v1/flows

列出所有流程实例。支持 `status` 查询参数过滤。

| 参数 | 类型 | 说明 |
|------|------|------|
| `status` | string | 可选，过滤状态：RUNNING/COMPLETED/FAILED/SUSPENDED/CANCELLED |

---

### GET /api/v1/flows/{id}

获取单个实例详情。

---

### POST /api/v1/flows/{id}/resume

恢复挂起的流程（断点命中或审批通过后）。

**请求体（可选）：**
```json
{ "additionalInputs": { "key": "value" } }
```

---

### POST /api/v1/flows/{id}/retry/{nodeId}

重试指定节点执行。

---

### POST /api/v1/flows/{id}/cancel

取消流程执行。

---

### GET /api/v1/flows/{id}/events

获取流程实例的所有事件记录。

---

### GET /api/v1/flows/{id}/snapshots

获取流程实例的快照列表。

---

### GET /api/v1/flows/{id}/diff/{nodeId}

获取指定节点的执行前后快照（用于 diff 对比）。

---

## SSE 实时事件 (Stream)

### GET /api/v1/flows/{id}/stream

建立 SSE 连接，实时接收流程执行事件。

**连接方式：**
```javascript
const es = new EventSource('/api/v1/flows/' + flowId + '/stream')
es.addEventListener('NODE_ENTER', (e) => { ... })
```

**超时：** 30 分钟

### SSE 事件类型完整列表

| 事件名 | 说明 | data 字段 |
|--------|------|-----------|
| `FLOW_STARTED` | 流程启动 | `{ flowId, definitionId }` |
| `FLOW_COMPLETED` | 流程成功完成 | `{ flowId, duration }` |
| `FLOW_FAILED` | 流程执行失败 | `{ flowId, error }` |
| `NODE_ENTER` | 节点开始执行 | `{ flowId, nodeId, nodeName, variables }` |
| `NODE_EXIT` | 节点执行完成 | `{ flowId, nodeId, duration, output }` |
| `NODE_TIMEOUT` | 节点执行超时 | `{ flowId, nodeId, timeoutMs }` |
| `NODE_RETRY` | 节点重试 | `{ flowId, nodeId, attempt, maxAttempts }` |
| `AGENT_THINK` | Agent 思考阶段 | `{ flowId, nodeId, thought, iteration }` |
| `AGENT_ACT` | Agent 执行工具 | `{ flowId, nodeId, tool, arguments }` |
| `AGENT_OBSERVE` | Agent 观察结果 | `{ flowId, nodeId, tool, result }` |
| `AGENT_DONE` | Agent 完成 | `{ flowId, nodeId, totalIterations, totalTokens }` |
| `AGENT_TOOL_TIMEOUT` | 工具调用超时 | `{ flowId, nodeId, tool }` |
| `AGENT_TOOL_RATE_LIMITED` | 工具调用频率限制 | `{ flowId, nodeId, tool }` |
| `AGENT_TOKEN_PRUNE` | Token 预算超限修剪 | `{ flowId, nodeId, prunedCount }` |
| `AGENT_OUTPUT_VALIDATION_FAILED` | 输出校验失败 | `{ flowId, nodeId, attempt }` |
| `BREAKPOINT_HIT` | 断点命中 | `{ flowId, nodeId }` |
| `CONTEXT_UPDATED` | 上下文被手动修改 | `{ flowId, keys }` |
| `PARALLEL_FORK` | 并行分支开始 | `{ flowId, wave, nodeIds }` |
| `PARALLEL_JOIN` | 并行分支汇合 | `{ flowId, wave, results }` |
| `APPROVAL_REQUESTED` | 审批请求创建 | `{ flowId, nodeId, approvalId, title }` |
| `APPROVAL_COMPLETED` | 审批完成 | `{ flowId, nodeId, approvalId, decision }` |
| `APPROVAL_TIMEOUT` | 审批超时 | `{ flowId, nodeId, approvalId }` |

---

## 调试 (Debug)

### POST /api/v1/debug/{flowId}/breakpoint/{nodeId}

添加断点。流程执行到该节点前会暂停。

---

### DELETE /api/v1/debug/{flowId}/breakpoint/{nodeId}

移除断点。

---

### GET /api/v1/debug/{flowId}/breakpoints

获取当前所有断点列表。

**响应：**
```json
{ "data": ["node-1", "node-3"] }
```

---

### POST /api/v1/debug/{flowId}/step

单步执行（执行下一个节点后暂停）。

---

### PUT /api/v1/debug/{flowId}/context

在线编辑流程上下文变量。

**请求体：**
```json
{ "variableName": "newValue", "anotherVar": 42 }
```

---

## 审批 (Approvals)

### GET /api/v1/approvals

获取审批请求列表。

| 参数 | 类型 | 说明 |
|------|------|------|
| `status` | string | 可选，过滤：PENDING/APPROVED/REJECTED/TIMEOUT |

---

### GET /api/v1/approvals/{id}

获取审批请求详情。

**响应：**
```json
{
  "data": {
    "id": "apr-001",
    "flowId": "inst-001",
    "nodeId": "approve-1",
    "title": "费用审批",
    "description": "...",
    "status": "PENDING",
    "deadline": "2024-01-02T00:00:00",
    "options": [
      { "label": "批准", "value": "approve" },
      { "label": "拒绝", "value": "reject" }
    ]
  }
}
```

---

### POST /api/v1/approvals/{id}/approve

批准审批（流程将恢复执行）。

**请求体（可选）：**
```json
{ "data": { "comment": "同意" } }
```

---

### POST /api/v1/approvals/{id}/reject

拒绝审批（流程走 error 边或取消）。

**请求体（可选）：**
```json
{ "reason": "预算不足" }
```

---

## 监控指标 (Metrics)

### GET /api/v1/metrics/summary

获取聚合指标快照。

**响应：**
```json
{
  "data": {
    "activeFlows": 5,
    "todayExecutions": 128,
    "successRate": 0.95,
    "avgDurationMs": 3200,
    "agent": {
      "totalTokens": 125000,
      "avgIterations": 3.2,
      "topTools": [
        { "tool": "search", "count": 45 },
        { "tool": "calculate", "count": 22 }
      ]
    },
    "llm": {
      "totalCalls": 89,
      "p50LatencyMs": 1200,
      "retryRate": 0.05
    },
    "nodeDurations": [
      { "nodeType": "http", "avgDurationMs": 450, "p95DurationMs": 2100, "count": 310 },
      { "nodeType": "agent", "avgDurationMs": 8500, "p95DurationMs": 25000, "count": 45 }
    ]
  }
}
```

---

## 原子能力 (Atomic Components)

### GET /api/v1/atomic/components

获取原子能力组件列表。支持 `category`、`status`、`keyword` 过滤。

### POST /api/v1/atomic/components

创建新的原子能力组件。

### GET /api/v1/atomic/components/{id}

获取组件详情。

### PUT /api/v1/atomic/components/{id}

更新组件。

### DELETE /api/v1/atomic/components/{id}

删除组件。

### POST /api/v1/atomic/components/{id}/publish

发布组件。

### POST /api/v1/atomic/invoke/{id}

调用原子能力。

**请求体：**
```json
{ "inputs": { "to": "user@example.com", "subject": "测试" } }
```

### GET /api/v1/atomic/registry

获取所有已注册节点类型。

### GET /api/v1/atomic/registry/components

获取已发布的原子能力列表。

---

## 错误码说明

| 错误码 | HTTP 状态码 | 说明 |
|--------|-------------|------|
| `DSL_PARSE_ERROR` | 422 | DSL JSON 解析失败或结构校验不通过 |
| `FLOW_NOT_FOUND` | 404 | 流程定义或实例不存在 |
| `FLOW_EXECUTION_ERROR` | 409 | 流程执行过程中的错误（如非法状态转换） |
| `VALIDATION_ERROR` | 400 | 请求参数校验失败 |
| `BAD_REQUEST` | 400 | 请求格式错误 |
| `METHOD_NOT_ALLOWED` | 405 | HTTP 方法不支持 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |

---

## 通用说明

- 所有 API 返回 JSON 格式
- 时间格式为 ISO-8601（`2024-01-01T00:00:00Z`）
- CORS 已配置支持前端开发环境
- 分页参数（部分接口支持）：`page`（从 0 开始）、`pageSize`（默认 20）
