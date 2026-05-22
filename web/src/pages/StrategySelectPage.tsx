import { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { App, Checkbox, Button, DatePicker, Collapse, Tag, Progress, Modal, Radio } from 'antd'
import { PlayCircleOutlined, RiseOutlined, FallOutlined, CloseCircleOutlined, UnorderedListOutlined, HistoryOutlined, DeleteOutlined, CopyOutlined, EyeOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useValidStrategies } from '../hooks/useStrategies'
import { useAuth } from '../contexts/AuthContext'
import { startStockScreen, getStockScreenProgress, getStockScreenList, cancelStockScreen, getScreenHistory, deleteScreenTask, getStockScreenDetail } from '../api/stock'
import { queryDaily } from '../api/stock'
import { addTrackStock } from '../api/track'
import type { ScreenTaskHistory } from '../api/stock'
import DataPanel from '../components/DataPanel/DataPanel'
import type { DailyQuote, BacktestSignal, BacktestResult, StockScreenMatch } from '../types'
import styles from './StrategySelectPage.module.css'

interface SelectedStock {
  tsCode: string
  name: string
  strategyId: number
  strategyName: string
  quotes: DailyQuote[]
  signals: BacktestSignal[]
  result: BacktestResult
  taskId: string
}

interface ScreenProgress {
  current: number
  total: number
  currentStock: string
}

export interface StrategySelectPageState {
  selectedStrategyIds: number[]
  startDate: string
  endDate: string
  screenMode: 'buy_signal' | 'holding'
  selectedStocks: SelectedStock[]
  selectedStockIndex: number | null
  loading: boolean
  progress: ScreenProgress
  taskId: string | null
  visibleStrategyIds: number[]
}

export const defaultStrategySelectPageState: StrategySelectPageState = {
  selectedStrategyIds: [],
  startDate: dayjs().subtract(2, 'year').format('YYYYMMDD'),
  endDate: dayjs().format('YYYYMMDD'),
  screenMode: 'buy_signal',
  selectedStocks: [],
  selectedStockIndex: null,
  loading: false,
  progress: { current: 0, total: 0, currentStock: '' },
  taskId: null,
  visibleStrategyIds: [],
}

interface StrategySelectPageProps {
  state: StrategySelectPageState
  onStateChange: (patch: Partial<StrategySelectPageState>) => void
}

let globalPollTimer: ReturnType<typeof setTimeout> | null = null
let globalPollGeneration = 0

const POLL_INTERVAL = 1500

function mapMatchToSelectedStock(match: StockScreenMatch, taskId: string): SelectedStock {
  return {
    tsCode: match.ts_code,
    name: match.name,
    strategyId: match.strategy_id,
    strategyName: match.strategy_name,
    quotes: [],
    signals: [],
    result: match.result,
    taskId,
  }
}

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

const formatTsCode = (tsCode: string) => {
  return tsCode.split('.')[0]
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

const StrategySelectPage = ({ state, onStateChange }: StrategySelectPageProps) => {
  const { message: msgApi } = App.useApp()
  const { strategies } = useValidStrategies()
  const { user } = useAuth()

  const { selectedStrategyIds, startDate, endDate, screenMode, selectedStocks, selectedStockIndex, loading, progress, taskId, visibleStrategyIds: visibleStrategyIdsArr } = state

  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [detailStockIndex, setDetailStockIndex] = useState<number | null>(null)
  const [historyModalOpen, setHistoryModalOpen] = useState(false)
  const [historyTasks, setHistoryTasks] = useState<ScreenTaskHistory[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [loadHistoryLoading, setLoadHistoryLoading] = useState(false)
  const pollFnRef = useRef<(tid: string, gen: number) => Promise<void>>(() => Promise.resolve())
  const mountedRef = useRef(true)
  const selectedStocksRef = useRef(selectedStocks)
  selectedStocksRef.current = selectedStocks
  const selectedStockIndexRef = useRef(selectedStockIndex)
  selectedStockIndexRef.current = selectedStockIndex
  const loadQuotesForStockRef = useRef<(stocks: SelectedStock[], index: number, overrideStartDate?: string, overrideEndDate?: string) => Promise<void>>(() => Promise.resolve())
  const stockListRef = useRef<HTMLDivElement>(null)
  const resultContainerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    mountedRef.current = true

    if (selectedStockIndex !== null && selectedStocks[selectedStockIndex]) {
      const stock = selectedStocks[selectedStockIndex]
      if (stock.quotes.length > 0 && stock.signals.length === 0 && stock.taskId) {
        loadQuotesForStockRef.current(selectedStocks, selectedStockIndex)
      }
    }

    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    if (loading && taskId) {
      globalPollGeneration++
      if (globalPollTimer) {
        clearTimeout(globalPollTimer)
        globalPollTimer = null
      }
      pollFnRef.current(taskId, globalPollGeneration)
    }
  }, [loading, taskId])

  const setLoading = useCallback((value: boolean) => {
    onStateChange({ loading: value })
  }, [onStateChange])

  const setProgress = useCallback((p: ScreenProgress) => {
    onStateChange({ progress: p })
  }, [onStateChange])

  const setTaskId = useCallback((id: string | null) => {
    onStateChange({ taskId: id })
  }, [onStateChange])

  const strategyStats = useMemo(() => {
    if (selectedStocks.length === 0) return []
    const statsMap = new Map<number, {
      strategyId: number
      strategyName: string
      count: number
      total_return: number
      avgReturn: number
      win_rate: number
    }>()

    for (const stock of selectedStocks) {
      const existing = statsMap.get(stock.strategyId)
      if (existing) {
        existing.count++
        existing.total_return += stock.result.total_return
        existing.avgReturn = existing.total_return / existing.count
      } else {
        statsMap.set(stock.strategyId, {
          strategyId: stock.strategyId,
          strategyName: stock.strategyName,
          count: 1,
          total_return: stock.result.total_return,
          avgReturn: stock.result.total_return,
          win_rate: stock.result.win_rate
        })
      }
    }

    return Array.from(statsMap.values()).sort((a, b) => b.count - a.count)
  }, [selectedStocks])

  const setSelectedStrategyIds = useCallback((ids: number[]) => {
    onStateChange({ selectedStrategyIds: ids })
  }, [onStateChange])

  const setStartDate = useCallback((date: string) => {
    onStateChange({ startDate: date })
  }, [onStateChange])

  const setEndDate = useCallback((date: string) => {
    onStateChange({ endDate: date })
  }, [onStateChange])

  const setScreenMode = useCallback((mode: 'buy_signal' | 'holding') => {
    onStateChange({ screenMode: mode })
  }, [onStateChange])

  const setSelectedStocks = useCallback((stocks: SelectedStock[]) => {
    onStateChange({ selectedStocks: stocks })
  }, [onStateChange])

  const setSelectedStockIndex = useCallback((index: number | null) => {
    onStateChange({ selectedStockIndex: index })
  }, [onStateChange])

  const mergeStocksWithQuotes = useCallback((newStocks: SelectedStock[]) => {
    const currentStocks = selectedStocksRef.current
    if (currentStocks.length === 0) {
      setSelectedStocks(newStocks)
      return
    }
    const dataMap = new Map<string, { quotes: DailyQuote[]; signals: BacktestSignal[] }>()
    for (const s of currentStocks) {
      const key = `${s.tsCode}-${s.strategyId}`
      const existing = dataMap.get(key)
      if (existing) {
        if (s.quotes.length > 0) existing.quotes = s.quotes
        if (s.signals.length > 0) existing.signals = s.signals
      } else {
        dataMap.set(key, { quotes: s.quotes, signals: s.signals })
      }
    }
    const merged = newStocks.map(s => {
      const key = `${s.tsCode}-${s.strategyId}`
      const existing = dataMap.get(key)
      if (existing && (existing.quotes.length > 0 || existing.signals.length > 0)) {
        return { ...s, quotes: existing.quotes.length > 0 ? existing.quotes : s.quotes, signals: existing.signals.length > 0 ? existing.signals : s.signals }
      }
      return s
    })
    setSelectedStocks(merged)
  }, [setSelectedStocks])

  const handleToggleAll = (checked: boolean) => {
    if (checked) {
      setSelectedStrategyIds(strategies.map(s => s.id).filter((id): id is number => id !== undefined))
    } else {
      setSelectedStrategyIds([])
    }
  }

  const handleToggleStrategy = (strategyId: number, checked: boolean) => {
    if (checked) {
      setSelectedStrategyIds([...selectedStrategyIds, strategyId])
    } else {
      setSelectedStrategyIds(selectedStrategyIds.filter(id => id !== strategyId))
    }
  }

  const lastMatchCountRef = useRef(0)

  const pollProgress = useCallback(async (tid: string, generation: number) => {
    if (generation !== globalPollGeneration) return

    try {
      const p = await getStockScreenProgress(tid)

      if (generation !== globalPollGeneration) return

      if (mountedRef.current) {
        setProgress({ current: p.current, total: p.total, currentStock: p.current_stock })
      }

      if (p.cancelled) {
        globalPollTimer = null
        if (mountedRef.current) {
          setLoading(false)
          setProgress({ current: 0, total: 0, currentStock: '' })
          setTaskId(null)
          msgApi.info('已取消选股')
        }
        return
      }

      if (p.init_error) {
        globalPollTimer = null
        if (mountedRef.current) {
          setLoading(false)
          setTaskId(null)
          msgApi.error('选股初始化失败: ' + p.init_error)
        }
        return
      }

      if (p.match_count > 0 && p.match_count !== lastMatchCountRef.current) {
        try {
          const matches = await getStockScreenList(tid)
          if (generation !== globalPollGeneration) return
          if (mountedRef.current) {
            const stocks = matches.map(m => mapMatchToSelectedStock(m, tid))
            mergeStocksWithQuotes(stocks)
          }
          lastMatchCountRef.current = p.match_count
        } catch {
        }
      }

      if (p.completed) {
        globalPollTimer = null
        try {
          const matches = await getStockScreenList(tid)
          if (generation !== globalPollGeneration) return
          if (mountedRef.current) {
            const stocks = matches.map(m => mapMatchToSelectedStock(m, tid))
            mergeStocksWithQuotes(stocks)
            if (stocks.length > 0 && selectedStockIndexRef.current === null) {
              setSelectedStockIndex(0)
              loadQuotesForStockRef.current(stocks, 0)
            }
            lastMatchCountRef.current = 0
            msgApi.success(`选股完成，找到 ${stocks.length} 只符合条件的股票`)
          }
        } catch {
          if (mountedRef.current && generation === globalPollGeneration) msgApi.error('获取选股结果失败')
        } finally {
          if (mountedRef.current && generation === globalPollGeneration) {
            setLoading(false)
          }
          setTaskId(null)
        }
        return
      }

      globalPollTimer = setTimeout(() => {
        pollFnRef.current(tid, generation)
      }, POLL_INTERVAL)
    } catch (err) {
      if (generation !== globalPollGeneration) return
      globalPollTimer = null
      if (mountedRef.current) {
        console.error('轮询进度失败:', err)
        setLoading(false)
        setTaskId(null)
        msgApi.error('选股任务已失效')
      }
    }
  }, [msgApi, setSelectedStocks, setLoading, setProgress, setTaskId, mergeStocksWithQuotes])

  pollFnRef.current = pollProgress

  const handleCancel = useCallback(async () => {
    globalPollGeneration++

    if (globalPollTimer) {
      clearTimeout(globalPollTimer)
      globalPollTimer = null
    }

    if (taskId) {
      try {
        await cancelStockScreen(taskId)
      } catch {
      }
    }
    setTaskId(null)
    setLoading(false)
    setProgress({ current: 0, total: 0, currentStock: '' })
    msgApi.info('已取消选股')
  }, [msgApi, taskId, setTaskId, setLoading, setProgress])

  const handleRun = useCallback(async () => {
    if (selectedStrategyIds.length === 0) {
      msgApi.warning('请先选择至少一个策略')
      return
    }
    if (!user?.username) {
      msgApi.warning('请先登录')
      return
    }

    globalPollGeneration++
    if (globalPollTimer) {
      clearTimeout(globalPollTimer)
      globalPollTimer = null
    }

    setLoading(true)
    setSelectedStocks([])
    setSelectedStockIndex(null)
    onStateChange({ visibleStrategyIds: [] })
    knownStrategyIdsRef.current = new Set()
    setProgress({ current: 0, total: 0, currentStock: '启动中...' })

    try {
      const newTaskId = await startStockScreen({
        username: user.username,
        strategyIds: selectedStrategyIds,
        startDate,
        endDate,
        screenMode,
      })

      setTaskId(newTaskId)
      pollProgress(newTaskId, globalPollGeneration)
    } catch (err) {
      if (mountedRef.current) {
        msgApi.error('启动选股失败: ' + (err as Error).message)
        setLoading(false)
      }
    }
  }, [user?.username, selectedStrategyIds, startDate, endDate, screenMode, msgApi, setSelectedStocks, setSelectedStockIndex, setLoading, setProgress, setTaskId, pollProgress, onStateChange])

  const [quotesLoading, setQuotesLoading] = useState(false)
  const visibleStrategyIds = useMemo(() => new Set(visibleStrategyIdsArr), [visibleStrategyIdsArr])
  const knownStrategyIdsRef = useRef<Set<number>>(new Set(strategyStats.map(s => s.strategyId)))

  const setVisibleStrategyIds = useCallback((updater: Set<number> | ((prev: Set<number>) => Set<number>)) => {
    const newSet = updater instanceof Set ? updater : updater(visibleStrategyIds)
    onStateChange({ visibleStrategyIds: [...newSet] })
  }, [onStateChange, visibleStrategyIds])

  useEffect(() => {
    const currentIds = new Set(strategyStats.map(s => s.strategyId))
    const trulyNewIds = [...currentIds].filter(id => !knownStrategyIdsRef.current.has(id))
    if (trulyNewIds.length > 0) {
      setVisibleStrategyIds(prev => new Set([...prev, ...trulyNewIds]))
    }
    knownStrategyIdsRef.current = currentIds
  }, [strategyStats, setVisibleStrategyIds])

  const filteredStocks = useMemo(() => {
    if (visibleStrategyIds.size === 0 && strategyStats.length === 0) return selectedStocks
    if (visibleStrategyIds.size === strategyStats.length) return selectedStocks
    return selectedStocks.filter(s => visibleStrategyIds.has(s.strategyId))
  }, [selectedStocks, visibleStrategyIds, strategyStats.length])

  const loadQuotesForStock = useCallback(async (stocks: SelectedStock[], index: number, overrideStartDate?: string, overrideEndDate?: string) => {
    const stock = stocks[index]
    const needsQuotes = stock.quotes.length === 0
    const needsSignals = stock.signals.length === 0

    if (!needsQuotes && !needsSignals) return

    const qStartDate = overrideStartDate ?? startDate
    const qEndDate = overrideEndDate ?? endDate

    setQuotesLoading(true)
    try {
      const targetKey = `${stock.tsCode}-${stock.strategyId}`
      let updatedStocks = [...selectedStocksRef.current]
      let targetIdx = updatedStocks.findIndex(s => `${s.tsCode}-${s.strategyId}` === targetKey)
      if (targetIdx < 0) {
        updatedStocks = [...stocks]
        targetIdx = updatedStocks.findIndex(s => `${s.tsCode}-${s.strategyId}` === targetKey)
      }
      if (targetIdx < 0) { setQuotesLoading(false); return }

      if (needsQuotes) {
        const quotes = await queryDaily({
          tsCode: stock.tsCode,
          startDate: qStartDate,
          endDate: qEndDate,
        })
        if (mountedRef.current && quotes.length > 0) {
          updatedStocks = updatedStocks.map(s =>
            `${s.tsCode}-${s.strategyId}` === targetKey ? { ...s, quotes } : s
          )
        }
      }

      if (needsSignals && stock.taskId) {
        try {
          const detail = await getStockScreenDetail(stock.taskId, stock.tsCode, stock.strategyId)
          if (mountedRef.current && detail?.result) {
            updatedStocks = updatedStocks.map(s =>
              `${s.tsCode}-${s.strategyId}` === targetKey ? { ...s, signals: detail.result.signals ?? [], result: { ...s.result, ...detail.result } } : s
            )
          }
        } catch {}
      }

      if (mountedRef.current) {
        setSelectedStocks(updatedStocks)
      }
    } catch {
      if (mountedRef.current) msgApi.error('加载K线数据失败')
    } finally {
      if (mountedRef.current) setQuotesLoading(false)
    }
  }, [startDate, endDate, msgApi, setSelectedStocks])

  loadQuotesForStockRef.current = loadQuotesForStock

  const handleStockClick = async (index: number) => {
    const stock = filteredStocks[index]
    const originalIndex = selectedStocks.findIndex(s => s.tsCode === stock.tsCode && s.strategyId === stock.strategyId)
    if (originalIndex >= 0) {
      setSelectedStockIndex(originalIndex)
      loadQuotesForStock(selectedStocks, originalIndex)
    }
  }

  const handleKeyNav = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
    if (filteredStocks.length === 0) return
    if ((e.target as HTMLElement).tagName === 'INPUT') return
    if (!resultContainerRef.current?.contains(e.target as HTMLElement)) return

    e.preventDefault()
    e.stopPropagation()

    const currentStock = selectedStockIndex !== null ? selectedStocks[selectedStockIndex] : null
    const currentIdx = currentStock
      ? filteredStocks.findIndex(s => s.tsCode === currentStock.tsCode && s.strategyId === currentStock.strategyId)
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
    const originalIndex = selectedStocks.findIndex(s => s.tsCode === stock.tsCode && s.strategyId === stock.strategyId)
    if (originalIndex >= 0) {
      setSelectedStockIndex(originalIndex)
      loadQuotesForStock(selectedStocks, originalIndex)
      requestAnimationFrame(() => {
        const el = stockListRef.current?.querySelector(`[data-ts-code="${stock.tsCode}"][data-strategy-id="${stock.strategyId}"]`) as HTMLElement | null
        el?.scrollIntoView({ block: 'nearest' })
      })
    }
  }, [filteredStocks, selectedStocks, selectedStockIndex, setSelectedStockIndex, loadQuotesForStock])

  const handleDetailClick = async (index: number, e: React.MouseEvent) => {
    e.stopPropagation()
    const stock = filteredStocks[index]
    const originalIndex = selectedStocks.findIndex(s => s.tsCode === stock.tsCode && s.strategyId === stock.strategyId)
    if (originalIndex >= 0) {
      setDetailStockIndex(originalIndex)
      setDetailModalOpen(true)
      loadQuotesForStock(selectedStocks, originalIndex)
    }
  }

  const handleTrackClick = async (index: number, e: React.MouseEvent) => {
    e.stopPropagation()
    const stock = filteredStocks[index]
    try {
      await addTrackStock({
        ts_code: stock.tsCode,
        stock_name: stock.name,
        strategy_id: stock.strategyId,
        strategy_name: stock.strategyName,
      })
      msgApi.success(`已添加 ${stock.name} 到跟踪`)
    } catch (err: any) {
      msgApi.error(err?.message || '添加跟踪失败')
    }
  }

  const handleOpenHistory = useCallback(async () => {
    if (!user?.username) {
      msgApi.warning('请先登录')
      return
    }
    setHistoryLoading(true)
    setHistoryModalOpen(true)
    try {
      const tasks = await getScreenHistory(user.username)
      if (mountedRef.current) {
        setHistoryTasks(tasks)
      }
    } catch {
      if (mountedRef.current) msgApi.error('获取历史任务失败')
    } finally {
      if (mountedRef.current) setHistoryLoading(false)
    }
  }, [user?.username, msgApi])

  const handleLoadHistoryTask = useCallback(async (task: ScreenTaskHistory) => {
    setLoadHistoryLoading(true)
    try {
      const matches = await getStockScreenList(task.task_id)
      if (matches && matches.length > 0) {
        const stocks = matches.map(m => mapMatchToSelectedStock(m, task.task_id))
        setSelectedStocks(stocks)
        setSelectedStockIndex(0)
        if (task.start_date) setStartDate(task.start_date)
        if (task.end_date) setEndDate(task.end_date)
        setHistoryModalOpen(false)
        loadQuotesForStock(stocks, 0, task.start_date, task.end_date)
        msgApi.success(`已加载历史任务，共 ${stocks.length} 只股票`)
      } else {
        msgApi.warning('该任务无匹配结果')
      }
    } catch (e: any) {
      msgApi.error(e.message || '加载历史任务失败')
    } finally {
      if (mountedRef.current) setLoadHistoryLoading(false)
    }
  }, [msgApi, setSelectedStocks, setSelectedStockIndex, setStartDate, setEndDate, loadQuotesForStock])

  const handleDeleteHistoryTask = useCallback(async (taskId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      await deleteScreenTask(taskId)
      setHistoryTasks(prev => prev.filter(t => t.task_id !== taskId))
      msgApi.success('已删除选股任务')
    } catch (e: any) {
      msgApi.error(e.message || '删除失败')
    }
  }, [msgApi])

  const progressPercent = progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : 0

  const selectedStock = selectedStockIndex !== null ? selectedStocks[selectedStockIndex] : null
  const selectedStockVisible = selectedStock ? visibleStrategyIds.has(selectedStock.strategyId) : false
  const detailStock = detailStockIndex !== null ? selectedStocks[detailStockIndex] : null

  return (
    <div className={styles.container}>
      <div className={styles.content}>
        <div className={styles.configPanel}>
          <div className={styles.configSection}>
            <div className={styles.sectionTitle}>
              <span>选择策略</span>
              <Checkbox
                checked={selectedStrategyIds.length === strategies.length && strategies.length > 0}
                indeterminate={selectedStrategyIds.length > 0 && selectedStrategyIds.length < strategies.length}
                onChange={e => handleToggleAll(e.target.checked)}
                style={{ marginLeft: 12 }}
              >
                全选
              </Checkbox>
            </div>
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
          </div>

          <div className={styles.configSection}>
            <div className={styles.sectionTitle}>时间范围</div>
            <div className={styles.dateRange}>
              <DatePicker
                value={dayjs(startDate, 'YYYYMMDD')}
                onChange={date => date && setStartDate(date.format('YYYYMMDD'))}
                style={{ width: 150 }}
                disabled={loading}
              />
              <span style={{ margin: '0 8px' }}>至</span>
              <DatePicker
                value={dayjs(endDate, 'YYYYMMDD')}
                onChange={date => date && setEndDate(date.format('YYYYMMDD'))}
                style={{ width: 150 }}
                disabled={loading}
              />
              {loading ? (
                <>
                  <Button
                    danger
                    icon={<CloseCircleOutlined />}
                    onClick={handleCancel}
                    style={{ marginLeft: 16 }}
                  >
                    取消
                  </Button>
                  <div className={styles.progressInline}>
                    <Progress
                      percent={progressPercent}
                      size="small"
                      status="active"
                      format={() => progress.total > 0 ? `${progress.current}/${progress.total}` : '准备中...'}
                      style={{ width: 150 }}
                    />
                    <span className={styles.progressStock}>{progress.currentStock}</span>
                  </div>
                </>
              ) : (
                <>
                  <Radio.Group
                    value={screenMode}
                    onChange={e => setScreenMode(e.target.value)}
                    optionType="button"
                    buttonStyle="solid"
                    size="small"
                    style={{ marginLeft: 16 }}
                  >
                    <Radio.Button value="buy_signal">最新买入信号</Radio.Button>
                    <Radio.Button value="holding">当前持仓状态</Radio.Button>
                  </Radio.Group>
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={handleRun}
                    disabled={selectedStrategyIds.length === 0}
                    style={{ marginLeft: 8 }}
                  >
                    开始选股
                  </Button>
                  <Button
                    icon={<HistoryOutlined />}
                    onClick={handleOpenHistory}
                    style={{ marginLeft: 8 }}
                  >
                    历史选股
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>

        {selectedStocks.length > 0 && (
          <div className={styles.resultPanel}>
            {strategyStats.length > 0 && (
              <Collapse
                className={styles.strategyCollapse}
                defaultActiveKey={[]}
                items={[
                  {
                    key: '1',
                    label: (
                      <div className={styles.strategyCollapseLabel} onClick={e => e.stopPropagation()}>
                        <Checkbox
                          checked={visibleStrategyIds.size === strategyStats.length && strategyStats.length > 0}
                          indeterminate={visibleStrategyIds.size > 0 && visibleStrategyIds.size < strategyStats.length}
                          onChange={e => {
                            if (e.target.checked) {
                              setVisibleStrategyIds(new Set(strategyStats.map(s => s.strategyId)))
                            } else {
                              setVisibleStrategyIds(new Set())
                            }
                          }}
                        >
                          各策略表现
                        </Checkbox>
                      </div>
                    ),
                    children: (
                      <div className={styles.strategyStatsList}>
                        {strategyStats.map((stat) => (
                          <div key={stat.strategyId} className={styles.strategyStatsItem}>
                            <Checkbox
                              checked={visibleStrategyIds.has(stat.strategyId)}
                              onChange={e => {
                                setVisibleStrategyIds(prev => {
                                  const next = new Set(prev)
                                  if (e.target.checked) {
                                    next.add(stat.strategyId)
                                  } else {
                                    next.delete(stat.strategyId)
                                  }
                                  return next
                                })
                              }}
                            />
                            <div className={styles.strategyStatsRank}>{strategyStats.findIndex(s => s.strategyId === stat.strategyId) + 1}</div>
                            <div className={styles.strategyStatsInfo}>
                              <div className={styles.strategyStatsName}>{stat.strategyName}</div>
                              <div className={styles.strategyStatsMeta}>
                                <span>选出 {stat.count} 只</span>
                                <span style={{ color: stat.avgReturn >= 0 ? '#ef5350' : '#26a69a', fontWeight: 600 }}>
                                  {stat.avgReturn >= 0 ? <RiseOutlined /> : <FallOutlined />} {stat.avgReturn.toFixed(2)}%
                                </span>
                                <span style={{ color: stat.win_rate >= 50 ? '#52c41a' : '#faad14' }}>
                                  胜率 {stat.win_rate.toFixed(1)}%
                                </span>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )
                  }
                ]}
              />
            )}

            <div className={styles.resultContainer} ref={resultContainerRef} onKeyDownCapture={handleKeyNav} tabIndex={-1}>
              <div className={styles.stockListPanel}>
                <div className={styles.stockListTitle}>
                  <span>选股结果 ({filteredStocks.length}{filteredStocks.length !== selectedStocks.length ? `/${selectedStocks.length}` : ''}){loading ? ' - 选股中...' : ''}</span>
                  {filteredStocks.length > 0 && (
                    <Button
                      type="text"
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => {
                        const text = filteredStocks.map(s => `${s.name}:${s.tsCode}`).join('\n')
                        navigator.clipboard.writeText(text).then(() => {
                          msgApi.success('已复制到剪贴板')
                        }).catch(() => {
                          msgApi.error('复制失败')
                        })
                      }}
                    />
                  )}
                </div>
                <div className={styles.stockList} ref={stockListRef}>
                  {filteredStocks.map((stock, index) => (
                    <div
                      key={`${stock.tsCode}-${stock.strategyId}`}
                      data-ts-code={stock.tsCode}
                      data-strategy-id={stock.strategyId}
                      className={`${styles.stockItem} ${selectedStockIndex !== null && selectedStock?.tsCode === stock.tsCode && selectedStock?.strategyId === stock.strategyId ? styles.stockItemActive : ''}`}
                      onClick={() => handleStockClick(index)}
                    >
                      <div className={styles.stockItemContent}>
                        <div>
                          <span className={styles.stockName}>{stock.name}</span>
                          <span className={styles.stockCode}>{formatTsCode(stock.tsCode)}</span>
                          <span style={{ marginLeft: 8, color: stock.result.profit_loss >= 0 ? '#ff4d4f' : '#52c41a', fontSize: 12 }}>
                            {stock.result.profit_loss != null ? `${stock.result.profit_loss >= 0 ? '+' : ''}${Math.round(stock.result.profit_loss / 10000)}万` : ''}
                          </span>
                        </div>
                        <div className={styles.stockStrategy}>{stock.strategyName}</div>
                      </div>
                      <div className={styles.stockItemActions}>
                        <Button type="link" size="small" icon={<EyeOutlined />} onClick={(e) => handleTrackClick(index, e)} className={styles.detailBtn} />
                        <Button type="link" size="small" icon={<UnorderedListOutlined />} onClick={(e) => handleDetailClick(index, e)} className={styles.detailBtn} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className={styles.chartPanel}>
                {selectedStock && selectedStockVisible ? (
                  quotesLoading ? (
                    <div className={styles.chartPlaceholder}>
                      正在加载K线数据...
                    </div>
                  ) : selectedStock.quotes.length > 0 ? (
                    <DataPanel
                      key={`${selectedStock.tsCode}-${selectedStock.strategyId}`}
                      data={selectedStock.quotes}
                      stockName={selectedStock.name}
                      signals={selectedStock.signals}
                    />
                  ) : (
                    <div className={styles.chartPlaceholder}>
                      K线数据加载失败
                    </div>
                  )
                ) : (
                  <div className={styles.chartPlaceholder}>
                    请从左侧列表选择股票
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      <Modal
        title="历史选股任务"
        open={historyModalOpen}
        onCancel={() => { if (!loadHistoryLoading) setHistoryModalOpen(false) }}
        footer={null}
        width={700}
        loading={historyLoading || loadHistoryLoading}
      >
        {historyTasks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无历史选股任务</div>
        ) : (
          <div className={styles.historyList}>
            {historyTasks.map(task => (
              <div
                key={task.task_id}
                className={styles.historyItem}
                onClick={() => handleLoadHistoryTask(task)}
              >
                <div className={styles.historyItemLeft}>
                  <div className={styles.historyItemDate}>
                    {task.created_at ? dayjs(task.created_at).format('YYYY-MM-DD HH:mm') : '-'}
                  </div>
                  <div className={styles.historyItemRange}>
                    {task.start_date} ~ {task.end_date}
                  </div>
                </div>
                <div className={styles.historyItemRight}>
                  <Tag color={task.completed ? 'green' : 'orange'}>
                    {task.completed ? '已完成' : '进行中'}
                  </Tag>
                  <span className={styles.historyItemMatch}>
                    匹配 {task.match_count} 只
                  </span>
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={(e) => handleDeleteHistoryTask(task.task_id, e)}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Modal
        title={detailStock ? `${detailStock.name} - ${detailStock.strategyName}` : '交易明细'}
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={null}
        width={1020}
        styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', maxHeight: '60vh' } }}
      >
        {detailStock && (
          <>
            <div className={styles.detailHeader}>
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
            <div className={styles.detailBody}>
              {computeTrades(detailStock.signals).map((t, i) => (
                <div key={i} className={styles.detailRow}>
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
              {computeTrades(detailStock.signals).length === 0 && (
                <div className={styles.detailEmpty}>无交易记录</div>
              )}
            </div>
            {computeTrades(detailStock.signals).length > 0 && (
              <div className={styles.detailSubtotal}>
                <span></span>
                <span>小计</span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span></span>
                <span className={detailStock.result.profit_loss >= 0 ? styles.valUp : styles.valDown}>
                  {detailStock.result.profit_loss >= 0 ? '+' : ''}{formatMoney(detailStock.result.profit_loss)}
                </span>
                <span className={detailStock.result.total_return >= 0 ? styles.valUp : styles.valDown}>
                  {detailStock.result.total_return >= 0 ? '+' : ''}{detailStock.result.total_return.toFixed(2)}%
                </span>
                <span>{formatMoney(detailStock.result.final_capital ?? 0)}</span>
              </div>
            )}
          </>
        )}
      </Modal>
    </div>
  )
}

export default StrategySelectPage
