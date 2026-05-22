import { useState, useEffect, useCallback, useRef } from 'react'
import {
  App, Card, Tabs, Table, Button, Input, InputNumber, Form, Space,
  Tag, Progress, Modal, Descriptions, Select, Tooltip, Typography, DatePicker, AutoComplete
} from 'antd'
import {
  PlayCircleOutlined, StopOutlined, ReloadOutlined,
  ThunderboltOutlined, HistoryOutlined, ExperimentOutlined,
  CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined,
  SyncOutlined, RobotOutlined, CalendarOutlined, EyeOutlined, GlobalOutlined,
  DeleteOutlined, CodeOutlined, RedoOutlined
} from '@ant-design/icons'
import {
  submitTraining, submitAllTraining, getTrainingTasks, getTrainingTask,
  cancelTrainingTask, deleteTrainingTask, getTaskOutput, getModelVersions, predict, getLossHistory,
  type TrainingTask, type ModelVersion, type TrainingRequest, type LossHistoryItem
} from '../api/prediction'
import { searchStocks } from '../api/stock'
import type { StockBasic } from '../types'
import styles from './PredictionPage.module.css'

const { TextArea } = Input
const { Title, Text } = Typography

const statusMap: Record<string, { color: string; icon: React.ReactNode; text: string }> = {
  PENDING: { color: 'default', icon: <ClockCircleOutlined />, text: '等待中' },
  RUNNING: { color: 'processing', icon: <SyncOutlined spin />, text: '训练中' },
  COMPLETED: { color: 'success', icon: <CheckCircleOutlined />, text: '已完成' },
  FAILED: { color: 'error', icon: <CloseCircleOutlined />, text: '失败' },
  CANCELLED: { color: 'warning', icon: <StopOutlined />, text: '已取消' },
}

const directionMap: Record<string, { color: string; text: string }> = {
  UP: { color: 'green', text: '↑ 上涨' },
  DOWN: { color: 'red', text: '↓ 下跌' },
  FLAT: { color: 'blue', text: '→ 横盘' },
}

