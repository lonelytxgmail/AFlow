// ============================================================
// AFlow 核心类型定义
// ============================================================

// --- 流程定义相关 ---

export interface NodeDefinition {
  id: string
  type: NodeType
  name: string
  config: Record<string, any>
  output?: string
  breakpoint?: boolean
  timeout?: number
  retryPolicy?: RetryPolicy
  ui?: { x: number; y: number }
}

export interface EdgeDefinition {
  from: string
  to: string
  type?: EdgeType
  condition?: string
}

export interface FlowDefinition {
  id?: string
  name: string
  description?: string
  nodes: NodeDefinition[]
  edges: EdgeDefinition[]
  variables?: FlowVariables
}

export interface FlowVariables {
  input?: Record<string, VariableSchema>
  output?: Record<string, VariableSchema>
}

export interface VariableSchema {
  type: string
  required?: boolean
  description?: string
  default?: any
}

export interface RetryPolicy {
  maxAttempts: number
  backoffMs: number
  backoffMultiplier: number
}

// --- 节点/边类型枚举 ---

export type NodeType =
  | 'start'
  | 'http'
  | 'condition'
  | 'transform'
  | 'script'
  | 'assign'
  | 'delay'
  | 'callback'
  | 'log'
  | 'forEach'
  | 'while'
  | 'composite'
  | 'agent'
  | 'subflow'
  | 'approval'
  | 'llm'
  | 'parallel'
  | 'join'

export type EdgeType = 'normal' | 'error' | 'conditional'

// --- Agent 配置 ---

export interface AgentConfig {
  model?: string
  systemPrompt?: string
  userPrompt?: string
  tools?: string | string[]
  maxIterations?: number
  temperature?: number
  toolTimeout?: number
  maxTokenBudget?: number
  toolResultMaxLength?: number
  toolRateLimit?: ToolRateLimit
  outputSchema?: Record<string, any>
  outputValidation?: OutputValidation
}

export interface ToolRateLimit {
  maxCallsPerTool?: number
  maxTotalCalls?: number
}

export interface OutputValidation {
  strategy: 'retry' | 'fail' | 'passthrough'
  maxRetries?: number
}

// --- 流程实例相关 ---

export type FlowStatus =
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SUSPENDED'
  | 'CANCELLED'

export type NodeExecutionStatus = 'SUCCESS' | 'FAILED' | 'RUNNING' | 'SUSPENDED'

export interface NodeExecutionEntry {
  nodeId: string
  status: NodeExecutionStatus
}

export interface FlowInstance {
  id: string
  definitionId: string
  definitionName?: string
  status: FlowStatus
  variables: Record<string, any>
  executionPath: string[] | NodeExecutionEntry[]
  startedAt?: string
  completedAt?: string
}

// --- 事件相关 ---

export type FlowEventType =
  | 'FLOW_STARTED'
  | 'FLOW_COMPLETED'
  | 'FLOW_FAILED'
  | 'NODE_ENTER'
  | 'NODE_EXIT'
  | 'NODE_TIMEOUT'
  | 'NODE_RETRY'
  | 'AGENT_THINK'
  | 'AGENT_ACT'
  | 'AGENT_OBSERVE'
  | 'AGENT_DONE'
  | 'AGENT_TOOL_TIMEOUT'
  | 'AGENT_TOOL_RATE_LIMITED'
  | 'AGENT_TOKEN_PRUNE'
  | 'AGENT_OUTPUT_VALIDATION_FAILED'
  | 'BREAKPOINT_HIT'
  | 'CONTEXT_UPDATED'
  | 'PARALLEL_FORK'
  | 'PARALLEL_JOIN'
  | 'APPROVAL_REQUESTED'
  | 'APPROVAL_COMPLETED'
  | 'APPROVAL_TIMEOUT'

export interface FlowEvent {
  type: FlowEventType
  flowId: string
  nodeId?: string
  timestamp: string
  traceId?: string
  data?: Record<string, any>
}

// --- 快照 ---

export interface Snapshot {
  id: string
  flowId: string
  nodeId: string
  phase: 'BEFORE' | 'AFTER'
  variables: Record<string, any>
  timestamp: string
}

// --- 原子能力 ---

export type ComponentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface AtomicComponent {
  id: string
  name: string
  description?: string
  type: string
  config: Record<string, any>
  inputSchema?: Record<string, any>
  status: ComponentStatus
  createdAt?: string
  updatedAt?: string
}

// --- API 响应 ---

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}
