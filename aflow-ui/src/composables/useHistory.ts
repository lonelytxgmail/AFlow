import { ref, computed } from 'vue'

export interface UseHistoryOptions {
  /** Maximum number of history entries to keep. Defaults to 50. */
  maxSize?: number
}

/**
 * Generic composable implementing command pattern for undo/redo.
 * Maintains a stack of deep-cloned state snapshots and provides
 * undo(), redo(), pushState(), canUndo, and canRedo.
 *
 * Usage:
 *   const { pushState, undo, redo, canUndo, canRedo, current } = useHistory<MyState>()
 *   pushState(initialState)
 *   // ... user makes changes ...
 *   pushState(newState)
 *   if (canUndo.value) undo()
 */
export function useHistory<T>(options: UseHistoryOptions = {}) {
  const { maxSize = 50 } = options

  // History stack: array of deep-cloned state snapshots
  const history = ref<T[]>([]) as { value: T[] }

  // Pointer to the current position in the history stack
  const pointer = ref(-1)

  const canUndo = computed(() => pointer.value > 0)
  const canRedo = computed(() => pointer.value < history.value.length - 1)

  /** The current state snapshot (or undefined if history is empty) */
  const current = computed<T | undefined>(() => {
    if (pointer.value >= 0 && pointer.value < history.value.length) {
      return history.value[pointer.value]
    }
    return undefined
  })

  /**
   * Deep clone a value using structuredClone (with JSON fallback).
   */
  function deepClone(value: T): T {
    try {
      return structuredClone(value)
    } catch {
      return JSON.parse(JSON.stringify(value))
    }
  }

  /**
   * Push a new state snapshot onto the history stack.
   * Discards any redo history beyond the current pointer.
   * Enforces the maxSize limit by trimming the oldest entries.
   */
  function pushState(state: T): void {
    const cloned = deepClone(state)

    // If we're not at the end, discard future states (redo history)
    if (pointer.value < history.value.length - 1) {
      history.value = history.value.slice(0, pointer.value + 1)
    }

    history.value.push(cloned)

    // Enforce max size by trimming from the beginning
    if (history.value.length > maxSize) {
      const excess = history.value.length - maxSize
      history.value = history.value.slice(excess)
    }

    pointer.value = history.value.length - 1
  }

  /**
   * Undo: move the pointer back one step and return the previous state.
   * Returns undefined if cannot undo.
   */
  function undo(): T | undefined {
    if (!canUndo.value) return undefined
    pointer.value--
    return deepClone(history.value[pointer.value])
  }

  /**
   * Redo: move the pointer forward one step and return the next state.
   * Returns undefined if cannot redo.
   */
  function redo(): T | undefined {
    if (!canRedo.value) return undefined
    pointer.value++
    return deepClone(history.value[pointer.value])
  }

  /**
   * Clear all history and reset pointer.
   */
  function clear(): void {
    history.value = []
    pointer.value = -1
  }

  return {
    pushState,
    undo,
    redo,
    clear,
    canUndo,
    canRedo,
    current
  }
}
