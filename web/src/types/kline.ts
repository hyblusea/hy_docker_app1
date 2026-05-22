export interface KlineSyncStatus {
  id: number
  ts_code: string
  period: string
  stock_name: string
  start_date: string | null
  last_sync_date: string
  status: string
  total_records: number
  data_years: number
  error_message: string | null
  consecutive_failures: number
  created_at: string
  updated_at: string
}

export interface KlineSyncProgress {
  taskId: string
  current: number
  total: number
  current_stock: string
  current_period: string
  completed: boolean
  cancelled: boolean
  success_count: number
  fail_count: number
  init_error?: string
}

export interface MissingDataResult {
  total_stocks: number
  complete_stocks: number
  incomplete_stocks: number
  avg_completion_rate: number
  missing_details: MissingDetail[]
}

export interface MissingDetail {
  ts_code: string
  stock_name: string
  missing_days: number
  first_missing_date: string
  last_missing_date: string
  period: string
}

export interface KlineSyncRequest {
  start_date?: string
  end_date?: string
  periods?: string[]
  concurrency?: number
  ts_codes?: string[]
}

export interface KlineRangeInfo {
  period: string
  start_date: string | null
  end_date: string | null
  stock_count: number
  total_records: number
}

export type KlineRangeMap = Record<string, KlineRangeInfo>

export interface KlineDashboard {
  missing_data: MissingDataResult
  range_map: KlineRangeMap
}

export interface KlineSyncStatusPage {
  content: KlineSyncStatus[]
  total_elements: number
  total_pages: number
  number: number
  size: number
}
