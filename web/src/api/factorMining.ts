import { apiGet, apiPost, apiDelete } from './client'
import type {
  FactorEvalRequest,
  FactorEvalProgress,
  FactorEvalResult,
  FactorEvalTaskHistory,
  FactorDefinition,
} from '../types/factorMining'

export async function getFactorDefinitions(): Promise<FactorDefinition[]> {
  return apiGet<FactorDefinition[]>('/factor/definitions')
}

export async function startFactorEval(request: FactorEvalRequest): Promise<string> {
  return apiPost<string>('/factor/eval', request)
}

export async function getFactorEvalProgress(taskId: string): Promise<FactorEvalProgress> {
  return apiGet<FactorEvalProgress>(`/factor/progress/${taskId}`)
}

export async function getFactorEvalResults(taskId: string): Promise<FactorEvalResult[]> {
  return apiGet<FactorEvalResult[]>(`/factor/results/${taskId}`)
}

export async function cancelFactorEval(taskId: string): Promise<void> {
  return apiPost<void>(`/factor/cancel/${taskId}`)
}

export async function getFactorEvalHistory(username: string): Promise<FactorEvalTaskHistory[]> {
  return apiGet<FactorEvalTaskHistory[]>(`/factor/history?username=${encodeURIComponent(username)}`)
}

export async function deleteFactorEvalTask(taskId: string): Promise<void> {
  await apiDelete(`/factor/${taskId}`)
}

export interface GenerateStrategyParams {
  factor_names: string[]
  task_id: string
  combination_method?: 'rule' | 'scoring'
}

export interface GeneratedStrategy {
  id: number
  name: string
  language: string
  description: string
  valid: boolean
}

export async function generateStrategy(params: GenerateStrategyParams): Promise<GeneratedStrategy> {
  return apiPost<GeneratedStrategy>('/factor/generate-strategy', params)
}
