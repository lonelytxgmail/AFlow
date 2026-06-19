import { ref, computed, onBeforeUnmount, type Ref, type ComputedRef } from 'vue'

// --- Types ---

export type SseConnectionState = 'connected' | 'reconnecting' | 'closed'

export interface UseSseStreamOptions {
  /** SSE endpoint URL */
  url: string
  /** Map of event type names to handler functions */
  handlers?: Record<string, (data: unknown) => void>
  /** Whether to connect immediately on composable creation. Defaults to true. */
  autoConnect?: boolean
  /** Maximum number of reconnection attempts before giving up. Defaults to 5. */
  maxRetries?: number
  /** Base delay in ms for exponential backoff. Defaults to 1000. */
  baseRetryMs?: number
}

export interface UseSseStreamReturn {
  /** Current connection state */
  state: Ref<SseConnectionState>
  /** Manually initiate connection */
  connect: () => void
  /** Manually close connection and stop reconnecting */
  disconnect: () => void
  /** Convenience computed: true when state is 'connected' */
  isConnected: ComputedRef<boolean>
}

/** Maximum backoff cap in milliseconds (30 seconds) */
const MAX_BACKOFF_MS = 30_000

/**
 * Composable for consuming Server-Sent Events (SSE) streams with
 * automatic reconnection (exponential backoff), event type dispatching,
 * connection state tracking, and auto-cleanup on component unmount.
 *
 * Usage:
 * ```ts
 * const { state, connect, disconnect, isConnected } = useSseStream({
 *   url: '/api/v1/flows/123/events',
 *   handlers: {
 *     NODE_ENTER: (data) => handleNodeEnter(data),
 *     FLOW_COMPLETED: (data) => handleComplete(data),
 *   },
 *   maxRetries: 8,
 * })
 * ```
 */
export function useSseStream(options: UseSseStreamOptions): UseSseStreamReturn {
  const {
    url,
    handlers = {},
    autoConnect = true,
    maxRetries = 5,
    baseRetryMs = 1000
  } = options

  const state = ref<SseConnectionState>('closed')
  const isConnected = computed(() => state.value === 'connected')

  let eventSource: EventSource | null = null
  let retryCount = 0
  let retryTimer: ReturnType<typeof setTimeout> | null = null

  /**
   * Parse incoming event data as JSON. Falls back to raw string if parse fails.
   */
  function parseEventData(raw: string): unknown {
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  }

  /**
   * Compute the reconnection delay using exponential backoff capped at MAX_BACKOFF_MS.
   */
  function getBackoffDelay(): number {
    const delay = baseRetryMs * Math.pow(2, retryCount)
    return Math.min(delay, MAX_BACKOFF_MS)
  }

  /**
   * Schedule a reconnection attempt after the computed backoff delay.
   */
  function scheduleReconnect(): void {
    if (retryCount >= maxRetries) {
      state.value = 'closed'
      return
    }

    state.value = 'reconnecting'
    const delay = getBackoffDelay()
    retryCount++

    retryTimer = setTimeout(() => {
      retryTimer = null
      connect()
    }, delay)
  }

  /**
   * Open the EventSource connection and wire up event listeners.
   */
  function connect(): void {
    // Clean up any existing connection first
    cleanup()

    eventSource = new EventSource(url)

    eventSource.onopen = () => {
      state.value = 'connected'
      retryCount = 0
    }

    eventSource.onerror = () => {
      if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
        // Connection is in a recoverable error state — attempt reconnect
        eventSource.close()
        eventSource = null
        scheduleReconnect()
      } else {
        // EventSource is fully closed — no automatic recovery
        cleanup()
        state.value = 'closed'
      }
    }

    // Register handler for the default "message" event
    eventSource.onmessage = (event: MessageEvent) => {
      const data = parseEventData(event.data)
      if (handlers['message']) {
        handlers['message'](data)
      }
    }

    // Register handlers for custom event types (non-"message" events)
    for (const eventType of Object.keys(handlers)) {
      if (eventType === 'message') continue
      eventSource.addEventListener(eventType, (event) => {
        const data = parseEventData((event as MessageEvent).data)
        handlers[eventType](data)
      })
    }
  }

  /**
   * Close the EventSource and cancel any pending reconnection timer.
   */
  function cleanup(): void {
    if (retryTimer !== null) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  /**
   * Manually disconnect and prevent further reconnection attempts.
   */
  function disconnect(): void {
    retryCount = maxRetries // prevent further reconnections
    cleanup()
    state.value = 'closed'
  }

  // Auto-connect on creation if enabled
  if (autoConnect) {
    connect()
  }

  // Auto-cleanup when the owning component unmounts
  onBeforeUnmount(() => {
    cleanup()
    state.value = 'closed'
  })

  return {
    state,
    connect,
    disconnect,
    isConnected
  }
}
