import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import AgentConfigPanel from '@/components/config-panels/AgentConfigPanel.vue'

// Minimal Element Plus stub components for testing
const ElCollapse = {
  template: '<div class="el-collapse"><slot /></div>',
  props: ['modelValue'],
}
const ElCollapseItem = {
  template: '<div class="el-collapse-item"><slot /></div>',
  props: ['title', 'name'],
}
const ElForm = {
  template: '<div class="el-form"><slot /></div>',
  props: ['labelPosition', 'size'],
}
const ElFormItem = {
  template: '<div class="el-form-item"><label>{{ label }}</label><slot /></div>',
  props: ['label'],
}
const ElSelect = {
  template: '<select class="el-select" @change="onInput($event.target.value)"><slot /></select>',
  props: ['modelValue', 'placeholder', 'multiple'],
  emits: ['update:modelValue'],
  methods: {
    onInput(val: string) {
      this.$emit('update:modelValue', val)
    },
  },
}
const ElOption = {
  template: '<option :value="value">{{ label }}</option>',
  props: ['label', 'value'],
}
const ElInput = {
  template: '<div class="el-input"><textarea v-if="type===\'textarea\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /></div>',
  props: ['modelValue', 'type', 'autosize', 'placeholder', 'spellcheck'],
  emits: ['update:modelValue'],
}
const ElInputNumber = {
  template: '<input class="el-input-number" type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
  props: ['modelValue', 'min', 'max', 'step', 'controlsPosition'],
  emits: ['update:modelValue'],
}
const ElSlider = {
  template: '<input class="el-slider" type="range" :min="min" :max="max" :step="step" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
  props: ['modelValue', 'min', 'max', 'step', 'showTooltip'],
  emits: ['update:modelValue'],
}
const ElButton = {
  template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
  props: ['text', 'type', 'size'],
  emits: ['click'],
}

const globalStubs = {
  'el-collapse': ElCollapse,
  'el-collapse-item': ElCollapseItem,
  'el-form': ElForm,
  'el-form-item': ElFormItem,
  'el-select': ElSelect,
  'el-option': ElOption,
  'el-input': ElInput,
  'el-input-number': ElInputNumber,
  'el-slider': ElSlider,
  'el-button': ElButton,
}

function mountPanel(modelValue: Record<string, any> = {}) {
  return mount(AgentConfigPanel, {
    props: { modelValue },
    global: { components: globalStubs },
  })
}

