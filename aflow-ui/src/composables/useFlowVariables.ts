import { computed, type Ref, type ComputedRef } from 'vue'
import type { FlowDefinition } from '@/types'

// --- Types ---

export interface FlowVariable {
  /** Variable name (e.g., "result", "inputData") */
  name: string
  /** Source description (e.g., "node: httpCall", "flow input") */
  source: string
  /** Inferred type (e.g., "object", "string", "any") */
  type: string
}

export interface UseFlowVariablesReturn {
  /** Computed list of all available variables in the current flow */
  variables: ComputedRef<FlowVariable[]>
}

/** Built-in system variables available in every flow */
const BUILTIN_VARIABLES: FlowVariable[] = [
  { name: '#flowId', source: 'system', type: 'string' },
  { name: '#nodeId', source: 'system', type: 'string' },
  { name: '#timestamp', source: 'system', type: 'string' },
  { name: '#executionCount', source: 'system', type: 'number' },
]

/**
 * Composable that analyzes a flow DSL definition and collects all available
 * variables: flow-level inputs, node outputs, and built-in system variables.
 *
 * Usage:
 * ```ts
 * const dsl = ref<FlowDefinition>({ nodes: [], edges: [], ... })
 * const { variables } = useFlowVariables(dsl)
 * // variables.value => FlowVariable[]
 * ```
 */
export function useFlowVariables(
  dsl: Ref<FlowDefinition | null | undefined>
): UseFlowVariablesReturn {
  const variables = computed<FlowVariable[]>(() => {
    const result: FlowVariable[] = []

    const definition = dsl.value
    if (!definition) {
      return [...BUILTIN_VARIABLES]
    }

    // 1. Flow-level input variables
    if (definition.variables?.input) {
      for (const [name, schema] of Object.entries(definition.variables.input)) {
        result.push({
          name,
          source: 'flow input',
          type: schema.type || 'any',
        })
      }
    }

    // 2. Node output variables
    if (definition.nodes) {
      for (const node of definition.nodes) {
        if (node.output) {
          result.push({
            name: node.output,
            source: `node: ${node.name || node.id}`,
            type: inferNodeOutputType(node.type),
          })
        }
      }
    }

    // 3. Built-in system variables
    result.push(...BUILTIN_VARIABLES)

    return result
  })

  return { variables }
}

/**
 * Infer the output type of a node based on its type.
 * This provides a reasonable default; actual type may vary by config.
 */
function inferNodeOutputType(nodeType: string): string {
  switch (nodeType) {
    case 'http':
      return 'object'
    case 'script':
      return 'any'
    case 'transform':
      return 'any'
    case 'condition':
      return 'boolean'
    case 'agent':
      return 'object'
    case 'llm':
      return 'string'
    case 'assign':
      return 'any'
    case 'composite':
      return 'object'
    case 'subflow':
      return 'object'
    default:
      return 'any'
  }
}
