import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

describe('Test Setup Verification', () => {
  it('vitest globals work correctly', () => {
    expect(1 + 1).toBe(2)
    expect('hello').toContain('ell')
  })

  it('jsdom environment is available', () => {
    const div = document.createElement('div')
    div.textContent = 'Hello'
    expect(div.textContent).toBe('Hello')
  })

  it('@vue/test-utils can mount a component', () => {
    const TestComponent = defineComponent({
      setup() {
        return () => h('div', { class: 'test' }, 'Test Component')
      }
    })

    const wrapper = mount(TestComponent)
    expect(wrapper.text()).toBe('Test Component')
    expect(wrapper.find('.test').exists()).toBe(true)
  })

  it('path alias @/ resolves correctly', async () => {
    // Verify that the types module can be imported via @/ alias
    const types = await import('@/types/index')
    expect(types).toBeDefined()
  })
})
