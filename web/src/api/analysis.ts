import { apiGet, apiPost, apiDelete } from './client'
import type { StrategyAnalysisRequest, StrategyAnalysisProgress, StrategyAnalysisResult } from '../types/analysis'
import type { BacktestResult } from '../types'

export async function startAnalysis(request: StrategyAnalysisRequest): Promise<string> {
  return apiPost<string>('/analysis/start', request)
}

export async function getAnalysisProgress(taskId: string): Promise<StrategyAnalysisProgress> {
  return apiGet<StrategyAnalysisProgress>(`/analysis/progress/${taskId}`)
}

export async function getAnalysisResults(taskId: string): Promise<StrategyAnalysisResult[]> {
  return apiGet<StrategyAnalysisResult[]>(`/analysis/results/${taskId}`)
}

export async function cancelAnalysis(taskId: string): Promise<void> {
  await apiPost<void>(`/analysis/cancel/${taskId}`)
}

export interface AnalysisTaskHistory {
  task_id: string
  username: string
  start_date: string
  end_date: string
  strategy_count: number
  total_stocks: number
  completed: boolean
  created_at: string
}

export async function getAnalysisHistory(username: string): Promise<AnalysisTaskHistory[]> {
  return apiGet<AnalysisTaskHistory[]>(`/analysis/history?username=${encodeURIComponent(username)}`)
}

export async function deleteAnalysisTask(taskId: string): Promise<void> {
  await apiDelete(`/analysis/${taskId}`)
}

export async function getStockBacktestResult(taskId: string, strategyId: number, tsCode: string): Promise<BacktestResult> {
  return apiGet<BacktestResult>(`/analysis/results/${taskId}/${strategyId}/${encodeURIComponent(tsCode)}`)
}
