import { describe, it, expect, beforeEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import NodePalette from '@/components/dag/NodePalette.vue'
import { NODE_CATEGORIES, NODE_TYPE_REGISTRY, type NodeCategory } from '@/config/nodeTypes'

// Stub Element Plus el-input as a simple input element
const ElInputStub = {
  template: `<input
    :value="modelValue"
    @input="$emit('update:modelValue', $event.target.value)"
    :placeholder="placeholder"
    class="el-input"
  />`,
  props: ['modelValue', 'placeholder', 'size', 'clearable', 'prefixIcon'],
  emits: ['update:modelValue']
}

function createWrapper() {
  return mount(NodePalette, {
    global: {
      stubs: {
        'el-input': ElInputStub
      }
    }
  })
}

describe('NodePalette', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    wrapper = createWrapper()
  })

  describe('Category Rendering', () => {
    it('renders all node categories', () => {
      const categoryHeaders = wrapper.findAll('.category-header')
      const categoryKeys = Object.keys(NODE_CATEGORIES) as NodeCategory[]

      expect(categoryHeaders.length).toBe(categoryKeys.length)

      categoryKeys.forEach((key, index) => {
        const label = categoryHeaders[index].find('.category-label')
        expect(label.text()).toBe(NODE_CATEGORIES[key].label)
      })
    })

    it('renders all node types within each category', () => {
      const paletteNodes = wrapper.findAll('.palette-node')
      expect(paletteNodes.length).toBe(NODE_TYPE_REGISTRY.length)

      // Verify each node type is present by label
      NODE_TYPE_REGISTRY.forEach(nodeMeta => {
        const matchingNode = paletteNodes.find(
          n => n.find('.node-label').text() === nodeMeta.label
        )
        expect(matchingNode).toBeDefined()
      })
    })

    it('displays node icon and description for each node', () => {
      const paletteNodes = wrapper.findAll('.palette-node')

      NODE_TYPE_REGISTRY.forEach(nodeMeta => {
        const matchingNode = paletteNodes.find(
          n => n.find('.node-label').text() === nodeMeta.label
        )
        expect(matchingNode).toBeDefined()
        expect(matchingNode!.find('.node-icon').text()).toBe(nodeMeta.icon)
        expect(matchingNode!.find('.node-desc').text()).toBe(nodeMeta.description)
      })
    })
  })

  describe('Search Filtering', () => {
    it('filters nodes by label when typing in search', async () => {
      const input = wrapper.find('input.el-input')
      await input.setValue('Agent')
      await nextTick()

      const visibleNodes = wrapper.findAll('.palette-node')
      // Only "AI Agent" label matches "Agent"
      expect(visibleNodes.length).toBeGreaterThan(0)
      visibleNodes.forEach(node => {
        const label = node.find('.node-label').text()
        const desc = node.find('.node-desc').text()
        const matchesSearch =
          label.toLowerCase().includes('agent') ||
          desc.toLowerCase().includes('agent')
        expect(matchesSearch).toBe(true)
      })
    })

    it('filters nodes by description', async () => {
      const input = wrapper.find('input.el-input')
      await input.setValue('SpEL')
      await nextTick()

      const visibleNodes = wrapper.findAll('.palette-node')
      expect(visibleNodes.length).toBeGreaterThan(0)
      visibleNodes.forEach(node => {
        const label = node.find('.node-label').text()
        const desc = node.find('.node-desc').text()
        const type = NODE_TYPE_REGISTRY.find(n => n.label === label)?.type ?? ''
        const matchesSearch =
          label.toLowerCase().includes('spel') ||
          desc.toLowerCase().includes('spel') ||
          type.toLowerCase().includes('spel')
        expect(matchesSearch).toBe(true)
      })
    })

    it('shows no nodes when search matches nothing', async () => {
      const input = wrapper.find('input.el-input')
      await input.setValue('xyz_nonexistent_node_type')
      await nextTick()

      const visibleNodes = wrapper.findAll('.palette-node')
      expect(visibleNodes.length).toBe(0)
    })

    it('filters by node type string', async () => {
      const input = wrapper.find('input.el-input')
      await input.setValue('http')
      await nextTick()

      const visibleNodes = wrapper.findAll('.palette-node')
      expect(visibleNodes.length).toBeGreaterThan(0)
      // "HTTP 请求" should be visible
      const labels = visibleNodes.map(n => n.find('.node-label').text())
      expect(labels).toContain('HTTP 请求')
    })
  })

  describe('Category Collapse/Expand', () => {
    it('all categories are expanded by default', () => {
      const categoryNodes = wrapper.findAll('.category-nodes')
      categoryNodes.forEach(nodes => {
        // v-show sets display:none when collapsed; visible elements have no inline display:none
        expect(nodes.isVisible()).toBe(true)
      })
    })

    it('clicking a category header collapses its nodes', async () => {
      const firstHeader = wrapper.findAll('.category-header')[0]
      await firstHeader.trigger('click')
      await nextTick()

      const firstCategoryNodes = wrapper.findAll('.category-nodes')[0]
      expect(firstCategoryNodes.isVisible()).toBe(false)
    })

    it('clicking a collapsed category header expands it again', async () => {
      const header = wrapper.findAll('.category-header')[0]

      // Verify the toggle icon initially shows expanded
      expect(header.find('.category-toggle').text()).toBe('▾')

      // Collapse first
      await header.trigger('click')
      await nextTick()
      expect(header.find('.category-toggle').text()).toBe('▸')

      // Expand again
      await header.trigger('click')
      await nextTick()
      expect(header.find('.category-toggle').text()).toBe('▾')
    })

    it('toggling one category does not affect others', async () => {
      const headers = wrapper.findAll('.category-header')
      const categoryNodeGroups = wrapper.findAll('.category-nodes')

      // Collapse the first category
      await headers[0].trigger('click')
      await nextTick()

      expect(categoryNodeGroups[0].isVisible()).toBe(false)
      // Others remain visible
      for (let i = 1; i < categoryNodeGroups.length; i++) {
        expect(categoryNodeGroups[i].isVisible()).toBe(true)
      }
    })
  })

  describe('Drag Events', () => {
    it('each node element has draggable attribute', () => {
      const paletteNodes = wrapper.findAll('.palette-node')
      paletteNodes.forEach(node => {
        expect(node.attributes('draggable')).toBe('true')
      })
    })

    it('dragstart sets correct dataTransfer payload', async () => {
      const firstNode = wrapper.findAll('.palette-node')[0]
      const firstNodeMeta = NODE_TYPE_REGISTRY[0]

      const dataTransferData: Record<string, string> = {}
      const mockDataTransfer = {
        setData: (type: string, data: string) => {
          dataTransferData[type] = data
        },
        effectAllowed: ''
      }

      await firstNode.trigger('dragstart', {
        dataTransfer: mockDataTransfer
      })

      expect(dataTransferData['application/vueflow']).toBeDefined()
      const payload = JSON.parse(dataTransferData['application/vueflow'])
      expect(payload.type).toBe(firstNodeMeta.type)
      expect(payload.name).toBe(firstNodeMeta.label)
      expect(payload.config).toEqual(firstNodeMeta.defaultConfig)
    })

    it('dragstart sets effectAllowed to move', async () => {
      const firstNode = wrapper.findAll('.palette-node')[0]

      const mockDataTransfer = {
        setData: () => {},
        effectAllowed: ''
      }

      await firstNode.trigger('dragstart', {
        dataTransfer: mockDataTransfer
      })

      expect(mockDataTransfer.effectAllowed).toBe('move')
    })

    it('drag payload contains correct data for different node types', async () => {
      // Test the HTTP node specifically
      const httpMeta = NODE_TYPE_REGISTRY.find(n => n.type === 'http')!
      const paletteNodes = wrapper.findAll('.palette-node')
      const httpNode = paletteNodes.find(
        n => n.find('.node-label').text() === httpMeta.label
      )!

      const dataTransferData: Record<string, string> = {}
      const mockDataTransfer = {
        setData: (type: string, data: string) => {
          dataTransferData[type] = data
        },
        effectAllowed: ''
      }

      await httpNode.trigger('dragstart', {
        dataTransfer: mockDataTransfer
      })

      const payload = JSON.parse(dataTransferData['application/vueflow'])
      expect(payload.type).toBe('http')
      expect(payload.name).toBe('HTTP 请求')
      expect(payload.config).toEqual({ url: '', method: 'GET', headers: {}, body: '' })
    })
  })
})
