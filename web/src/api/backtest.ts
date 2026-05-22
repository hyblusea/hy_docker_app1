import { apiPost } from './client'
import type { DailyQuote, BacktestResult, StrategyBacktestResult } from '../types'

export async function runBacktest(strategyId: number, quotes: DailyQuote[]): Promise<BacktestResult> {
  return apiPost<BacktestResult>('/backtest', {
    strategy_id: strategyId,
    quotes,
  })
}

export async function runBatchBacktest(params: {
  tsCode: string
  startDate: string
  endDate: string
  strategyIds: number[]
}): Promise<StrategyBacktestResult[]> {
  return apiPost<StrategyBacktestResult[]>('/backtest/batch', {
    ts_code: params.tsCode,
    start_date: params.startDate,
    end_date: params.endDate,
    strategy_ids: params.strategyIds,
  })
}
