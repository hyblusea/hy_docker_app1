import { useState, useEffect, useRef, useCallback } from 'react'
import { App, Checkbox, Button, Table, Tag, Progress, Space, DatePicker, Collapse, Statistic, Row, Col, Modal } from 'antd'
import { BarChartOutlined, PlayCircleOutlined, StopOutlined, HistoryOutlined, LineChartOutlined, DeleteOutlined } from '@ant-design/icons'
import dayjs, { Dayjs } from 'dayjs'
import { useValidStrategies } from '../hooks/useStrategies'
import { useAuth } from '../contexts/AuthContext'
import { startAnalysis, getAnalysisProgress, cancelAnalysis, getAnalysisResults, getAnalysisHistory, deleteAnalysisTask, getStockBacktestResult } from '../api/analysis'
import type { StrategyAnalysisProgress, StrategyAnalysisResult, AnalysisTaskHistory } from '../api/analysis'
import { queryDaily } from '../api/stock'
import DataPanel from '../components/DataPanel/DataPanel'
import type { DailyQuote, BacktestSignal } from '../types'
import type { StockPerformance } from '../types/analysis'
import styles from './StrategyAnalysisPage.module.css'

const safeNum = (v: unknown): number => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

export interface StrategyAnalysisPageState {
  selectedStrategyIds: number[]
  startDate: string
  endDate: string
  taskId: string | null
  progress: StrategyAnalysisProgress | null
  results: StrategyAnalysisResult[] | null
  loading: boolean
}

export const defaultStrategyAnalysisPageState: StrategyAnalysisPageState = {
  selectedStrategyIds: [],
  startDate: dayjs().subtract(1, 'year').format('YYYYMMDD'),
  endDate: dayjs().format('YYYYMMDD'),
  taskId: null,
  progress: null,
  results: null,
  loading: false,
}

interface StrategyAnalysisPageProps {
  state: StrategyAnalysisPageState
  onStateChange: (patch: Partial<StrategyAnalysisPageState>) => void
}

let globalPollTimer: ReturnType<typeof setTimeout> | null = null
let globalPollGeneration = 0

const POLL_INTERVAL = 2000

