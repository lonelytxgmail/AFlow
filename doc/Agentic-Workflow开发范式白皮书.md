# Agentic Workflow 开发范式与设计规范白皮书

> 版本：1.0 | 日期：2026-06-18
> 基于 2025-2026 年主流 Agentic AI 平台（LangGraph、CrewAI、Dify、AutoGen、Strands Agents）
> 的工程实践与学术研究整理。

---

## 目录

1. [概述与范式定义](#一概述与范式定义)
2. [核心架构分层模型](#二核心架构分层模型)
3. [八大编排设计模式](#三八大编排设计模式)
4. [工作流引擎内核设计规范](#四工作流引擎内核设计规范)
5. [Agent 执行器设计规范](#五agent-执行器设计规范)
6. [Tool 系统设计规范](#六tool-系统设计规范)
7. [RAG 与知识检索模块设计规范](#七rag-与知识检索模块设计规范)
8. [记忆系统设计规范](#八记忆系统设计规范)
9. [可观测性与评估体系设计规范](#九可观测性与评估体系设计规范)
10. [韧性与容错设计规范](#十韧性与容错设计规范)
11. [可视化编辑器设计规范](#十一可视化编辑器设计规范)
12. [DSL 与契约设计规范](#十二dsl-与契约设计规范)
13. [生产化部署方法论](#十三生产化部署方法论)
14. [开发方法论与工程实践](#十四开发方法论与工程实践)
15. [参考文献](#十五参考文献)

---

## 一、概述与范式定义

### 1.1 什么是 Agentic Workflow

Agentic Workflow 是一种将 LLM 驱动的自主 Agent 嵌入结构化工作流编排中的系统架构范式。
与传统自动化工作流（固定路径、确定性执行）不同，Agentic Workflow 的核心特征是：

- **运行时决策**：LLM 在执行过程中动态决定下一步行动
- **工具增强**：Agent 可调用外部系统完成任务
- **自我修正**：通过反思和评估循环修正中间结果
- **人机协作**：在关键决策点引入人类审批

### 1.2 范式演进（2020-2026）

```
Prompt Engineering (2020-2022)
    → Chain-of-Thought / Few-Shot (2022-2023)
        → ReAct / Tool Use (2023-2024)
            → Flow Engineering / Multi-Agent (2024-2025)
                → Production Agentic Workflow (2025-2026)
```

2026 年的行业共识：**从 "Prompt Engineering" 转向 "Flow Engineering"**。
构建生产级 AI 系统不再是写一个好 prompt，而是设计一个包含状态管理、容错、
可观测性的完整工作流系统。

### 1.3 适用场景判断框架

| 场景特征 | 推荐方案 |
|----------|---------|
| 单步 LLM 调用，输出确定 | 直接 API 调用 |
| 多步固定流程，步骤已知 | Prompt Chaining / DAG Workflow |
| 步骤已知但需动态判断路径 | Routing + Condition 节点 |
| 任务分解方式不确定 | ReAct Agent / Plan-and-Execute |
| 多角色协作，职责不同 | Multi-Agent Orchestration |
| 需要人类参与决策 | Human-in-the-Loop Workflow |

---

## 二、核心架构分层模型

一个生产级 Agentic Workflow 平台应包含以下六层架构：

```
┌─────────────────────────────────────────────────┐
│  Layer 6: 可视化编辑器 & 用户界面                    │
│  (Visual Editor, Monitoring Dashboard, Chat UI)  │
├─────────────────────────────────────────────────┤
│  Layer 5: API & 集成层                            │
│  (REST API, SSE/WebSocket, Webhook, MCP)         │
├─────────────────────────────────────────────────┤
│  Layer 4: 编排引擎 (Orchestration Engine)          │
│  (DAG Executor, State Machine, Scheduler)        │
├─────────────────────────────────────────────────┤
│  Layer 3: Agent 执行层                            │
│  (ReAct Loop, Planning, Memory, Guardrails)      │
├─────────────────────────────────────────────────┤
│  Layer 2: 能力层 (Capabilities)                   │
│  (LLM Service, Tool Registry, RAG, Knowledge)    │
├─────────────────────────────────────────────────┤
│  Layer 1: 基础设施 (Infrastructure)               │
│  (DB, Vector Store, Message Queue, Observability) │
└─────────────────────────────────────────────────┘
```

### 各层职责定义

| 层级 | 职责 | 关键设计原则 |
|------|------|-------------|
| L6 可视化 | 流程设计、运行监控、配置管理 | 所见即所得、渐进式披露 |
| L5 集成 | 对外暴露能力、事件推送、第三方对接 | 契约驱动、版本化 API |
| L4 编排 | DAG 执行、状态流转、并行/串行控制 | 确定性执行、可恢复 |
| L3 Agent | LLM 推理循环、自主决策、输出校验 | 有界自主、可中断 |
| L2 能力 | 模型调用、工具执行、知识检索 | 抽象接口、可替换 |
| L1 基础设施 | 持久化、缓存、消息、监控 | 高可用、可扩展 |

---

## 三、八大编排设计模式

基于 2025-2026 年行业实践，Agentic Workflow 的核心编排模式可归纳为八种：

### 3.1 Single-Shot（单次调用）

```
Input → LLM → Output
```

**特征**：一次 LLM 调用完成任务，无循环无工具。
**适用**：分类、摘要、翻译等确定性任务。
**实现要点**：
- 使用 Structured Output 确保输出格式
- Temperature 设为 0 获得确定性结果
- 配合 JSON Schema 校验输出

### 3.2 Prompt Chaining（提示链）

```
Input → LLM₁ → Transform → LLM₂ → Transform → LLM₃ → Output
```

**特征**：多步串行处理，每步输出是下一步输入，步骤固定。
**适用**：内容生成流水线、数据提取+验证+格式化。
**实现要点**：
- 每步之间插入验证/转换节点（Gate Pattern）
- 步骤间传递结构化数据而非自由文本
- 失败可从中间步骤恢复

### 3.3 Routing（路由分发）

```
Input → Router(LLM/Rule) ──→ Handler_A → Output
                           ├→ Handler_B → Output
                           └→ Handler_C → Output
```

**特征**：根据输入特征将任务分发到不同处理路径。
**适用**：客服分流、意图识别后分支处理。
**实现要点**：
- Router 可以是 LLM（分类）或规则引擎（SpEL/条件表达式）
- 路由结果应有 fallback 路径
- 路由决策应该被记录以供后续分析

### 3.4 Tool Use / ReAct（工具使用）

```
Input → [Thought → Action → Observation]* → Final Answer
```

**特征**：Agent 自主循环推理，动态选择工具执行。
**适用**：信息检索、数据分析、API 编排等开放性任务。
**实现要点**：
- 设置最大迭代次数防止无限循环
- Tool 执行设超时，超时不终止循环
- 工具结果截断后送入 LLM，全量结果留存事件
- Token 预算管理防止上下文溢出

### 3.5 Evaluator-Optimizer（评估优化循环）

```
Input → Generator(LLM) → Evaluator(LLM/Rule) ──pass──→ Output
                              │ fail
                              └──→ Feedback → Generator (retry)
```

**特征**：生成-评估-修正的迭代循环，直到满足质量标准。
**适用**：代码生成、文案优化、数据质量校验。
**实现要点**：
- Evaluator 可以是另一个 LLM（LLM-as-Judge）或规则校验
- 设置最大修正轮次（通常 3-5 轮）
- 每轮将评估反馈注入 Generator 的上下文
- 记录每轮得分趋势以检测是否收敛

### 3.6 Parallelization（并行执行）

```
Input → [Agent_A, Agent_B, Agent_C] (并行) → Aggregator → Output
```

**特征**：多个无依赖任务并行执行，结果聚合。
**适用**：多源数据采集、多角度分析、批量处理。
**实现要点**：
- DAG 中识别无依赖节点自动并行
- 聚合器等待所有分支完成（或设超时）
- 部分失败时的降级策略（忽略/重试/终止）

### 3.7 Orchestrator-Workers（编排-工人）

```
Input → Orchestrator(LLM) → [Worker_1, Worker_2, ...] → Orchestrator → Output
```

**特征**：编排者动态分解任务并分配给专业 Worker。
**适用**：复杂文档处理、多步骤研究任务。
**实现要点**：
- Orchestrator 基于 LLM 做任务规划（Plan-and-Execute）
- Worker 是专用 Agent（有限 Tool 集）或子流程
- Orchestrator 汇总 Worker 结果后决定是否需要补充任务
- 控制 Worker 数量防止资源耗尽

### 3.8 Multi-Agent Collaboration（多 Agent 协作）

```
Agent_Researcher ←→ Agent_Analyst ←→ Agent_Writer
        ↕                 ↕                ↕
   [Tools]           [Tools]          [Tools]
```

**特征**：多个 Agent 具有不同角色和能力，通过消息传递协作。
**适用**：软件开发团队模拟、辩论式决策、复杂项目管理。
**实现要点**：
- 明确每个 Agent 的角色边界和 Tool 集
- 使用共享状态（Blackboard）或消息总线通信
- 设置 Supervisor 防止对话发散
- Handoff 协议：明确何时/如何将控制权转交

### 3.9 模式选择决策树

```
任务是否需要 LLM？
├─ 否 → 传统自动化
└─ 是 → 单步能否完成？
    ├─ 是 → Single-Shot
    └─ 否 → 步骤是否固定？
        ├─ 是 → Prompt Chaining / Routing
        └─ 否 → 需要外部数据/行动？
            ├─ 否 → Evaluator-Optimizer
            └─ 是 → 单 Agent 能否胜任？
                ├─ 是 → ReAct
                └─ 否 → 任务可并行？
                    ├─ 是 → Parallelization
                    └─ 否 → Orchestrator / Multi-Agent
```

---

## 四、工作流引擎内核设计规范

### 4.1 核心概念模型

```
FlowDefinition (静态)
├── NodeDefinition[]     # 节点定义
│   ├── id, type, name
│   ├── config{}         # 节点特定配置
│   └── policies{}       # 超时/重试/缓存策略
├── EdgeDefinition[]     # 边定义
│   ├── from, to
│   ├── type (normal/error/conditional)
│   └── condition        # 条件表达式
└── Variables{}          # 流程级变量声明

FlowInstance (运行时)
├── id, definitionId, status
├── FlowContext          # 运行时上下文(变量/状态)
├── ExecutionPath[]      # 已执行节点路径
├── Snapshots[]          # 状态快照
└── Events[]             # 执行事件流
```

### 4.2 执行模型设计原则

| 原则 | 说明 |
|------|------|
| **DAG 拓扑排序执行** | 按依赖关系决定执行顺序，无依赖节点可并行 |
| **状态机驱动** | 流程/节点状态严格遵循有限状态机转移规则 |
| **快照可恢复** | 每个节点执行前后保存上下文快照，支持从任意点恢复 |
| **事件溯源** | 所有状态变更以事件形式记录，支持完整审计和重放 |
| **虚拟线程** | 使用 Java 21 虚拟线程(或协程)避免阻塞平台线程 |
| **背压控制** | 并发执行数受限，防止资源耗尽 |

### 4.3 节点生命周期状态机

```
PENDING → RUNNING → COMPLETED
                 ↘ FAILED → RETRYING → RUNNING
                                     ↘ FAILED (final)
         → SUSPENDED (等待外部事件/审批)
         → SKIPPED (条件跳过)
         → CANCELLED (用户取消)
```

### 4.4 节点类型体系

一个完整的 Agentic Workflow 平台应支持以下节点类型分类：

| 分类 | 节点类型 | 说明 |
|------|---------|------|
| **控制流** | Start / End | 流程入口/出口 |
| | Condition / Switch | 条件分支 |
| | ForEach / While | 循环迭代 |
| | Parallel / Join | 并行分叉/汇合 |
| | Subflow | 子流程调用 |
| **AI 推理** | LLM | 单次模型调用（非 Agent） |
| | Agent | ReAct 循环 Agent |
| | Knowledge Retrieval | RAG 检索节点 |
| **数据处理** | Transform / Template | 数据转换/模板渲染 |
| | Code (Python/JS/Groovy) | 脚本执行 |
| | Assign | 变量赋值 |
| | Aggregate | 多源结果聚合 |
| **集成** | HTTP | API 调用 |
| | Webhook / Callback | 外部回调 |
| | Message Queue | 消息收发 |
| | Database | 数据库操作 |
| **人机交互** | Approval | 人工审批 |
| | Form Input | 表单收集 |
| | Notification | 通知推送 |
| **工具** | Tool Call | 通用工具调用 |
| | Composite | 可复用组件引用 |

### 4.5 并行执行模型

```java
// 伪代码：DAG 并行执行
for each wave in topologicalSort(dag):
    List<CompletableFuture<NodeResult>> futures = wave.nodes().stream()
        .map(node -> CompletableFuture.supplyAsync(
            () -> executeNode(node, context), virtualThreadExecutor
        ).orTimeout(node.timeoutMs(), MILLISECONDS))
        .toList();
    
    List<NodeResult> results = CompletableFuture.allOf(futures)
        .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
        .join();
    
    // 处理结果：更新上下文、判断分支、处理失败
```

### 4.6 错误处理模型

```
节点执行失败
├── 配置了 retryPolicy？
│   ├── 是 → 重试循环（指数退避）
│   │         └── 重试耗尽 → 检查错误边
│   └── 否 → 直接检查错误边
├── 存在 error 类型出边？
│   ├── 是 → 走错误处理路径（Try-Catch 语义）
│   └── 否 → 流程标记失败（Fail-Fast）
└── 发布 NODE_FAILED / FLOW_FAILED 事件
```

---

## 五、Agent 执行器设计规范

### 5.1 ReAct 循环架构

```
┌─────────────────────────────────────────────┐
│              Agent Executor                   │
│                                             │
│  ┌─────────┐    ┌──────────┐   ┌────────┐  │
│  │ System  │    │ Message  │   │ Token  │  │
│  │ Prompt  │    │ History  │   │ Budget │  │
│  └────┬────┘    └────┬─────┘   └───┬────┘  │
│       │              │              │        │
│       ▼              ▼              ▼        │
│  ┌──────────────────────────────────────┐   │
│  │         LLM Service (with retry)      │   │
│  └──────────────┬───────────────────────┘   │
│                 │                            │
│      ┌──────── ▼ ────────┐                  │
│      │  Response Type?   │                  │
│      └──┬─────────────┬──┘                  │
│         │ToolCall     │FinalAnswer          │
│         ▼             ▼                      │
│  ┌────────────┐  ┌──────────────┐           │
│  │Tool Execute│  │Output Validate│           │
│  │(w/ timeout)│  │(Guardrails)   │           │
│  └─────┬──────┘  └──────┬───────┘           │
│        │                 │                   │
│        ▼                 ▼                   │
│  ┌──────────┐     ┌──────────┐              │
│  │ Truncate │     │  Return  │              │
│  │ & Append │     │  Result  │              │
│  └─────┬────┘     └──────────┘              │
│        │                                    │
│        └──→ Loop (back to LLM) ←────────    │
└─────────────────────────────────────────────┘
```

### 5.2 Agent 配置模型

```yaml
agent_node:
  # 基础配置
  model: "gpt-4o"
  temperature: 0.1
  systemPrompt: "You are a data analyst..."
  userPrompt: "Analyze the following: {{input}}"
  maxIterations: 10

  # 工具配置
  tools: ["search", "calculate", "query_db"]  # 或 SpEL: "#{availableTools}"
  toolTimeout: 30000                           # ms
  toolRateLimit:
    maxCallsPerTool: 5
    maxTotalCalls: 20

  # 韧性配置
  maxTokenBudget: 100000
  toolResultMaxLength: 4000

  # 输出校验
  outputSchema: { type: object, properties: {...}, required: [...] }
  outputValidation:
    strategy: "retry"       # retry | fail | passthrough
    maxRetries: 3
```

### 5.3 关键设计原则

| 原则 | 说明 |
|------|------|
| **有界自主** | Agent 必须有最大迭代次数、Token 预算、工具频率限制 |
| **优雅降级** | Tool 超时不终止循环，将错误信息回传 LLM 让其自主决策 |
| **上下文管理** | 超预算时裁剪早期消息，保留 system prompt + 最近交互 |
| **结果截断** | 工具结果超长时截断后送 LLM，全量保存到事件系统 |
| **输出 Guardrails** | JSON Schema 校验输出，失败可重试/降级/直通 |
| **结构化事件** | 每步(THINK/ACT/OBSERVE/DONE)发出结构化事件供前端展示 |

### 5.4 Planning 模式（Plan-and-Execute）

```
User Input → Planner(LLM) → [Step1, Step2, Step3, ...]
                                    │
                                    ▼
                              Executor Loop:
                              for step in plan:
                                  result = execute(step)
                                  if needs_replan:
                                      plan = replan(remaining, result)
                              → Final Synthesis → Output
```

**规范要点**：
- Planner 输出结构化 Plan（JSON array of steps）
- 每步执行后评估是否需要 Replan
- Plan 存入上下文，前端可展示执行进度
- 设置最大 Replan 次数防止无限修改

### 5.5 输出 Guardrails 规范

```
Agent Output
    │
    ▼
Schema Validation (JSON Schema)
    │
    ├─ PASS → Return result
    │
    └─ FAIL → Strategy?
        ├─ retry → 注入错误信息重新调用 LLM (max N times)
        ├─ fail → 节点标记失败，走错误处理路径
        └─ passthrough → 记录警告，原样输出
```

---

## 六、Tool 系统设计规范

### 6.1 Tool 定义规范

一个 Tool 的完整定义应包含：

```json
{
  "name": "search_knowledge_base",
  "description": "Search the company knowledge base for relevant documents. Use when the user asks about internal policies, procedures, or historical data.",
  "parameters": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "The search query in natural language"
      },
      "top_k": {
        "type": "integer",
        "description": "Number of results to return (1-20)",
        "default": 5
      },
      "filter": {
        "type": "object",
        "description": "Optional metadata filters",
        "properties": {
          "department": { "type": "string", "enum": ["engineering", "sales", "hr"] },
          "date_after": { "type": "string", "format": "date" }
        }
      }
    },
    "required": ["query"]
  }
}
```

### 6.2 Tool 设计六原则

| 原则 | 说明 | 反例 |
|------|------|------|
| **单一职责** | 每个 Tool 做一件事 | 一个 Tool 又搜索又写入 |
| **清晰命名** | 动词_名词格式 | `do_stuff`, `helper` |
| **精确描述** | description 是 LLM 选择工具的唯一依据 | 模糊的 "A useful tool" |
| **严格 Schema** | 必填参数、类型、enum、默认值明确 | 全部 string 无约束 |
| **幂等安全** | 读操作标记 safe，写操作需确认 | 无法重试的写操作 |
| **结果结构化** | 返回 JSON 而非自由文本 | 返回 HTML 页面源码 |

### 6.3 Tool 分类管理

```
Tool Registry
├── Built-in Tools (系统内置)
│   ├── http_request
│   ├── code_execute
│   ├── knowledge_search
│   └── ...
├── Custom Tools (用户自定义)
│   ├── API-based (HTTP endpoint)
│   ├── Code-based (函数定义)
│   └── Composite (工作流即工具)
└── External Tools (第三方集成)
    ├── MCP Protocol
    └── Plugin Marketplace
```

### 6.4 Tool 执行安全规范

| 维度 | 规范 |
|------|------|
| **超时** | 每次 Tool 调用必须设超时（建议 30s） |
| **频率限制** | 单 Tool 最大调用次数 + 总调用次数上限 |
| **结果截断** | 超过阈值的结果截断后送 LLM，全量存事件 |
| **沙箱隔离** | 代码执行类 Tool 在隔离沙箱中运行 |
| **权限控制** | Tool 调用前校验当前用户/流程的权限 |
| **审计记录** | 所有 Tool 调用的入参/出参/耗时记录 |

### 6.5 Composite Tool（工作流即工具）

将已发布的工作流或原子能力暴露为 Agent 可调用的 Tool：

```
Atomic Component (Published)
    ↓ 自动注册
Tool Registry
    ↓ Agent 节点配置 tools=["component_xxx"]
Agent ReAct Loop → tool.execute(args)
    ↓
Component Executor → NodeResult
    ↓ 结果回填
Agent 继续推理
```

**规范要点**：
- Component 的 inputSchema 自动映射为 Tool 的 parameters
- Component 必须是 PUBLISHED 状态才可作为 Tool
- Tool description 从 Component 的 description 字段生成
- 嵌套调用深度限制（防止递归爆栈）

---

## 七、RAG 与知识检索模块设计规范

### 7.1 RAG 架构全景

```
┌──────────── Offline: Ingestion Pipeline ────────────┐
│                                                      │
│  Documents → Chunking → Embedding → Vector Store     │
│      ↓           ↓          ↓           ↓           │
│  PDF/MD/...  Split策略  Embedding Model  pgvector/   │
│              (512-1500    (text-         Qdrant/      │
│               tokens)    embedding-3)   Pinecone     │
└──────────────────────────────────────────────────────┘

┌──────────── Online: Query Pipeline ─────────────────┐
│                                                      │
│  User Query → Query Rewrite → Hybrid Retrieval       │
│                    ↓              ↓                   │
│              LLM/Rule        Vector + Keyword         │
│                                   ↓                  │
│                              Rerank (Cross-Encoder)   │
│                                   ↓                  │
│                              Context Assembly         │
│                                   ↓                  │
│                              LLM Generation          │
└──────────────────────────────────────────────────────┘
```

### 7.2 知识库管理规范

| 维度 | 规范 |
|------|------|
| **文档格式** | 支持 PDF、Markdown、DOCX、TXT、HTML、CSV |
| **分块策略** | 递归分割，chunk_size=512-1500 tokens，overlap=128 tokens |
| **元数据提取** | 标题、日期、来源、分类标签随 chunk 存储 |
| **索引更新** | 增量索引（新增/修改/删除），避免全量重建 |
| **多知识库** | 一个 Agent 可关联多个知识库，查询时可指定范围 |

### 7.3 检索策略规范

```yaml
retrieval_config:
  mode: "hybrid"              # vector | keyword | hybrid
  vector:
    model: "text-embedding-3-large"
    dimensions: 1536
    top_k: 20
    score_threshold: 0.7
  keyword:
    algorithm: "bm25"
    top_k: 20
  rerank:
    enabled: true
    model: "cross-encoder/ms-marco-MiniLM-L-6-v2"
    top_n: 5                  # 最终返回 top N
  query_rewrite:
    enabled: true
    strategy: "multi_query"   # multi_query | step_back | decompose
```

### 7.4 Knowledge Retrieval 节点设计

在 Agentic Workflow 中，RAG 可以以两种形式存在：

**形式 A：独立节点**
```
User Input → Knowledge Retrieval Node → LLM Node (with context)
```

**形式 B：Agent 内置 Tool**
```
Agent ReAct Loop → tool: search_knowledge(query) → 检索结果回填
```

**推荐**：两种形式都支持。对于确定需要知识检索的流程用形式 A（确定性更强），
对于 Agent 自主决定是否需要检索的场景用形式 B。

### 7.5 Agentic RAG 高级模式

```
User Query → Agent (Orchestrator)
    ├── 分析意图，判断是否需要检索
    ├── 选择知识库 + 构造查询
    ├── 执行检索
    ├── 评估检索结果质量
    │   ├── 质量不足 → 改写查询重试
    │   └── 质量足够 → 继续
    ├── 综合多源结果
    └── 生成最终回答 (with citations)
```

**规范要点**：
- Agent 具备"判断检索结果是否足够"的能力
- 支持查询改写和多轮检索
- 输出包含引用来源（chunk_id + 文档名）
- 检索相关性指标纳入可观测体系

---

## 八、记忆系统设计规范

### 8.1 记忆分层模型

参考认知科学的记忆分类，Agent 记忆系统应包含三层：

```
┌─────────────────────────────────────────────┐
│  Working Memory (工作记忆)                    │
│  - 当前对话的 message history                 │
│  - 固定窗口（如最近 10 轮）                    │
│  - 生命周期：单次执行内                        │
├─────────────────────────────────────────────┤
│  Episodic Memory (情景记忆)                   │
│  - 历史执行的关键事件和结果                     │
│  - 结构化记录：who/what/when/outcome          │
│  - 生命周期：跨执行，按时间衰减                 │
├─────────────────────────────────────────────┤
│  Semantic Memory (语义记忆)                   │
│  - 从多次执行中归纳的知识和规则                  │
│  - 向量化存储 + 实体关系图                     │
│  - 生命周期：长期持久，显式管理                  │
└─────────────────────────────────────────────┘
```

### 8.2 记忆系统接口规范

```java
public interface AgentMemory {

    /** 写入一条记忆 */
    void store(MemoryEntry entry);

    /** 基于语义相似度检索相关记忆 */
    List<MemoryEntry> recall(String query, int topK, MemoryFilter filter);

    /** 合并/压缩旧记忆（定期 consolidation） */
    void consolidate();

    /** 遗忘过期或低重要性记忆 */
    void forget(ForgetPolicy policy);
}

public record MemoryEntry(
    String id,
    MemoryType type,          // EPISODIC | SEMANTIC | PROCEDURAL
    String content,
    Map<String, Object> metadata,
    double importance,        // 0.0 - 1.0
    Instant createdAt,
    Instant lastAccessedAt
) {}
```

### 8.3 记忆管理策略

| 策略 | 说明 |
|------|------|
| **滑动窗口** | 工作记忆保留最近 N 轮，超出部分摘要后存入情景记忆 |
| **重要性评估** | 使用 LLM 或规则评估记忆重要性，低于阈值的自动衰减 |
| **时间衰减** | 长期未访问的记忆降低检索权重 |
| **合并压缩** | 多条相似记忆合并为一条语义记忆（consolidation） |
| **容量控制** | 每个 Agent 实例的记忆总量设上限 |

### 8.4 记忆在 Workflow 中的集成

```yaml
agent_node:
  memory:
    enabled: true
    working:
      windowSize: 10              # 保留最近 10 轮
    episodic:
      store: "redis"              # redis | postgres | vector_db
      maxEntries: 1000
      ttlDays: 30
    semantic:
      store: "pgvector"
      embeddingModel: "text-embedding-3-small"
      maxEntries: 500
    recall:
      topK: 5
      scoreThreshold: 0.75
      injectAsSystemContext: true  # 检索到的记忆注入 system prompt
```

---

## 九、可观测性与评估体系设计规范

### 9.1 四级可观测性模型

```
Level 1: Flow-Level
├── 流程执行状态、总耗时、成功率
├── 活跃流程数、排队深度
└── Span: flow_execution

Level 2: Node-Level
├── 节点耗时分布、重试次数、超时率
├── 各节点类型的执行计数
└── Span: node_execution (parent: flow)

Level 3: LLM-Level
├── 调用延迟、Token 消耗(input/output)
├── 模型版本、成功率、重试次数
└── Span: llm_call (parent: node)

Level 4: Tool-Level
├── 工具调用次数、耗时、成功率
├── 超时率、截断率
└── Span: tool_execution (parent: llm_call)
```

### 9.2 指标体系（Metrics）

| 分类 | 指标名 | 类型 | 标签 |
|------|--------|------|------|
| 流程 | `aflow.flow.active` | Gauge | status |
| | `aflow.flow.duration` | Timer | definition_id, status |
| | `aflow.flow.started.total` | Counter | definition_id |
| 节点 | `aflow.node.duration` | Timer | node_type, status |
| | `aflow.node.retry.total` | Counter | node_type |
| | `aflow.node.timeout.total` | Counter | node_type |
| Agent | `aflow.agent.iterations` | Distribution | agent_id |
| | `aflow.agent.tool.calls` | Counter | tool_name |
| | `aflow.agent.tool.duration` | Timer | tool_name |
| | `aflow.agent.tool.timeout` | Counter | tool_name |
| LLM | `aflow.llm.duration` | Timer | model, provider |
| | `aflow.llm.tokens.input` | Distribution | model |
| | `aflow.llm.tokens.output` | Distribution | model |
| | `aflow.llm.retry.total` | Counter | model, reason |
| | `aflow.llm.cost.usd` | Counter | model |

### 9.3 分布式追踪规范

```
Flow Span (root)
├── Node Span: "http_fetch"
│   └── Tool Span: "http_request"
├── Node Span: "agent_analyze"
│   ├── LLM Span: "gpt-4o call #1"
│   ├── Tool Span: "search_knowledge"
│   ├── LLM Span: "gpt-4o call #2"
│   ├── Tool Span: "calculate"
│   └── LLM Span: "gpt-4o call #3" (final answer)
└── Node Span: "transform_output"
```

**规范要点**：
- 使用 OpenTelemetry 标准，Span 之间正确建立 parent-child 关系
- 每个 Span 携带关键属性：`flow.id`, `node.id`, `model.name`, `tool.name`
- SSE 事件携带 `traceId`，前端可关联到追踪系统

### 9.4 Agent 质量评估体系

| 评估维度 | 指标 | 方法 |
|----------|------|------|
| **任务完成度** | 最终输出是否满足需求 | LLM-as-Judge / 人工标注 |
| **工具使用效率** | 调用次数 vs 最优解 | 对比基准执行路径 |
| **推理质量** | 中间推理步骤的逻辑性 | Trajectory 评估 |
| **安全性** | 是否触发 Guardrails | Guardrails 拦截率 |
| **成本效率** | Token 消耗 / 完成时间 | 与基线对比 |
| **幻觉率** | 输出是否基于工具结果 | Faithfulness 检测 |

### 9.5 实时事件流规范

所有运行时事件通过 SSE（Server-Sent Events）实时推送到前端：

```
Event Types:
├── FLOW_STARTED / FLOW_COMPLETED / FLOW_FAILED
├── NODE_ENTER / NODE_EXIT / NODE_TIMEOUT / NODE_RETRY
├── AGENT_THINK / AGENT_ACT / AGENT_OBSERVE / AGENT_DONE
├── AGENT_TOOL_TIMEOUT / AGENT_TOOL_RATE_LIMITED
├── AGENT_TOKEN_PRUNE / AGENT_OUTPUT_VALIDATION_FAILED
├── BREAKPOINT_HIT / CONTEXT_UPDATED
└── SUBFLOW_STARTED / SUBFLOW_COMPLETED

Event Structure:
{
  "type": "AGENT_ACT",
  "flowId": "...",
  "nodeId": "...",
  "timestamp": "...",
  "traceId": "...",
  "data": {
    "toolName": "search_knowledge",
    "toolCallId": "call_abc123",
    "arguments": { "query": "..." }
  }
}
```

---

## 十、韧性与容错设计规范

### 10.1 韧性设计总览

```
┌────────────────────────────────────────────────────┐
│                  Resilience Layers                   │
├────────────────────────────────────────────────────┤
│  L1: Node Timeout          → 防止单节点阻塞全流程    │
│  L2: Node Retry            → 自动恢复瞬时故障        │
│  L3: Error Edge            → 失败走备选路径          │
│  L4: Tool Timeout          → 防止工具调用卡死        │
│  L5: LLM Retry             → 处理 API 瞬时故障      │
│  L6: Token Budget          → 防止上下文溢出          │
│  L7: Result Truncation     → 防止单工具结果占满预算   │
│  L8: Rate Limiting         → 防止工具滥用            │
│  L9: Output Guardrails     → 防止无效输出            │
│  L10: Circuit Breaker      → 持续故障时快速失败       │
└────────────────────────────────────────────────────┘
```

### 10.2 超时策略规范

| 层级 | 默认超时 | 配置方式 | 超时行为 |
|------|---------|---------|---------|
| 流程总超时 | 30 min | 流程定义级 | 取消所有运行中节点 |
| 节点超时 | 5 min | 节点 config.timeout | FAILED + NODE_TIMEOUT event |
| Tool 超时 | 30 s | Agent config.toolTimeout | 错误回传 LLM，循环继续 |
| LLM 调用超时 | 60 s | LLM provider 配置 | 触发 retry（如果是 transient） |
| HTTP 连接超时 | 10 s | HTTP 节点 config | 节点失败或走错误边 |

### 10.3 重试策略规范

```java
public record RetryPolicy(
    int maxAttempts,           // 最大尝试次数（含首次）
    long backoffMs,            // 初始退避时间
    double backoffMultiplier,  // 退避倍数
    long maxBackoffMs,         // 最大退避时间（cap）
    Set<String> retryableErrors // 可重试的错误类型
) {
    public long computeBackoff(int attempt) {
        long backoff = (long)(backoffMs * Math.pow(backoffMultiplier, attempt - 1));
        return Math.min(backoff, maxBackoffMs);
    }
}
```

**分层重试**：
- **节点级**：由引擎控制，每次重试取新快照
- **LLM 级**：装饰器模式，区分 transient(5xx/429/timeout) vs non-transient(4xx)
- **Tool 级**：通常不重试（Tool 超时直接返回错误给 LLM 自行决策）

### 10.4 Token 预算管理规范

```
消息列表
├── System Prompt (永远保留)
├── [早期对话...]  ← 预算超出时从这里开始裁剪
├── ...
├── 最近 N 轮交互 (保留)
└── 最新 User Message (保留)

裁剪策略：
1. 计算当前估算 token 数
2. 如果 <= budget，不裁剪
3. 否则，从 index 1 开始删除最旧的 tool_result 和 assistant 消息
4. 插入占位符 "[Earlier context removed due to token budget]"
5. 重新计算，确保 <= budget
6. 发布 AGENT_TOKEN_PRUNE 事件
```

### 10.5 Circuit Breaker 规范（高级）

当某个 LLM provider 或 Tool 持续失败时，启用熔断：

```
CLOSED (正常) → 连续 N 次失败 → OPEN (拒绝请求)
                                      ↓ 等待 cooldown
                               HALF-OPEN (探测)
                                      ↓ 成功
                               CLOSED (恢复)
```

---

## 十一、可视化编辑器设计规范

### 11.1 编辑器架构分层

```
┌─────────────────────────────────────────────────────┐
│  Layer 4: 业务组件层                                  │
│  - NodeConfigPanel (每种节点类型专用配置面板)           │
│  - VariableSelector (变量引用选择器)                   │
│  - ExpressionEditor (表达式编辑器)                    │
├─────────────────────────────────────────────────────┤
│  Layer 3: DAG 交互层                                 │
│  - DagEditor (画布 + 节点 + 边)                      │
│  - NodePalette (节点拖拽面板)                         │
│  - MiniMap + AutoLayout                             │
├─────────────────────────────────────────────────────┤
│  Layer 2: 图引擎层                                   │
│  - Vue Flow / React Flow (节点/边渲染 + 交互)         │
│  - Layout Engine (dagre/elk 自动布局)                │
├─────────────────────────────────────────────────────┤
│  Layer 1: 数据模型层                                  │
│  - DSL ↔ Graph Model 双向同步                        │
│  - Undo/Redo 历史栈                                 │
│  - Validation Engine                                │
└─────────────────────────────────────────────────────┘
```

### 11.2 三栏布局规范

```
┌──────────┬──────────────────────────┬──────────────┐
│ 节点面板  │       DAG 画布            │  配置面板     │
│ (左侧)   │    (可缩放/平移)          │  (右侧)      │
│          │                          │             │
│ ┌──────┐ │  ┌───┐   ┌───┐         │ 节点属性     │
│ │Agent │ │  │ A │──→│ B │         │ ├─ 名称      │
│ ├──────┤ │  └───┘   └─┬─┘         │ ├─ 类型      │
│ │ HTTP │ │            │            │ ├─ 配置表单   │
│ ├──────┤ │         ┌──▼──┐        │ │  (结构化)   │
│ │Code  │ │         │  C  │        │ ├─ 超时/重试   │
│ ├──────┤ │         └─────┘        │ └─ 高级选项   │
│ │ RAG  │ │                          │             │
│ └──────┘ │                          │             │
├──────────┴──────────────────────────┴──────────────┤
│ 底部状态栏：验证错误 | 节点数 | 缩放比例              │
└────────────────────────────────────────────────────┘
```

### 11.3 节点可视化规范

| 元素 | 规范 |
|------|------|
| **节点形状** | 圆角矩形，按类型着色（AI=紫色，控制流=蓝色，集成=绿色） |
| **节点图标** | 每种类型有唯一图标，16x16px SVG |
| **节点标签** | 居中显示，最大 20 字符，超出截断 |
| **端口** | 输入端口在上方，输出端口在下方，error 端口用红色标识 |
| **状态指示** | 运行时：运行中=蓝色脉冲，成功=绿勾，失败=红叉，挂起=橙色 |
| **断点标识** | 左上角红色圆点 |

### 11.4 边（Edge）可视化规范

| 类型 | 样式 |
|------|------|
| Normal | 蓝色实线，带箭头 |
| Conditional | 蓝色虚线，边上显示条件摘要 |
| Error | 红色虚线，带 "error" 标签 |
| Selected | 绿色加粗 |
| Animated | 运行时数据流方向动画 |

### 11.5 节点配置面板设计原则

| 原则 | 说明 |
|------|------|
| **每种节点类型一个专用面板** | 而非通用 JSON 编辑器 |
| **渐进式披露** | 基础配置默认展开，高级选项折叠 |
| **变量引用可视化** | `{{variable}}` 自动提示可用变量 |
| **即时验证** | 配置变更实时校验，红色标记错误字段 |
| **JSON 兜底** | 始终保留"高级 JSON 编辑"入口 |

### 11.6 运行时调试视图

```
┌──────────────────────────────────────────┐
│  DAG Viewer (只读，标记执行状态)            │
│  - 已执行节点绿色高亮                      │
│  - 当前节点蓝色脉冲                       │
│  - 失败节点红色                           │
│  - 点击节点查看快照 diff                   │
├──────────────────────────────────────────┤
│  Event Timeline (事件流)                  │
│  - 时间线展示 + 类型筛选                   │
│  - Agent 节点展开显示 THINK/ACT/OBSERVE    │
│  - 点击事件跳转对应 Trace                  │
├──────────────────────────────────────────┤
│  调试控制栏                               │
│  - [Resume] [Step] [Cancel] [Retry Node] │
│  - 断点管理                              │
│  - 上下文变量查看/编辑                     │
└──────────────────────────────────────────┘
```

---

## 十二、DSL 与契约设计规范

### 12.1 DSL 设计原则

| 原则 | 说明 |
|------|------|
| **声明式** | 描述 "做什么" 而非 "怎么做" |
| **向后兼容** | 新增字段可选 + 有默认值，旧定义无需修改 |
| **人类可读** | YAML/JSON 格式，字段名自解释 |
| **Schema 驱动** | 每个版本对应 JSON Schema，支持校验 |
| **可序列化** | 定义可完整序列化/反序列化，无运行时依赖 |

### 12.2 完整 DSL 结构规范

```yaml
# Flow Definition DSL v1
version: "1.0"
id: "customer-support-flow"
name: "客户支持智能流程"
description: "处理客户工单的自动化流程"
metadata:
  author: "team-ai"
  tags: ["support", "agent"]
  createdAt: "2026-06-18T10:00:00Z"

# 流程级变量声明
variables:
  input:
    customer_query: { type: string, required: true }
    customer_id: { type: string, required: true }
  output:
    response: { type: string }
    resolution_status: { type: string, enum: [resolved, escalated, pending] }

# 节点定义
nodes:
  - id: "classify"
    type: "agent"
    name: "意图分类"
    config:
      model: "gpt-4o-mini"
      systemPrompt: "分类客户意图..."
      outputSchema:
        type: object
        properties:
          intent: { type: string, enum: [billing, technical, general] }
          urgency: { type: string, enum: [low, medium, high] }
        required: [intent, urgency]
    output: "classification"
    timeout: 60000
    retryPolicy:
      maxAttempts: 2
      backoffMs: 1000

  - id: "route"
    type: "condition"
    name: "路由分发"
    config:
      conditions:
        - expression: "#classification.intent == 'billing'"
          target: "billing_agent"
        - expression: "#classification.intent == 'technical'"
          target: "tech_agent"
        - expression: "true"
          target: "general_agent"

  - id: "billing_agent"
    type: "agent"
    name: "账单处理 Agent"
    config:
      model: "gpt-4o"
      systemPrompt: "你是账单专员..."
      tools: ["query_billing", "create_refund"]
      maxIterations: 8
      toolTimeout: 30000
      maxTokenBudget: 80000
      toolResultMaxLength: 4000
      outputSchema: { ... }
    output: "agent_result"

# 边定义
edges:
  - from: "classify"
    to: "route"
    type: "normal"
  - from: "route"
    to: "billing_agent"
    type: "conditional"
    condition: "#classification.intent == 'billing'"
  - from: "billing_agent"
    to: "respond"
    type: "normal"
  - from: "billing_agent"
    to: "escalate"
    type: "error"
```

### 12.3 API 契约规范

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/definitions` | GET/POST | 流程定义 CRUD |
| `/api/v1/definitions/{id}` | GET/PUT/DELETE | 单个定义操作 |
| `/api/v1/definitions/{id}/publish` | POST | 发布定义 |
| `/api/v1/definitions/{id}/versions` | GET | 版本历史 |
| `/api/v1/flows/start` | POST | 启动流程实例 |
| `/api/v1/flows/{id}` | GET | 获取实例状态 |
| `/api/v1/flows/{id}/resume` | POST | 恢复挂起流程 |
| `/api/v1/flows/{id}/retry/{nodeId}` | POST | 重试节点 |
| `/api/v1/flows/{id}/cancel` | POST | 取消流程 |
| `/api/v1/flows/{id}/stream` | GET (SSE) | 实时事件流 |
| `/api/v1/flows/{id}/snapshots` | GET | 快照列表 |
| `/api/v1/flows/{id}/diff/{nodeId}` | GET | 节点前后 diff |
| `/api/v1/debug/{id}/breakpoint/{nodeId}` | POST/DELETE | 断点管理 |
| `/api/v1/debug/{id}/step` | POST | 单步执行 |
| `/api/v1/debug/{id}/context` | PUT | 热更新上下文 |

### 12.4 版本策略

- API URL 包含版本号 (`/api/v1/`)
- DSL 定义包含 `version` 字段
- 破坏性变更递增大版本号
- 保持至少 N-1 版本向后兼容

---

## 十三、生产化部署方法论

### 13.1 生产化六步法

```
Step 1: 锁定契约
├── Agent 输入/输出 Schema 冻结
├── Tool 接口定义冻结
└── API 版本策略确定

Step 2: 选择可调试的编排框架
├── 状态可持久化、可恢复
├── 执行路径可追踪
└── 支持断点/单步/热更新

Step 3: 端到端可观测性
├── 四级 Span (Flow→Node→LLM→Tool)
├── 结构化日志 (JSON + MDC)
├── 指标 + 告警 (Prometheus + Grafana)
└── SSE 事件含 traceId

Step 4: 离线评估体系
├── Golden Dataset (标准答案集)
├── LLM-as-Judge 自动评分
├── Trajectory 评估 (不只看最终结果)
└── 回归测试 (prompt/tool 变更后自动跑)

Step 5: 在线质量监控
├── 输出质量打分 (sampling)
├── 异常检测 (成本/延迟/失败率突增)
├── Drift 检测 (输出分布变化)
└── A/B 测试 (不同 prompt/model 对比)

Step 6: 安全 Guardrails
├── 输入过滤 (PII/注入检测)
├── 输出校验 (Schema + 敏感内容)
├── Tool 调用权限控制
└── 人工升级通道 (confidence < threshold)
```

### 13.2 部署架构规范

```
┌─────────────────────────────────────────────────┐
│                 Load Balancer                     │
├─────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │  App #1  │  │  App #2  │  │  App #3  │      │
│  │ (Engine) │  │ (Engine) │  │ (Engine) │      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘      │
│       │              │              │            │
├───────┼──────────────┼──────────────┼────────────┤
│  ┌────▼──────────────▼──────────────▼────┐       │
│  │         Shared Infrastructure          │       │
│  │  ┌─────────┐ ┌──────────┐ ┌────────┐ │       │
│  │  │PostgreSQL│ │  Redis   │ │Vector  │ │       │
│  │  │ (state) │ │ (cache/  │ │  DB    │ │       │
│  │  │         │ │  lock)   │ │(RAG)   │ │       │
│  │  └─────────┘ └──────────┘ └────────┘ │       │
│  └───────────────────────────────────────┘       │
├──────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────┐     │
│  │         Observability Stack              │     │
│  │  Prometheus + Grafana + Jaeger/Tempo     │     │
│  └─────────────────────────────────────────┘     │
└──────────────────────────────────────────────────┘
```

### 13.3 水平扩展要点

| 组件 | 扩展策略 |
|------|---------|
| 引擎实例 | 无状态化（上下文存 Redis/DB）+ 分布式锁防并发 |
| 流程执行 | 按 flowId hash 分片，同一流程固定在一个实例 |
| LLM 调用 | 连接池 + 限流 + provider 级 circuit breaker |
| SSE 连接 | Sticky session 或 Redis Pub/Sub 广播 |
| 知识库 | 独立部署 Vector DB，与引擎解耦 |

### 13.4 优雅停机规范

```
1. 收到 SIGTERM
2. 停止接收新的 flow.start() 请求
3. 等待运行中流程到达安全点（节点边界）
4. 持久化所有活跃 FlowContext 到 DB
5. 关闭 SSE 连接（发送 SHUTDOWN 事件）
6. 排空线程池（给定超时 30s）
7. 关闭数据库连接
8. 退出
```

---

## 十四、开发方法论与工程实践

### 14.1 Spec-Driven Development（规范驱动开发）

Agentic Workflow 项目推荐采用三阶段规范驱动流程：

```
Requirements (需求规范)
    ↓ 分析 + 明确验收标准
Design (设计规范)
    ↓ 架构决策 + 接口定义 + 正确性属性
Tasks (实现计划)
    ↓ 依赖排序 + 逐步实现 + 检查点
Implementation
    ↓ 编码 + 属性测试 + 单元测试
Verification
    ↓ 编译 + 测试 + 集成验证
```

**关键实践**：
- 每个 Feature 对应一份 Requirements → Design → Tasks 三件套
- Design 中明确定义**正确性属性**（Correctness Properties）
- Tasks 中标注依赖关系和并行度（wave-based 执行）
- 属性测试（Property-Based Testing）验证核心不变量

### 14.2 测试策略金字塔

```
                    ╱╲
                   ╱  ╲     E2E Tests
                  ╱ UI ╲    (Playwright)
                 ╱──────╲
                ╱        ╲   Integration Tests
               ╱ API/SSE  ╲  (Spring Boot Test)
              ╱────────────╲
             ╱              ╲  Property Tests
            ╱   Correctness  ╲ (jqwik/Hypothesis)
           ╱──────────────────╲
          ╱                    ╲ Unit Tests
         ╱   Components/Utils   ╲ (JUnit/Vitest)
        ╱────────────────────────╲
```

**每层职责**：

| 层级 | 工具 | 验证内容 |
|------|------|---------|
| Unit | JUnit 5 + Mockito | 单个类/方法的行为 |
| Property | jqwik / QuickCheck | 核心算法在所有有效输入下的不变量 |
| Integration | Spring Boot Test | 模块协作、API 端点、DB 操作 |
| E2E | Playwright / Cypress | 前端流程完整操作链 |

### 14.3 Property-Based Testing 实践

对于 Agentic Workflow 引擎，以下场景**必须**使用属性测试：

| 场景 | 属性 |
|------|------|
| 超时机制 | ∀ (timeout, duration): duration > timeout ⇒ FAILED |
| 重试机制 | ∀ (maxAttempts, failures): attempts = min(failures+1, max) |
| 退避计算 | ∀ (base, mult, attempt): backoff = base × mult^(attempt-1) |
| Token 估算 | ∀ (string): estimate = f(cjk_count, ascii_count) + overhead |
| 结果截断 | ∀ (s, maxLen): truncated starts with head, ends with tail, contains marker |
| Token 裁剪 | ∀ (messages, budget): post_prune_tokens ≤ budget ∧ system_preserved |
| DAG 执行 | ∀ (dag): executed_set ⊆ reachable_set ∧ order_respects_edges |

### 14.4 模块化架构实践

推荐的 Maven/Gradle 多模块划分：

```
project-root/
├── common/          # 共享模型、接口、工具类
├── core/            # 引擎内核：DAG执行、状态机、事件
├── components/      # 内置节点实现
├── agent/           # Agent 执行器、LLM 集成、Tool 管理
├── knowledge/       # RAG、知识库、向量检索 (新模块)
├── persistence/     # JPA、Flyway、仓储实现
├── api/             # REST Controller、DTO、SSE
├── server/          # Spring Boot 启动器、配置
└── ui/              # 前端工程（独立构建）
```

**模块依赖规则**：
- `common` 不依赖任何其他模块
- `core` 只依赖 `common`
- `agent` 依赖 `common` + `core`(事件系统)
- `components` 依赖 `common` + `core`
- `api` 依赖所有业务模块
- `server` 聚合所有模块

### 14.5 LLM 集成工程实践

| 实践 | 说明 |
|------|------|
| **Provider 抽象** | 接口层隔离具体 LLM 提供商（OpenAI/Anthropic/本地模型） |
| **装饰器模式** | Retry、Logging、Metrics 通过装饰器叠加，不侵入核心实现 |
| **结构化输出** | 优先使用 provider 的 structured output 而非 prompt 约束 |
| **Fallback 链** | 主模型不可用时自动降级到备选模型 |
| **成本追踪** | 每次调用记录 input/output tokens，按模型计费价格计算 |
| **Prompt 版本化** | System prompt 存储在定义中，变更可追溯 |

### 14.6 前端工程实践

| 实践 | 说明 |
|------|------|
| **TypeScript 强制** | 类型安全 + IDE 重构支持 + API 类型自动生成 |
| **Composition API** | 逻辑复用 composables，避免 Mixins |
| **API 层自动生成** | 从 OpenAPI spec 生成 TS 客户端 |
| **组件粒度** | 单一职责，每种节点类型对应一个配置面板组件 |
| **状态管理** | Pinia composition stores，按领域划分 |
| **SSE 封装** | 抽象 composable 管理连接生命周期和重连 |
| **测试覆盖** | Vitest(单元) + Vue Test Utils(组件) + Playwright(E2E) |

### 14.7 CI/CD 流水线

```
Push → Lint → Compile → Unit Tests → Property Tests
    → Integration Tests → Build Docker Image
    → Deploy Staging → E2E Tests → Deploy Production
```

**关键检查点**：
- Property tests 必须通过（核心不变量保证）
- API contract tests（防止破坏性变更）
- LLM 调用使用 mock（CI 中不实际调用 LLM）
- 前后端独立构建但联合 E2E

---

## 十五、参考文献

### 学术论文

1. **[arxiv:2512.08769]** "A Practical Guide for Designing, Developing, and Deploying Production-Grade Agentic AI Workflows" — 生产级 Agentic AI 系统的设计、开发、部署实践指南。[来源](https://arxiv.org/abs/2512.08769)

2. **[arxiv:2504.02441]** "Cognitive Memory in Large Language Models" — LLM Agent 的认知记忆架构研究，覆盖情景记忆、语义记忆、程序性记忆的实现。[来源](https://arxiv.org/html/2504.02441v1)

3. **[arxiv:2511.12960]** "ENGRAM: Effective, Lightweight Memory Orchestration for Conversational Agents" — 将对话组织为情景、语义、程序三种记忆类型的轻量级系统。[来源](https://arxiv.org/html/2511.12960v1)

4. **[arxiv:2403.11322]** "StateFlow: Enhancing LLM Task-Solving through State-Driven Workflows" — 将 LLM 任务求解建模为状态机的方法论。[来源](https://arxiv.org/html/2403.11322v1)

5. **[MDPI 2025]** "LLM-Based Multi-Agent Orchestration: A Survey of Frameworks, Communication Protocols, and Emerging Patterns" — 集中式/分布式/层级式三种拓扑的多 Agent 编排综述。[来源](https://www.mdpi.com/1999-5903/18/6/326)

### 行业实践

6. **HuggingFace** "Design Patterns for Building Agentic Workflows" — 六大基础设计模式：Evaluator-Optimizer, Context-Augmentation, Prompt-Chaining, Parallelization, Routing, Orchestrator-Workers。[来源](https://huggingface.co/blog/dcarpintero/design-patterns-for-building-agentic-workflows)

7. **Augment Code** "What Are Agentic Design Patterns? 2026 Pattern Catalog" — Agentic 设计模式的系统分类。[来源](https://www.augmentcode.com/guides/agentic-design-patterns)

8. **Prompt Engineering Org** "The 2026 Playbook for Building Reliable Agentic Workflows" — 构建可靠 Agent 系统的实操指南，含 guardrails 和验证循环。[来源](https://promptengineering.org/agents-at-work-the-2026-playbook-for-building-reliable-agentic-workflows/)

9. **DataRobot** "Production-ready agentic AI: evaluation, monitoring, and governance" — Agent 全生命周期的评估、监控与治理。[来源](https://www.datarobot.com/blog/production-ready-agentic-ai-evaluation-monitoring-governance/)

10. **FutureAGI** "How to Productionize Agentic Applications" — 六步法将 Agent 应用生产化。[来源](https://futureagi.com/blog/how-to-productionize-agentic-applications)

### 平台与框架

11. **LangGraph** — 基于状态图的 Agent 编排框架，支持有环图和检查点。
12. **CrewAI** — 多 Agent 协作框架，角色定义 + 任务分配模式。
13. **Dify** — 开源 LLM 应用平台，可视化工作流 + RAG + Agent。[来源](https://docs.dify.ai)
14. **AutoGen (Microsoft)** — 多 Agent 对话协作框架。
15. **Strands Agents (AWS)** — 高级编排技术的 Agent 框架。[来源](https://aws.amazon.com/blogs/machine-learning/customize-agent-workflows-with-advanced-orchestration-techniques-using-strands-agents/)
16. **Serverless Workflow Specification** — 声明式工作流 DSL 标准。[来源](https://github.com/serverlessworkflow/specification)

### 工具设计

17. **"Function Calling Patterns That Actually Work"** — Tool Schema 设计的实操经验。[来源](https://tianpan.co/blog/2025-10-12-tool-use-function-calling-patterns)
18. **"AI Agent Tool Design: Schemas, Descriptions, And Pitfalls"** — 大多数 Agent 失败是 Tool 设计失败。[来源](https://www.alexcloudstar.com/blog/ai-agent-tool-design-2026/)

### 可观测性

19. **Coralogix** "Agentic AI Observability: A Practical Guide for 2026"。[来源](https://coralogix.com/ai-blog/agentic-ai-observability/)
20. **Oracle** "OCI Observability for Agentic AI" — Agent 可观测性应成为持久的证据链。[来源](https://blogs.oracle.com/ai-and-datascience/oci-observability-for-agentic-ai)

---

## 附录 A：术语表

| 术语 | 定义 |
|------|------|
| **Agentic Workflow** | 将 LLM Agent 嵌入结构化工作流编排的系统架构 |
| **ReAct** | Reasoning + Acting 循环模式 |
| **DAG** | 有向无环图，工作流的拓扑结构 |
| **Tool** | Agent 可调用的外部能力/函数 |
| **RAG** | Retrieval-Augmented Generation，检索增强生成 |
| **Guardrails** | 约束 Agent 输出合规性的验证机制 |
| **Token Budget** | Agent 单次执行的 Token 消耗上限 |
| **Circuit Breaker** | 熔断器，持续失败时快速拒绝防止级联故障 |
| **Backoff** | 重试间隔的退避策略（通常指数增长） |
| **Span** | 分布式追踪中的一个操作单元 |
| **SSE** | Server-Sent Events，服务端实时推送 |
| **DSL** | Domain-Specific Language，领域特定语言 |
| **Correctness Property** | 系统在所有有效输入下必须满足的不变量 |

---

## 附录 B：AFlow 对标一览

| 白皮书规范 | AFlow 当前实现 | 差距 |
|-----------|---------------|------|
| 八大编排模式 | 支持 5/8 (缺并行/Orchestrator/Multi-Agent) | 中 |
| 四级可观测 | ✅ 完整实现 | 无 |
| 韧性十层 | 实现 9/10 (缺 Circuit Breaker) | 小 |
| RAG 知识检索 | ❌ 未实现 | 大 |
| 记忆系统 | 仅工作记忆(Token Budget) | 大 |
| 可视化编辑器三栏布局 | 有但粗糙，缺专用配置面板 | 大 |
| DSL 版本化 | ❌ 无版本管理 | 中 |
| 属性测试覆盖 | ✅ jqwik 9 个属性 | 无 |
| Tool 系统 | ✅ Composite + 动态选择 + 频率限制 | 小 |
| Spec-Driven 开发 | ✅ Requirements→Design→Tasks | 无 |
| TypeScript 前端 | ❌ 纯 JS | 大 |
| 生产化部署 | 缺容器化/优雅停机/分布式锁 | 中 |

---

*本白皮书综合了 2025-2026 年 Agentic AI 领域的学术研究、开源平台实践和生产经验，
旨在为 Agentic Workflow 平台的开发提供系统性的设计指导和质量基线。*
