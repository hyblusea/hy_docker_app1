import { useState, useEffect, useCallback, useRef } from 'react'
import { Card, Button, Table, Tag, Modal, Progress, Space, message, Select } from 'antd'
import { DatabaseOutlined, SyncOutlined, CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined, BarChartOutlined } from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { getDashboard, getSyncStatusPaged, startFullSync, getSyncProgress, cancelSync } from '../api/kline'
import type { KlineSyncStatus, KlineSyncProgress, MissingDataResult, KlineRangeMap } from '../types/kline'
import styles from './DataManagementPage.module.css'

const PERIOD_OPTIONS = [
  { value: '', label: '全部周期' },
  { value: 'day', label: '日线' },
  { value: 'week', label: '周线' },
  { value: 'month', label: '月线' },
]

const STATUS_MAP: Record<string, { color: string; text: string }> = {
  SUCCESS: { color: 'green', text: '已完成' },
  SYNCING: { color: 'blue', text: '同步中' },
  PENDING: { color: 'default', text: '待同步' },
  FAILED: { color: 'red', text: '失败' },
}

const PERIOD_MAP: Record<string, string> = {
  day: '日线',
  week: '周线',
  month: '月线',
}

const DataManagementPage = () => {
  const { isRoot } = useAuth()
  const [statusList, setStatusList] = useState<KlineSyncStatus[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [missingData, setMissingData] = useState<MissingDataResult | null>(null)
  const [rangeMap, setRangeMap] = useState<KlineRangeMap>({})
  const [periodFilter, setPeriodFilter] = useState('')
  const [loading, setLoading] = useState(false)
  const [syncTaskId, setSyncTaskId] = useState<string | null>(null)
  const [progress, setProgress] = useState<KlineSyncProgress | null>(null)
  const [progressVisible, setProgressVisible] = useState(false)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchDashboard = useCallback(async () => {
    try {
      const dashboard = await getDashboard()
      setMissingData(dashboard.missing_data)
      setRangeMap(dashboard.range_map)
    } catch (e: any) {
      message.error(e.message || '加载统计数据失败')
    }
  }, [])

  const fetchStatusList = useCallback(async (page: number, size: number, period: string) => {
    setLoading(true)
    try {
      const result = await getSyncStatusPaged({ period: period || undefined, page, size })
      setStatusList(result.content)
      setTotalElements(result.total_elements)
    } catch (e: any) {
      message.error(e.message || '加载状态列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDashboard()
    fetchStatusList(currentPage, pageSize, periodFilter)
  }, [fetchDashboard, fetchStatusList, currentPage, pageSize, periodFilter])

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  const handleRefresh = useCallback(() => {
    fetchDashboard()
    fetchStatusList(currentPage, pageSize, periodFilter)
  }, [fetchDashboard, fetchStatusList, currentPage, pageSize, periodFilter])

  const startPolling = (taskId: string) => {
    setSyncTaskId(taskId)
    setProgressVisible(true)
    if (pollRef.current) clearInterval(pollRef.current)

    pollRef.current = setInterval(async () => {
      try {
        const p = await getSyncProgress(taskId)
        setProgress(p)
        if (p.completed || p.cancelled) {
          if (pollRef.current) clearInterval(pollRef.current)
          pollRef.current = null
          if (p.completed) {
            message.success('同步完成')
          }
          handleRefresh()
        }
      } catch {
        if (pollRef.current) clearInterval(pollRef.current)
        pollRef.current = null
      }
    }, 2000)
  }

  const handleFullSync = async () => {
    Modal.confirm({
      title: '全量同步',
      content: '将同步所有股票的K线数据，可能需要较长时间。确定开始？',
      onOk: async () => {
        try {
          const taskId = await startFullSync({ periods: ['day', 'week', 'month'] })
          startPolling(taskId)
          message.info('全量同步已启动')
        } catch (e: any) {
          message.error(e.message || '启动同步失败')
        }
      },
    })
  }

  const handleCancelSync = async () => {
    if (!syncTaskId) return
    try {
      await cancelSync(syncTaskId)
      message.info('已取消同步')
      setProgressVisible(false)
    } catch (e: any) {
      message.error(e.message || '取消失败')
    }
  }

  const handlePeriodChange = (value: string) => {
    setPeriodFilter(value)
    setCurrentPage(0)
  }

  const columns = [
    {
      title: '股票代码',
      dataIndex: 'ts_code',
      key: 'ts_code',
      width: 140,
    },
    {
      title: '股票名称',
      dataIndex: 'stock_name',
      key: 'stock_name',
      width: 120,
    },
    {
      title: '周期',
      dataIndex: 'period',
      key: 'period',
      width: 80,
      render: (v: string) => PERIOD_MAP[v] || v,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (v: string) => {
        const s = STATUS_MAP[v] || { color: 'default', text: v }
        return <Tag color={s.color}>{s.text}</Tag>
      },
    },
    {
      title: '开始日期',
      dataIndex: 'start_date',
      key: 'start_date',
      width: 120,
      render: (v: string) => fmtDate(v) || '-',
    },
    {
      title: '结束日期',
      dataIndex: 'last_sync_date',
      key: 'last_sync_date',
      width: 120,
      render: (v: string) => fmtDate(v) || '-',
    },
    {
      title: '记录数',
      dataIndex: 'total_records',
      key: 'total_records',
      width: 100,
      render: (v: number) => v?.toLocaleString() || '0',
    },
    {
      title: '错误信息',
      dataIndex: 'error_message',
      key: 'error_message',
      ellipsis: true,
    },
    {
      title: '同步时间',
      dataIndex: 'updated_at',
      key: 'updated_at',
      width: 170,
      render: (v: string) => fmtDateTime(v) || '-',
    },
  ]

  const progressPercent = progress && progress.total > 0
    ? Math.round((progress.current / progress.total) * 100)
    : 0

  const periodLabel: Record<string, string> = { day: '日线', week: '周线', month: '月线' }

  const fmtDate = (d: string | null | undefined) => {
    if (!d) return '-'
    if (d.length === 8) return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`
    return d
  }

  const fmtDateTime = (d: string | null | undefined) => {
    if (!d) return '-'
    const date = new Date(d)
    if (isNaN(date.getTime())) return d
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const h = String(date.getHours()).padStart(2, '0')
    const min = String(date.getMinutes()).padStart(2, '0')
    const s = String(date.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}:${s}`
  }

  const rangeCards = Object.entries(rangeMap).map(([period, info]) => (
    <div key={period} className={styles.periodCard}>
      <div className={styles.periodInfo}>
        <div className={styles.periodName}>
          <BarChartOutlined className={styles.periodNameIcon} />
          {periodLabel[period] || period}
        </div>
        <div className={styles.periodDate}>
          {info.start_date ? fmtDate(info.start_date) : '-'} ~ {info.end_date ? fmtDate(info.end_date) : '-'}
        </div>
        <div className={styles.periodCount}>
          {info.stock_count} 只 / {(info.total_records || 0).toLocaleString()} 行
        </div>
      </div>
    </div>
  ))

  return (
    <div className={styles.page}>
      <div style={{ display: 'flex', gap: 16, marginBottom: 16, flexShrink: 0 }}>
        <div style={{ flex: '1 1 0%', minWidth: 0 }}>
          <Card size="small" title="周期数据" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
            styles={{ body: { flex: 1, display: 'flex', alignItems: 'center', padding: 12 } }}>
            <div className={styles.periodGrid}>
              {rangeCards}
            </div>
          </Card>
        </div>
        <div style={{ flex: '1 1 0%', minWidth: 0 }}>
          <Card size="small" title="同步统计" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
            styles={{ body: { flex: 1, display: 'flex', alignItems: 'center', padding: 12 } }}>
            <div className={styles.syncGrid}>
              <div className={styles.syncItem}>
                <DatabaseOutlined className={styles.syncIcon} />
                <div className={styles.syncValue}>{(missingData?.total_stocks || 0).toLocaleString()}</div>
                <div className={styles.syncLabel}>股票总数</div>
              </div>
              <div className={styles.syncItem}>
                <CheckCircleOutlined className={`${styles.syncIcon} ${styles.syncSuccess}`} />
                <div className={`${styles.syncValue} ${styles.syncSuccess}`}>{(missingData?.complete_stocks || 0).toLocaleString()}</div>
                <div className={styles.syncLabel}>已完成</div>
              </div>
              <div className={styles.syncItem}>
                <ExclamationCircleOutlined className={`${styles.syncIcon} ${styles.syncWarning}`} />
                <div className={`${styles.syncValue} ${styles.syncWarning}`}>{(missingData?.incomplete_stocks || 0).toLocaleString()}</div>
                <div className={styles.syncLabel}>不完整</div>
              </div>
              <div className={styles.syncItem}>
                <div className={styles.syncValue}>{(missingData?.avg_completion_rate || 0).toFixed(1)}%</div>
                <div className={styles.syncLabel}>完整率</div>
              </div>
            </div>
          </Card>
        </div>
      </div>

      <div className={styles.toolbar}>
        <Space>
          <Button type="primary" icon={<SyncOutlined />} onClick={handleFullSync} disabled={!isRoot}>
            全量同步
          </Button>

          <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
            刷新状态
          </Button>
        </Space>
        <Select
          value={periodFilter}
          onChange={handlePeriodChange}
          options={PERIOD_OPTIONS}
          style={{ width: 120 }}
        />
      </div>

      <div className={styles.tableContainer}>
        <Table
          columns={columns}
          dataSource={statusList}
          rowKey={(r) => `${r.ts_code}-${r.period}`}
          loading={loading}
          pagination={{
            current: currentPage + 1,
            pageSize: pageSize,
            total: totalElements,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => {
              setCurrentPage(page - 1)
              setPageSize(size)
            },
          }}
          size="small"
        />
      </div>

      <Modal
        title="同步进度"
        open={progressVisible}
        onCancel={() => setProgressVisible(false)}
        footer={progress && !progress.completed && !progress.cancelled ? [
          <Button key="cancel" danger onClick={handleCancelSync}>取消同步</Button>,
        ] : [
          <Button key="close" onClick={() => setProgressVisible(false)}>关闭</Button>,
        ]}
        width={500}
        maskClosable={false}
      >
        {progress && (
          <div className={styles.progressContent}>
            <Progress percent={progressPercent} status={progress.cancelled ? 'exception' : progress.completed ? 'success' : 'active'} />
            <div className={styles.progressInfo}>
              <p>当前: {progress.current_stock || '-'}</p>
              <p>周期: {PERIOD_MAP[progress.current_period] || progress.current_period || '-'}</p>
              <p>进度: {progress.current} / {progress.total}</p>
              <p>成功: {progress.success_count} | 失败: {progress.fail_count}</p>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default DataManagementPage
