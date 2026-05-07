import { useState, useEffect, useCallback } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import { Select, Button, Modal, App } from 'antd'
import { RightOutlined, PlayCircleOutlined, ClearOutlined, UnorderedListOutlined, FileExcelOutlined } from '@ant-design/icons'
import * as XLSX from 'xlsx'
import { useValidStrategies } from '../../hooks/useStrategies'
import { runBacktest } from '../../api/backtest'
import type { DailyQuote, BacktestSignal, BacktestResult } from '../../types'
import styles from './BacktestPanel.module.css'

interface BacktestPanelProps {
  quotes: DailyQuote[]
  resetKey: number
  onSignals: (signals: BacktestSignal[]) => void
  onClearSignals: () => void
}

interface TradeRecord {
  entry: BacktestSignal
  exit?: BacktestSignal
}

const formatMoney = (v: number) => {
  if (Math.abs(v) >= 10000) return (v / 10000).toFixed(2) + '涓?
  return v.toFixed(2)
}

const BacktestPanel = ({ quotes, resetKey, onSignals, onClearSignals }: BacktestPanelProps) => {
  const { message } = App.useApp()
  const { strategies } = useValidStrategies()
  const [expanded, setExpanded] = useState(false)
  const [selectedStrategyId, setSelectedStrategyId] = useState<number | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<BacktestResult | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const handleDragStart = useCallback((e: ReactMouseEvent) => {
    if ((e.target as HTMLElement).closest('button')) return
    e.preventDefault()
    const modal = (e.currentTarget as HTMLElement).closest('.ant-modal') as HTMLElement
    if (!modal) return
    const rect = modal.getBoundingClientRect()
    const offsetX = e.clientX - rect.left
    const offsetY = e.clientY - rect.top
    const onMove = (ev: MouseEvent) => {
      modal.style.margin = '0'
      modal.style.left = `${ev.clientX - offsetX}px`
      modal.style.top = `${ev.clientY - offsetY}px`
    }
    const onUp = () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
  }, [])

  useEffect(() => {
    setResult(null)
    setSelectedStrategyId(undefined)
    onClearSignals()
  }, [resetKey, onClearSignals])

  const handleRun = useCallback(async () => {
    if (!selectedStrategyId) {
      message.warning('璇峰厛閫夋嫨涓€涓瓥鐣?)
      return
    }
    if (quotes.length === 0) {
      message.warning('璇峰厛鏌ヨ鑲＄エ鏁版嵁')
      return
    }
    setLoading(true)
    try {
      const res = await runBacktest(selectedStrategyId, quotes)
      setResult(res)
      onSignals(res.signals)
      message.success('鍥炴祴瀹屾垚')
    } catch (err) {
      message.error('鍥炴祴澶辫触: ' + (err as Error).message)
    } finally {
      setLoading(false)
    }
  }, [selectedStrategyId, quotes, onSignals, message])

  const handleClear = useCallback(() => {
    setResult(null)
    onClearSignals()
  }, [onClearSignals])

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

  const trades = result ? computeTrades(result.signals) : []

  const strategyName = strategies.find(s => s.id === selectedStrategyId)?.name ?? ''

  const subtotalPl = trades.reduce((sum, t) => sum + (t.exit?.profit ?? 0), 0)
  const subtotalProfitPct = result ? result.totalReturn : 0

  const handleExportExcel = useCallback(() => {
    if (trades.length === 0) {
      message.warning('娌℃湁浜ゆ槗璁板綍鍙鍑?)
      return
    }
    const rows: Record<string, string | number>[] = trades.map((t, i) => ({
      搴忓彿: i + 1,
      涔板叆鏃ユ湡: formatTradeDate(t.entry.trade_date),
      涔板叆浠? t.entry.price.toFixed(2),
      涔板叆鑲℃暟: t.entry.shares,
      涔板叆鎬婚噾棰? t.entry.buyAmount.toFixed(2),
      涔板叆鎵嬬画璐? t.entry.fees.toFixed(2),
      鍗栧嚭鏃ユ湡: t.exit ? formatTradeDate(t.exit.trade_date) : '-',
      鍗栧嚭浠? t.exit ? t.exit.price.toFixed(2) : '-',
      鍗栧嚭鎵嬬画璐? t.exit ? t.exit.sellFees.toFixed(2) : '-',
      鐩堜簭閲戦: t.exit ? (t.exit.profit >= 0 ? '+' : '') + (t.exit.profit / 10000).toFixed(2) + '涓? : '-',
      鏀剁泭鐜? t.exit ? (t.exit.profitPct >= 0 ? '+' : '') + t.exit.profitPct.toFixed(2) + '%' : '-',
      鍓╀綑閲戦: formatMoney(t.exit ? t.exit.remainingCash : t.entry.remainingCash),
    }))
    rows.push({
      搴忓彿: '',
      涔板叆鏃ユ湡: '灏忚',
      涔板叆浠? '',
      涔板叆鑲℃暟: '',
      涔板叆鎬婚噾棰? '',
      涔板叆鎵嬬画璐? '',
      鍗栧嚭鏃ユ湡: '',
      鍗栧嚭浠? '',
      鍗栧嚭鎵嬬画璐? '',
      鐩堜簭閲戦: (subtotalPl >= 0 ? '+' : '') + (subtotalPl / 10000).toFixed(2) + '涓?,
      鏀剁泭鐜? (subtotalProfitPct >= 0 ? '+' : '') + subtotalProfitPct.toFixed(2) + '%',
      鍓╀綑閲戦: '',
    })
    const ws = XLSX.utils.json_to_sheet(rows)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '浜ゆ槗鏄庣粏')
    XLSX.writeFile(wb, `浜ゆ槗鏄庣粏_${new Date().toISOString().slice(0, 10)}.xlsx`)
    message.success('瀵煎嚭鎴愬姛')
  }, [trades, subtotalPl, message])

  return (
    <div className={styles.panel}>
      <div className={styles.header} onClick={() => setExpanded(!expanded)}>
        <div className={styles.headerTitle}>
          <RightOutlined className={expanded ? styles.headerIconOpen : styles.headerIcon} />
          <span>绛栫暐鍥炴祴</span>
        </div>
        {result && (
          <span className={styles.headerSummary}>
            [{strategyName}]  鏀剁泭鐜? <span className={result.totalReturn >= 0 ? styles.valUp : styles.valDown}>
              {result.totalReturn >= 0 ? '+' : ''}{result.totalReturn.toFixed(2)}%
            </span>
            {', '}鏀剁泭: <span className={result.profitLoss >= 0 ? styles.valUp : styles.valDown}>
              {result.profitLoss >= 0 ? '+' : ''}{formatMoney(result.profitLoss)}鍏?            </span>
            {', '}浜ゆ槗: {result.tradeCount}娆?            {result.openPositionCount > 0 && <span style={{ color: '#faad14' }}> (+{result.openPositionCount}鎸佷粨涓?</span>}
          </span>
        )}
      </div>

      {expanded && (
        <div className={styles.content}>
          <div className={styles.fieldGroup}>
            <span className={styles.label}>绛栫暐</span>
            <Select
              className={styles.strategySelect}
              placeholder="閫夋嫨绛栫暐"
              value={selectedStrategyId}
              onChange={setSelectedStrategyId}
              options={strategies.map((s) => ({ value: s.id, label: s.name }))}
            />
          </div>

          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={handleRun}
            loading={loading}
            disabled={!selectedStrategyId || quotes.length === 0}
          >
            杩愯鍥炴祴
          </Button>

          {result && (
            <div className={styles.resultActions}>
              <Button
                icon={<UnorderedListOutlined />}
                onClick={() => setDetailOpen(true)}
                size="small"
              >
                鏄庣粏
              </Button>
              <Button
                icon={<ClearOutlined />}
                onClick={handleClear}
                size="small"
              >
                娓呴櫎
              </Button>
            </div>
          )}
        </div>
      )}

      <Modal
        title={
          <div className={styles.modalTitle} onMouseDown={handleDragStart} style={{ cursor: 'move' }}>
            <span>浜ゆ槗鏄庣粏 {strategyName}</span>
            <Button
              icon={<FileExcelOutlined />}
              size="small"
              onClick={handleExportExcel}
            >
              瀵煎嚭Excel
            </Button>
          </div>
        }
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={1020}
        styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', maxHeight: '60vh' } }}
        modalRender={(modal) => modal}
      >
        <div className={styles.detailHeader}>
          <span>搴忓彿</span>
          <span>涔板叆鏃ユ湡</span>
          <span>涔板叆浠?/span>
          <span>涔板叆鑲℃暟</span>
          <span>涔板叆鎬婚噾棰?/span>
          <span>涔板叆鎵嬬画璐?/span>
          <span>鍗栧嚭鏃ユ湡</span>
          <span>鍗栧嚭浠?/span>
          <span>鍗栧嚭鎵嬬画璐?/span>
          <span>鐩堜簭閲戦</span>
          <span>鏀剁泭鐜?/span>
          <span>鍓╀綑閲戦</span>
        </div>
        <div className={styles.detailBody}>
          {trades.map((t, i) => (
            <div key={i} className={styles.detailRow}>
              <span>{i + 1}</span>
              <span>{formatTradeDate(t.entry.trade_date)}</span>
              <span>{t.entry.price.toFixed(2)}</span>
              <span>{t.entry.shares}</span>
              <span>{t.entry.buyAmount.toFixed(2)}</span>
              <span>{t.entry.fees.toFixed(2)}</span>
              <span>{t.exit ? formatTradeDate(t.exit.trade_date) : '-'}</span>
              <span>{t.exit ? t.exit.price.toFixed(2) : '-'}</span>
              <span>{t.exit ? t.exit.sellFees.toFixed(2) : '-'}</span>
              <span>
                {t.exit ? (
                  <span className={t.exit.profit >= 0 ? styles.valUp : styles.valDown}>
                    {t.exit.profit >= 0 ? '+' : ''}{(t.exit.profit / 10000).toFixed(2)}涓?                  </span>
                ) : (
                  <span className={styles.holdTag}>鎸佷粨涓?/span>
                )}
              </span>
              <span>
                {t.exit ? (
                  <span className={t.exit.profitPct >= 0 ? styles.valUp : styles.valDown}>
                    {t.exit.profitPct >= 0 ? '+' : ''}{t.exit.profitPct.toFixed(2)}%
                  </span>
                ) : (
                  <span className={styles.holdTag}>鎸佷粨涓?/span>
                )}
              </span>
              <span>{formatMoney(t.exit ? t.exit.remainingCash : t.entry.remainingCash)}</span>
            </div>
          ))}
          {trades.length === 0 && (
            <div className={styles.detailEmpty}>鏃犱氦鏄撹褰?/div>
          )}
        </div>
        {trades.length > 0 && (
          <div className={styles.detailSubtotal}>
            <span></span>
            <span>灏忚</span>
            <span></span>
            <span></span>
            <span></span>
            <span></span>
            <span></span>
            <span></span>
            <span></span>
            <span className={subtotalPl >= 0 ? styles.valUp : styles.valDown}>
              {subtotalPl >= 0 ? '+' : ''}{(subtotalPl / 10000).toFixed(2)}涓?            </span>
            <span className={subtotalProfitPct >= 0 ? styles.valUp : styles.valDown}>
              {subtotalProfitPct >= 0 ? '+' : ''}{subtotalProfitPct.toFixed(2)}%
            </span>
            <span>{formatMoney(result?.finalCapital ?? 0)}</span>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default BacktestPanel
