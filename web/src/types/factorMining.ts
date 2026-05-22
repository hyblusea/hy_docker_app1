export interface FactorDefinition {
  factor_name: string
  factor_label: string
  factor_category: string
  important: boolean
}

export interface FactorEvalRequest {
  username: string
  start_date: string
  end_date: string
  factor_names: string[]
  forward_days: number
}

export interface FactorEvalProgress {
  task_id: string
  current: number
  total: number
  current_stock: string
  current_factor: string
  completed: boolean
  cancelled: boolean
  factor_completed: number
  total_factors: number
  init_error?: string
}

export interface FactorEvalResult {
  factor_name: string
  factor_label: string
  factor_category: string
  ic_mean: number
  ic_std: number
  icir: number
  ic_win_rate: number
  coverage: number
  layer_returns_json?: string
  pearson_ic_mean: number
  pearson_icir: number
}

export interface FactorEvalTaskHistory {
  task_id: string
  username: string
  start_date: string
  end_date: string
  factor_names: string
  forward_days: number
  total_stocks: number
  completed: boolean
  created_at: string
}
