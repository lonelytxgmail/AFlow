import { get, post, put, del } from './client'
import type {
  CreateDefinitionRequest,
  UpdateDefinitionRequest,
  PublishDefinitionResponse,
  DefinitionVersionListResponse,
  DefinitionVersion,
  ValidationResult,
} from './types'
import type { FlowDefinition } from '@/types'

/**
 * Definition CRUD + publish + version management
 */
export const definitionApi = {
  /** List all flow definitions */
  list: () => get<FlowDefinition[]>('/definitions'),

  /** Get a single flow definition by ID */
  get: (id: string) => get<FlowDefinition>(`/definitions/${id}`),

  /** Create a new flow definition */
  create: (data: CreateDefinitionRequest) =>
    post<FlowDefinition>('/definitions', data),

  /** Update an existing flow definition */
  update: (id: string, data: UpdateDefinitionRequest) =>
    put<FlowDefinition>(`/definitions/${id}`, data),

  /** Delete a flow definition */
  delete: (id: string) => del<void>(`/definitions/${id}`),

  /** Publish a flow definition (creates a version snapshot) */
  publish: (id: string) =>
    post<PublishDefinitionResponse>(`/definitions/${id}/publish`),

  /** List all versions of a definition */
  listVersions: (id: string) =>
    get<DefinitionVersionListResponse>(`/definitions/${id}/versions`),

  /** Get a specific version */
  getVersion: (id: string, versionNumber: number) =>
    get<DefinitionVersion>(`/definitions/${id}/versions/${versionNumber}`),

  /** Rollback to a specific version */
  rollback: (id: string, versionNumber: number) =>
    post<void>(`/definitions/${id}/versions/${versionNumber}/rollback`),

  /** Validate DSL definition without saving */
  validate: (dslContent: string) =>
    post<ValidationResult>('/definitions/validate', { dslContent }),
}
