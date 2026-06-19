import type { NodeType } from '@/types'

export type NodeCategory = 'control' | 'ai' | 'data' | 'integration'

export interface NodeTypeMeta {
  type: NodeType
  label: string
  description: string
  category: NodeCategory
  icon: string
  color: string
  defaultConfig: Record<string, any>
}

export const NODE_CATEGORIES: Record<NodeCategory, { label: string; color: string }> = {
  control: { label: '控制流', color: '#409EFF' },
  ai: { label: 'AI 推理', color: '#9B59B6' },
  data: { label: '数据处理', color: '#606266' },
  integration: { label: '集成', color: '#67C23A' }
}

export const NODE_TYPE_REGISTRY: NodeTypeMeta[] = [
  // 控制流
  {
    type: 'start',
    label: '开始',
    description: '流程起始节点',
    category: 'control',
    icon: '▶',
    color: '#409EFF',
    defaultConfig: {}
  },
  {
    type: 'condition',
    label: '条件分支',
    description: '基于 SpEL 表达式进行路由',
    category: 'control',
    icon: '⑂',
    color: '#409EFF',
    defaultConfig: { conditions: [] }
  },
  {
    type: 'forEach',
    label: '集合遍历',
    description: '遍历数组/集合中的每个元素',
    category: 'control',
    icon: '↻',
    color: '#409EFF',
    defaultConfig: { collection: '', itemVar: 'item' }
  },
  {
    type: 'while',
    label: '条件循环',
    description: '满足条件时重复执行',
    category: 'control',
    icon: '⟳',
    color: '#409EFF',
    defaultConfig: { condition: '', maxIterations: 100 }
  },
  {
    type: 'subflow',
    label: '子流程',
    description: '调用另一个工作流定义',
    category: 'control',
    icon: '⧉',
    color: '#409EFF',
    defaultConfig: { definitionId: '', params: {} }
  },
  {
    type: 'parallel',
    label: '并行分叉',
    description: '多分支并行执行',
    category: 'control',
    icon: '⫘',
    color: '#409EFF',
    defaultConfig: { strategy: 'waitAll' }
  },
  {
    type: 'join',
    label: '汇合',
    description: '等待并行分支完成',
    category: 'control',
    icon: '⫗',
    color: '#409EFF',
    defaultConfig: {}
  },
  {
    type: 'approval',
    label: '人工审批',
    description: '挂起等待人工审批决策',
    category: 'control',
    icon: '✋',
    color: '#E6A23C',
    defaultConfig: { title: '', description: '', options: [], timeoutMinutes: 60 }
  },
  // AI 推理
  {
    type: 'agent',
    label: 'AI Agent',
    description: 'ReAct 循环 Agent，可调用工具',
    category: 'ai',
    icon: '🤖',
    color: '#9B59B6',
    defaultConfig: { model: 'gpt-4o', maxIterations: 10, tools: '*' }
  },
  {
    type: 'llm',
    label: 'LLM 调用',
    description: '单次 LLM 推理（非 Agent）',
    category: 'ai',
    icon: '💬',
    color: '#9B59B6',
    defaultConfig: { model: 'gpt-4o-mini', temperature: 0 }
  },
  // 数据处理
  {
    type: 'transform',
    label: '数据转换',
    description: 'SpEL 表达式转换数据',
    category: 'data',
    icon: '⇄',
    color: '#606266',
    defaultConfig: { expression: '' }
  },
  {
    type: 'script',
    label: '脚本执行',
    description: '执行 Groovy 脚本',
    category: 'data',
    icon: '{ }',
    color: '#606266',
    defaultConfig: { language: 'groovy', script: '' }
  },
  {
    type: 'assign',
    label: '变量赋值',
    description: '设置上下文变量',
    category: 'data',
    icon: '=',
    color: '#606266',
    defaultConfig: { assignments: {} }
  },
  {
    type: 'log',
    label: '日志输出',
    description: '记录日志信息',
    category: 'data',
    icon: '📋',
    color: '#606266',
    defaultConfig: { message: '', level: 'INFO' }
  },
  // 集成
  {
    type: 'http',
    label: 'HTTP 请求',
    description: '调用外部 REST API',
    category: 'integration',
    icon: '🌐',
    color: '#67C23A',
    defaultConfig: { url: '', method: 'GET', headers: {}, body: '' }
  },
  {
    type: 'callback',
    label: '外部回调',
    description: '挂起等待外部系统回调',
    category: 'integration',
    icon: '📞',
    color: '#67C23A',
    defaultConfig: { callbackId: '' }
  },
  {
    type: 'delay',
    label: '延时等待',
    description: '等待指定时间后继续',
    category: 'integration',
    icon: '⏱',
    color: '#67C23A',
    defaultConfig: { delayMs: 1000 }
  },
  {
    type: 'composite',
    label: '原子能力',
    description: '引用已发布的原子能力组件',
    category: 'integration',
    icon: '🧩',
    color: '#67C23A',
    defaultConfig: { componentId: '', params: {} }
  }
]

export function getNodeMeta(type: NodeType): NodeTypeMeta | undefined {
  return NODE_TYPE_REGISTRY.find(n => n.type === type)
}

export function getNodesByCategory(category: NodeCategory): NodeTypeMeta[] {
  return NODE_TYPE_REGISTRY.filter(n => n.category === category)
}
