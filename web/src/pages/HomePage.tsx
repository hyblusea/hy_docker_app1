import { useCallback, useRef, useEffect } from 'react'
import { App } from 'antd'
import SearchPanel from '../components/SearchPanel/SearchPanel'
import type { SearchPanelHandle } from '../components/SearchPanel/SearchPanel'
import HistoryTags from '../components/HistoryTags/HistoryTags'
import BacktestPanel from '../components/BacktestPanel/BacktestPanel'
import DataPanel from '../components/DataPanel/DataPanel'
import { queryDaily } from '../api/stock'
import type { DailyQuote, SearchQuery, HistoryTag, BacktestSignal } from '../types'

const STORAGE_KEY = 'tradingx_history_tags'
const MAX_TAGS = 50

function loadTags(): HistoryTag[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return parsed.slice(0, MAX_TAGS)
    }
  } catch {}
  return []
}

function saveTags(tags: HistoryTag[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tags.slice(0, MAX_TAGS)))
  } catch {}
}

export interface HomePageState {
  dailyData: DailyQuote[]
  currentStock: string
  signals: BacktestSignal[]
  backtestKey: number
  history: HistoryTag[]
}

export const defaultHomePageState: HomePageState = {
  dailyData: [],
  currentStock: '',
  signals: [],
  backtestKey: 0,
  history: loadTags(),
}

interface HomePageProps {
  state: HomePageState
  onStateChange: (patch: Partial<HomePageState>) => void
}

const HomePage = ({ state, onStateChange }: HomePageProps) => {
  const { message } = App.useApp()
  const searchPanelRef = useRef<SearchPanelHandle>(null)

  useEffect(() => {
    saveTags(state.history)
  }, [state.history])

  const handleSearch = useCallback(async (query: SearchQuery) => {
    if (query.period !== 'day') {
      message.info('目前仅支持日K查询，其他周期后续开放')
      return
    }
    try {
      const data = await queryDaily({
        tsCode: query.tsCode,
        startDate: query.startDate,
        endDate: query.endDate,
      })
      const newTag = { tsCode: query.tsCode, name: query.name }
      onStateChange({
        dailyData: data,
        currentStock: query.name,
        signals: [],
        backtestKey: state.backtestKey + 1,
        history: (() => {
          const prev = state.history.filter(h => h.tsCode !== query.tsCode)
          return [newTag, ...prev].slice(0, MAX_TAGS)
        })(),
      })
    } catch (err) {
      message.error('查询失败: ' + (err as Error).message)
    }
  }, [message, onStateChange, state.backtestKey, state.history])

  const handleHistorySelect = useCallback((tag: HistoryTag) => {
    searchPanelRef.current?.selectAndQuery(tag.tsCode, tag.name)
  }, [])

  const handleHistoryRemove = useCallback((index: number) => {
    onStateChange({ history: state.history.filter((_, i) => i !== index) })
  }, [onStateChange, state.history])

  const handleSignals = useCallback((newSignals: BacktestSignal[]) => {
    onStateChange({ signals: newSignals })
  }, [onStateChange])

  const handleClearSignals = useCallback(() => {
    onStateChange({ signals: [] })
  }, [onStateChange])

  return (
    <>
      <SearchPanel ref={searchPanelRef} onSearch={handleSearch} />
      <HistoryTags queries={state.history} onSelect={handleHistorySelect} onRemove={handleHistoryRemove} />
      <BacktestPanel
        resetKey={state.backtestKey}
        quotes={state.dailyData}
        onSignals={handleSignals}
        onClearSignals={handleClearSignals}
      />
      <DataPanel data={state.dailyData} stockName={state.currentStock} signals={state.signals} />
    </>
  )
}

export default HomePage