const StrategyAnalysisPage = ({ state, onStateChange }: StrategyAnalysisPageProps) => {
  const { message: msgApi } = App.useApp()
  const { strategies } = useValidStrategies()
  const { user } = useAuth()

  const { selectedStrategyIds, startDate, endDate, taskId, progress, results, loading } = state

  const mountedRef = useRef(true)
  const pollFnRef = useRef<(id: string, gen: number) => Promise<void>>(() => Promise.resolve())

  const [historyModalOpen, setHistoryModalOpen] = useState(false)
  const [historyTasks, setHistoryTasks] = useState<AnalysisTaskHistory[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [deletingTaskId, setDeletingTaskId] = useState<string | null>(null)
  const [resultTaskId, setResultTaskId] = useState<string | null>(null)

  const [chartModalOpen, setChartModalOpen] = useState(false)
  const [chartStockName, setChartStockName] = useState('')
  const [chartData, setChartData] = useState<DailyQuote[]>([])
  const [chartSignals, setChartSignals] = useState<BacktestSignal[]>([])
  const [chartLoading, setChartLoading] = useState(false)

  useEffect(() => {
    mountedRef.current = true

    if (loading && taskId) {
      globalPollGeneration++
      if (globalPollTimer) {
        clearTimeout(globalPollTimer)
        globalPollTimer = null
      }
      pollFnRef.current(taskId, globalPollGeneration)
    }

    return () => {
      mountedRef.current = false
    }
  }, [])

  const setLoading = useCallback((value: boolean) => {
    onStateChange({ loading: value })
  }, [onStateChange])

  const setTaskId = useCallback((id: string | null) => {
    onStateChange({ taskId: id })
  }, [onStateChange])

  const setProgress = useCallback((p: StrategyAnalysisProgress | null) => {
    onStateChange({ progress: p })
  }, [onStateChange])

  const setResults = useCallback((r: StrategyAnalysisResult[] | null) => {
    onStateChange({ results: r })
  }, [onStateChange])

  const setSelectedStrategyIds = useCallback((ids: number[]) => {
    onStateChange({ selectedStrategyIds: ids })
  }, [onStateChange])

  const setStartDate = useCallback((date: string) => {
    onStateChange({ startDate: date })
  }, [onStateChange])

  const setEndDate = useCallback((date: string) => {
    onStateChange({ endDate: date })
  }, [onStateChange])

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

  const pollProgress = useCallback(async (id: string, generation: number) => {
    if (generation !== globalPollGeneration) return

    try {
      const p = await getAnalysisProgress(id)
      if (generation !== globalPollGeneration) return

      setProgress(p)

      if (p.cancelled) {
        globalPollTimer = null
        setLoading(false)
        setProgress(null)
        setTaskId(null)
        if (mountedRef.current) {
          msgApi.info('已取消分析')
        }
        return
      }

      if (p.init_error) {
        globalPollTimer = null
        setLoading(false)
        setTaskId(null)
        if (mountedRef.current) {
          msgApi.error('分析初始化失败: ' + p.init_error)
        }
        return
      }

      if (p.completed) {
        globalPollTimer = null
        try {
          const r = await getAnalysisResults(id)
          if (generation !== globalPollGeneration) return
          setResults(r)
          setResultTaskId(id)
          if (mountedRef.current) {
            msgApi.success('分析完成')
          }
        } catch {
          if (mountedRef.current) {
            msgApi.error('获取分析结果失败')
          }
        } finally {
          setLoading(false)
          setTaskId(null)
        }
        return
      }

      globalPollTimer = setTimeout(() => {
        pollFnRef.current(id, generation)
      }, POLL_INTERVAL)
    } catch {
      if (generation !== globalPollGeneration) return
      globalPollTimer = null
      setLoading(false)
      setTaskId(null)
      if (mountedRef.current) {
        msgApi.error('分析任务已失效')
      }
    }
  }, [msgApi, setProgress, setResults, setLoading, setTaskId])

  pollFnRef.current = pollProgress

  const startPolling = useCallback((id: string) => {
    setTaskId(id)
    globalPollGeneration++
    if (globalPollTimer) {
      clearTimeout(globalPollTimer)
      globalPollTimer = null
    }
    pollFnRef.current(id, globalPollGeneration)
  }, [setTaskId])

  const handleStart = useCallback(async () => {
    if (!user) return
    setLoading(true)
    setResults(null)
    setProgress(null)
    try {
      const id = await startAnalysis({
        username: user.username,
        start_date: dayjs(startDate, 'YYYYMMDD').format('YYYYMMDD'),
        end_date: dayjs(endDate, 'YYYYMMDD').format('YYYYMMDD'),
        strategy_ids: selectedStrategyIds.length > 0 ? selectedStrategyIds : undefined,
      })
      startPolling(id)
      msgApi.info('分析任务已启动')
    } catch (e: any) {
      msgApi.error(e.message || '启动分析失败')
      setLoading(false)
    }
  }, [user, startDate, endDate, selectedStrategyIds, msgApi, setLoading, setResults, setProgress, startPolling])

  const handleCancel = useCallback(async () => {
    globalPollGeneration++
    if (globalPollTimer) {
      clearTimeout(globalPollTimer)
      globalPollTimer = null
    }
    if (taskId) {
      try {
        await cancelAnalysis(taskId)
      } catch {}
    }
    setTaskId(null)
    setLoading(false)
    setProgress(null)
    msgApi.info('已取消分析')
  }, [taskId, msgApi, setTaskId, setLoading, setProgress])

  const handleOpenHistory = useCallback(async () => {
    if (!user?.username) {
      msgApi.warning('请先登录')
      return
    }
    setHistoryLoading(true)
    setHistoryModalOpen(true)
    try {
      const tasks = await getAnalysisHistory(user.username)
      setHistoryTasks(tasks)
    } catch {
      msgApi.error('获取历史分析任务失败')
    } finally {
      setHistoryLoading(false)
    }
  }, [user?.username, msgApi])

  const handleLoadHistoryTask = useCallback(async (task: AnalysisTaskHistory) => {
    try {
      const r = await getAnalysisResults(task.task_id)
      if (r && r.length > 0) {
        setResults(r)
        setResultTaskId(task.task_id)
        if (task.start_date) setStartDate(task.start_date)
        if (task.end_date) setEndDate(task.end_date)
        setHistoryModalOpen(false)
        msgApi.success('已加载历史分析结果')
      } else {
        msgApi.warning('该任务无分析结果')
      }
    } catch (e: any) {
      msgApi.error(e.message || '加载历史分析结果失败')
    }
  }, [msgApi, setResults, setStartDate, setEndDate])

  const handleDeleteHistoryTask = useCallback(async (taskId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    setDeletingTaskId(taskId)
    try {
      await deleteAnalysisTask(taskId)
      setHistoryTasks(prev => prev.filter(t => t.task_id !== taskId))
      msgApi.success('已删除分析任务')
    } catch (e: any) {
      msgApi.error(e.message || '删除失败')
    } finally {
      setDeletingTaskId(null)
    }
  }, [msgApi])

  const handleViewChart = useCallback(async (tsCode: string, stockName: string, strategyId: number) => {
    setChartStockName(stockName)
    setChartLoading(true)
    setChartModalOpen(true)
    setChartData([])
    setChartSignals([])

    try {
      const [data, backtestResult] = await Promise.all([
        queryDaily({ tsCode, startDate, endDate }),
        resultTaskId ? getStockBacktestResult(resultTaskId, strategyId, tsCode) : Promise.resolve(null),
      ])
      setChartData(data)

      const signals: BacktestSignal[] = []
      if (backtestResult?.signals) {
        backtestResult.signals.forEach((s, idx) => {
          signals.push({
            trade_date: s.trade_date,
            type: s.type as 'BUY' | 'SELL',
            price: s.price,
            index: idx,
            shares: 0,
            buy_amount: 0,
            fees: 0,
            sell_fees: 0,
            profit: 0,
            profit_pct: 0,
            remaining_cash: 0,
          })
        })
      }
      setChartSignals(signals)
    } catch {
      msgApi.error('获取行情数据失败')
    } finally {
      setChartLoading(false)
    }
  }, [startDate, endDate, resultTaskId, msgApi])

  const progressPercent = progress && progress.total > 0
    ? Math.round((progress.current / progress.total) * 100)
    : 0

  const getStockColumns = useCallback((strategyId: number) => [
    { title: '股票', dataIndex: 'stock_name', key: 'stock_name', width: 140 },
    { title: '代码', dataIndex: 'ts_code', key: 'ts_code', width: 110 },
    { title: '收益率', dataIndex: 'total_return', key: 'total_return', width: 90, render: (v: number) => <span style={{ color: safeNum(v) >= 0 ? '#52c41a' : '#ff4d4f' }}>{safeNum(v).toFixed(2)}%</span> },
    { title: '胜率', dataIndex: 'win_rate', key: 'win_rate', width: 70, render: (v: number) => `${(safeNum(v) * 100).toFixed(1)}%` },
    { title: '交易次数', dataIndex: 'trade_count', key: 'trade_count', width: 70 },
    { title: '最大回撤', dataIndex: 'max_drawdown', key: 'max_drawdown', width: 90, render: (v: number) => `${safeNum(v).toFixed(2)}%` },
    {
      title: '查看',
      key: 'action',
      width: 60,
      render: (_: any, record: StockPerformance) => (
        <Button
          type="link"
          size="small"
          icon={<LineChartOutlined />}
          onClick={() => handleViewChart(record.ts_code, record.stock_name, strategyId)}
        />
      ),
    },
  ], [handleViewChart])

  return (
    <div className={styles.page}>
      <div className={styles.content}>
        <div className={styles.configCard}>
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
                  disabled={loading}
                >
                  {strategy.name}
                </Checkbox>
              </div>
            ))}
          </div>
        </div>

        <div className={styles.configSection}>
          <div className={styles.sectionTitle}>时间范围与操作</div>
          <div className={styles.actionRow}>
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
                  icon={<StopOutlined />}
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
                    format={() => progress && progress.total > 0 ? `${progress.current}/${progress.total}` : '准备中...'}
                    style={{ width: 150 }}
                  />
                  {progress && (
                    <span className={styles.progressStock}>
                      {progress.current_strategy || '-'}
                    </span>
                  )}
                </div>
              </>
            ) : (
              <>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={handleStart}
                  disabled={selectedStrategyIds.length === 0}
                  style={{ marginLeft: 16 }}
                >
                  开始分析
                </Button>
                <Button
                  icon={<HistoryOutlined />}
                  onClick={handleOpenHistory}
                  style={{ marginLeft: 8 }}
                >
                  历史分析
                </Button>
              </>
            )}
          </div>
        </div>
        </div>

        {progress && !progress.completed && !progress.cancelled && (
          <div className={styles.progressCard}>
            <Progress percent={progressPercent} status="active" />
            <div className={styles.progressInfo}>
              <span>当前策略: {progress.current_strategy || '-'}</span>
              <span>当前股票: {progress.current_stock || '-'}</span>
              <span>进度: {progress.current} / {progress.total}</span>
              <span>已完成策略: {progress.strategy_completed}</span>
            </div>
          </div>
        )}

        {results && results.length > 0 && (
          <div className={styles.results}>
            <Collapse
              items={results.map((r) => ({
                key: r.strategy_id.toString(),
                label: (
                  <Space>
                    <BarChartOutlined />
                    <strong>{r.strategy_name}</strong>
                    <Tag color={safeNum(r.avg_return) >= 0 ? 'green' : 'red'}>
                      平均收益 {safeNum(r.avg_return).toFixed(2)}%
                    </Tag>
                    <Tag>胜率 {(safeNum(r.avg_win_rate) * 100).toFixed(1)}%</Tag>
                    <Tag>盈亏比 {safeNum(r.profit_factor).toFixed(2)}</Tag>
                  </Space>
                ),
                children: (
                  <div>
                    <div className={styles.statsRow}>
                      <div className={styles.statItem}><span className={styles.statLabel}>股票</span><span className={styles.statValue}>{r.total_stocks}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>盈利</span><span className={styles.statValue} style={{ color: '#ff4d4f' }}>{r.profitable_stocks}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>亏损</span><span className={styles.statValue} style={{ color: '#52c41a' }}>{r.losing_stocks}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>平均收益</span><span className={styles.statValue}>{safeNum(r.avg_return).toFixed(2)}%</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>胜率</span><span className={styles.statValue}>{(safeNum(r.avg_win_rate) * 100).toFixed(1)}%</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>总盈利</span><span className={styles.statValue} style={{ color: '#ff4d4f' }}>{r.total_profit != null ? (safeNum(r.total_profit) / 10000).toFixed(2) + '万' : '-'}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>总亏损</span><span className={styles.statValue} style={{ color: '#52c41a' }}>{r.total_loss != null ? (safeNum(r.total_loss) / 10000).toFixed(2) + '万' : '-'}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>盈亏比</span><span className={styles.statValue}>{safeNum(r.profit_factor).toFixed(2)}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>交易次数</span><span className={styles.statValue}>{r.total_trades}</span></div>
                      <div className={styles.statItem}><span className={styles.statLabel}>平均回撤</span><span className={styles.statValue}>{safeNum(r.avg_max_drawdown).toFixed(2)}%</span></div>
                    </div>
                    <Row gutter={16} style={{ marginTop: 4 }}>
                      <Col span={12}>
                        <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>收益 Top 50</div>
                        <Table columns={getStockColumns(r.strategy_id)} dataSource={r.top_stocks} rowKey="ts_code" size="small" pagination={{ pageSize: 10, size: 'small' }} />
                      </Col>
                      <Col span={12}>
                        <div style={{ fontSize: 12, color: '#666', marginBottom: 2 }}>收益 Worst 50</div>
                        <Table columns={getStockColumns(r.strategy_id)} dataSource={r.worst_stocks} rowKey="ts_code" size="small" pagination={{ pageSize: 10, size: 'small' }} />
                      </Col>
                    </Row>
                  </div>
                ),
              }))}
              defaultActiveKey={results.map(r => r.strategy_id.toString())}
            />
          </div>
        )}
      </div>

      <Modal
        title="历史分析任务"
        open={historyModalOpen}
        onCancel={() => setHistoryModalOpen(false)}
        footer={null}
        width={700}
        loading={historyLoading}
      >
        {historyTasks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 24, color: '#999' }}>暂无历史分析任务</div>
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
                    {task.strategy_count}个策略 / {task.total_stocks}只股票
                  </span>
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    loading={deletingTaskId === task.task_id}
                    onClick={(e) => handleDeleteHistoryTask(task.task_id, e)}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Modal
        title={chartStockName}
        open={chartModalOpen}
        onCancel={() => setChartModalOpen(false)}
        footer={null}
        width={1000}
        destroyOnClose
        loading={chartLoading}
      >
        <div className={styles.chartModalContent}>
          {chartData.length > 0 ? (
            <DataPanel data={chartData} stockName={chartStockName} signals={chartSignals} />
          ) : (
            <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
              {chartLoading ? '加载中...' : '暂无数据'}
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}

export default StrategyAnalysisPage
