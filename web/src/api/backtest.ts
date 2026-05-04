import { apiPost } from './client'
import type { DailyQuote, BacktestResult } from '../types'

export async function runBacktest(strategyId: number, quotes: DailyQuote[]): Promise<BacktestResult> {
  return apiPost<BacktestResult>('/backtest', {
    strategy_id: strategyId,
    quotes,
  })
}