const PredictionPage = () => {
  const { message } = App.useApp()
  const [tasks, setTasks] = useState<TrainingTask[]>([])
  const [models, setModels] = useState<ModelVersion[]>([])
  const [loading, setLoading] = useState(false)
  const [predicting, setPredicting] = useState(false)
  const [predictResult, setPredictResult] = useState<any>(null)
  const [logModal, setLogModal] = useState<{ open: boolean; content: string }>({ open: false, content: '' })
  const [form] = Form.useForm()

  const [trainStockInput, setTrainStockInput] = useState('')
  const [trainStockOptions, setTrainStockOptions] = useState<{ value: string; label: string }[]>([])
  const [trainSelectedStock, setTrainSelectedStock] = useState<StockBasic | null>(null)
  const trainStockMapRef = useRef<Map<string, StockBasic>>(new Map())
  const trainDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const [predictStockInput, setPredictStockInput] = useState('')
  const [predictStockOptions, setPredictStockOptions] = useState<{ value: string; label: string }[]>([])
  const [predictSelectedStock, setPredictSelectedStock] = useState<StockBasic | null>(null)
  const predictStockMapRef = useRef<Map<string, StockBasic>>(new Map())
  const predictDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const [lossModal, setLossModal] = useState<{ open: boolean; data: LossHistoryItem[]; loading: boolean }>({
    open: false, data: [], loading: false
  })
  const [modelParamsModal, setModelParamsModal] = useState<{ open: boolean; model: ModelVersion | null }>({
    open: false, model: null
  })
  const [liveOutputModal, setLiveOutputModal] = useState<{
    open: boolean
    taskId: number | null
    output: string
    status: string
  }>({ open: false, taskId: null, output: '', status: '' })
  const liveOutputRef = useRef<HTMLPreElement>(null)
  const liveOutputTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const refreshTasks = useCallback(async () => {
    setLoading(true)
    try {
      const [taskList, modelList] = await Promise.all([getTrainingTasks(), getModelVersions()])
      setTasks(taskList)
      setModels(modelList)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    refreshTasks()
    const timer = setInterval(refreshTasks, 5000)
    return () => clearInterval(timer)
  }, [refreshTasks])

  const handleSubmitTraining = async (values: TrainingRequest) => {
    try {
      const req: TrainingRequest = {
        ...values,
        symbol: trainSelectedStock?.ts_code || trainSelectedStock?.symbol || values.symbol,
      }
      const task = await submitTraining(req)
      message.success(`训练任务已提交，ID: ${task.id}`)
      form.resetFields()
      setTrainStockInput('')
      setTrainSelectedStock(null)
      setTrainStockOptions([])
      trainStockMapRef.current.clear()
      refreshTasks()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const handleTrainAll = async () => {
    try {
      const values = await form.validateFields()
      const req: TrainingRequest = {
        ...values,
        trainAll: true,
      }
      const task = await submitAllTraining(req)
      message.success(`全A训练任务已提交，ID: ${task.id}`)
      form.resetFields()
      setTrainStockInput('')
      setTrainSelectedStock(null)
      setTrainStockOptions([])
      trainStockMapRef.current.clear()
      refreshTasks()
    } catch (e: any) {
      if (e.message) message.error(e.message)
    }
  }

  const handleCancelTask = async (id: number) => {
    try {
      await cancelTrainingTask(id)
      message.success('任务已取消')
      refreshTasks()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const handleTrainStockSearch = useCallback((value: string) => {
    setTrainStockInput(value)
    if (!value || value.length < 1) {
      setTrainSelectedStock(null)
      setTrainStockOptions([])
      trainStockMapRef.current.clear()
      form.setFieldValue('symbol', undefined)
      return
    }
    if (trainSelectedStock && `${trainSelectedStock.name} (${trainSelectedStock.ts_code})` === value) return
    setTrainSelectedStock(null)
    form.setFieldValue('symbol', undefined)
    if (trainDebounceRef.current) clearTimeout(trainDebounceRef.current)
    trainDebounceRef.current = setTimeout(async () => {
      try {
        const stocks = await searchStocks(value)
        const map = new Map<string, StockBasic>()
        const opts = stocks.slice(0, 20).map((s) => {
          const key = s.ts_code || s.symbol || ''
          const name = s.name || key
          const display = `${name} (${key})`
          map.set(display, s)
          return {
            value: display,
            label: `${name}  ${key}  ${s.industry || ''}`,
          }
        })
        trainStockMapRef.current = map
        setTrainStockOptions(opts)
      } catch {
        setTrainStockOptions([])
        trainStockMapRef.current.clear()
      }
    }, 300)
  }, [trainSelectedStock, form])

  const handleTrainStockSelect = useCallback((value: string) => {
    setTrainStockInput(value)
    const stock = trainStockMapRef.current.get(value)
    if (stock) {
      setTrainSelectedStock(stock)
      form.setFieldValue('symbol', stock.ts_code || stock.symbol)
    }
    setTrainStockOptions([])
  }, [form])

  const handlePredictStockSearch = useCallback((value: string) => {
    setPredictStockInput(value)
    if (!value || value.length < 1) {
      setPredictSelectedStock(null)
      setPredictStockOptions([])
      predictStockMapRef.current.clear()
      return
    }
    if (predictSelectedStock && `${predictSelectedStock.name} (${predictSelectedStock.ts_code})` === value) return
    setPredictSelectedStock(null)
    if (predictDebounceRef.current) clearTimeout(predictDebounceRef.current)
    predictDebounceRef.current = setTimeout(async () => {
      try {
        const stocks = await searchStocks(value)
        const map = new Map<string, StockBasic>()
        const opts = stocks.slice(0, 20).map((s) => {
          const key = s.ts_code || s.symbol || ''
          const name = s.name || key
          const display = `${name} (${key})`
          map.set(display, s)
          return {
            value: display,
            label: `${name}  ${key}  ${s.industry || ''}`,
          }
        })
        predictStockMapRef.current = map
        setPredictStockOptions(opts)
      } catch {
        setPredictStockOptions([])
        predictStockMapRef.current.clear()
      }
    }, 300)
  }, [predictSelectedStock])

  const handlePredictStockSelect = useCallback((value: string) => {
    setPredictStockInput(value)
    const stock = predictStockMapRef.current.get(value)
    if (stock) {
      setPredictSelectedStock(stock)
    }
    setPredictStockOptions([])
  }, [])

  const handlePredict = async () => {
    if (!predictSelectedStock) {
      message.warning('请选择股票')
      return
    }
    const symbol = predictSelectedStock.ts_code || predictSelectedStock.symbol || ''
    if (!symbol.trim()) {
      message.warning('请选择股票')
      return
    }
    setPredicting(true)
    setPredictResult(null)
    try {
      const result = await predict(symbol.trim())
      setPredictResult(result)
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setPredicting(false)
    }
  }

  const handleViewLoss = async (taskId: number) => {
    setLossModal({ open: true, data: [], loading: true })
    try {
      const data = await getLossHistory(taskId)
      setLossModal(prev => ({ ...prev, data, loading: false }))
    } catch (e: any) {
      message.error(e.message)
      setLossModal(prev => ({ ...prev, loading: false }))
    }
  }

  const handleViewModelParams = (model: ModelVersion) => {
    setModelParamsModal({ open: true, model })
  }

  const handleRetryTask = async (record: TrainingTask) => {
    try {
      const req: TrainingRequest = {
        symbol: record.symbol || undefined,
        epochs: record.epochs ?? undefined,
        batchSize: record.batchSize ?? undefined,
        seqLength: record.seqLength ?? undefined,
        predictionHorizon: record.predictionHorizon ?? undefined,
        learningRate: record.learningRate ?? undefined,
        modelName: record.modelName || undefined,
        trainAll: record.trainAll ?? undefined,
      }
      const task = record.trainAll
        ? await submitAllTraining(req)
        : await submitTraining(req)
      message.success(`重试任务已提交，新任务 ID: ${task.id}`)
      refreshTasks()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const handleDeleteTask = async (id: number) => {
    try {
      await deleteTrainingTask(id)
      message.success('任务已删除')
      refreshTasks()
    } catch (e: any) {
      message.error(e.message)
    }
  }

  const handleViewLiveOutput = async (taskId: number, status: string) => {
    setLiveOutputModal({ open: true, taskId, output: '正在加载输出...\n', status })
    try {
      const output = await getTaskOutput(taskId)
      setLiveOutputModal(prev => ({ ...prev, output: output || '暂无输出' }))
    } catch (e: any) {
      setLiveOutputModal(prev => ({ ...prev, output: `加载失败: ${e.message}` }))
    }
  }

  const closeLiveOutput = useCallback(() => {
    if (liveOutputTimerRef.current) {
      clearInterval(liveOutputTimerRef.current)
      liveOutputTimerRef.current = null
    }
    setLiveOutputModal({ open: false, taskId: null, output: '', status: '' })
  }, [])

  useEffect(() => {
    if (liveOutputModal.open && liveOutputModal.taskId && liveOutputModal.status === 'RUNNING') {
      if (liveOutputTimerRef.current) clearInterval(liveOutputTimerRef.current)
      liveOutputTimerRef.current = setInterval(async () => {
        try {
          const output = await getTaskOutput(liveOutputModal.taskId!)
          setLiveOutputModal(prev => ({ ...prev, output: output || '暂无输出' }))
        } catch {
          // ignore polling errors
        }
      }, 2000)
    }
    if (!liveOutputModal.open || liveOutputModal.status !== 'RUNNING') {
      if (liveOutputTimerRef.current) {
        clearInterval(liveOutputTimerRef.current)
        liveOutputTimerRef.current = null
      }
    }
    return () => {
      if (liveOutputTimerRef.current) {
        clearInterval(liveOutputTimerRef.current)
        liveOutputTimerRef.current = null
      }
    }
  }, [liveOutputModal.open, liveOutputModal.taskId, liveOutputModal.status])

  useEffect(() => {
    if (liveOutputRef.current) {
      liveOutputRef.current.scrollTop = liveOutputRef.current.scrollHeight
    }
  }, [liveOutputModal.output])

  const taskColumns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '股票',
      dataIndex: 'symbol',
      width: 90,
      render: (v: string | null) => v || '-',
    },
    {
      title: '模型名称',
      dataIndex: 'modelName',
      width: 140,
      render: (v: string | null) => v || '-',
    },
    {
      title: 'Epochs',
      dataIndex: 'epochs',
      width: 70,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: 'Batch Size',
      dataIndex: 'batchSize',
      width: 80,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: 'Seq Length',
      dataIndex: 'seqLength',
      width: 80,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: 'Pred Horizon',
      dataIndex: 'predictionHorizon',
      width: 90,
      render: (v: number | null) => v ?? '-',
    },
    {
      title: 'Learning Rate',
      dataIndex: 'learningRate',
      width: 100,
      render: (v: number | null) => v != null ? v.toFixed(4) : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => {
        const s = statusMap[status] || { color: 'default', icon: null, text: status }
        return <Tag color={s.color} icon={s.icon}>{s.text}</Tag>
      },
    },
    {
      title: '进度',
      dataIndex: 'progressPct',
      width: 120,
      render: (v: number | null, record: TrainingTask) => {
        if (record.status === 'COMPLETED') return <Progress percent={100} size="small" status="success" />
        if (record.status === 'RUNNING' && v != null) return <Progress percent={Math.round(v)} size="small" />
        return '-'
      },
    },
    {
      title: '训练轮次',
      width: 100,
      render: (_: any, record: TrainingTask) =>
        record.currentEpoch != null && record.epochs != null
          ? `${record.currentEpoch}/${record.epochs}`
          : '-',
    },
    {
      title: '训练损失',
      dataIndex: 'trainLoss',
      width: 100,
      render: (v: number | null) => v != null ? v.toFixed(6) : '-',
    },
    {
      title: '验证损失',
      dataIndex: 'validLoss',
      width: 100,
      render: (v: number | null) => v != null ? v.toFixed(6) : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => v ? v.replace('T', ' ').substring(0, 19) : '-',
    },
    {
      title: '操作',
      width: 280,
      render: (_: any, record: TrainingTask) => (
        <Space>
          {record.status === 'RUNNING' && (
            <>
              <Button size="small" icon={<CodeOutlined />} onClick={() => handleViewLiveOutput(record.id, record.status)}>
                实时输出
              </Button>
              <Button size="small" danger icon={<StopOutlined />} onClick={() => handleCancelTask(record.id)}>
                取消
              </Button>
            </>
          )}
          {record.status === 'COMPLETED' && (
            <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewLoss(record.id)}>
              查看
            </Button>
          )}
          {record.status === 'FAILED' && (
            <Button size="small" icon={<RedoOutlined />} onClick={() => handleRetryTask(record)}>
              重试
            </Button>
          )}
          {record.outputLog && record.status !== 'RUNNING' && (
            <Button size="small" onClick={() => setLogModal({ open: true, content: record.outputLog || '' })}>
              日志
            </Button>
          )}
          {record.status !== 'RUNNING' && (
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => {
              Modal.confirm({
                title: '确认删除',
                content: `确定要删除任务 #${record.id} 吗？此操作不可恢复。`,
                okText: '删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: () => handleDeleteTask(record.id),
              })
            }}>
              删除
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const modelColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '模型名称', dataIndex: 'modelName', width: 160 },
    { title: '版本', dataIndex: 'version', width: 180 },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag> },
    { title: '最佳验证损失', dataIndex: 'bestValidLoss', width: 120, render: (v: number | null) => v != null ? v.toFixed(6) : '-' },
    { title: '方向准确率', dataIndex: 'directionAccuracy', width: 120, render: (v: number | null) => v != null ? `${(v * 100).toFixed(1)}%` : '-' },
    { title: '创建时间', dataIndex: 'createdAt', width: 160, render: (v: string) => v ? v.replace('T', ' ').substring(0, 19) : '-' },
    {
      title: '操作',
      width: 100,
      render: (_: any, record: ModelVersion) => (
        <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewModelParams(record)}>
          查看参数
        </Button>
      ),
    },
  ]

  const lossColumns = [
    { title: 'Epoch', dataIndex: 'epoch', width: 80 },
    { title: '训练损失', dataIndex: 'train_loss', width: 120, render: (v: number) => v?.toFixed(6) ?? '-' },
    { title: '验证损失', dataIndex: 'val_loss', width: 120, render: (v: number) => v?.toFixed(6) ?? '-' },
    { title: '训练准确率', dataIndex: 'train_acc', width: 120, render: (v: number | null) => v != null ? `${(v * 100).toFixed(1)}%` : '-' },
    { title: '验证准确率', dataIndex: 'val_acc', width: 120, render: (v: number | null) => v != null ? `${(v * 100).toFixed(1)}%` : '-' },
  ]

  return (
    <div className={styles.container}>
      <Tabs
        defaultActiveKey="train"
        items={[
          {
            key: 'train',
            label: <span><ExperimentOutlined /> 模型训练</span>,
            children: (
              <div className={styles.tabContent}>
                <Card title="训练参数配置" size="small" style={{ marginBottom: 16 }}>
                  <Form
                    form={form}
                    layout="inline"
                    onFinish={handleSubmitTraining}
                    initialValues={{
                      epochs: 100,
                      batchSize: 32,
                      seqLength: 60,
                      predictionHorizon: 5,
                      learningRate: 0.001,
                    }}
                  >
                    <Form.Item label="股票代码">
                      <AutoComplete
                        value={trainStockInput}
                        options={trainStockOptions}
                        onSearch={handleTrainStockSearch}
                        onSelect={handleTrainStockSelect}
                        placeholder="输入股票代码或名称"
                        style={{ width: 220 }}
                        allowClear
                        onClear={() => {
                          setTrainStockInput('')
                          setTrainSelectedStock(null)
                          setTrainStockOptions([])
                          trainStockMapRef.current.clear()
                          form.setFieldValue('symbol', undefined)
                        }}
                      />
                    </Form.Item>
                    <Form.Item name="symbol" hidden><Input /></Form.Item>
                    <Form.Item label="Epochs" name="epochs">
                      <InputNumber min={1} max={1000} style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item label="Batch Size" name="batchSize">
                      <InputNumber min={8} max={2048} style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item label="序列长度" name="seqLength">
                      <InputNumber min={10} max={500} style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item label="预测步长" name="predictionHorizon">
                      <InputNumber min={1} max={60} style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item label="学习率" name="learningRate">
                      <InputNumber min={0.00001} max={0.1} step={0.0001} style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item label="模型名称" name="modelName">
                      <Input placeholder="可选" style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item label="训练时间范围" name="dateRange">
                      <DatePicker.RangePicker
                        style={{ width: 300 }}
                        placeholder={['开始日期', '结束日期']}
                        format="YYYY-MM-DD"
                        onChange={(dates) => {
                          if (dates) {
                            form.setFieldValue('startDate', dates[0]?.format('YYYY-MM-DD'))
                            form.setFieldValue('endDate', dates[1]?.format('YYYY-MM-DD'))
                          }
                        }}
                      />
                    </Form.Item>
                    <Form.Item name="startDate" hidden><Input /></Form.Item>
                    <Form.Item name="endDate" hidden><Input /></Form.Item>
                    <Form.Item>
                      <Space>
                        <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />}>
                          开始训练
                        </Button>
                        <Button icon={<GlobalOutlined />} onClick={handleTrainAll}>
                          全A训练
                        </Button>
                      </Space>
                    </Form.Item>
                  </Form>
                </Card>

                <Card
                  title="训练任务"
                  size="small"
                  extra={<Button icon={<ReloadOutlined />} onClick={refreshTasks} loading={loading}>刷新</Button>}
                >
                  <Table
                    dataSource={tasks}
                    columns={taskColumns}
                    rowKey="id"
                    size="small"
                    pagination={{ pageSize: 10 }}
                    loading={loading}
                    scroll={{ x: 1600 }}
                  />
                </Card>
              </div>
            ),
          },
          {
            key: 'predict',
            label: <span><ThunderboltOutlined /> 智能预测</span>,
            children: (
              <div className={styles.tabContent}>
                <Card title="K线预测" size="small" style={{ marginBottom: 16 }}>
                  <Space>
                    <AutoComplete
                      value={predictStockInput}
                      options={predictStockOptions}
                      onSearch={handlePredictStockSearch}
                      onSelect={handlePredictStockSelect}
                      placeholder="输入股票代码或名称"
                      style={{ width: 280 }}
                      allowClear
                      onClear={() => {
                        setPredictStockInput('')
                        setPredictSelectedStock(null)
                        setPredictStockOptions([])
                        predictStockMapRef.current.clear()
                      }}
                    />
                    <Button type="primary" icon={<ThunderboltOutlined />} onClick={handlePredict} loading={predicting}>
                      预测
                    </Button>
                  </Space>
                </Card>

                {predictResult && (
                  <Card title="预测结果" size="small">
                    <Descriptions column={2} bordered size="small">
                      <Descriptions.Item label="股票代码">{predictResult.symbol}</Descriptions.Item>
                      <Descriptions.Item label="预测方向">
                        <Tag
                          color={directionMap[predictResult.direction]?.color || 'default'}
                          style={{ fontSize: 14, fontWeight: 'bold' }}
                        >
                          {directionMap[predictResult.direction]?.text || predictResult.direction || '-'}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="预测信号">
                        <Tag color={
                          predictResult.signal === 'BUY' ? 'green' :
                          predictResult.signal === 'SELL' ? 'red' : 'default'
                        } style={{ fontSize: 14, fontWeight: 'bold' }}>
                          {predictResult.signal === 'BUY' ? '买入' :
                           predictResult.signal === 'SELL' ? '卖出' : '持有'}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="涨跌幅">
                        {predictResult.magnitude != null ? (
                          <span style={{
                            color: predictResult.magnitude >= 0 ? '#cf1322' : '#3f8600',
                            fontWeight: 'bold',
                            fontSize: 16,
                          }}>
                            {predictResult.magnitude >= 0 ? '+' : ''}{(predictResult.magnitude * 100).toFixed(2)}%
                          </span>
                        ) : (
                          <span>{predictResult.prediction?.toFixed(4)}</span>
                        )}
                      </Descriptions.Item>
                      <Descriptions.Item label="置信度">
                        <Progress
                          percent={Math.round((predictResult.confidence || 0) * 100)}
                          size="small"
                          status={predictResult.confidence > 0.6 ? 'success' : 'normal'}
                        />
                      </Descriptions.Item>
                      {predictResult.probabilities && (
                        <Descriptions.Item label="概率分布">
                          <Space>
                            <Tag color="red">下跌 {(predictResult.probabilities.down * 100).toFixed(1)}%</Tag>
                            <Tag color="blue">横盘 {(predictResult.probabilities.flat * 100).toFixed(1)}%</Tag>
                            <Tag color="green">上涨 {(predictResult.probabilities.up * 100).toFixed(1)}%</Tag>
                          </Space>
                        </Descriptions.Item>
                      )}
                    </Descriptions>
                  </Card>
                )}
              </div>
            ),
          },
          {
            key: 'models',
            label: <span><HistoryOutlined /> 预测模型管理</span>,
            children: (
              <div className={styles.tabContent}>
                <Card
                  title="模型版本"
                  size="small"
                  extra={<Button icon={<ReloadOutlined />} onClick={refreshTasks} loading={loading}>刷新</Button>}
                >
                  <Table
                    dataSource={models}
                    columns={modelColumns}
                    rowKey="id"
                    size="small"
                    pagination={{ pageSize: 10 }}
                    loading={loading}
                  />
                </Card>
              </div>
            ),
          },
        ]}
      />

      <Modal
        title="训练日志"
        open={logModal.open}
        onCancel={() => setLogModal({ open: false, content: '' })}
        footer={null}
        width={800}
      >
        <TextArea
          value={logModal.content}
          readOnly
          autoSize={{ minRows: 10, maxRows: 30 }}
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
      </Modal>

      <Modal
        title="训练损失历史"
        open={lossModal.open}
        onCancel={() => setLossModal({ open: false, data: [], loading: false })}
        footer={null}
        width={700}
      >
        <Table
          dataSource={lossModal.data}
          columns={lossColumns}
          rowKey="epoch"
          size="small"
          pagination={{ pageSize: 15 }}
          loading={lossModal.loading}
        />
      </Modal>

      <Modal
        title="模型参数与指标"
        open={modelParamsModal.open}
        onCancel={() => setModelParamsModal({ open: false, model: null })}
        footer={null}
        width={600}
      >
        {modelParamsModal.model && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="模型名称">{modelParamsModal.model.modelName}</Descriptions.Item>
            <Descriptions.Item label="版本">{modelParamsModal.model.version}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={modelParamsModal.model.status === 'ACTIVE' ? 'green' : 'default'}>
                {modelParamsModal.model.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="引擎类型">{modelParamsModal.model.engineType || '-'}</Descriptions.Item>
            <Descriptions.Item label="最佳验证损失">
              {modelParamsModal.model.bestValidLoss != null ? modelParamsModal.model.bestValidLoss.toFixed(6) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="方向准确率">
              {modelParamsModal.model.directionAccuracy != null ? `${(modelParamsModal.model.directionAccuracy * 100).toFixed(1)}%` : '-'}
            </Descriptions.Item>
            {modelParamsModal.model.metrics && (() => {
              try {
                const metrics = JSON.parse(modelParamsModal.model.metrics)
                return Object.entries(metrics).map(([key, value]) => (
                  <Descriptions.Item label={key} key={key}>
                    {typeof value === 'number' ? (Number.isInteger(value) ? String(value) : value.toFixed(6)) : String(value)}
                  </Descriptions.Item>
                ))
              } catch {
                return <Descriptions.Item label="指标数据" span={2}>{modelParamsModal.model.metrics}</Descriptions.Item>
              }
            })()}
          </Descriptions>
        )}
      </Modal>

      <Modal
        title={
          <Space>
            <CodeOutlined />
            <span>任务 #{liveOutputModal.taskId} 实时输出</span>
            {liveOutputModal.status === 'RUNNING' && (
              <Tag color="processing" icon={<SyncOutlined spin />}>运行中</Tag>
            )}
          </Space>
        }
        open={liveOutputModal.open}
        onCancel={closeLiveOutput}
        footer={null}
        width={1100}
        destroyOnClose
      >
        <pre ref={liveOutputRef} className={styles.terminal}>
          {liveOutputModal.output}
        </pre>
      </Modal>
    </div>
  )
}

export default PredictionPage
