export interface StockBasic {
  ts_code: string
  symbol: string
  name: string
  area: string
  industry: string
  cnspell: string
  market: string
  list_date: string
  act_name: string
  act_ent_type: string
}

export interface DailyQuote {
  ts_code: string
  trade_date: string
  open: number
  high: number
  low: number
  close: number
  pre_close: number
  change: number
  pct_chg: number
  vol: number
  amount: number
}

export interface SearchQuery {
  tsCode: string
  name: string
  period: string
  startDate: string
  endDate: string
}

export interface HistoryTag {
  tsCode: string
  name: string
}

export interface BacktestSignal {
  trade_date: string
  type: 'BUY' | 'SELL'
  price: number
  index: number
  shares: number
  buyAmount: number
  fees: number
  sellFees: number
  profit: number
  profitPct: number
  remainingCash: number
}

export interface BacktestResult {
  signals: BacktestSignal[]
  totalReturn: number
  winRate: number
  tradeCount: number
  winningTrades: number
  losingTrades: number
  maxDrawdown: number
  profitLoss: number
  openPositionCount: number
  initialCapital: number
  finalCapital: number
  totalFees: number
}
