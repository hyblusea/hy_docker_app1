import type { BacktestResult } from './index'

export interface StrategyAnalysisRequest {
  username: string
  start_date: string
  end_date: string
  strategy_ids?: number[]
}

export interface StrategyAnalysisProgress {
  task_id: string
  current: number
  total: number
  current_strategy: string
  current_stock: string
  completed: boolean
  cancelled: boolean
  strategy_completed: number
  match_count: number
  init_error?: string
}

export interface StrategyAnalysisResult {
  strategy_id: number
  strategy_name: string
  total_stocks: number
  profitable_stocks: number
  losing_stocks: number
  avg_return: number
  max_return: number
  max_return_stock: string
  min_return: number
  min_return_stock: string
  avg_win_rate: number
  avg_max_drawdown: number
  total_trades: number
  avg_trade_count: number
  profit_factor: number
  total_profit: number
  total_loss: number
  top_stocks: StockPerformance[]
  worst_stocks: StockPerformance[]
}

export interface StockPerformance {
  ts_code: string
  stock_name: string
  total_return: number
  win_rate: number
  trade_count: number
  max_drawdown: number
  backtest_result?: BacktestResult
}
