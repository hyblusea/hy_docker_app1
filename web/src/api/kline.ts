import { apiGet, apiPost } from './client'
import type { KlineSyncStatus, KlineSyncProgress, MissingDataResult, KlineSyncRequest, KlineRangeMap, KlineDashboard, KlineSyncStatusPage } from '../types/kline'

export async function getDashboard(): Promise<KlineDashboard> {
  return apiGet<KlineDashboard>('/kline/dashboard')
}

export async function getSyncStatus(period?: string): Promise<KlineSyncStatus[]> {
  const params = period ? `?period=${encodeURIComponent(period)}` : ''
  return apiGet<KlineSyncStatus[]>(`/kline/sync/status${params}`)
}

export async function getSyncStatusPaged(params: { period?: string; page: number; size: number }): Promise<KlineSyncStatusPage> {
  const searchParams = new URLSearchParams()
  if (params.period) searchParams.set('period', params.period)
  searchParams.set('page', String(params.page))
  searchParams.set('size', String(params.size))
  return apiGet<KlineSyncStatusPage>(`/kline/sync/status/paged?${searchParams.toString()}`)
}

export async function startFullSync(request?: KlineSyncRequest): Promise<string> {
  return apiPost<string>('/kline/sync/full', request || {})
}

export async function getSyncProgress(taskId: string): Promise<KlineSyncProgress> {
  return apiGet<KlineSyncProgress>(`/kline/sync/progress/${taskId}`)
}

export async function cancelSync(taskId: string): Promise<void> {
  await apiPost<void>(`/kline/sync/cancel/${taskId}`)
}

export async function getMissingDataStats(): Promise<MissingDataResult> {
  return apiGet<MissingDataResult>('/kline/missing')
}

export async function fillMissingData(): Promise<string> {
  return apiPost<string>('/kline/sync/fill')
}

export async function getGlobalDateRange(): Promise<KlineRangeMap> {
  return apiGet<KlineRangeMap>('/kline/range')
}
