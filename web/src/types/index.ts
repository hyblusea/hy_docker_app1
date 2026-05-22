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
  market_value: number | null
  market_value_circulating: number | null
  total_shares: number | null
  circulating_shares: number | null
  pe_ratio: number | null
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
  buy_amount: number
  fees: number
  sell_fees: number
  profit: number
  profit_pct: number
  remaining_cash: number
}

export interface BacktestResult {
  signals: BacktestSignal[]
  total_return: number
  win_rate: number
  trade_count: number
  winning_trades: number
  losing_trades: number
  max_drawdown: number
  profit_loss: number
  open_position_count: number
  initial_capital: number
  final_capital: number
  total_fees: number
}

export interface StockScreenMatch {
  ts_code: string
  name: string
  strategy_id: number
  strategy_name: string
  quotes: DailyQuote[]
  result: BacktestResult
}

export interface StockScreenProgress {
  current: number
  total: number
  current_stock: string
  completed: boolean
  cancelled: boolean
  match_count: number
  init_error?: string
}

export interface StrategyBacktestResult {
  strategy_id: number
  strategy_name: string
  result: BacktestResult
}
