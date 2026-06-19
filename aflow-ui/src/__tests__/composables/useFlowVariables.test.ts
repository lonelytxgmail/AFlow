import { describe, it, expect } from 'vitest'
import { ref, nextTick } from 'vue'
import { useFlowVariables, type FlowVariable } from '@/composables/useFlowVariables'
import type { FlowDefinition } from '@/types'

describe('useFlowVariables', () => {
  const BUILTIN_NAMES = ['#flowId', '#nodeId', '#timestamp', '#executionCount']

  describe('returns only built-in variables when DSL is null/undefined', () => {
    it('returns built-in variables when dsl is null', () => {
      const dsl = ref<FlowDefinition | null>(null)
      const { variables } = useFlowVariables(dsl)

      expect(variables.value).toHaveLength(4)
      const names = variables.value.map((v) => v.name)
      expect(names).toEqual(BUILTIN_NAMES)
      variables.value.forEach((v) => {
        expect(v.source).toBe('system')
      })
    })

    it('returns built-in variables when dsl is undefined', () => {
      const dsl = ref<FlowDefinition | undefined>(undefined)
      const { variables } = useFlowVariables(dsl)

      expect(variables.value).toHaveLength(4)
      const names = variables.value.map((v) => v.name)
      expect(names).toEqual(BUILTIN_NAMES)
    })
  })

  describe('extracts flow input variables from dsl.variables.input', () => {
    it('extracts input variables with correct names and types', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [],
        edges: [],
        variables: {
          input: {
            userId: { type: 'string', required: true },
            count: { type: 'number' },
          },
        },
      })
      const { variables } = useFlowVariables(dsl)

      const inputVars = variables.value.filter((v) => v.source === 'flow input')
      expect(inputVars).toHaveLength(2)
      expect(inputVars).toContainEqual({ name: 'userId', source: 'flow input', type: 'string' })
      expect(inputVars).toContainEqual({ name: 'count', source: 'flow input', type: 'number' })
    })

    it('defaults type to "any" when type is not specified', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [],
        edges: [],
        variables: {
          input: {
            data: { type: '' },
          },
        },
      })
      const { variables } = useFlowVariables(dsl)

      const inputVars = variables.value.filter((v) => v.source === 'flow input')
      expect(inputVars[0].type).toBe('any')
    })
  })

  describe('extracts node output variables from nodes with output field', () => {
    it('collects output variables from nodes that define an output', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [
          { id: 'n1', type: 'http', name: 'fetchUser', config: {}, output: 'userData' },
          { id: 'n2', type: 'script', name: 'transform', config: {} }, // no output
          { id: 'n3', type: 'condition', name: 'check', config: {}, output: 'checkResult' },
        ],
        edges: [],
      })
      const { variables } = useFlowVariables(dsl)

      const nodeVars = variables.value.filter((v) => v.source.startsWith('node:'))
      expect(nodeVars).toHaveLength(2)
      expect(nodeVars).toContainEqual({ name: 'userData', source: 'node: fetchUser', type: 'object' })
      expect(nodeVars).toContainEqual({ name: 'checkResult', source: 'node: check', type: 'boolean' })
    })

    it('uses node id when name is not specified', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [
          { id: 'node-123', type: 'script', name: '', config: {}, output: 'result' },
        ],
        edges: [],
      })
      const { variables } = useFlowVariables(dsl)

      const nodeVars = variables.value.filter((v) => v.source.startsWith('node:'))
      expect(nodeVars[0].source).toBe('node: node-123')
    })
  })

  describe('infers correct types for different node types', () => {
    const typeMapping: Array<{ nodeType: string; expectedType: string }> = [
      { nodeType: 'http', expectedType: 'object' },
      { nodeType: 'script', expectedType: 'any' },
      { nodeType: 'transform', expectedType: 'any' },
      { nodeType: 'condition', expectedType: 'boolean' },
      { nodeType: 'agent', expectedType: 'object' },
      { nodeType: 'llm', expectedType: 'string' },
      { nodeType: 'assign', expectedType: 'any' },
      { nodeType: 'composite', expectedType: 'object' },
      { nodeType: 'subflow', expectedType: 'object' },
    ]

    typeMapping.forEach(({ nodeType, expectedType }) => {
      it(`infers type "${expectedType}" for node type "${nodeType}"`, () => {
        const dsl = ref<FlowDefinition>({
          name: 'test-flow',
          nodes: [
            { id: 'n1', type: nodeType as any, name: 'testNode', config: {}, output: 'out' },
          ],
          edges: [],
        })
        const { variables } = useFlowVariables(dsl)

        const nodeVar = variables.value.find((v) => v.name === 'out')
        expect(nodeVar?.type).toBe(expectedType)
      })
    })

    it('defaults to "any" for unknown node types', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [
          { id: 'n1', type: 'unknown-type' as any, name: 'testNode', config: {}, output: 'out' },
        ],
        edges: [],
      })
      const { variables } = useFlowVariables(dsl)

      const nodeVar = variables.value.find((v) => v.name === 'out')
      expect(nodeVar?.type).toBe('any')
    })
  })

  describe('reactively updates when the DSL ref changes', () => {
    it('updates variables when dsl ref value changes', async () => {
      const dsl = ref<FlowDefinition | null>(null)
      const { variables } = useFlowVariables(dsl)

      // Initially only built-in
      expect(variables.value).toHaveLength(4)

      // Update DSL with nodes
      dsl.value = {
        name: 'test-flow',
        nodes: [
          { id: 'n1', type: 'http', name: 'api', config: {}, output: 'response' },
        ],
        edges: [],
      }
      await nextTick()

      // Now includes node output + built-in
      expect(variables.value).toHaveLength(5)
      expect(variables.value.find((v) => v.name === 'response')).toBeDefined()
    })

    it('reverts to built-in only when dsl becomes null', async () => {
      const dsl = ref<FlowDefinition | null>({
        name: 'test-flow',
        nodes: [
          { id: 'n1', type: 'http', name: 'api', config: {}, output: 'response' },
        ],
        edges: [],
      })
      const { variables } = useFlowVariables(dsl)

      expect(variables.value.length).toBeGreaterThan(4)

      dsl.value = null
      await nextTick()

      expect(variables.value).toHaveLength(4)
      expect(variables.value.every((v) => v.source === 'system')).toBe(true)
    })
  })

  describe('does not duplicate built-in variables', () => {
    it('built-in variables appear exactly once with a populated DSL', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [
          { id: 'n1', type: 'http', name: 'api', config: {}, output: 'response' },
        ],
        edges: [],
        variables: {
          input: {
            userId: { type: 'string' },
          },
        },
      })
      const { variables } = useFlowVariables(dsl)

      BUILTIN_NAMES.forEach((name) => {
        const matches = variables.value.filter((v) => v.name === name)
        expect(matches).toHaveLength(1)
      })
    })

    it('total variables = input vars + node output vars + 4 built-in', () => {
      const dsl = ref<FlowDefinition>({
        name: 'test-flow',
        nodes: [
          { id: 'n1', type: 'http', name: 'a', config: {}, output: 'out1' },
          { id: 'n2', type: 'script', name: 'b', config: {}, output: 'out2' },
        ],
        edges: [],
        variables: {
          input: {
            x: { type: 'string' },
            y: { type: 'number' },
          },
        },
      })
      const { variables } = useFlowVariables(dsl)

      // 2 input + 2 node output + 4 built-in = 8
      expect(variables.value).toHaveLength(8)
    })
  })
})
