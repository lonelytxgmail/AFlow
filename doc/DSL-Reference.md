# AFlow DSL 参考文档

本文档定义了 AFlow 工作流定义 DSL（JSON 格式）的完整规范。

## 概述

AFlow DSL 用于描述工作流的结构，包括节点（nodes）、边（edges）和变量声明（variables）。
定义以 JSON 格式存储，支持版本管理和实时校验。

---

## 顶层结构

```json
{
  "id": "flow-001",
  "name": "示例工作流",
  "version": 1,
  "variables": { ... },
  "environment": { ... },
  "nodes": [ ... ],
  "edges": [ ... ],
  "parallelStrategy": "FAIL_FAST"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 否 | 流程唯一标识（不填则由系统自动生成） |
| `name` | string | 是 | 流程显示名称 |
| `version` | int | 否 | 版本号（发布时自增） |
| `variables` | object | 否 | 流程级变量定义（输入/输出 schema） |
| `environment` | object | 否 | 只读环境配置，表达式中可引用 |
| `nodes` | array | 是 | 节点列表（至少一个） |
| `edges` | array | 否 | 边列表（定义节点间连接） |
| `parallelStrategy` | string | 否 | 并行执行失败策略，默认 `FAIL_FAST` |

---

## 变量定义 (variables)

```json
{
  "variables": {
    "inputText": {
      "type": "STRING",
      "required": true,
      "description": "用户输入文本",
      "defaultValue": null
    },
    "maxRetries": {
      "type": "INTEGER",
      "required": false,
      "defaultValue": 3
    }
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 变量类型：`STRING`, `INTEGER`, `BOOLEAN`, `OBJECT`, `ARRAY` |
| `required` | boolean | 是否必填（启动流程时须传入） |
| `description` | string | 变量描述 |
| `defaultValue` | any | 默认值 |

---

## 节点定义 (NodeDefinition)

每个节点的通用结构：

```json
{
  "id": "node-1",
  "type": "http",
  "name": "调用外部API",
  "config": { ... },
  "output": "apiResult",
  "breakpoint": false
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 节点唯一标识（流程内唯一） |
| `type` | string | 是 | 节点类型（见下方类型列表） |
| `name` | string | 否 | 节点显示名称 |
| `config` | object | 否 | 类型特定配置（不同类型有不同结构） |
| `output` | string | 否 | 输出变量名（结果存入 FlowContext） |
| `breakpoint` | boolean | 否 | 是否设置断点（调试用） |

---

## 节点类型

### 控制流类型

#### `start` — 起始节点

流程入口点，通常不需要配置。

```json
{ "id": "start", "type": "start", "name": "开始" }
```

#### `condition` — 条件分支

根据 SpEL 表达式决定走哪条出边。

```json
{
  "id": "check",
  "type": "condition",
  "name": "判断结果",
  "config": {
    "conditions": [
      { "expression": "#result.score > 80", "target": "success-node" },
      { "expression": "#result.score > 50", "target": "retry-node" }
    ],
    "default": "fail-node"
  }
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `conditions` | array | 条件列表（按顺序评估，首个为 true 的生效） |
| `conditions[].expression` | string | SpEL 表达式 |
| `conditions[].target` | string | 目标节点 ID |
| `default` | string | 无条件匹配时的默认目标 |

#### `forEach` — 循环

对集合中的每个元素执行子流程。

```json
{
  "id": "loop",
  "type": "forEach",
  "config": {
    "collection": "#items",
    "itemVariable": "currentItem",
    "body": "process-node"
  }
}
```

#### `while` — 条件循环

```json
{
  "id": "retry-loop",
  "type": "while",
  "config": {
    "condition": "#retryCount < 3",
    "body": "retry-node"
  }
}
```

#### `parallel` — 并行开始

标记并行分支的起点。

#### `join` — 并行汇合

等待所有并行分支完成后继续。

#### `subflow` — 子流程

调用另一个已发布的流程定义。

```json
{
  "id": "call-sub",
  "type": "subflow",
  "config": {
    "definitionId": "sub-flow-001",
    "inputMapping": {
      "param1": "#mainVar1",
      "param2": "#mainVar2"
    }
  },
  "output": "subResult"
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `definitionId` | string | 子流程定义 ID |
| `inputMapping` | object | 输入参数映射（参数名 → SpEL 表达式） |

### AI 类型

#### `agent` — Agent 节点

自主 ReAct 循环执行（THINK → ACT → OBSERVE）。

```json
{
  "id": "agent-1",
  "type": "agent",
  "name": "智能分析Agent",
  "config": {
    "model": "gpt-4",
    "systemPrompt": "你是一个数据分析专家...",
    "userPrompt": "请分析以下数据：#{inputData}",
    "tools": ["search", "calculate"],
    "maxIterations": 10,
    "temperature": 0.7,
    "toolTimeout": 30000,
    "maxTokenBudget": 8000,
    "toolResultMaxLength": 2000,
    "toolRateLimit": { "maxCallsPerTool": 5, "maxTotalCalls": 20 },
    "outputSchema": { "type": "object", "properties": { "summary": { "type": "string" } } },
    "outputValidation": { "strategy": "retry", "maxRetries": 2 }
  },
  "output": "agentResult"
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `model` | string | LLM 模型名 |
| `systemPrompt` | string | 系统 Prompt |
| `userPrompt` | string | 用户 Prompt（支持 `#{variable}` 插值） |
| `tools` | string/array | 可用工具列表 |
| `maxIterations` | int | 最大迭代轮次 |
| `temperature` | number | 温度参数 (0-2) |
| `toolTimeout` | int | 工具调用超时（ms） |
| `maxTokenBudget` | int | Token 预算上限 |
| `toolResultMaxLength` | int | 工具返回结果最大长度 |
| `toolRateLimit` | object | 工具调用频率限制 |
| `outputSchema` | object | 输出 JSON Schema |
| `outputValidation` | object | 输出验证策略 |

#### `llm` — 独立 LLM 节点

单次 LLM 调用（非循环），用于 Prompt Chaining。

```json
{
  "id": "summarize",
  "type": "llm",
  "name": "摘要生成",
  "config": {
    "model": "gpt-4",
    "systemPrompt": "你是一个摘要助手",
    "userPrompt": "请将以下内容概括为3句话：#{inputText}",
    "temperature": 0.3,
    "outputSchema": { "type": "object", "properties": { "summary": { "type": "string" } } }
  },
  "output": "summaryResult"
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `model` | string | LLM 模型名 |
| `systemPrompt` | string | 系统 Prompt |
| `userPrompt` | string | 用户 Prompt（支持变量插值） |
| `temperature` | number | 温度参数 |
| `outputSchema` | object | 可选 Structured Output schema |

### 数据类型

#### `transform` — 数据转换

对 FlowContext 中的变量执行转换操作。

```json
{
  "id": "transform-1",
  "type": "transform",
  "config": {
    "expression": "#input.toUpperCase()"
  },
  "output": "transformed"
}
```

#### `script` — 脚本执行

执行自定义脚本（Groovy/JavaScript）。

```json
{
  "id": "calc",
  "type": "script",
  "config": {
    "language": "groovy",
    "script": "return input.size() * 2"
  },
  "output": "calcResult"
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `language` | string | 脚本语言：`groovy` 或 `javascript` |
| `script` | string | 脚本代码 |

#### `assign` — 变量赋值

```json
{
  "id": "set-var",
  "type": "assign",
  "config": {
    "assignments": {
      "count": "0",
      "status": "'pending'"
    }
  }
}
```

#### `log` — 日志输出

```json
{
  "id": "log-1",
  "type": "log",
  "config": {
    "level": "INFO",
    "message": "当前处理进度：#{progress}%"
  }
}
```

### 集成类型

#### `http` — HTTP 调用

```json
{
  "id": "api-call",
  "type": "http",
  "name": "调用用户服务",
  "config": {
    "url": "https://api.example.com/users/#{userId}",
    "method": "GET",
    "headers": {
      "Authorization": "Bearer #{token}"
    },
    "body": null,
    "timeout": 10000
  },
  "output": "userInfo"
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `url` | string | 请求 URL（支持变量插值） |
| `method` | string | HTTP 方法：GET/POST/PUT/DELETE |
| `headers` | object | 请求头（key-value） |
| `body` | string/object | 请求体 |
| `timeout` | int | 超时时间（ms） |

#### `callback` — 回调等待

等待外部系统回调通知。

```json
{
  "id": "wait-callback",
  "type": "callback",
  "config": {
    "callbackUrl": "/api/v1/callbacks/#{flowId}",
    "timeout": 3600000
  },
  "output": "callbackData"
}
```

#### `delay` — 延时

```json
{
  "id": "wait",
  "type": "delay",
  "config": {
    "duration": 5000
  }
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `duration` | int | 延时毫秒数 |

#### `composite` — 原子能力调用

调用已注册的原子能力组件。

```json
{
  "id": "invoke-component",
  "type": "composite",
  "config": {
    "componentId": "comp-email-sender",
    "inputs": {
      "to": "#recipientEmail",
      "subject": "通知",
      "body": "#notificationText"
    }
  },
  "output": "sendResult"
}
```

#### `approval` — 审批节点

将流程挂起，等待人工审批。

```json
{
  "id": "approve-1",
  "type": "approval",
  "name": "主管审批",
  "config": {
    "title": "费用报销审批",
    "description": "金额：#{amount}元，请审批",
    "options": [
      { "label": "批准", "value": "approve" },
      { "label": "拒绝", "value": "reject" }
    ],
    "timeout": 86400000,
    "timeoutAction": "reject"
  },
  "output": "approvalResult"
}
```

| config 字段 | 类型 | 说明 |
|-------------|------|------|
| `title` | string | 审批标题 |
| `description` | string | 审批描述（支持变量插值） |
| `options` | array | 审批选项列表 |
| `timeout` | int | 超时时间（ms） |
| `timeoutAction` | string | 超时处理：`approve`/`reject`/`escalate` |

---

## 边定义 (EdgeDefinition)

```json
{
  "from": "node-a",
  "to": "node-b",
  "type": "normal",
  "condition": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `from` | string | 是 | 源节点 ID |
| `to` | string | 是 | 目标节点 ID |
| `type` | string | 否 | 边类型，默认 `normal` |
| `condition` | string | 否 | SpEL 条件表达式（仅 conditional 边） |

### 边类型

| 类型 | 说明 | 视觉样式 |
|------|------|----------|
| `normal` | 正常执行路径（默认） | 蓝色实线 + 箭头 |
| `error` | 错误处理路径（try-catch 语义） | 红色虚线 + "error" 标签 |
| `conditional` | 条件路径（condition 表达式为 true 时走） | 蓝色虚线 + 条件摘要 |

---

## 变量引用语法

AFlow 支持在 Prompt 模板和配置字段中引用流程变量：

### SpEL 表达式（在条件和表达式字段中）

使用 `#variableName` 语法引用 FlowContext 中的变量：

```
#result.score > 80
#items.size() > 0
#userInput != null && #userInput.length() > 0
```

### Prompt 模板插值

在 `userPrompt`、`description` 等文本字段中使用 `#{variableName}` 语法：

```
请分析以下数据：#{inputData}
用户 #{userName} 提交了审批，金额：#{amount}元
```

### 变量来源

| 来源 | 说明 | 引用方式 |
|------|------|----------|
| 流程输入 | `variables` 中声明的输入变量 | `#variableName` |
| 节点输出 | 各节点执行结果（`output` 字段命名） | `#outputName` |
| 环境变量 | `environment` 中定义的只读配置 | `#env.keyName` |

---

## 并行策略 (parallelStrategy)

当 DAG 中存在无依赖的多节点时，引擎自动并行执行。通过 `parallelStrategy` 控制部分失败的处理：

| 策略 | 说明 |
|------|------|
| `FAIL_FAST` | 一个节点失败立即终止所有并行分支（默认） |
| `WAIT_ALL` | 等所有节点完成后再判断成功/失败 |
| `BEST_EFFORT` | 忽略失败节点，只收集成功结果 |

---

## 完整示例

```json
{
  "id": "order-process",
  "name": "订单处理工作流",
  "version": 1,
  "variables": {
    "orderId": { "type": "STRING", "required": true },
    "userId": { "type": "STRING", "required": true }
  },
  "nodes": [
    { "id": "start", "type": "start", "name": "开始" },
    {
      "id": "fetch-order",
      "type": "http",
      "name": "获取订单",
      "config": { "url": "https://api.example.com/orders/#{orderId}", "method": "GET" },
      "output": "orderData"
    },
    {
      "id": "check-amount",
      "type": "condition",
      "name": "金额检查",
      "config": {
        "conditions": [
          { "expression": "#orderData.amount > 10000", "target": "approval" }
        ],
        "default": "process"
      }
    },
    {
      "id": "approval",
      "type": "approval",
      "name": "大额审批",
      "config": {
        "title": "大额订单审批",
        "description": "订单金额 #{orderData.amount} 元",
        "timeout": 86400000,
        "timeoutAction": "reject"
      },
      "output": "approvalResult"
    },
    {
      "id": "process",
      "type": "script",
      "name": "处理订单",
      "config": { "language": "groovy", "script": "return [status: 'processed']" },
      "output": "processResult"
    },
    {
      "id": "notify",
      "type": "llm",
      "name": "生成通知",
      "config": {
        "model": "gpt-4",
        "userPrompt": "为订单 #{orderId} 生成处理完成通知",
        "temperature": 0.5
      },
      "output": "notification"
    }
  ],
  "edges": [
    { "from": "start", "to": "fetch-order" },
    { "from": "fetch-order", "to": "check-amount" },
    { "from": "check-amount", "to": "approval", "condition": "#orderData.amount > 10000" },
    { "from": "check-amount", "to": "process" },
    { "from": "approval", "to": "process" },
    { "from": "process", "to": "notify" }
  ],
  "parallelStrategy": "FAIL_FAST"
}
```

---

## 校验规则

保存/创建时，后端会执行以下校验：

1. **节点 ID 唯一** — 同一流程中不允许重复 ID
2. **边引用完整** — `from`/`to` 必须指向已存在的节点
3. **无自环** — 不允许 `from === to` 的边
4. **无孤立节点** — 所有节点必须从起始节点可达
5. **无环路** — DAG 中不允许循环依赖
6. **必填字段** — 节点 `id` 和 `type` 不能为空
7. **边类型有效** — 必须是 `normal` 或 `error`

校验错误以结构化列表返回：

```json
{
  "valid": false,
  "errors": [
    { "field": "node:node-3", "code": "ORPHAN_NODE", "message": "...", "nodeId": "node-3" }
  ]
}
```
