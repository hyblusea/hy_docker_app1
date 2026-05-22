import { apiGet, apiPost, apiDelete } from './client'
import type { StockBasic, DailyQuote, StockScreenMatch, StockScreenProgress } from '../types'

export async function getStockList(): Promise<StockBasic[]> {
  return apiGet<StockBasic[]>('/stock/list')
}

export async function searchStocks(keyword: string): Promise<StockBasic[]> {
  return apiGet<StockBasic[]>(`/stock/search?keyword=${encodeURIComponent(keyword)}`)
}

export async function queryDaily(params: {
  tsCode: string
  tradeDate?: string
  startDate?: string
  endDate?: string
  period?: string
}): Promise<DailyQuote[]> {
  return apiPost<DailyQuote[]>('/stock/daily', {
    ts_code: params.tsCode,
    trade_date: params.tradeDate,
    start_date: params.startDate,
    end_date: params.endDate,
    period: params.period,
  })
}

export async function startStockScreen(params: {
  username: string
  strategyIds: number[]
  startDate: string
  endDate: string
  screenMode: string
}): Promise<string> {
  return apiPost<string>('/stock/screen', {
    username: params.username,
    strategy_ids: params.strategyIds,
    start_date: params.startDate,
    end_date: params.endDate,
    screen_mode: params.screenMode,
  })
}

export async function getStockScreenProgress(taskId: string): Promise<StockScreenProgress> {
  return apiGet<StockScreenProgress>(`/stock/screen/${taskId}/progress`)
}

export async function getStockScreenResult(taskId: string): Promise<StockScreenMatch[]> {
  return apiGet<StockScreenMatch[]>(`/stock/screen/${taskId}/result`)
}

export async function getStockScreenList(taskId: string): Promise<StockScreenMatch[]> {
  return apiGet<StockScreenMatch[]>(`/stock/screen/${taskId}/list`)
}

export async function getStockScreenDetail(
  taskId: string,
  tsCode: string,
  strategyId: number
): Promise<StockScreenMatch> {
  return apiGet<StockScreenMatch>(`/stock/screen/${taskId}/detail?tsCode=${encodeURIComponent(tsCode)}&strategyId=${strategyId}`)
}

export async function cancelStockScreen(taskId: string): Promise<void> {
  return apiPost<void>(`/stock/screen/${taskId}/cancel`)
}

export interface ScreenTaskHistory {
  task_id: string
  username: string
  start_date: string
  end_date: string
  total_stocks: number
  match_count: number
  completed: boolean
  created_at: string
}

export async function getScreenHistory(username: string): Promise<ScreenTaskHistory[]> {
  return apiGet<ScreenTaskHistory[]>(`/stock/screen/history?username=${encodeURIComponent(username)}`)
}

export async function deleteScreenTask(taskId: string): Promise<void> {
  await apiDelete(`/stock/screen/${taskId}`)
}
