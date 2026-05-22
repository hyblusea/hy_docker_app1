import { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { App, Checkbox, Button, DatePicker, Input, Modal } from 'antd'
import { PlayCircleOutlined, SearchOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { useValidStrategies } from '../hooks/useStrategies'
import { runBatchBacktest } from '../api/backtest'
import { getStockList, queryDaily } from '../api/stock'
import DataPanel from '../components/DataPanel/DataPanel'
import type { StockBasic, DailyQuote, BacktestSignal, StrategyBacktestResult } from '../types'
import styles from './BacktestPage.module.css'

interface TradeRecord {
  entry: BacktestSignal
  exit?: BacktestSignal
}

const formatMoney = (v: number) => {
  if (Math.abs(v) >= 10000) return (v / 10000).toFixed(2) + '万'
  return v.toFixed(2)
}

const formatTradeDate = (d: string) => {
  if (d.length === 8) return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`
  return d
}

const computeTrades = (signals: BacktestSignal[]): TradeRecord[] => {
  const trades: TradeRecord[] = []
  let current: BacktestSignal | null = null
  for (const s of signals) {
    if (s.type === 'BUY') {
      current = s
    } else if (s.type === 'SELL' && current) {
      trades.push({ entry: current, exit: s })
      current = null
    }
  }
  if (current) {
    trades.push({ entry: current })
  }
  return trades
}

const formatMarketValue = (v: number | null): string => {
  if (v == null) return '-'
  if (v >= 1e12) return (v / 1e12).toFixed(2) + '万亿'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(2) + '万'
  return v.toFixed(2)
}

export interface BacktestPageState {
  selectedStrategyIds: number[]
  startDate: string
  endDate: string
  selectedStock: StockBasic | null
  backtestResults: StrategyBacktestResult[]
  selectedResultIndex: number | null
  loading: boolean
}

export const defaultBacktestPageState: BacktestPageState = {
  selectedStrategyIds: [],
  startDate: dayjs().subtract(2, 'year').format('YYYYMMDD'),
  endDate: dayjs().format('YYYYMMDD'),
  selectedStock: null,
  backtestResults: [],
  selectedResultIndex: null,
  loading: false,
}

interface BacktestPageProps {
  state: BacktestPageState
  onStateChange: (patch: Partial<BacktestPageState>) => void
}

const BacktestPage = ({ state, onStateChange }: BacktestPageProps) => {
  const { message: msgApi } = App.useApp()
  const { strategies } = useValidStrategies()
  const [searchParams] = useSearchParams()

  const { selectedStrategyIds, startDate, endDate, selectedStock, backtestResults, selectedResultIndex, loading } = state

  const [stockList, setStockList] = useState<StockBasic[]>([])
  const [filterText, setFilterText] = useState('')
  const [quotes, setQuotes] = useState<DailyQuote[]>([])
  const [quotesLoading, setQuotesLoading] = useState(false)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [detailResultIndex, setDetailResultIndex] = useState<number | null>(null)
  const [chartRatio, setChartRatio] = useState(60)
  const [dragging, setDragging] = useState(false)
  const mountedRef = useRef(true)
  const autoRunRef = useRef(false)
  const backtestSeqRef = useRef(0)
  const rightPanelRef = useRef<HTMLDivElement | null>(null)
  const leftPanelRef = useRef<HTMLDivElement | null>(null)
  const stockListRef = useRef<HTMLDivElement | null>(null)
  const chartRatioRef = useRef(chartRatio)
  chartRatioRef.current = chartRatio

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  useEffect(() => {
    getStockList().then(list => {
      if (mountedRef.current) setStockList(list)
    }).catch(() => {})
  }, [])

  const filteredStocks = useMemo(() => {
    if (!filterText.trim()) return stockList
    const kw = filterText.trim().toLowerCase()
    return stockList.filter(s =>
      s.name.toLowerCase().includes(kw) ||
      s.ts_code.toLowerCase().includes(kw) ||
      s.symbol.toLowerCase().includes(kw) ||
      (s.cnspell && s.cnspell.toLowerCase().includes(kw))
    )
  }, [stockList, filterText])

  const handleToggleAll = useCallback((checked: boolean) => {
    if (checked) {
      onStateChange({ selectedStrategyIds: strategies.map(s => s.id).filter((id): id is number => id !== undefined) })
    } else {
      onStateChange({ selectedStrategyIds: [] })
    }
  }, [strategies, onStateChange])

  const handleToggleStrategy = useCallback((strategyId: number, checked: boolean) => {
    if (checked) {
      onStateChange({ selectedStrategyIds: [...selectedStrategyIds, strategyId] })
    } else {
      onStateChange({ selectedStrategyIds: selectedStrategyIds.filter(id => id !== strategyId) })
    }
  }, [selectedStrategyIds, onStateChange])

  const handleStockClick = useCallback(async (stock: StockBasic) => {
    onStateChange({ selectedStock: stock, backtestResults: [], selectedResultIndex: null })
    setQuotes([])
    leftPanelRef.current?.focus()
    setQuotesLoading(true)
    try {
      const q = await queryDaily({
        tsCode: stock.ts_code,
        startDate,
        endDate,
      })
      if (mountedRef.current) setQuotes(q)
    } catch {
      if (mountedRef.current) msgApi.error('加载K线数据失败')
    } finally {
      if (mountedRef.current) setQuotesLoading(false)
    }
  }, [startDate, endDate, msgApi, onStateChange])

  const doRunBacktest = useCallback(async (tsCode: string, sIds: number[], sDate: string, eDate: string) => {
    const seq = ++backtestSeqRef.current
    onStateChange({ loading: true, backtestResults: [], selectedResultIndex: null })
    try {
      const results = await runBatchBacktest({
        tsCode,
        startDate: sDate,
        endDate: eDate,
        strategyIds: sIds,
      })
      if (mountedRef.current && seq === backtestSeqRef.current) {
        const sorted = [...results].sort((a, b) => b.result.profit_loss - a.result.profit_loss)
        onStateChange({
          backtestResults: sorted,
          selectedResultIndex: sorted.length > 0 ? 0 : null,
          loading: false,
        })
        const q = await queryDaily({ tsCode, startDate: sDate, endDate: eDate })
        if (mountedRef.current && seq === backtestSeqRef.current) {
          setQuotes(q)
        }
      }
    } catch (err) {
      if (mountedRef.current && seq === backtestSeqRef.current) {
        msgApi.error('回测失败: ' + (err as Error).message)
        onStateChange({ loading: false })
      }
    }
  }, [msgApi, onStateChange])

  const handleRunBacktest = useCallback(async () => {
    if (!selectedStock) {
      msgApi.warning('请先选择一只股票')
      return
    }
    if (selectedStrategyIds.length === 0) {
      msgApi.warning('请先选择至少一个策略')
      return
    }
    await doRunBacktest(selectedStock.ts_code, selectedStrategyIds, startDate, endDate)
  }, [selectedStock, selectedStrategyIds, startDate, endDate, doRunBacktest, msgApi])

  const handleKeyNav = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
    if (filteredStocks.length === 0) return
    if ((e.target as HTMLElement).tagName === 'INPUT') return
    if (!leftPanelRef.current?.contains(e.target as HTMLElement)) return

    e.preventDefault()
    e.stopPropagation()

    const currentIdx = selectedStock
      ? filteredStocks.findIndex(s => s.ts_code === selectedStock.ts_code)
      : -1
    let nextIdx: number
    if (e.key === 'ArrowDown') {
      nextIdx = currentIdx < filteredStocks.length - 1 ? currentIdx + 1 : currentIdx
    } else {
      nextIdx = currentIdx > 0 ? currentIdx - 1 : currentIdx
    }
    if (nextIdx < 0) nextIdx = 0
    if (nextIdx === currentIdx) return
    const stock = filteredStocks[nextIdx]
    if (stock) {
      onStateChange({ selectedStock: stock, backtestResults: [], selectedResultIndex: null })
      setQuotes([])
      requestAnimationFrame(() => {
        const el = stockListRef.current?.querySelector(`[data-ts-code="${stock.ts_code}"]`) as HTMLElement | null
        el?.scrollIntoView({ block: 'nearest' })
      })
      if (selectedStrategyIds.length > 0 && !loading) {
        doRunBacktest(stock.ts_code, selectedStrategyIds, startDate, endDate)
      }
    }
  }, [filteredStocks, selectedStock, selectedStrategyIds, loading, startDate, endDate, onStateChange, doRunBacktest])

  const scrollToStock = useCallback((tsCode: string) => {
    requestAnimationFrame(() => {
      const container = stockListRef.current
      if (!container) return
      const el = container.querySelector(`[data-ts-code="${tsCode}"]`) as HTMLElement | null
      if (el) {
        el.scrollIntoView({ block: 'center', behavior: 'smooth' })
      }
    })
  }, [])

  useEffect(() => {
    if (autoRunRef.current) return
    const paramTsCode = searchParams.get('tsCode')
    const paramStrategyId = searchParams.get('strategyId')
    if (!paramTsCode || !paramStrategyId) return

    const strategyId = Number(paramStrategyId)
    if (isNaN(strategyId)) return

    autoRunRef.current = true

    const autoStartDate = dayjs().subtract(3, 'year').format('YYYYMMDD')
    const autoEndDate = dayjs().format('YYYYMMDD')

    const found = stockList.find(s => s.ts_code === paramTsCode)
    if (found) {
      onStateChange({
        selectedStock: found,
        selectedStrategyIds: [strategyId],
        startDate: autoStartDate,
        endDate: autoEndDate,
      })
      scrollToStock(paramTsCode)
      doRunBacktest(paramTsCode, [strategyId], autoStartDate, autoEndDate)
    } else {
      getStockList().then(list => {
        if (!mountedRef.current) return
        setStockList(list)
        const stock = list.find(s => s.ts_code === paramTsCode)
        if (stock) {
          onStateChange({
            selectedStock: stock,
            selectedStrategyIds: [strategyId],
            startDate: autoStartDate,
            endDate: autoEndDate,
          })
          scrollToStock(paramTsCode)
          doRunBacktest(paramTsCode, [strategyId], autoStartDate, autoEndDate)
        } else {
          msgApi.warning('未找到股票: ' + paramTsCode)
          autoRunRef.current = false
        }
      })
    }
  }, [searchParams, stockList, onStateChange, doRunBacktest, msgApi, scrollToStock])

  const handleSelectResult = useCallback((index: number) => {
    onStateChange({ selectedResultIndex: index })
  }, [onStateChange])

  const handleDetailClick = useCallback((index: number, e: React.MouseEvent) => {
    e.stopPropagation()
    setDetailResultIndex(index)
    setDetailModalOpen(true)
  }, [])

  const handleResizerMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    setDragging(true)
    const panel = rightPanelRef.current
    if (!panel) return
    const panelHeight = panel.getBoundingClientRect().height
    const startY = e.clientY
    const startTopRatio = 100 - chartRatioRef.current

    const onMouseMove = (ev: MouseEvent) => {
      const dy = ev.clientY - startY
      const dyPercent = (dy / panelHeight) * 100
      const newTopRatio = Math.min(80, Math.max(20, startTopRatio + dyPercent))
      setChartRatio(100 - newTopRatio)
    }

    const onMouseUp = () => {
      setDragging(false)
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    document.body.style.cursor = 'row-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }, [])

  const selectedResult = selectedResultIndex !== null ? backtestResults[selectedResultIndex] : null
  const currentSignals = selectedResult?.result.signals ?? []
  const detailResult = detailResultIndex !== null ? backtestResults[detailResultIndex] : null

  return (
    <div className={styles.container}>
      <div className={styles.leftPanel} ref={leftPanelRef} onKeyDownCapture={handleKeyNav} tabIndex={-1}>
        <div className={styles.leftHeader}>
          <span>股票列表</span>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{filteredStocks.length}</span>
        </div>
        <div className={styles.searchBox}>
          <Input
            prefix={<SearchOutlined />}
            placeholder="代码/拼音/名称"
            value={filterText}
            onChange={e => setFilterText(e.target.value)}
            allowClear
            size="small"
          />
        </div>
        <div className={styles.stockList} ref={stockListRef}>
          {filteredStocks.map((stock, index) => (
            <div
              key={stock.ts_code}
              data-ts-code={stock.ts_code}
              className={`${styles.stockItem} ${selectedStock?.ts_code === stock.ts_code ? styles.stockItemActive : ''}`}
              onClick={() => handleStockClick(stock)}
            >
              <span className={styles.stockName}>{stock.name}</span>
              <span className={styles.stockCode}>{stock.ts_code}</span>
            </div>
          ))}
          {filteredStocks.length === 0 && (
            <div className={styles.emptyState}>无匹配股票</div>
          )}
        </div>
      </div>

      <div className={styles.rightPanel} ref={rightPanelRef}>
        <div className={styles.topHalf} style={{ height: `${100 - chartRatio}%` }}>
          <div className={styles.strategyBar}>
            <Checkbox
              checked={selectedStrategyIds.length === strategies.length && strategies.length > 0}
              indeterminate={selectedStrategyIds.length > 0 && selectedStrategyIds.length < strategies.length}
              onChange={e => handleToggleAll(e.target.checked)}
            >
              全选
            </Checkbox>
            <div className={styles.strategyList}>
              {strategies.filter(s => s.id !== undefined).map(strategy => (
                <div key={strategy.id!} className={styles.strategyItem}>
                  <Checkbox
                    checked={selectedStrategyIds.includes(strategy.id!)}
                    onChange={e => handleToggleStrategy(strategy.id!, e.target.checked)}
                  >
                    {strategy.name}
                  </Checkbox>
                </div>
              ))}
            </div>
            <div className={styles.dateRange}>
              <DatePicker
                value={dayjs(startDate, 'YYYYMMDD')}
                onChange={date => date && onStateChange({ startDate: date.format('YYYYMMDD') })}
                style={{ width: 130 }}
                disabled={loading}
                size="small"
              />
              <span style={{ margin: '0 4px', fontSize: 12 }}>至</span>
              <DatePicker
                value={dayjs(endDate, 'YYYYMMDD')}
                onChange={date => date && onStateChange({ endDate: date.format('YYYYMMDD') })}
                style={{ width: 130 }}
                disabled={loading}
                size="small"
              />
            </div>
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={handleRunBacktest}
              disabled={!selectedStock || selectedStrategyIds.length === 0}
              loading={loading}
              size="small"
            >
              回测
            </Button>
          </div>

          <div className={styles.infoBar}>
            {selectedStock ? (
              <>
                <span><span className={styles.infoLabel}>名称</span><span className={styles.infoValue}>{selectedStock.name}</span></span>
                <span><span className={styles.infoLabel}>代码</span><span className={styles.infoValue}>{selectedStock.ts_code}</span></span>
                <span><span className={styles.infoLabel}>板块</span><span className={styles.infoValue}>{selectedStock.industry || '-'}</span></span>
                <span><span className={styles.infoLabel}>市盈率</span><span className={styles.infoValue}>{selectedStock.pe_ratio != null ? selectedStock.pe_ratio.toFixed(2) : '-'}</span></span>
                <span><span className={styles.infoLabel}>总市值</span><span className={styles.infoValue}>{formatMarketValue(selectedStock.market_value)}</span></span>
                <span><span className={styles.infoLabel}>流通市值</span><span className={styles.infoValue}>{formatMarketValue(selectedStock.market_value_circulating)}</span></span>
              </>
            ) : (
              <span style={{ color: 'var(--text-muted)' }}>请从左侧选择股票</span>
            )}
          </div>

          <div className={styles.resultTable}>
            <div className={styles.tableHeader}>
              <span>股票名</span>
              <span>策略名</span>
              <span>交易次数</span>
              <span>交易明细</span>
              <span>盈亏金额</span>
              <span>盈亏百分比</span>
            </div>
            <div className={styles.tableBody}>
              {backtestResults.length > 0 ? backtestResults.map((item, index) => (
                <div
                  key={item.strategy_id}
                  className={`${styles.tableRow} ${selectedResultIndex === index ? styles.tableRowActive : ''}`}
                  onClick={() => handleSelectResult(index)}
                >
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{selectedStock?.name || '-'}</span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.strategy_name}</span>
                  <span>{item.result.trade_count}</span>
                  <span>
                    <Button
                      type="link"
                      size="small"
                      className={styles.detailBtn}
                      onClick={(e) => handleDetailClick(index, e)}
                    >
                      明细
                    </Button>
                  </span>
                  <span className={item.result.profit_loss >= 0 ? styles.valUp : styles.valDown}>
                    {item.result.profit_loss >= 0 ? '+' : ''}{formatMoney(item.result.profit_loss)}
                  </span>
                  <span className={item.result.total_return >= 0 ? styles.valUp : styles.valDown}>
                    {item.result.total_return >= 0 ? '+' : ''}{item.result.total_return.toFixed(2)}%
                  </span>
                </div>
              )) : (
                <div className={styles.emptyState}>
                  {loading ? '回测中...' : selectedStock ? '点击回测按钮开始' : '请先选择股票和策略'}
                </div>
              )}
            </div>
          </div>
        </div>

        <div
          className={`${styles.resizer} ${dragging ? styles.resizerDragging : ''}`}
          onMouseDown={handleResizerMouseDown}
        >
          <div className={styles.resizerLine} />
        </div>

        <div className={styles.chartArea} style={{ height: `${chartRatio}%` }}>
          {selectedStock && quotes.length > 0 ? (
            <>
              {quotesLoading && (
                <div style={{ padding: '4px 12px', fontSize: 12, color: 'var(--text-secondary)', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border-color)' }}>
                  加载K线中...
                </div>
              )}
              <DataPanel
                data={quotes}
                stockName={`${selectedStock.name} - ${selectedResult?.strategy_name ?? ''}`}
                signals={currentSignals}
              />
            </>
          ) : (
            <div className={styles.chartPlaceholder}>
              {selectedStock ? (quotesLoading ? '正在加载K线数据...' : '点击回测按钮查看图表') : '请从左侧选择股票'}
            </div>
          )}
        </div>
      </div>

      <Modal
        title={detailResult ? `${detailResult.strategy_name} - 交易明细` : '交易明细'}
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={null}
        width={1020}
        styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', maxHeight: '60vh' } }}
      >
        {detailResult && (
          <>
            <div className={styles.modalDetailHeader}>
              <span>序号</span>
              <span>买入日期</span>
              <span>买入价</span>
              <span>买入股数</span>
              <span>买入总金额</span>
              <span>买入手续费</span>
              <span>卖出日期</span>
              <span>卖出价</span>
              <span>卖出手续费</span>
              <span>盈亏金额</span>
              <span>收益率</span>
              <span>剩余金额</span>
            </div>
            <div className={styles.modalDetailBody}>
              {computeTrades(detailResult.result.signals).map((t, i) => (
                <div key={i} className={styles.modalDetailRow}>
                  <span>{i + 1}</span>
                  <span>{formatTradeDate(t.entry.trade_date)}</span>
                  <span>{t.entry.price.toFixed(2)}</span>
                  <span>{t.entry.shares}</span>
                  <span>{t.entry.buy_amount.toFixed(2)}</span>
                  <span>{t.entry.fees.toFixed(2)}</span>
                  <span>{t.exit ? formatTradeDate(t.exit.trade_date) : '-'}</span>
                  <span>{t.exit ? t.exit.price.toFixed(2) : '-'}</span>
                  <span>{t.exit ? t.exit.sell_fees.toFixed(2) : '-'}</span>
                  <span>
                    {t.exit ? (
                      <span className={t.exit.profit >= 0 ? styles.valUp : styles.valDown}>
                        {t.exit.profit >= 0 ? '+' : ''}{(t.exit.profit / 10000).toFixed(2)}万
                      </span>
                    ) : (
                      <span className={styles.holdTag}>持仓中</span>
                    )}
                  </span>
                  <span>
                    {t.exit ? (
                      <span className={t.exit.profit_pct >= 0 ? styles.valUp : styles.valDown}>
                        {t.exit.profit_pct >= 0 ? '+' : ''}{t.exit.profit_pct.toFixed(2)}%
                      </span>
                    ) : (
                      <span className={styles.holdTag}>持仓中</span>
                    )}
                  </span>
                  <span>{formatMoney(t.exit ? t.exit.remaining_cash : t.entry.remaining_cash)}</span>
                </div>
              ))}
              {computeTrades(detailResult.result.signals).length === 0 && (
                <div className={styles.modalDetailEmpty}>无交易记录</div>
              )}
            </div>
            {computeTrades(detailResult.result.signals).length > 0 && (
              <div className={styles.modalDetailSubtotal}>
                <span></span>
                <span>小计</span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span className={detailResult.result.profit_loss >= 0 ? styles.valUp : styles.valDown}>
                  {detailResult.result.profit_loss >= 0 ? '+' : ''}{formatMoney(detailResult.result.profit_loss)}
                </span>
                <span className={detailResult.result.total_return >= 0 ? styles.valUp : styles.valDown}>
                  {detailResult.result.total_return >= 0 ? '+' : ''}{detailResult.result.total_return.toFixed(2)}%
                </span>
                <span>{formatMoney(detailResult.result.final_capital ?? 0)}</span>
              </div>
            )}
          </>
        )}
      </Modal>
    </div>
  )
}

export default BacktestPage
