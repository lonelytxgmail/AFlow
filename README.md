# AFlow — Agentic Workflow Engine

AFlow 是一个 **Agentic 工作流编排引擎**，支持将 LLM Agent、工具调用、人工审批、条件路由等能力以 DAG 图的形式可视化编排和执行。

## 核心特性

- **可视化 DAG 编辑器** — 三栏布局 + 拖拽编排 + 自动布局 + Undo/Redo
- **丰富的节点类型** — Agent、LLM、HTTP、条件分支、脚本、子流程、审批、并行等
- **Agent 节点** — ReAct 循环 + 工具调用 + Guardrails + Token 预算管理
- **并行执行** — 自动拓扑排序 + Wave 并行 + 失败策略
- **Human-in-the-Loop** — 审批节点 + 超时处理 + 前端审批页面
- **实时调试** — 断点 + 单步执行 + 变量热编辑 + SSE 事件推送
- **版本管理** — 发布自动快照 + 历史查看 + 一键回滚
- **监控面板** — 流程/Agent/LLM 维度指标 + 图表可视化
- **DSL 校验** — 实时结构化校验 + 错误定位

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        aflow-ui (Vue 3 + TypeScript)            │
│  ┌───────────┐  ┌─────────────┐  ┌─────────────────────────┐   │
│  │ NodePalette│  │  DagEditor  │  │  ConfigPanels/Debug      │   │
│  └───────────┘  └─────────────┘  └─────────────────────────┘   │
│                         │ REST + SSE                             │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────────┐
│                    aflow-api (Spring Boot REST)                  │
│  ┌──────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │DefinitionCtrl│  │ FlowCtrl │  │DebugCtrl │  │ApprovalCtrl│  │
│  └──────────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────────┐
│                    aflow-core (Engine)                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │WorkflowEngine│  │ DslParser    │  │ FlowDefinitionValid│    │
│  │(并行执行/重试)│  │(解析/校验)   │  │ (结构化校验)       │    │
│  └──────────────┘  └──────────────┘  └────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │ FlowEventBus │  │ SnapshotSvc  │  │ ExpressionEngine   │    │
│  └──────────────┘  └──────────────┘  └────────────────────┘    │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────────┐
│               aflow-agent (LLM + Tool)                          │
│  ┌──────────────────┐  ┌──────────────┐  ┌────────────────┐    │
│  │AgentNodeExecutor  │  │ LlmService   │  │ ToolRegistry   │    │
│  │(ReAct + Guardrails)│  │(重试/预算)   │  │(工具注册/调用) │    │
│  └──────────────────┘  └──────────────┘  └────────────────┘    │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────────────┐
│            aflow-persistence (JPA + Flyway)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │DefinitionRepo│  │ InstanceRepo │  │ ApprovalRequestRepo│    │
│  └──────────────┘  └──────────────┘  └────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Vue Flow + Element Plus + ECharts |
| 后端 | Java 21 + Spring Boot 3 + Maven multi-module |
| 引擎 | DAG 拓扑排序 + CompletableFuture 并行 + Virtual Threads |
| 表达式 | Spring Expression Language (SpEL) |
| 持久化 | Spring Data JPA + Flyway + H2/PostgreSQL |
| AI | Spring AI（多模型适配）+ ReAct Agent Loop |
| 实时通信 | Server-Sent Events (SSE) |
| 监控 | Micrometer + 自定义指标 |
| 测试 | JUnit 5 + jqwik (Property-Based Testing) + Vitest |

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- Maven 3.9+

### 后端启动

```bash
# 编译并运行测试
mvn clean verify

# 启动开发服务器
mvn spring-boot:run -pl aflow-api
```

服务默认运行在 `http://localhost:8080`

### 前端启动

```bash
cd aflow-ui

# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
```

前端默认运行在 `http://localhost:5173`，自动代理 API 请求到后端。

### 快速体验

1. 打开浏览器访问 `http://localhost:5173`
2. 在"定义管理"页面创建新的工作流定义
3. 从左侧面板拖拽节点到画布，连线编排
4. 配置节点参数后保存
5. 在"实例"页面启动流程，实时查看执行状态

## 项目结构

```
AFlow/
├── aflow-api/          # REST API 层（Controller + Service）
├── aflow-core/         # 引擎核心（执行器、DSL解析、事件总线）
├── aflow-agent/        # Agent 模块（LLM 调用、工具注册）
├── aflow-components/   # 节点执行器实现
├── aflow-common/       # 通用模型 + 异常 + 工具类
├── aflow-persistence/  # 数据持久化（JPA Entity + Repository）
├── aflow-ui/           # Vue 3 前端
└── doc/                # 文档
    ├── DSL-Reference.md    # DSL 完整规范
    ├── API-Reference.md    # REST API 参考
    └── examples/           # 集成验证场景
```

## 文档

- [DSL 参考文档](doc/DSL-Reference.md) — 节点类型、边类型、变量语法完整说明
- [API 参考文档](doc/API-Reference.md) — REST 端点 + SSE 事件 + 错误码

## License

Private — All rights reserved.
