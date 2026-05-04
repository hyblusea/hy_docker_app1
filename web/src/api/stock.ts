import { apiGet, apiPost } from './client'
import type { StockBasic, DailyQuote } from '../types'

export async function searchStocks(keyword: string): Promise<StockBasic[]> {
  return apiGet<StockBasic[]>(`/stock/search?keyword=${encodeURIComponent(keyword)}`)
}

export async function queryDaily(params: {
  tsCode: string
  tradeDate?: string
  startDate?: string
  endDate?: string
}): Promise<DailyQuote[]> {
  return apiPost<DailyQuote[]>('/stock/daily', {
    ts_code: params.tsCode,
    trade_date: params.tradeDate,
    start_date: params.startDate,
    end_date: params.endDate,
  })
}
