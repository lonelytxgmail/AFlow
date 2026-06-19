import { ref } from 'vue'
import { defineStore } from 'pinia'
import { definitionApi } from '@/api'
import type { FlowDefinition } from '@/types'

export const useDefinitionStore = defineStore('definition', () => {
  const definitions = ref<FlowDefinition[]>([])
  const currentDefinition = ref<FlowDefinition | null>(null)
  const loading = ref(false)

  async function fetchDefinitions() {
    loading.value = true
    try {
      definitions.value = await definitionApi.list()
    } finally {
      loading.value = false
    }
  }

  async function fetchDefinition(id: string) {
    loading.value = true
    try {
      currentDefinition.value = await definitionApi.get(id)
      return currentDefinition.value
    } finally {
      loading.value = false
    }
  }

  async function createDefinition(data: Partial<FlowDefinition>) {
    const result = await definitionApi.create(data)
    await fetchDefinitions()
    return result
  }

  async function updateDefinition(id: string, data: Partial<FlowDefinition>) {
    const result = await definitionApi.update(id, data)
    await fetchDefinitions()
    return result
  }

  async function deleteDefinition(id: string) {
    await definitionApi.delete(id)
    await fetchDefinitions()
  }

  async function publishDefinition(id: string) {
    await definitionApi.publish(id)
    await fetchDefinitions()
  }

  return {
    definitions,
    currentDefinition,
    loading,
    fetchDefinitions,
    fetchDefinition,
    createDefinition,
    updateDefinition,
    deleteDefinition,
    publishDefinition
  }
})
