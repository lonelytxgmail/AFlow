import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'

// --- Mock EventSource ---

type EventSourceListener = (event: MessageEvent) => void

class MockEventSource {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 2

  readonly CONNECTING = 0
  readonly OPEN = 1
  readonly CLOSED = 2

  url: string
  readyState: number = MockEventSource.CONNECTING
  onopen: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null

  private listeners: Record<string, EventSourceListener[]> = {}

  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: EventSourceListener): void {
    if (!this.listeners[type]) {
      this.listeners[type] = []
    }
    this.listeners[type].push(listener)
  }

  removeEventListener(type: string, listener: EventSourceListener): void {
    if (this.listeners[type]) {
      this.listeners[type] = this.listeners[type].filter((l) => l !== listener)
    }
  }

  close(): void {
    this.readyState = MockEventSource.CLOSED
  }

  dispatchEvent(_event: Event): boolean {
    return true
  }

  // --- Test helpers ---

  simulateOpen(): void {
    this.readyState = MockEventSource.OPEN
    if (this.onopen) {
      this.onopen(new Event('open'))
    }
  }

  simulateError(): void {
    if (this.onerror) {
      this.onerror(new Event('error'))
    }
  }

  simulateMessage(data: string): void {
    const event = new MessageEvent('message', { data })
    if (this.onmessage) {
      this.onmessage(event)
    }
  }

  simulateCustomEvent(type: string, data: string): void {
    const event = new MessageEvent(type, { data })
    if (this.listeners[type]) {
      for (const listener of this.listeners[type]) {
        listener(event)
      }
    }
  }

  // --- Static registry ---

  static instances: MockEventSource[] = []

  static reset(): void {
    MockEventSource.instances = []
  }

  static get latest(): MockEventSource {
    return MockEventSource.instances[MockEventSource.instances.length - 1]
  }
}

// Mock onBeforeUnmount to be a no-op in tests (composable runs outside component context)
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    onBeforeUnmount: vi.fn()
  }
})

// Assign the mock to globalThis
vi.stubGlobal('EventSource', MockEventSource)

describe('useSseStream', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    MockEventSource.reset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function importComposable() {
    const mod = await import('@/composables/useSseStream')
    return mod.useSseStream
  }

  it('initial state is "closed" when autoConnect is false', async () => {
    const useSseStream = await importComposable()

    const { state } = useSseStream({
      url: '/api/events',
      autoConnect: false
    })

    expect(state.value).toBe('closed')
  })

  it('transitions to "connected" when EventSource fires "open"', async () => {
    const useSseStream = await importComposable()

    const { state } = useSseStream({
      url: '/api/events',
      autoConnect: true
    })

    // Initially the EventSource is created but not yet open
    const es = MockEventSource.latest
    expect(es).toBeDefined()

    // Simulate server accepting the connection
    es.simulateOpen()

    expect(state.value).toBe('connected')
  })

  it('transitions to "reconnecting" on error (non-CLOSED readyState)', async () => {
    const useSseStream = await importComposable()

    const { state } = useSseStream({
      url: '/api/events',
      autoConnect: true,
      maxRetries: 3
    })

    const es = MockEventSource.latest
    es.simulateOpen()
    expect(state.value).toBe('connected')

    // Simulate an error while connection is still in CONNECTING or OPEN state
    // The readyState should be non-CLOSED to trigger reconnection
    es.readyState = MockEventSource.CONNECTING
    es.simulateError()

    expect(state.value).toBe('reconnecting')
  })

  it('dispatches JSON-parsed events to the correct handler', async () => {
    const useSseStream = await importComposable()

    const nodeEnterHandler = vi.fn()
    const messageHandler = vi.fn()

    useSseStream({
      url: '/api/events',
      autoConnect: true,
      handlers: {
        NODE_ENTER: nodeEnterHandler,
        message: messageHandler
      }
    })

    const es = MockEventSource.latest
    es.simulateOpen()

    // Test custom event with JSON data
    const jsonPayload = JSON.stringify({ nodeId: 'node-1', status: 'running' })
    es.simulateCustomEvent('NODE_ENTER', jsonPayload)

    expect(nodeEnterHandler).toHaveBeenCalledTimes(1)
    expect(nodeEnterHandler).toHaveBeenCalledWith({ nodeId: 'node-1', status: 'running' })

    // Test default message event
    const messagePayload = JSON.stringify({ type: 'heartbeat' })
    es.simulateMessage(messagePayload)

    expect(messageHandler).toHaveBeenCalledTimes(1)
    expect(messageHandler).toHaveBeenCalledWith({ type: 'heartbeat' })
  })

  it('stops after maxRetries', async () => {
    const useSseStream = await importComposable()

    const { state } = useSseStream({
      url: '/api/events',
      autoConnect: true,
      maxRetries: 2,
      baseRetryMs: 100
    })

    const firstEs = MockEventSource.latest
    firstEs.simulateOpen()

    // First error → reconnecting
    firstEs.readyState = MockEventSource.CONNECTING
    firstEs.simulateError()
    expect(state.value).toBe('reconnecting')

    // Advance timer for first retry
    vi.advanceTimersByTime(100) // baseRetryMs * 2^0 = 100
    const secondEs = MockEventSource.latest
    secondEs.readyState = MockEventSource.CONNECTING
    secondEs.simulateError()
    expect(state.value).toBe('reconnecting')

    // Advance timer for second retry
    vi.advanceTimersByTime(200) // baseRetryMs * 2^1 = 200
    const thirdEs = MockEventSource.latest
    thirdEs.readyState = MockEventSource.CONNECTING
    thirdEs.simulateError()

    // After maxRetries (2), should be closed
    expect(state.value).toBe('closed')
  })

  it('disconnect() closes the connection and sets state to "closed"', async () => {
    const useSseStream = await importComposable()

    const { state, disconnect } = useSseStream({
      url: '/api/events',
      autoConnect: true
    })

    const es = MockEventSource.latest
    es.simulateOpen()
    expect(state.value).toBe('connected')

    disconnect()

    expect(state.value).toBe('closed')
    expect(es.readyState).toBe(MockEventSource.CLOSED)
  })

  it('falls back to raw string when JSON parse fails', async () => {
    const useSseStream = await importComposable()

    const handler = vi.fn()

    useSseStream({
      url: '/api/events',
      autoConnect: true,
      handlers: {
        message: handler
      }
    })

    const es = MockEventSource.latest
    es.simulateOpen()

    // Send non-JSON data
    es.simulateMessage('plain text that is not JSON')

    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler).toHaveBeenCalledWith('plain text that is not JSON')
  })
})
