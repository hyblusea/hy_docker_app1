import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react'
import { Tabs, DatePicker, Select, Checkbox, Button, Progress, Table, Tag, Modal, Collapse, Empty, Spin, App, Radio, Tooltip } from 'antd'
import { PlayCircleOutlined, StopOutlined, HistoryOutlined, DeleteOutlined, CheckCircleOutlined, StarFilled, StarOutlined, ThunderboltOutlined, InfoCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { getFactorDefinitions, startFactorEval, getFactorEvalProgress, getFactorEvalResults, cancelFactorEval, getFactorEvalHistory, deleteFactorEvalTask, generateStrategy } from '../api/factorMining'
import type { FactorDefinition, FactorEvalResult, FactorEvalProgress, FactorEvalTaskHistory } from '../types/factorMining'
import { useAuth } from '../contexts/AuthContext'
import styles from './FactorMiningPage.module.css'

export interface FactorMiningPageState {
  results: FactorEvalResult[]
  activeTab: string
  currentTaskId: string | null
  selectedFactorNames: string[]
  selectedResultKeys: string[]
  loading: boolean
  progress: FactorEvalProgress | null
}

export const defaultFactorMiningPageState: FactorMiningPageState = {
  results: [],
  activeTab: 'compute',
  currentTaskId: null,
  selectedFactorNames: [],
  selectedResultKeys: [],
  loading: false,
  progress: null,
}

interface FactorMiningPageProps {
  state: FactorMiningPageState
  onStateChange: (patch: Partial<FactorMiningPageState>) => void
}

const CATEGORY_MAP: Record<string, string> = {
  price: '价格因子',
  ma: '均线因子',
  oscillator: '振荡因子',
  volatility: '波动因子',
  volume: '量价因子',
  stats: '统计因子',
}

const CATEGORY_ORDER = ['price', 'ma', 'oscillator', 'volatility', 'volume', 'stats']

let globalPollTimer: ReturnType<typeof setTimeout> | null = null
let globalPollGeneration = 0

const POLL_INTERVAL = 2000

const parseFactorCount = (factorNames: string): number => {
  try {
    const arr = JSON.parse(factorNames)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return factorNames.split(',').filter(Boolean).length
  }
}

const FactorMiningPage = ({ state, onStateChange }: FactorMiningPageProps) => {
  const { results, activeTab, currentTaskId } = state
  const persistedSelectedFactorNames = state.selectedFactorNames
  const persistedSelectedResultKeys = state.selectedResultKeys
  const persistedLoading = state.loading
  const persistedProgress = state.progress
  const { user } = useAuth()
  const { message: msgApi } = App.useApp()

  const [startDate, setStartDate] = useState(dayjs().subtract(5, 'year').format('YYYYMMDD'))
  const [endDate, setEndDate] = useState(dayjs().format('YYYYMMDD'))
  const [forwardDays, setForwardDays] = useState(5)
  const [selectedFactorNames, setSelectedFactorNamesLocal] = useState<string[]>(persistedSelectedFactorNames)
  const [loading, setLoading] = useState(persistedLoading)
  const [progress, setProgressLocal] = useState<FactorEvalProgress | null>(persistedProgress)
  const [taskId, setTaskId] = useState<string | null>(currentTaskId)
  const [factorDefinitions, setFactorDefinitions] = useState<FactorDefinition[]>([])
  const [defsLoading, setDefsLoading] = useState(false)
  const [historyModalOpen, setHistoryModalOpen] = useState(false)
  const [historyTasks, setHistoryTasks] = useState<FactorEvalTaskHistory[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [selectedResultKeys, setSelectedResultKeysLocal] = useState<string[]>(persistedSelectedResultKeys)
  const [generateLoading, setGenerateLoading] = useState(false)
  const [strategyModalOpen, setStrategyModalOpen] = useState(false)
  const [generatedStrategy, setGeneratedStrategy] = useState<{ id: number; name: string; valid: boolean; description: string } | null>(null)
  const [combinationMethod, setCombinationMethod] = useState<'rule' | 'scoring'>('scoring')

  const setSelectedFactorNames = useCallback((val: string[] | ((prev: string[]) => string[])) => {
    setSelectedFactorNamesLocal(prev => {
      const next = typeof val === 'function' ? val(prev) : val
      return next
    })
  }, [])

  const setSelectedResultKeys = useCallback((val: string[] | ((prev: string[]) => string[])) => {
    setSelectedResultKeysLocal(prev => {
      const next = typeof val === 'function' ? val(prev) : val
      return next
    })
  }, [])

  const setProgress = useCallback((val: FactorEvalProgress | null | ((prev: FactorEvalProgress | null) => FactorEvalProgress | null)) => {
    setProgressLocal(prev => {
      const next = typeof val === 'function' ? val(prev) : val
      return next
    })
  }, [])

  const setLoadingPersisted = useCallback((val: boolean | ((prev: boolean) => boolean)) => {
    setLoading(prev => {
      const next = typeof val === 'function' ? val(prev) : val
      return next
    })
  }, [])

  useEffect(() => { onStateChange({ selectedFactorNames }) }, [selectedFactorNames, onStateChange])
  useEffect(() => { onStateChange({ selectedResultKeys }) }, [selectedResultKeys, onStateChange])
  useEffect(() => { onStateChange({ progress }) }, [progress, onStateChange])
  useEffect(() => { onStateChange({ loading }) }, [loading, onStateChange])

  const mountedRef = useRef(true)
  const pollFnRef = useRef<(tid: string, gen: number) => Promise<void>>(() => Promise.resolve())
  const restoredRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    if (restoredRef.current) return
    if (!currentTaskId) return
    restoredRef.current = true

    const timer = setTimeout(() => {
      if (mountedRef.current && currentTaskId) {
        setLoadingPersisted(true)
        setTaskId(currentTaskId)
        globalPollGeneration++
        pollFnRef.current(currentTaskId, globalPollGeneration)
      }
    }, 300)

    return () => clearTimeout(timer)
  }, [currentTaskId])

  useEffect(() => {
    const fetchDefs = async () => {
      setDefsLoading(true)
      try {
        const defs = await getFactorDefinitions()
        if (mountedRef.current) {
          setFactorDefinitions(defs)
        }
      } catch {
        if (mountedRef.current) {
          msgApi.error('获取因子定义失败')
        }
      } finally {
        if (mountedRef.current) {
          setDefsLoading(false)
        }
      }
    }
    fetchDefs()
  }, [])

  const groupedFactors: Record<string, FactorDefinition[]> = {}
  for (const f of factorDefinitions) {
    if (!groupedFactors[f.factor_category]) {
      groupedFactors[f.factor_category] = []
    }
    groupedFactors[f.factor_category].push(f)
  }

  const handleToggleCategory = useCallback((category: string, checked: boolean) => {
    const categoryNames = factorDefinitions
      .filter(f => f.factor_category === category)
      .map(f => f.factor_name)
    if (checked) {
      setSelectedFactorNames(prev => [...new Set([...prev, ...categoryNames])])
    } else {
      setSelectedFactorNames(prev => prev.filter(n => !categoryNames.includes(n)))
    }
  }, [factorDefinitions])

  const handleToggleFactor = useCallback((factorName: string, checked: boolean) => {
    if (checked) {
      setSelectedFactorNames(prev => [...prev, factorName])
    } else {
      setSelectedFactorNames(prev => prev.filter(n => n !== factorName))
    }
  }, [])

  const pollProgress = useCallback(async (tid: string, generation: number) => {
    if (generation !== globalPollGeneration) return

    try {
      const p = await getFactorEvalProgress(tid)
      if (generation !== globalPollGeneration) return

      if (mountedRef.current) {
        setProgress(p)
      }

      if (p.cancelled) {
        globalPollTimer = null
        if (mountedRef.current) {
          setLoadingPersisted(false)
          setProgress(null)
          setTaskId(null)
          onStateChange({ currentTaskId: null })
          msgApi.info('已取消因子计算')
        }
        return
      }

      if (p.init_error) {
        globalPollTimer = null
        if (mountedRef.current) {
          setLoadingPersisted(false)
          setProgress(null)
          setTaskId(null)
          onStateChange({ currentTaskId: null })
          msgApi.error('因子计算初始化失败: ' + p.init_error)
        }
        return
      }

      if (p.completed) {
        globalPollTimer = null
        try {
          const r = await getFactorEvalResults(tid)
          if (generation !== globalPollGeneration) return
          if (mountedRef.current) {
            onStateChange({ results: r, currentTaskId: tid })
            msgApi.success(`因子计算完成，共 ${r.length} 个因子`)
          }
        } catch {
          if (mountedRef.current) {
            msgApi.error('获取因子评估结果失败')
          }
        } finally {
          if (mountedRef.current) {
            setLoadingPersisted(false)
          }
          setTaskId(null)
        }
        return
      }

      globalPollTimer = setTimeout(() => {
        pollFnRef.current(tid, generation)
      }, POLL_INTERVAL)
    } catch {
      if (generation !== globalPollGeneration) return
      globalPollTimer = null
      if (mountedRef.current) {
        setLoadingPersisted(false)
        setProgress(null)
        setTaskId(null)
        onStateChange({ currentTaskId: null })
        msgApi.error('因子计算任务已失效')
      }
    }
  }, [onStateChange, msgApi])

  pollFnRef.current = pollProgress

  const handleStart = useCallback(async () => {
    if (selectedFactorNames.length === 0) {
      msgApi.warning('请先选择至少一个因子')
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

    setLoadingPersisted(true)
    setProgress(null)
    onStateChange({ results: [], currentTaskId: null })

    try {
      const newTaskId = await startFactorEval({
        username: user.username,
        start_date: startDate,
        end_date: endDate,
        factor_names: selectedFactorNames,
        forward_days: forwardDays,
      })

      setTaskId(newTaskId)
      globalPollGeneration++
      pollFnRef.current(newTaskId, globalPollGeneration)
    } catch (err) {
      if (mountedRef.current) {
        msgApi.error('启动因子计算失败: ' + (err as Error).message)
        setLoadingPersisted(false)
      }
    }
  }, [user?.username, startDate, endDate, selectedFactorNames, forwardDays, onStateChange, msgApi])

  const handleCancel = useCallback(async () => {
    globalPollGeneration++
    if (globalPollTimer) {
      clearTimeout(globalPollTimer)
      globalPollTimer = null
    }
    if (taskId) {
      try {
        await cancelFactorEval(taskId)
      } catch {}
    }
    setTaskId(null)
    setLoadingPersisted(false)
    setProgress(null)
    onStateChange({ currentTaskId: null })
    msgApi.info('已取消因子计算')
  }, [taskId, onStateChange, msgApi])

  const handleOpenHistory = useCallback(async () => {
    if (!user?.username) {
      msgApi.warning('请先登录')
      return
    }
    setHistoryLoading(true)
    setHistoryModalOpen(true)
    try {
      const tasks = await getFactorEvalHistory(user.username)
      if (mountedRef.current) {
        setHistoryTasks(tasks)
      }
    } catch {
      if (mountedRef.current) {
        msgApi.error('获取历史评估任务失败')
      }
    } finally {
      if (mountedRef.current) {
        setHistoryLoading(false)
      }
    }
  }, [user?.username, msgApi])

  const handleLoadHistoryTask = useCallback(async (task: FactorEvalTaskHistory) => {
    try {
      const r = await getFactorEvalResults(task.task_id)
      if (r && r.length > 0) {
        onStateChange({ results: r, activeTab: 'eval', currentTaskId: task.task_id })
        setHistoryModalOpen(false)
        msgApi.success(`已加载历史评估结果，共 ${r.length} 个因子`)
      } else {
        msgApi.warning('该任务无评估结果')
      }
    } catch (e: any) {
      msgApi.error(e.message || '加载历史评估结果失败')
    }
  }, [onStateChange, msgApi])

  const handleDeleteHistoryTask = useCallback(async (tid: string, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      await deleteFactorEvalTask(tid)
      setHistoryTasks(prev => prev.filter(t => t.task_id !== tid))
      msgApi.success('已删除评估任务')
    } catch (e: any) {
      msgApi.error(e.message || '删除失败')
    }
  }, [msgApi])

  const handleGenerateStrategy = useCallback(async () => {
    if (selectedResultKeys.length === 0) {
      msgApi.warning('请先在评估结果表格中选择因子')
      return
    }
    if (!currentTaskId) {
      msgApi.warning('缺少任务ID，请重新运行因子计算')
      return
    }

    setGenerateLoading(true)
    try {
      const result = await generateStrategy({
        factor_names: selectedResultKeys,
        task_id: currentTaskId,
        combination_method: combinationMethod,
      })
      if (mountedRef.current) {
        setGeneratedStrategy(result)
        setStrategyModalOpen(true)
        msgApi.success('策略生成成功')
      }
    } catch (e: any) {
      if (mountedRef.current) {
        msgApi.error(e.message || '策略生成失败')
      }
    } finally {
      if (mountedRef.current) setGenerateLoading(false)
    }
  }, [selectedResultKeys, currentTaskId, combinationMethod, msgApi])

  const isEvaluating = progress && progress.total > 0 && progress.current >= progress.total && progress.total_factors > 0 && progress.factor_completed < progress.total_factors
  const progressPercent = isEvaluating
    ? Math.round((progress!.factor_completed / progress!.total_factors) * 100)
    : (progress && progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : 0)

  const collapseItems = CATEGORY_ORDER
    .filter(cat => groupedFactors[cat])
    .map(category => {
      const factors = groupedFactors[category]
      const categoryLabel = CATEGORY_MAP[category] || category
      const allSelected = factors.every(f => selectedFactorNames.includes(f.factor_name))
      const someSelected = factors.some(f => selectedFactorNames.includes(f.factor_name)) && !allSelected

      return {
        key: category,
        label: (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
            <span>{categoryLabel} ({factors.length})</span>
            <Checkbox
              checked={allSelected}
              indeterminate={someSelected}
              onChange={e => handleToggleCategory(category, e.target.checked)}
              onClick={e => e.stopPropagation()}
            >
              全选
            </Checkbox>
          </div>
        ),
        children: (
          <div className={styles.factorGrid}>
            {factors.map(f => (
              <Checkbox
                key={f.factor_name}
                checked={selectedFactorNames.includes(f.factor_name)}
                onChange={e => handleToggleFactor(f.factor_name, e.target.checked)}
                disabled={loading}
              >
                <span className={f.important ? styles.importantFactor : undefined}>{f.factor_label}</span>
              </Checkbox>
            ))}
          </div>
        ),
      }
    })

  for (const category of Object.keys(groupedFactors)) {
    if (!CATEGORY_ORDER.includes(category)) {
      const factors = groupedFactors[category]
      const allSelected = factors.every(f => selectedFactorNames.includes(f.factor_name))
      const someSelected = factors.some(f => selectedFactorNames.includes(f.factor_name)) && !allSelected

      collapseItems.push({
        key: category,
        label: (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
            <span>{CATEGORY_MAP[category] || category} ({factors.length})</span>
            <Checkbox
              checked={allSelected}
              indeterminate={someSelected}
              onChange={e => handleToggleCategory(category, e.target.checked)}
              onClick={e => e.stopPropagation()}
            >
              全选
            </Checkbox>
          </div>
        ),
        children: (
          <div className={styles.factorGrid}>
            {factors.map(f => (
              <Checkbox
                key={f.factor_name}
                checked={selectedFactorNames.includes(f.factor_name)}
                onChange={e => handleToggleFactor(f.factor_name, e.target.checked)}
                disabled={loading}
              >
                <span className={f.important ? styles.importantFactor : undefined}>{f.factor_label}</span>
              </Checkbox>
            ))}
          </div>
        ),
      })
    }
  }

  const importantFactorNames = useMemo(() => {
    return new Set(factorDefinitions.filter(f => f.important).map(f => f.factor_name))
  }, [factorDefinitions])

  const getStarCount = (icir: number): number => {
    const abs = Math.abs(icir)
    if (abs >= 0.5) return 5
    if (abs >= 0.4) return 4
    if (abs >= 0.3) return 3
    if (abs >= 0.2) return 2
    if (abs >= 0.1) return 1
    return 0
  }

  const renderStars = (count: number) => {
    const stars: React.ReactNode[] = []
    for (let i = 0; i < 5; i++) {
      stars.push(
        i < count
          ? <StarFilled key={i} style={{ color: '#faad14', fontSize: 11 }} />
          : <StarOutlined key={i} style={{ color: '#d9d9d9', fontSize: 11 }} />
      )
    }
    return <span style={{ marginLeft: 4, whiteSpace: 'nowrap' }}>{stars}</span>
  }

  const isUsableFactor = (icir: number, icWinRate: number): boolean => {
    return Math.abs(icir) >= 0.2
  }

  // useEffect(() => {
  //   if (results.length > 0 && selectedResultKeys.length === 0) {
  //     const usable = results
  //       .filter(r => isUsableFactor(r.icir, r.ic_win_rate))
  //       .map(r => r.factor_name)
  //     if (usable.length > 0) {
  //       setSelectedResultKeys(usable)
  //     }
  //   }
  // }, [results])

  const columns = [
    {
      title: '因子名称',
      dataIndex: 'factor_label',
      key: 'factor_label',
      width: 140,
      render: (v: string, record: FactorEvalResult) => {
        const usable = isUsableFactor(record.icir, record.ic_win_rate)
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            {usable && <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 12 }} />}
            <span className={importantFactorNames.has(record.factor_name) ? styles.importantFactor : undefined}>{v}</span>
          </span>
        )
      },
    },
    {
      title: '类别',
      dataIndex: 'factor_category',
      key: 'factor_category',
      width: 90,
      render: (v: string) => CATEGORY_MAP[v] || v,
    },
    {
      title: 'IC均值',
      dataIndex: 'ic_mean',
      key: 'ic_mean',
      width: 90,
      render: (v: number) => v.toFixed(4),
    },
    {
      title: 'IC标准差',
      dataIndex: 'ic_std',
      key: 'ic_std',
      width: 90,
      render: (v: number) => v.toFixed(4),
    },
    {
      title: 'ICIR',
      dataIndex: 'icir',
      key: 'icir',
      width: 160,
      defaultSortOrder: 'ascend' as const,
      sorter: (a: FactorEvalResult, b: FactorEvalResult) => Math.abs(b.icir) - Math.abs(a.icir),
      render: (v: number) => {
        const stars = getStarCount(v)
        const absIcir = Math.abs(v)
        let color = 'var(--text-secondary)'
        if (absIcir >= 0.5) color = '#52c41a'
        else if (absIcir >= 0.3) color = '#faad14'
        else if (absIcir >= 0.2) color = '#fa8c16'
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}>
            <span style={{ color, fontWeight: 600 }}>{v.toFixed(4)}</span>
            {renderStars(stars)}
          </span>
        )
      },
    },
    {
      title: 'IC胜率',
      dataIndex: 'ic_win_rate',
      key: 'ic_win_rate',
      width: 90,
      render: (v: number) => `${(v * 100).toFixed(1)}%`,
    },
    {
      title: 'Pearson IC',
      dataIndex: 'pearson_ic_mean',
      key: 'pearson_ic_mean',
      width: 90,
      render: (v: number) => v?.toFixed(4) ?? '-',
    },
    {
      title: 'Pearson ICIR',
      dataIndex: 'pearson_icir',
      key: 'pearson_icir',
      width: 100,
      render: (v: number) => {
        if (v == null) return '-'
        const absV = Math.abs(v)
        let color = 'var(--text-secondary)'
        if (absV >= 0.5) color = '#52c41a'
        else if (absV >= 0.3) color = '#faad14'
        else if (absV >= 0.2) color = '#fa8c16'
        return <span style={{ color, fontWeight: 600 }}>{v.toFixed(4)}</span>
      },
    },
    {
      title: '覆盖率',
      dataIndex: 'coverage',
      key: 'coverage',
      width: 90,
      render: (v: number) => `${(v * 100).toFixed(1)}%`,
    },
  ]

  return (
    <div className={styles.container}>
      <Tabs
        activeKey={activeTab}
        onChange={key => onStateChange({ activeTab: key })}
        items={[
          {
            key: 'compute',
            label: '因子计算',
            children: (
              <>
                <div className={styles.configPanel}>
                  <div className={styles.configSection}>
                    <div className={styles.sectionTitle}>参数配置</div>
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
                      <span style={{ margin: '0 12px' }}>前瞻天数：</span>
                      <Select
                        value={forwardDays}
                        onChange={setForwardDays}
                        style={{ width: 100 }}
                        disabled={loading}
                        options={[
                          { value: 1, label: '1日' },
                          { value: 3, label: '3日' },
                          { value: 5, label: '5日' },
                          { value: 10, label: '10日' },
                          { value: 20, label: '20日' },
                          { value: 30, label: '30日' },
                          { value: 60, label: '60日' },
                          { value: 90, label: '90日' },
                          { value: 120, label: '120日' },
                          { value: 250, label: '250日' },
                        ]}
                      />
                    </div>
                  </div>

                  <div className={styles.configSectionGrow}>
                    <div className={styles.sectionTitle}>
                      <span>选择因子</span>
                      <span style={{ marginLeft: 8, fontSize: 12, fontWeight: 400, color: 'var(--text-secondary)' }}>
                        已选 {selectedFactorNames.length} / {factorDefinitions.length}
                      </span>
                    </div>
                    {defsLoading ? (
                      <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
                    ) : factorDefinitions.length === 0 ? (
                      <Empty description="暂无可用因子" />
                    ) : (
                      <div className={styles.factorList}>
                        <Collapse items={collapseItems} defaultActiveKey={CATEGORY_ORDER} />
                      </div>
                    )}
                  </div>

                  <div className={styles.actionRow}>
                    <Button
                      type="primary"
                      icon={loading ? <StopOutlined /> : <PlayCircleOutlined />}
                      onClick={loading ? handleCancel : handleStart}
                      danger={loading}
                      disabled={!loading && selectedFactorNames.length === 0}
                    >
                      {loading ? '取消计算' : '开始计算'}
                    </Button>
                    {loading && progress && (
                      <div className={styles.progressInline}>
                        <Progress
                          percent={progressPercent}
                          size="small"
                          status="active"
                          format={() => {
                            if (isEvaluating) {
                              return `评估 ${progress.factor_completed}/${progress.total_factors}`
                            }
                            return progress.total > 0 ? `${progress.current}/${progress.total}` : '准备中...'
                          }}
                          style={{ width: 180 }}
                        />
                        <span className={styles.progressStock}>
                          {isEvaluating
                            ? (progress.current_factor || '因子评估中...')
                            : (progress.current_stock || '')}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              </>
            ),
          },
          {
            key: 'eval',
            label: '因子评估',
            children: (
              <div className={styles.resultsSection}>
                <div className={styles.resultsHeader}>
                  <span className={styles.resultsTitle}>评估结果</span>
                  <div className={styles.resultsActions}>
                    {results.length > 0 && (
                      <>
                        <Tooltip title="规则模式: AND/OR逻辑组合; 打分模式: Fama-MacBeth回归拟合权重，加权合成综合得分">
                          <InfoCircleOutlined style={{ color: 'var(--text-secondary)', marginRight: 4 }} />
                        </Tooltip>
                        <Radio.Group
                          value={combinationMethod}
                          onChange={e => setCombinationMethod(e.target.value)}
                          size="small"
                          optionType="button"
                          buttonStyle="solid"
                        >
                          <Radio.Button value="scoring">回归打分</Radio.Button>
                          <Radio.Button value="rule">规则组合</Radio.Button>
                        </Radio.Group>
                        <Button
                          type="primary"
                          icon={<ThunderboltOutlined />}
                          onClick={handleGenerateStrategy}
                          loading={generateLoading}
                          disabled={selectedResultKeys.length === 0}
                        >
                          生成策略 ({selectedResultKeys.length})
                        </Button>
                      </>
                    )}
                    <Button icon={<HistoryOutlined />} onClick={handleOpenHistory}>历史任务</Button>
                  </div>
                </div>
                {results.length > 0 ? (
                  <div className={styles.resultsTableWrap}>
                    <Table
                      columns={columns}
                      dataSource={results}
                      rowKey="factor_name"
                      size="small"
                      pagination={false}
                      rowSelection={{
                        selectedRowKeys: selectedResultKeys,
                        onChange: (keys) => setSelectedResultKeys(keys as string[]),
                      }}
                    />
                  </div>
                ) : (
                  <Empty description="暂无评估结果，请先在因子计算Tab中运行计算" />
                )}
              </div>
            ),
          },
        ]}
      />

      <Modal
        title="历史评估任务"
        open={historyModalOpen}
        onCancel={() => setHistoryModalOpen(false)}
        footer={null}
        width={700}
      >
        {historyLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : historyTasks.length === 0 ? (
          <Empty description="暂无历史评估任务" />
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
                    {parseFactorCount(task.factor_names)}个因子 / 前瞻{task.forward_days}日
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
        title="策略生成成功"
        open={strategyModalOpen}
        onCancel={() => setStrategyModalOpen(false)}
        footer={null}
        width={600}
      >
        {generatedStrategy && (
          <div className={styles.strategyResult}>
            <div className={styles.strategyResultRow}>
              <span className={styles.strategyResultLabel}>策略名称</span>
              <span className={styles.strategyResultValue}>{generatedStrategy.name}</span>
            </div>
            <div className={styles.strategyResultRow}>
              <span className={styles.strategyResultLabel}>编译状态</span>
              <Tag color={generatedStrategy.valid ? 'green' : 'red'}>
                {generatedStrategy.valid ? '编译通过' : '编译失败'}
              </Tag>
            </div>
            <div className={styles.strategyResultRow}>
              <span className={styles.strategyResultLabel}>策略描述</span>
            </div>
            <div className={styles.strategyResultDesc}>
              {generatedStrategy.description?.split('\n').map((line, i) => (
                <div key={i}>{line}</div>
              ))}
            </div>
            <div style={{ marginTop: 16, textAlign: 'right' }}>
              <Button onClick={() => setStrategyModalOpen(false)}>关闭</Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default FactorMiningPage
