import { ref, computed, type Ref, type ComputedRef } from 'vue'
import { flowApi } from '@/api/flows'
import { useSseStream, type UseSseStreamReturn } from '@/composables/useSseStream'
import type { FlowStatus, FlowEvent, FlowEventType } from '@/types'

// --- Types ---

export interface UseFlowExecutionReturn {
  /** Current flow execution status */
  status: Ref<FlowStatus | null>
  /** Accumulated SSE events */
  events: Ref<FlowEvent[]>
  /** Currently active node (last NODE_ENTER without matching NODE_EXIT) */
  currentNode: Ref<string | null>
  /** ID of the running flow instance */
  flowId: Ref<string | null>
  /** Convenience computed: true when status is RUNNING */
  isRunning: ComputedRef<boolean>
  /** Start a new flow execution and begin listening to SSE events */
  startFlow: (definitionId: string, inputs?: Record<string, unknown>) => Promise<void>
  /** Resume a suspended flow */
  resumeFlow: (data?: Record<string, unknown>) => Promise<void>
  /** Cancel the running flow and close the SSE stream */
  cancelFlow: () => Promise<void>
}

/** Event types that indicate terminal flow states */
const TERMINAL_EVENTS: FlowEventType[] = ['FLOW_COMPLETED', 'FLOW_FAILED']

/** Event type to FlowStatus mapping for terminal events */
const EVENT_STATUS_MAP: Partial<Record<FlowEventType, FlowStatus>> = {
  FLOW_COMPLETED: 'COMPLETED',
  FLOW_FAILED: 'FAILED',
}

/**
 * Composable that orchestrates flow execution lifecycle:
 * start/resume/cancel operations combined with real-time SSE event streaming.
 *
 * Usage:
 * ```ts
 * const { status, events, currentNode, flowId, isRunning, startFlow, resumeFlow, cancelFlow } =
 *   useFlowExecution()
 *
 * await startFlow('my-definition-id', { key: 'value' })
 * // events will accumulate as SSE pushes arrive
 * // currentNode reflects the latest entered node
 * ```
 */
export function useFlowExecution(): UseFlowExecutionReturn {
  const status = ref<FlowStatus | null>(null)
  const events = ref<FlowEvent[]>([])
  const currentNode = ref<string | null>(null)
  const flowId = ref<string | null>(null)

  const isRunning = computed(() => status.value === 'RUNNING')

  let sseStream: UseSseStreamReturn | null = null

  /**
   * Handle an incoming SSE event: accumulate it, update currentNode,
   * and detect terminal states.
   */
  function handleSseEvent(data: unknown): void {
    const event = data as FlowEvent
    events.value.push(event)

    // Track current node based on NODE_ENTER / NODE_EXIT
    if (event.type === 'NODE_ENTER' && event.nodeId) {
      currentNode.value = event.nodeId
    } else if (event.type === 'NODE_EXIT' && event.nodeId === currentNode.value) {
      currentNode.value = null
    }

    // Detect terminal flow states
    if (TERMINAL_EVENTS.includes(event.type)) {
      status.value = EVENT_STATUS_MAP[event.type] ?? null
      closeSseStream()
    }

    // Handle suspended state (e.g., approval node)
    if (event.type === 'BREAKPOINT_HIT' || event.type === 'APPROVAL_REQUESTED') {
      status.value = 'SUSPENDED'
    }
  }

  /**
   * Build SSE event handlers map.
   * We listen to the generic "message" event since the backend may send all
   * events under the default SSE event name.
   */
  function buildSseHandlers(): Record<string, (data: unknown) => void> {
    return {
      message: handleSseEvent,
    }
  }

  /**
   * Open an SSE stream for the given flow instance.
   */
  function openSseStream(id: string): void {
    closeSseStream()
    sseStream = useSseStream({
      url: `/api/v1/flows/${id}/events/stream`,
      handlers: buildSseHandlers(),
      autoConnect: true,
      maxRetries: 5,
    })
  }

  /**
   * Close the active SSE stream if one exists.
   */
  function closeSseStream(): void {
    if (sseStream) {
      sseStream.disconnect()
      sseStream = null
    }
  }

  /**
   * Start a new flow execution.
   * Calls the start API, then opens an SSE stream to receive real-time events.
   */
  async function startFlow(
    definitionId: string,
    inputs?: Record<string, unknown>
  ): Promise<void> {
    // Reset state for new execution
    events.value = []
    currentNode.value = null
    status.value = 'RUNNING'

    const instance = await flowApi.start({
      flowDefinitionId: definitionId,
      inputs,
    })

    flowId.value = instance.id
    status.value = instance.status

    // Open SSE stream only if the flow is still running
    if (instance.status === 'RUNNING') {
      openSseStream(instance.id)
    }
  }

  /**
   * Resume a suspended flow (e.g., after approval or breakpoint).
   */
  async function resumeFlow(data?: Record<string, unknown>): Promise<void> {
    if (!flowId.value) {
      throw new Error('No active flow to resume')
    }

    const instance = await flowApi.resume(flowId.value, {
      additionalInputs: data,
    })

    status.value = instance.status

    // Re-open SSE stream if the flow is running again
    if (instance.status === 'RUNNING') {
      openSseStream(instance.id)
    }
  }

  /**
   * Cancel the running flow and close the SSE stream.
   */
  async function cancelFlow(): Promise<void> {
    if (!flowId.value) {
      throw new Error('No active flow to cancel')
    }

    closeSseStream()

    const instance = await flowApi.cancel(flowId.value)
    status.value = instance.status ?? 'CANCELLED'
  }

  return {
    status,
    events,
    currentNode,
    flowId,
    isRunning,
    startFlow,
    resumeFlow,
    cancelFlow,
  }
}
