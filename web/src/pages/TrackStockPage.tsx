import { useState, useCallback, useEffect, useRef, useMemo } from 'react'
import { Table, Button, Modal, App, Tabs } from 'antd'
import { EyeOutlined, DeleteOutlined, ThunderboltOutlined, FlagOutlined, FundOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { listTrackStocks, removeTrackStock, type TrackStock } from '../api/track'
import { queryDaily } from '../api/stock'
import { runBacktest } from '../api/backtest'
import DataPanel from '../components/DataPanel/DataPanel'
import type { DailyQuote, BacktestResult } from '../types'
import styles from './TrackStockPage.module.css'

const safeNum = (v: unknown): number => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

export default function TrackStockPage() {
  const { message: msgApi } = App.useApp()
  const navigate = useNavigate()
  const [tracks, setTracks] = useState<TrackStock[]>([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<string>('all')

  const [chartModalOpen, setChartModalOpen] = useState(false)
  const [chartStock, setChartStock] = useState<TrackStock | null>(null)
  const [chartQuotes, setChartQuotes] = useState<DailyQuote[]>([])
  const [chartSignals, setChartSignals] = useState<BacktestResult['signals']>([])
  const [chartLoading, setChartLoading] = useState(false)
  const [backtestLoading, setBacktestLoading] = useState(false)

  const loadingRef = useRef(false)

  const loadData = useCallback(async () => {
    if (loadingRef.current) return
    loadingRef.current = true
    setLoading(true)
    try {
      const data = await listTrackStocks()
      setTracks(data)
    } catch {
      msgApi.error('加载跟踪列表失败')
    } finally {
      loadingRef.current = false
      setLoading(false)
    }
  }, [msgApi])

  useEffect(() => {
    loadData()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const strategyGroups = useMemo(() => {
    const map = new Map<number, { name: string; items: TrackStock[] }>()
    for (const t of tracks) {
      const key = t.strategy_id
      if (!map.has(key)) {
        map.set(key, { name: t.strategy_name, items: [] })
      }
      map.get(key)!.items.push(t)
    }
    return map
  }, [tracks])

  const handleViewChart = useCallback(async (track: TrackStock) => {
    setChartStock(track)
    setChartQuotes([])
    setChartSignals([])
    setChartModalOpen(true)
    setChartLoading(true)
    try {
      const endDate = dayjs().format('YYYYMMDD')
      const startDate = dayjs().subtract(1, 'year').format('YYYYMMDD')
      const quotes = await queryDaily({ tsCode: track.ts_code, startDate, endDate })
      setChartQuotes(quotes)
    } catch {
      msgApi.error('加载K线数据失败')
    } finally {
      setChartLoading(false)
    }
  }, [msgApi])

  const handleRunBacktest = useCallback(async () => {
    if (!chartStock) return
    setBacktestLoading(true)
    try {
      const endDate = dayjs().format('YYYYMMDD')
      const startDate = dayjs().subtract(2, 'year').format('YYYYMMDD')
      const quotes = await queryDaily({ tsCode: chartStock.ts_code, startDate, endDate })
      if (quotes.length === 0) {
        msgApi.warning('无行情数据')
        return
      }
      const result = await runBacktest(chartStock.strategy_id, quotes)
      setChartQuotes(quotes)
      setChartSignals(result.signals)
      msgApi.success(`回测完成: 收益 ${(result.total_return * 100).toFixed(2)}%, 胜率 ${(result.win_rate * 100).toFixed(1)}%`)
    } catch (e: any) {
      msgApi.error(e.message || '回测失败')
    } finally {
      setBacktestLoading(false)
    }
  }, [chartStock, msgApi])

  const handleRemove = useCallback(async (id: number) => {
    try {
      await removeTrackStock(id)
      msgApi.success('已移除')
      loadData()
    } catch {
      msgApi.error('移除失败')
    }
  }, [loadData, msgApi])

  const handleGoBacktest = useCallback((track: TrackStock) => {
    navigate(`/backtest?tsCode=${encodeURIComponent(track.ts_code)}&strategyId=${track.strategy_id}`)
  }, [navigate])

  const renderChangeVal = (val: number | null, suffix = '') => {
    if (val == null) return '-'
    const cls = val >= 0 ? styles.changePositive : styles.changeNegative
    return <span className={cls}>{val >= 0 ? '+' : ''}{safeNum(val).toFixed(2)}{suffix}</span>
  }

  const buildColumns = (showStrategy: boolean) => [
    {
      title: '#',
      key: 'index',
      width: 36,
      render: (_: unknown, __: TrackStock, index: number) => index + 1,
    },
    ...(showStrategy ? [{
      title: '策略',
      dataIndex: 'strategy_name',
      key: 'strategy_name',
      width: 140,
      ellipsis: true as const,
    }] : []),
    {
      title: '股票名称',
      dataIndex: 'stock_name',
      key: 'stock_name',
      width: 90,
      ellipsis: true,
    },
    {
      title: '代码',
      dataIndex: 'ts_code',
      key: 'ts_code',
      width: 90,
      ellipsis: true,
    },
    {
      title: '加入日',
      dataIndex: 'add_date',
      key: 'add_date',
      width: 90,
      render: (v: string) => v ? dayjs(v, 'YYYYMMDD').format('MM-DD') : '-',
    },
    {
      title: '天数',
      key: 'days',
      width: 50,
      render: (_: unknown, record: TrackStock) => {
        if (!record.add_date) return '-'
        return dayjs().diff(dayjs(record.add_date, 'YYYYMMDD'), 'day')
      },
    },
    {
      title: '加入价',
      key: 'add_price',
      width: 70,
      render: (_: unknown, record: TrackStock) => {
        if (record.add_price == null) return '-'
        return safeNum(record.add_price).toFixed(2)
      },
    },
    {
      title: '累计涨跌额',
      key: 'change_amount',
      width: 80,
      render: (_: unknown, record: TrackStock) => renderChangeVal(record.change_amount),
    },
    {
      title: '累计涨跌幅',
      key: 'change_rate',
      width: 80,
      render: (_: unknown, record: TrackStock) => renderChangeVal(record.change_rate, '%'),
    },
    {
      title: '今日涨跌额',
      key: 'today_change',
      width: 80,
      render: (_: unknown, record: TrackStock) => renderChangeVal(record.today_change),
    },
    {
      title: '今日涨跌幅',
      key: 'today_pct',
      width: 80,
      render: (_: unknown, record: TrackStock) => renderChangeVal(record.today_pct, '%'),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: unknown, record: TrackStock) => (
        <div style={{ display: 'flex', gap: 2 }}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewChart(record)} />
          <Button type="link" size="small" icon={<FundOutlined />} onClick={() => handleGoBacktest(record)} title="跳转回测" />
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleRemove(record.id)} />
        </div>
      ),
    },
  ]

  const buildFooter = (items: TrackStock[]) => {
    const validItems = items.filter(t => t.change_rate != null)
    const count = validItems.length
    if (count === 0) return null
    const avgRate = validItems.reduce((s, t) => s + safeNum(t.change_rate), 0) / count
    const totalAmount = validItems.reduce((s, t) => s + safeNum(t.change_amount), 0)
    const profitCount = validItems.filter(t => safeNum(t.change_rate) >= 0).length
    
    const todayValidItems = items.filter(t => t.today_change != null)
    const todayTotalChange = todayValidItems.reduce((s, t) => s + safeNum(t.today_change), 0)
    const todayAvgPct = todayValidItems.length > 0 
      ? todayValidItems.reduce((s, t) => s + safeNum(t.today_pct), 0) / todayValidItems.length 
      : 0
    
    return () => (
      <div className={styles.footerRow}>
        <span>共{items.length}只 | 盈{profitCount} 亏{count - profitCount}</span>
        <span className={styles.footerDivider}>|</span>
        <span>累计涨跌额 {renderChangeVal(totalAmount)}</span>
        <span className={styles.footerDivider}>|</span>
        <span>累计均幅 {renderChangeVal(avgRate, '%')}</span>
        <span className={styles.footerDivider}>|</span>
        <span>今日涨跌额 {renderChangeVal(todayTotalChange)}</span>
        <span className={styles.footerDivider}>|</span>
        <span>今日均幅 {renderChangeVal(todayAvgPct, '%')}</span>
      </div>
    )
  }

  const tabItems = useMemo(() => {
    const items: { key: string; label: string; children: React.ReactNode }[] = [
      {
        key: 'all',
        label: `全部 (${tracks.length})`,
        children: (
          <Table
            dataSource={tracks}
            columns={buildColumns(true)}
            rowKey="id"
            loading={loading}
            pagination={false}
            size="small"
            sticky
            footer={buildFooter(tracks)}
          />
        ),
      },
    ]
    for (const [strategyId, group] of strategyGroups) {
      const validItems = group.items.filter(t => t.change_rate != null)
      const profitCount = validItems.filter(t => safeNum(t.change_rate) >= 0).length
      items.push({
        key: String(strategyId),
        label: `${group.name} (${group.items.length})`,
        children: (
          <Table
            dataSource={group.items}
            columns={buildColumns(false)}
            rowKey="id"
            pagination={false}
            size="small"
            sticky
            footer={buildFooter(group.items)}
          />
        ),
      })
    }
    return items
  }, [tracks, strategyGroups, loading]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={styles.container}>
      {tracks.length === 0 && !loading ? (
        <div className={styles.emptyState}>
          <FlagOutlined className={styles.emptyIcon} />
          <div>暂无跟踪股票，可从策略选股结果中添加</div>
        </div>
      ) : (
        <div className={styles.tableWrap}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={tabItems}
            size="small"
            className={styles.tabs}
          />
        </div>
      )}

      <Modal
        title={chartStock ? `${chartStock.stock_name} (${chartStock.ts_code}) - ${chartStock.strategy_name}` : 'K线图'}
        open={chartModalOpen}
        onCancel={() => setChartModalOpen(false)}
        footer={null}
        width={900}
        destroyOnClose
      >
        <div className={styles.modalBody}>
          <div className={styles.chartHeader}>
            <span className={styles.chartTitle}>
              {chartLoading ? '加载中...' : chartQuotes.length > 0 ? `近1年K线 (${chartQuotes.length}条)` : ''}
            </span>
            <Button
              type="primary"
              size="small"
              icon={<ThunderboltOutlined />}
              loading={backtestLoading}
              onClick={handleRunBacktest}
              className={styles.backtestBtn}
            >
              回测
            </Button>
          </div>
          <div className={styles.chartArea}>
            {chartQuotes.length > 0 && (
              <DataPanel
                data={chartQuotes}
                stockName={chartStock?.stock_name || ''}
                signals={chartSignals}
              />
            )}
          </div>
        </div>
      </Modal>
    </div>
  )
}
