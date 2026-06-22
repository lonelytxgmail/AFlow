import { get, post, put, del } from './client'
import type {
  CreateAtomicComponentRequest,
  UpdateAtomicComponentRequest,
  AtomicInvokeRequest,
  AtomicInvokeResponse,
  NodeRegistryItem,
  ComponentRegistryItem,
  AtomicComponentListParams,
} from './types'
import type { AtomicComponent } from '@/types'

/**
 * Atomic components: CRUD, invoke, registry queries
 */
export const atomicApi = {
  /** Get built-in node type registry */
  registry: () => get<NodeRegistryItem[]>('/atomic/node-registry'),

  /** Get published component registry */
  componentRegistry: () =>
    get<ComponentRegistryItem[]>('/atomic/component-registry'),

  /** Invoke a built-in node type directly */
  invoke: (componentType: string, data: AtomicInvokeRequest) =>
    post<AtomicInvokeResponse>(`/atomic/${componentType}`, data),

  /** Invoke a saved atomic component (pass only business variables) */
  invokeComponent: (id: string, variables: Record<string, unknown>) =>
    post<Record<string, any>>(`/atomic/components/${id}/invoke`, variables),

  /** Get required variables for an atomic component */
  getVariables: (id: string) =>
    get<Array<{ name: string; expression: string; context: string }>>(`/atomic/components/${id}/variables`),

  /** List atomic components with optional filters */
  listComponents: (params?: AtomicComponentListParams) =>
    get<AtomicComponent[]>('/atomic/components', params as Record<string, unknown>),

  /** Get a single atomic component */
  getComponent: (id: string) =>
    get<AtomicComponent>(`/atomic/components/${id}`),

  /** Create a new atomic component */
  createComponent: (data: CreateAtomicComponentRequest) =>
    post<AtomicComponent>('/atomic/components', data),

  /** Update an existing atomic component */
  updateComponent: (id: string, data: UpdateAtomicComponentRequest) =>
    put<AtomicComponent>(`/atomic/components/${id}`, data),

  /** Delete an atomic component */
  deleteComponent: (id: string) =>
    del<void>(`/atomic/components/${id}`),
}
