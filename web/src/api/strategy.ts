import { apiGet, apiPost, apiPut, apiDelete } from './client'
import type { Strategy } from '../types/strategy'

export async function listStrategies(): Promise<Strategy[]> {
  return apiGet<Strategy[]>('/strategy/list')
}

export async function listValidStrategies(): Promise<Strategy[]> {
  return apiGet<Strategy[]>('/strategy/list-valid')
}

export async function getStrategy(id: number): Promise<Strategy> {
  return apiGet<Strategy>(`/strategy/${id}`)
}

export async function createStrategy(strategy: Omit<Strategy, 'id' | 'created_at' | 'updated_at'>): Promise<Strategy> {
  return apiPost<Strategy>('/strategy', strategy)
}

export async function updateStrategy(id: number, strategy: Partial<Strategy>): Promise<Strategy> {
  return apiPut<Strategy>(`/strategy/${id}`, strategy)
}

export async function deleteStrategy(id: number): Promise<void> {
  await apiDelete(`/strategy/${id}`)
}
