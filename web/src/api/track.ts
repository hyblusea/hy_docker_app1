import { apiGet, apiPost, apiDelete } from './client'

export interface TrackStock {
  id: number
  ts_code: string
  stock_name: string
  strategy_id: number
  strategy_name: string
  add_date: string
  add_price: number | null
  current_price: number | null
  change_rate: number | null
  change_amount: number | null
  today_change: number | null
  today_pct: number | null
}

export async function addTrackStock(params: {
  ts_code: string
  stock_name: string
  strategy_id: number
  strategy_name: string
}): Promise<boolean> {
  return apiPost('/track/add', params)
}

export async function listTrackStocks(): Promise<TrackStock[]> {
  return apiGet('/track/list')
}

export async function removeTrackStock(id: number): Promise<boolean> {
  return apiDelete<boolean>(`/track/${id}`)
}