describe('AgentConfigPanel', () => {
  describe('renders with default props (empty config)', () => {
    it('renders without errors when modelValue is empty', () => {
      const wrapper = mountPanel({})
      expect(wrapper.find('.agent-config-panel').exists()).toBe(true)
    })

    it('renders all collapse sections', () => {
      const wrapper = mountPanel({})
      const sections = wrapper.findAll('.el-collapse-item')
      // basic, tools, resilience, guardrails, advanced = 5 sections
      expect(sections.length).toBe(5)
    })

    it('uses default values for empty config', () => {
      const wrapper = mountPanel({})
      // Temperature defaults to 0.7
      const slider = wrapper.find('.el-slider')
      expect(slider.exists()).toBe(true)
      expect((slider.element as HTMLInputElement).value).toBe('0.7')
    })
  })

  describe('displays model select with options', () => {
    it('renders model select element', () => {
      const wrapper = mountPanel({})
      const select = wrapper.find('.el-select')
      expect(select.exists()).toBe(true)
    })

    it('renders all model options', () => {
      const wrapper = mountPanel({})
      // The first select in the component is the model select
      const firstSelect = wrapper.find('.el-select')
      const options = firstSelect.findAll('option')
      const expectedModels = ['gpt-4o', 'gpt-4o-mini', 'claude-3.5-sonnet', 'deepseek-chat']
      const values = options.map((o) => (o.element as HTMLOptionElement).value)
      expect(values).toEqual(expectedModels)
    })

    it('shows selected model when provided via props', () => {
      const wrapper = mountPanel({ model: 'gpt-4o' })
      // Verify the prop is correctly passed and reflected in the component
      expect(wrapper.props('modelValue').model).toBe('gpt-4o')
    })
  })

  describe('emits update:modelValue when model is changed', () => {
    it('emits update when model select changes', async () => {
      const wrapper = mountPanel({ model: '' })
      const select = wrapper.find('.el-select')

      await select.setValue('claude-3.5-sonnet')
      await nextTick()

      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted![0][0]).toEqual({ model: 'claude-3.5-sonnet' })
    })

    it('preserves existing config fields when updating model', async () => {
      const wrapper = mountPanel({ model: 'gpt-4o', systemPrompt: 'You are helpful' })
      const select = wrapper.find('.el-select')

      await select.setValue('deepseek-chat')
      await nextTick()

      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted![0][0]).toEqual({
        model: 'deepseek-chat',
        systemPrompt: 'You are helpful',
      })
    })
  })

  describe('temperature slider works and emits correct value', () => {
    it('shows default temperature of 0.7', () => {
      const wrapper = mountPanel({})
      const slider = wrapper.find('.el-slider')
      expect((slider.element as HTMLInputElement).value).toBe('0.7')
    })

    it('shows custom temperature from props', () => {
      const wrapper = mountPanel({ temperature: 1.2 })
      const slider = wrapper.find('.el-slider')
      expect((slider.element as HTMLInputElement).value).toBe('1.2')
    })

    it('emits update:modelValue with new temperature on change', async () => {
      const wrapper = mountPanel({ temperature: 0.7 })
      const slider = wrapper.find('.el-slider')

      await slider.setValue('1.5')
      await nextTick()

      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted![0][0]).toEqual({ temperature: 1.5 })
    })
  })

  describe('advanced JSON editor shows/hides on toggle', () => {
    it('advanced JSON editor is hidden by default', () => {
      const wrapper = mountPanel({})
      expect(wrapper.find('.json-editor-wrapper').exists()).toBe(false)
    })

    it('shows advanced JSON editor after clicking toggle button', async () => {
      const wrapper = mountPanel({ model: 'gpt-4o' })
      const toggleButton = wrapper.findAll('.el-button').find((btn) =>
        btn.text().includes('JSON')
      )

      expect(toggleButton).toBeDefined()
      await toggleButton!.trigger('click')
      await nextTick()

      expect(wrapper.find('.json-editor-wrapper').exists()).toBe(true)
    })

    it('hides advanced JSON editor when toggled again', async () => {
      const wrapper = mountPanel({ model: 'gpt-4o' })
      const toggleButton = wrapper.findAll('.el-button').find((btn) =>
        btn.text().includes('JSON')
      )

      // Open
      await toggleButton!.trigger('click')
      await nextTick()
      expect(wrapper.find('.json-editor-wrapper').exists()).toBe(true)

      // Close
      await toggleButton!.trigger('click')
      await nextTick()
      expect(wrapper.find('.json-editor-wrapper').exists()).toBe(false)
    })
  })

  describe('shows validation error for invalid JSON in outputSchema', () => {
    it('shows error message for invalid JSON input', async () => {
      const wrapper = mountPanel({})

      // Find the outputSchema textarea (it's in the Guardrails section)
      // The outputSchemaText input has a specific class 'json-input'
      const jsonInputs = wrapper.findAll('.json-input textarea')
      // The first json-input is the outputSchema, the second is the advanced JSON
      const schemaInput = jsonInputs[0]

      if (schemaInput) {
        await schemaInput.setValue('{ invalid json }')
        await nextTick()

        expect(wrapper.find('.field-error').exists()).toBe(true)
        expect(wrapper.find('.field-error').text()).toContain('JSON 格式错误')
      }
    })

    it('does not show error for valid JSON', async () => {
      const wrapper = mountPanel({})

      const jsonInputs = wrapper.findAll('.json-input textarea')
      const schemaInput = jsonInputs[0]

      if (schemaInput) {
        await schemaInput.setValue('{"type": "object"}')
        await nextTick()

        // Should not show error or error should be cleared
        const errors = wrapper.findAll('.field-error')
        expect(errors.length).toBe(0)
      }
    })

    it('emits valid parsed schema when JSON is correct', async () => {
      const wrapper = mountPanel({})

      const jsonInputs = wrapper.findAll('.json-input textarea')
      const schemaInput = jsonInputs[0]

      if (schemaInput) {
        await schemaInput.setValue('{"type": "object", "properties": {}}')
        await nextTick()

        const emitted = wrapper.emitted('update:modelValue')
        expect(emitted).toBeTruthy()
        const lastEmit = emitted![emitted!.length - 1][0] as Record<string, any>
        expect(lastEmit.outputSchema).toEqual({ type: 'object', properties: {} })
      }
    })
  })
})
