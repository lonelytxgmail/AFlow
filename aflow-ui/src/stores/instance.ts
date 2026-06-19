import { ref } from 'vue'
import { defineStore } from 'pinia'
import { flowApi, debugApi } from '@/api'
import type { FlowInstance, Snapshot, FlowEvent } from '@/types'

export const useInstanceStore = defineStore('instance', () => {
  const instances = ref<FlowInstance[]>([])
  const currentInstance = ref<FlowInstance | null>(null)
  const snapshots = ref<Snapshot[]>([])
  const events = ref<FlowEvent[]>([])
  const breakpoints = ref<string[]>([])
  const loading = ref(false)

  async function fetchInstances(status?: string) {
    loading.value = true
    try {
      instances.value = await flowApi.list(status)
    } finally {
      loading.value = false
    }
  }

  async function fetchInstance(id: string) {
    loading.value = true
    try {
      currentInstance.value = await flowApi.get(id)
      return currentInstance.value
    } finally {
      loading.value = false
    }
  }

  async function startFlow(data: { definitionId: string; variables?: Record<string, any> }) {
    return await flowApi.start(data)
  }

  async function resumeFlow(id: string, data?: Record<string, any>) {
    currentInstance.value = await flowApi.resume(id, data)
    return currentInstance.value
  }

  async function cancelFlow(id: string) {
    currentInstance.value = await flowApi.cancel(id)
    return currentInstance.value
  }

  async function retryFlow(id: string, nodeId: string) {
    currentInstance.value = await flowApi.retry(id, nodeId)
    return currentInstance.value
  }

  async function fetchSnapshots(id: string) {
    snapshots.value = await flowApi.snapshots(id)
  }

  async function fetchEvents(id: string) {
    events.value = await flowApi.events(id)
  }

  async function fetchBreakpoints(flowId: string) {
    try {
      breakpoints.value = await debugApi.getBreakpoints(flowId)
    } catch {
      breakpoints.value = []
    }
  }

  async function toggleBreakpoint(flowId: string, nodeId: string) {
    if (breakpoints.value.includes(nodeId)) {
      await debugApi.removeBreakpoint(flowId, nodeId)
      breakpoints.value = breakpoints.value.filter((n) => n !== nodeId)
    } else {
      await debugApi.addBreakpoint(flowId, nodeId)
      breakpoints.value.push(nodeId)
    }
  }

  async function stepFlow(flowId: string) {
    currentInstance.value = await debugApi.step(flowId)
    return currentInstance.value
  }

  return {
    instances,
    currentInstance,
    snapshots,
    events,
    breakpoints,
    loading,
    fetchInstances,
    fetchInstance,
    startFlow,
    resumeFlow,
    cancelFlow,
    retryFlow,
    fetchSnapshots,
    fetchEvents,
    fetchBreakpoints,
    toggleBreakpoint,
    stepFlow
  }
})
