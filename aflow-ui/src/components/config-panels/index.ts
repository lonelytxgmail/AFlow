import { defineAsyncComponent, type Component } from 'vue'
import type { NodeType } from '@/types'

/**
 * 节点配置面板注册表
 *
 * 使用 defineAsyncComponent 懒加载各面板组件，
 * 按需加载以减少初始 bundle 大小。
 */
const panelRegistry: Partial<Record<NodeType, Component>> = {
  agent: defineAsyncComponent(() => import('./AgentConfigPanel.vue')),
  http: defineAsyncComponent(() => import('./HttpConfigPanel.vue')),
  condition: defineAsyncComponent(() => import('./ConditionConfigPanel.vue')),
  script: defineAsyncComponent(() => import('./ScriptConfigPanel.vue')),
  subflow: defineAsyncComponent(() => import('./SubflowConfigPanel.vue')),
  composite: defineAsyncComponent(() => import('./CompositeConfigPanel.vue')),
  assign: defineAsyncComponent(() => import('./AssignConfigPanel.vue')),
  delay: defineAsyncComponent(() => import('./DelayConfigPanel.vue')),
  log: defineAsyncComponent(() => import('./LogConfigPanel.vue')),
  // future panels
  llm: defineAsyncComponent(() => import('./LlmConfigPanel.vue')),
  approval: defineAsyncComponent(() => import('./ApprovalConfigPanel.vue')),
}

/**
 * 通用 JSON 编辑面板（兜底）
 */
const GenericConfigPanel = defineAsyncComponent(() => import('./GenericConfigPanel.vue'))

/**
 * 根据节点类型获取对应的配置面板组件。
 * 未注册的节点类型会返回通用 JSON 编辑面板。
 *
 * @param nodeType - 节点类型
 * @returns 对应的配置面板 Vue 组件
 */
export function getConfigPanel(nodeType: NodeType): Component {
  return panelRegistry[nodeType] ?? GenericConfigPanel
}

export { GenericConfigPanel }
export default panelRegistry
