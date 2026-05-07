import { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Button, Input, InputNumber, Select, Radio, App, Modal, Empty, Tooltip, Spin } from 'antd'
import { PlusOutlined, DeleteOutlined, SaveOutlined, EyeOutlined, CheckCircleOutlined, CloseCircleOutlined, SwapOutlined, ThunderboltOutlined, BulbOutlined, ImportOutlined, LoadingOutlined, CopyOutlined } from '@ant-design/icons'
import JavaEditor from '../components/JavaEditor'
import { getStrategy, createStrategy, updateStrategy, deleteStrategy, aiGenerateStrategyStream, validateCode } from '../api/strategy'
import type { Strategy } from '../types/strategy'
import { useStrategies } from '../hooks/useStrategies'
import { useAuth } from '../contexts/AuthContext'
import {
  INDICATOR_CATALOG,
  RULE_CATALOG,
  getIndicatorDef,
  getRuleDef,
  nextId,
  getIndicatorLabel,
  createDefaultConfig,
  migrateConfig,
  generateJavaCode,
  type VisualStrategyConfig,
  type IndicatorNode,
  type RuleNode,
  type LeafRuleNode,
  type GroupRuleNode,
} from '../types/visualStrategy'
import styles from './VisualStrategyPage.module.css'

const EXIT_ONLY_RULES = new Set(RULE_CATALOG.filter(d => d.exitOnly).map(d => d.type.toLowerCase()))

function isRuleAllowedInSection(ruleType: string, section: 'entry' | 'exit'): boolean {
  if (section === 'exit') return true
  return !EXIT_ONLY_RULES.has(ruleType.toLowerCase())
}

const CATEGORY_ICONS: Record<string, string> = {
  '价格指标': '💰',
  '均线指标': '📈',
  '振荡指标': '📊',
  '统计指标': '📉',
  '辅助指标': '🔧',
  'K线形态': '🕯️',
  '成交量指标': '📦',
  'Ichimoku': '☁️',
  '布林带': '🎯',
  '指标运算': '🧮',
  '比较规则': '🔀',
  '风控规则': '🛡️',
  '趋势规则': '📐',
  '范围规则': '📏',
  '其他规则': '⚙️',
}

type RulePath = (string | number)[]

function updateRuleNode(root: RuleNode, path: RulePath, updater: (node: RuleNode) => RuleNode): RuleNode {
  if (path.length === 0) return updater(root)
  const [head, ...rest] = path
  if (root.kind === 'group' && typeof head === 'number') {
    const newChildren = [...root.children]
    newChildren[head] = updateRuleNode(newChildren[head], rest, updater)
    return { ...root, children: newChildren }
  }
  return root
}

function removeRuleNode(root: GroupRuleNode, path: RulePath): GroupRuleNode {
  if (path.length === 1 && typeof path[0] === 'number') {
    const newChildren = root.children.filter((_, i) => i !== path[0])
    return { ...root, children: newChildren }
  }
  if (path.length > 1 && typeof path[0] === 'number') {
    const child = root.children[path[0]]
    if (child.kind === 'group') {
      const newChildren = [...root.children]
      newChildren[path[0]] = removeRuleNode(child, path.slice(1))
      return { ...root, children: newChildren }
    }
  }
  return root
}

function addLeafToGroup(group: GroupRuleNode, type: string, indicators: IndicatorNode[]): GroupRuleNode {
  const def = getRuleDef(type)
  if (!def) return group
  const newLeaf: LeafRuleNode = {
    id: nextId('leaf'),
    kind: 'leaf',
    type,
    params: Object.fromEntries(def.params.map(p => [p.name, p.default])),
    indicatorInputs: [],
  }
  if (def.indicatorInputCount > 0 && indicators.length > 0) {
    const needed = Math.min(def.indicatorInputCount, indicators.length)
    newLeaf.indicatorInputs = indicators.slice(0, needed).map(i => i.id)
  }
  return { ...group, children: [...group.children, newLeaf] }
}

function addSubGroup(group: GroupRuleNode, combinator: 'AND' | 'OR' = 'AND'): GroupRuleNode {
  const newGroup: GroupRuleNode = {
    id: nextId('group'),
    kind: 'group',
    combinator,
    children: [],
  }
  return { ...group, children: [...group.children, newGroup] }
}

interface VisualStrategyPageProps {
  onStrategyChanged?: () => void
}

const VisualStrategyPage = ({ onStrategyChanged }: VisualStrategyPageProps) => {
  const { message, modal } = App.useApp()
  const { isRoot, user } = useAuth()
  const [config, setConfig] = useState<VisualStrategyConfig>(createDefaultConfig)
  const [strategyId, setStrategyId] = useState<number | null>(null)
  const [strategyName, setStrategyName] = useState('')
  const [strategyOwner, setStrategyOwner] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [codeModalOpen, setCodeModalOpen] = useState(false)
  const { strategies: allStrategies, loading: strategiesLoading, invalidate: invalidateStrategies } = useStrategies()
  const strategyList = useMemo(() => allStrategies, [allStrategies])
  const [loadingStrategy, setLoadingStrategy] = useState(false)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [newName, setNewName] = useState('')
  const [aiModalOpen, setAiModalOpen] = useState(false)
  const [aiBuyDesc, setAiBuyDesc] = useState('')
  const [aiSellDesc, setAiSellDesc] = useState('')
  const [aiGenerating, setAiGenerating] = useState(false)
  const [aiThinking, setAiThinking] = useState('')
  const [aiResult, setAiResult] = useState<{ suggestedName: string; code: string; valid: boolean; compileError: string } | null>(null)
  const [aiStrategyName, setAiStrategyName] = useState('')
  const [aiCode, setAiCode] = useState('')
  const [aiRetryCount, setAiRetryCount] = useState(0)
  const [aiMaxRetries, setAiMaxRetries] = useState(10)
  const aiAbortRef = useRef<AbortController | null>(null)
  const typingBufferRef = useRef('')
  const typingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const aiThinkingRef = useRef<HTMLDivElement | null>(null)
  const [codeImportModalOpen, setCodeImportModalOpen] = useState(false)
  const [codeImportName, setCodeImportName] = useState('')
  const [codeImportCode, setCodeImportCode] = useState('')
  const [codeImportValidating, setCodeImportValidating] = useState(false)
  const [aiImporting, setAiImporting] = useState(false)
  const [codeImportResult, setCodeImportResult] = useState<{ valid: boolean; compileError: string } | null>(null)
  const [javaCodeView, setJavaCodeView] = useState('')
  const [javaCodeEdited, setJavaCodeEdited] = useState(false)
  const [dragOverTarget, setDragOverTarget] = useState<string | null>(null)
  const [paletteFilter, setPaletteFilter] = useState('')

  const filteredIndicatorCatalog = useMemo(() => {
    if (!paletteFilter) return INDICATOR_CATALOG
    const kw = paletteFilter.toLowerCase()
    return INDICATOR_CATALOG.filter(d => d.label.toLowerCase().includes(kw) || d.type.toLowerCase().includes(kw) || d.category.toLowerCase().includes(kw))
  }, [paletteFilter])

  const filteredRuleCatalog = useMemo(() => {
    if (!paletteFilter) return RULE_CATALOG
    const kw = paletteFilter.toLowerCase()
    return RULE_CATALOG.filter(d => d.label.toLowerCase().includes(kw) || d.type.toLowerCase().includes(kw) || d.category.toLowerCase().includes(kw))
  }, [paletteFilter])

  const filteredIndicatorCategories = useMemo(() =>
    [...new Set(filteredIndicatorCatalog.map(d => d.category))],
    [filteredIndicatorCatalog]
  )

  const filteredRuleCategories = useMemo(() =>
    [...new Set(filteredRuleCatalog.map(d => d.category))],
    [filteredRuleCatalog]
  )

  const indicatorOptions = useMemo(() =>
    config.indicators.map(ind => ({
      value: ind.id,
      label: `${ind.id.split('_')[0]}: ${getIndicatorLabel(ind)}`,
    })),
    [config.indicators]
  )

  const handleAddIndicator = useCallback((type: string) => {
    const def = getIndicatorDef(type)
    if (!def) return
    setConfig(prev => {
      const newInd: IndicatorNode = {
        id: nextId('ind'),
        type,
        params: Object.fromEntries(def.params.map(p => [p.name, p.default])),
        inputs: [],
      }
      if (def.needsBarSeries && def.inputCount === 0) {
        return { ...prev, indicators: [...prev.indicators, newInd] }
      }
      if (def.inputCount > 0 && prev.indicators.length > 0) {
        const firstPriceInd = prev.indicators.find(i => {
          const d = getIndicatorDef(i.type)
          return d?.needsBarSeries && d?.inputCount === 0
        })
        newInd.inputs = firstPriceInd ? [firstPriceInd.id] : [prev.indicators[0].id]
      }
      return { ...prev, indicators: [...prev.indicators, newInd] }
    })
  }, [])

  const handleRemoveIndicator = useCallback((id: string) => {
    setConfig(prev => {
      const newIndicators = prev.indicators.filter(i => i.id !== id)
      const cleanedIndicators = newIndicators.map(i => ({
        ...i,
        inputs: i.inputs.filter(ref => ref !== id),
      }))
      return { ...prev, indicators: cleanedIndicators }
    })
  }, [])

  const handleUpdateIndicatorParam = useCallback((id: string, paramName: string, value: number | null) => {
    if (value === null) return
    setConfig(prev => ({
      ...prev,
      indicators: prev.indicators.map(i =>
        i.id === id ? { ...i, params: { ...i.params, [paramName]: value } } : i
      ),
    }))
  }, [])

  const handleUpdateIndicatorInput = useCallback((id: string, inputIndex: number, refId: string) => {
    setConfig(prev => ({
      ...prev,
      indicators: prev.indicators.map(i => {
        if (i.id !== id) return i
        const newInputs = [...i.inputs]
        newInputs[inputIndex] = refId
        return { ...i, inputs: newInputs }
      }),
    }))
  }, [])

  const handleAddRuleToGroup = useCallback((section: 'entry' | 'exit', path: RulePath, type: string) => {
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      if (root.kind !== 'group') return prev
      const updated = updateRuleNode(root, path, (node) => {
        if (node.kind !== 'group') return node
        return addLeafToGroup(node, type, prev.indicators)
      }) as GroupRuleNode
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleAddSubGroup = useCallback((section: 'entry' | 'exit', path: RulePath) => {
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      if (root.kind !== 'group') return prev
      const updated = updateRuleNode(root, path, (node) => {
        if (node.kind !== 'group') return node
        return addSubGroup(node, 'AND')
      }) as GroupRuleNode
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleRemoveRuleNode = useCallback((section: 'entry' | 'exit', path: RulePath) => {
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      if (root.kind !== 'group') return prev
      if (path.length === 0) return prev
      const updated = removeRuleNode(root, path)
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleToggleNegate = useCallback((section: 'entry' | 'exit', path: RulePath) => {
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      const updated = updateRuleNode(root, path, (node) => ({
        ...node,
        negated: !node.negated,
      }))
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleUpdateCombinator = useCallback((section: 'entry' | 'exit', path: RulePath, combinator: 'AND' | 'OR') => {
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      const updated = updateRuleNode(root, path, (node) => {
        if (node.kind !== 'group') return node
        return { ...node, combinator }
      })
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleUpdateLeafParam = useCallback((section: 'entry' | 'exit', path: RulePath, paramName: string, value: number | null) => {
    if (value === null) return
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      const updated = updateRuleNode(root, path, (node) => {
        if (node.kind !== 'leaf') return node
        return { ...node, params: { ...node.params, [paramName]: value } }
      })
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleUpdateLeafInput = useCallback((section: 'entry' | 'exit', path: RulePath, inputIndex: number, refId: string) => {
    setConfig(prev => {
      const root = section === 'entry' ? prev.entryRule : prev.exitRule
      const updated = updateRuleNode(root, path, (node) => {
        if (node.kind !== 'leaf') return node
        const newInputs = [...node.indicatorInputs]
        newInputs[inputIndex] = refId
        return { ...node, indicatorInputs: newInputs }
      })
      return { ...prev, [section === 'entry' ? 'entryRule' : 'exitRule']: updated }
    })
  }, [])

  const handleSave = useCallback(async () => {
    if (!strategyName.trim()) {
      message.warning('请输入策略名称')
      return
    }
    setSaving(true)
    try {
      const code = JSON.stringify(config)
      if (strategyId) {
        const updated = await updateStrategy(strategyId, { name: strategyName.trim(), code })
        if (updated.valid) {
          message.success('保存成功，策略配置有效')
        } else {
          message.warning('保存成功，但策略配置无效: ' + (updated.compile_error || ''))
        }
      } else {
        const s = await createStrategy({
          name: strategyName.trim(),
          language: 'visual',
          code,
        })
        setStrategyId(s.id!)
        if (s.valid) {
          message.success('创建成功，策略配置有效')
        } else {
          message.warning('创建成功，但策略配置无效: ' + (s.compile_error || ''))
        }
      }
      onStrategyChanged?.()
      invalidateStrategies()
    } catch {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }, [strategyName, strategyId, config, message, invalidateStrategies])

  const handleJavaSave = useCallback(async () => {
    if (!strategyName.trim() || !strategyId || !javaCodeView.trim()) return
    setSaving(true)
    try {
      const updated = await updateStrategy(strategyId, {
        name: strategyName.trim(),
        code: javaCodeView,
      })
      if (updated.valid) {
        message.success('保存成功，代码编译通过')
        setJavaCodeEdited(false)
      } else {
        message.warning('已保存，但代码编译未通过: ' + (updated.compile_error || ''))
        setJavaCodeEdited(false)
      }
      onStrategyChanged?.()
      invalidateStrategies()
    } catch {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }, [strategyName, strategyId, javaCodeView, message, invalidateStrategies])

  const handleLoad = useCallback(async (id: number) => {
    setLoadingStrategy(true)
    try {
      const s = await getStrategy(id)
      setStrategyOwner(s.created_by || null)
      if (s.language === 'java') {
        setStrategyId(s.id!)
        setStrategyName(s.name)
        setJavaCodeView(s.code || '')
        setJavaCodeEdited(false)
        setConfig(createDefaultConfig())
        message.info('已加载Java代码策略')
        return
      }
      setJavaCodeView('')
      const raw = JSON.parse(s.code)
      const parsed = migrateConfig(raw)
      setConfig(parsed)
      setStrategyId(s.id!)
      setStrategyName(s.name)
      message.success('加载成功')
    } catch {
      message.error('加载策略失败')
    } finally {
      setLoadingStrategy(false)
    }
  }, [message])

  const handleNew = useCallback(() => {
    setConfig(createDefaultConfig())
    setStrategyId(null)
    setStrategyName('')
    setStrategyOwner(null)
    setJavaCodeView('')
  }, [])

  const handleCreate = useCallback(async () => {
    if (!newName.trim()) {
      message.warning('请输入策略名称')
      return
    }
    try {
      const code = JSON.stringify(createDefaultConfig())
      const s = await createStrategy({
        name: newName.trim(),
        language: 'visual',
        code,
      })
      message.success('创建成功')
      setCreateModalOpen(false)
      setNewName('')
      onStrategyChanged?.()
      invalidateStrategies()
      handleLoad(s.id!)
    } catch {
      message.error('创建失败')
    }
  }, [newName, message, invalidateStrategies, handleLoad])

  useEffect(() => {
    if (aiThinkingRef.current) {
      aiThinkingRef.current.scrollTop = aiThinkingRef.current.scrollHeight
    }
  }, [aiThinking])

  const handleAiGenerate = useCallback(() => {
    if (!aiBuyDesc.trim() || !aiSellDesc.trim()) {
      message.warning('请输入买入和卖出策略描述')
      return
    }
    setAiGenerating(true)
    setAiResult(null)
    setAiThinking('')
    setAiCode('')
    setAiRetryCount(0)
    typingBufferRef.current = ''
    if (typingTimerRef.current) {
      clearInterval(typingTimerRef.current)
      typingTimerRef.current = null
    }

    typingTimerRef.current = setInterval(() => {
      const buf = typingBufferRef.current
      if (buf.length > 0) {
        const charsToTake = Math.min(buf.length, 3)
        typingBufferRef.current = buf.slice(charsToTake)
        setAiThinking(prev => prev + buf.slice(0, charsToTake))
      }
    }, 20)

    const controller = aiGenerateStrategyStream(
      aiBuyDesc.trim(),
      aiSellDesc.trim(),
      (chunk) => {
        typingBufferRef.current += chunk
      },
      (result) => {
        if (typingTimerRef.current) {
          clearInterval(typingTimerRef.current)
          typingTimerRef.current = null
        }
        if (typingBufferRef.current.length > 0) {
          setAiThinking(prev => prev + typingBufferRef.current)
          typingBufferRef.current = ''
        }
        setAiResult(result)
        setAiStrategyName(result.suggestedName || '')
        setAiCode(result.code || '')
        setAiGenerating(false)
        if (result.valid) {
          message.success('AI生成成功，代码编译通过')
        } else {
          message.warning('AI生成完成，但代码编译未通过，请检查或手动修改')
        }
      },
      (msg) => {
        if (typingTimerRef.current) {
          clearInterval(typingTimerRef.current)
          typingTimerRef.current = null
        }
        typingBufferRef.current = ''
        message.error('AI生成失败: ' + msg)
        setAiGenerating(false)
      },
      (retryInfo) => {
        setAiRetryCount(retryInfo.retryCount)
        setAiMaxRetries(retryInfo.maxRetries)
        setAiThinking('')
        typingBufferRef.current = ''
      },
      (msg) => {
        if (typingTimerRef.current) {
          clearInterval(typingTimerRef.current)
          typingTimerRef.current = null
        }
        typingBufferRef.current = ''
        message.warning(msg)
        setAiGenerating(false)
      },
    )
    aiAbortRef.current = controller
  }, [aiBuyDesc, aiSellDesc, message])

  const handleAiImport = useCallback(async () => {
    if (!aiStrategyName.trim()) {
      message.warning('请输入策略名称')
      return
    }
    if (!aiCode.trim()) {
      message.warning('没有可导入的代码')
      return
    }
    setAiImporting(true)
    try {
      const description = `买入策略: ${aiBuyDesc.trim()}\n卖出策略: ${aiSellDesc.trim()}`
      const s = await createStrategy({
        name: aiStrategyName.trim(),
        language: 'java',
        code: aiCode.trim(),
        description,
      })
      message.success('策略导入成功')
      setAiModalOpen(false)
      setAiBuyDesc('')
      setAiSellDesc('')
      setAiResult(null)
      setAiStrategyName('')
      setAiCode('')
      onStrategyChanged?.()
      invalidateStrategies()
      handleLoad(s.id!)
    } catch {
      message.error('策略导入失败')
    } finally {
      setAiImporting(false)
    }
  }, [aiStrategyName, aiCode, message, invalidateStrategies, handleLoad])

  const handleCodeValidate = useCallback(async () => {
    if (!codeImportCode.trim()) {
      message.warning('请输入Java代码')
      return
    }
    setCodeImportValidating(true)
    setCodeImportResult(null)
    try {
      const result = await validateCode(codeImportCode.trim())
      setCodeImportResult(result)
      if (result.valid) {
        message.success('代码校验通过，可以导入')
      } else {
        message.warning('代码校验未通过，请修改后重新验证')
      }
    } catch {
      message.error('代码校验失败')
    } finally {
      setCodeImportValidating(false)
    }
  }, [codeImportCode, message])

  const handleCodeImport = useCallback(async () => {
    if (!codeImportName.trim()) {
      message.warning('请输入策略名称')
      return
    }
    if (!codeImportResult?.valid) {
      message.warning('请先验证代码，确保编译通过后再导入')
      return
    }
    setCodeImportValidating(true)
    try {
      await createStrategy({
        name: codeImportName.trim(),
        language: 'java',
        code: codeImportCode.trim(),
      })
      message.success('代码导入成功，策略已创建')
      setCodeImportModalOpen(false)
      setCodeImportName('')
      setCodeImportCode('')
      setCodeImportResult(null)
      onStrategyChanged?.()
      invalidateStrategies()
    } catch {
      message.error('导入失败')
    } finally {
      setCodeImportValidating(false)
    }
  }, [codeImportName, codeImportCode, codeImportResult, message, invalidateStrategies])

  const handleRename = useCallback(async (id: number) => {
    const s = strategyList.find((s) => s.id === id)
    if (!s) return
    let renameValue = ''
    Modal.confirm({
      title: '重命名策略',
      width: s.description ? 520 : 416,
      content: (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Input
            defaultValue={s.name}
            onChange={(e) => { renameValue = e.target.value }}
          />
          {s.description && (
            <div style={{ marginTop: 4 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-secondary, #666)' }}>AI生成描述</span>
                <Button
                  type="link"
                  size="small"
                  icon={<CopyOutlined />}
                  style={{ padding: 0, height: 'auto', fontSize: 12 }}
                  onClick={() => {
                    navigator.clipboard.writeText(s.description!)
                    message.success('描述已复制到剪贴板')
                  }}
                >
                  复制
                </Button>
              </div>
              <div style={{
                background: 'var(--bg-secondary, #f5f5f5)',
                border: '1px solid var(--border-color, #e8e8e8)',
                borderRadius: 6,
                padding: '8px 12px',
                fontSize: 13,
                lineHeight: 1.6,
                color: 'var(--text-secondary, #666)',
                whiteSpace: 'pre-wrap',
                maxHeight: 120,
                overflowY: 'auto',
                userSelect: 'text',
              }}>
                {s.description}
              </div>
            </div>
          )}
        </div>
      ),
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        if (!renameValue.trim()) return
        try {
          await updateStrategy(id, { name: renameValue.trim() })
          message.success('重命名成功')
          onStrategyChanged?.()
          invalidateStrategies()
          if (strategyId === id) {
            setStrategyName(renameValue.trim())
          }
        } catch {
          message.error('重命名失败')
        }
      },
    })
  }, [strategyList, strategyId, message, invalidateStrategies])

  const handleDelete = useCallback(async (id: number) => {
    modal.confirm({
      title: '确认删除',
      content: '删除后无法恢复，确定要删除该策略吗？',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteStrategy(id)
          message.success('删除成功')
          if (strategyId === id) {
            handleNew()
          }
          onStrategyChanged?.()
          invalidateStrategies()
        } catch {
          message.error('删除失败')
        }
      },
    })
  }, [strategyId, message, modal, handleNew, invalidateStrategies])

  const isOwner = useCallback((s: Strategy) => {
    if (isRoot) return true
    return s.created_by === user?.username
  }, [isRoot, user])

  const canEdit = isRoot || !strategyOwner || strategyOwner === user?.username

  const generatedCode = useMemo(() => {
    const className = strategyName.trim().replace(/[^a-zA-Z0-9]/g, '') || 'Strategy'
    return generateJavaCode(config, className)
  }, [config, strategyName])

  const renderIndicatorCard = (ind: IndicatorNode) => {
    const def = getIndicatorDef(ind.type)
    if (!def) return null
    return (
      <div key={ind.id} className={styles.indicatorCard}>
        <div className={styles.cardHeader}>
          <span className={styles.cardType}>{CATEGORY_ICONS[def.category] || '📊'} {def.label}</span>
          <Tooltip title="删除指标">
            <button className={styles.cardDeleteBtn} onClick={() => handleRemoveIndicator(ind.id)}>
              <DeleteOutlined />
            </button>
          </Tooltip>
        </div>
        <div className={styles.cardBody}>
          {def.params.map(p => (
            <div key={p.name} className={styles.paramRow}>
              <label className={styles.paramLabel}>{p.label}:</label>
              <InputNumber
                size="small"
                value={ind.params[p.name] ?? p.default}
                min={p.min}
                max={p.max}
                step={p.step ?? 1}
                onChange={(v) => handleUpdateIndicatorParam(ind.id, p.name, v)}
                className={styles.paramInput}
              />
            </div>
          ))}
          {def.inputCount > 0 && def.inputLabels.map((label, idx) => (
            <div key={label} className={styles.paramRow}>
              <label className={styles.paramLabel}>{label}:</label>
              <Select
                size="small"
                value={ind.inputs[idx] || undefined}
                onChange={(v) => handleUpdateIndicatorInput(ind.id, idx, v)}
                options={indicatorOptions}
                placeholder="选择指标"
                className={styles.paramSelect}
                popupMatchSelectWidth={false}
              />
            </div>
          ))}
        </div>
      </div>
    )
  }

  const renderRuleNode = (node: RuleNode, section: 'entry' | 'exit', path: RulePath): React.ReactNode => {
    if (node.kind === 'leaf') {
      return renderLeafCard(node, section, path)
    }
    return renderGroupCard(node, section, path)
  }

  const renderLeafCard = (leaf: LeafRuleNode, section: 'entry' | 'exit', path: RulePath) => {
    const def = getRuleDef(leaf.type)
    if (!def) return null
    return (
      <div key={leaf.id} className={`${styles.conditionCard} ${leaf.negated ? styles.negatedCard : ''}`}>
        <div className={styles.cardHeader}>
          <span className={styles.cardType}>
            {leaf.negated && <span className={styles.notBadge}>NOT</span>}
            {CATEGORY_ICONS[def.category] || '🔀'} {def.label}
          </span>
          <div className={styles.cardActions}>
            <Tooltip title={leaf.negated ? '取消取反' : '取反(NOT)'}>
              <button
                className={`${styles.cardActionBtn} ${leaf.negated ? styles.cardActionBtnActive : ''}`}
                onClick={() => handleToggleNegate(section, path)}
              >
                <SwapOutlined />
              </button>
            </Tooltip>
            <Tooltip title="删除条件">
              <button className={styles.cardDeleteBtn} onClick={() => handleRemoveRuleNode(section, path)}>
                <DeleteOutlined />
              </button>
            </Tooltip>
          </div>
        </div>
        <div className={styles.cardBody}>
          {def.params.map(p => (
            <div key={p.name} className={styles.paramRow}>
              <label className={styles.paramLabel}>{p.label}:</label>
              <InputNumber
                size="small"
                value={leaf.params[p.name] ?? p.default}
                min={p.min}
                max={p.max}
                step={p.step ?? 1}
                onChange={(v) => handleUpdateLeafParam(section, path, p.name, v)}
                className={styles.paramInput}
              />
            </div>
          ))}
          {def.indicatorInputLabels.map((label, idx) => (
            <div key={label} className={styles.paramRow}>
              <label className={styles.paramLabel}>{label}:</label>
              <Select
                size="small"
                value={leaf.indicatorInputs[idx] || undefined}
                onChange={(v) => handleUpdateLeafInput(section, path, idx, v)}
                options={indicatorOptions}
                placeholder="选择指标"
                className={styles.paramSelect}
                popupMatchSelectWidth={false}
              />
            </div>
          ))}
        </div>
      </div>
    )
  }

  const renderGroupCard = (group: GroupRuleNode, section: 'entry' | 'exit', path: RulePath) => {
    const isRoot = path.length === 0
    const groupDragId = `${section}_${path.join('_')}`
    return (
      <div key={group.id} className={`${styles.ruleGroup} ${group.negated ? styles.negatedGroup : ''} ${isRoot ? styles.ruleGroupRoot : ''}`}>
        <div className={styles.ruleGroupHeader}>
          <div className={styles.ruleGroupHeaderLeft}>
            {group.negated && <span className={styles.notBadge}>NOT</span>}
            <Radio.Group
              size="small"
              value={group.combinator}
              onChange={(e) => handleUpdateCombinator(section, path, e.target.value)}
              optionType="button"
              buttonStyle="solid"
            >
              <Radio.Button value="AND">AND</Radio.Button>
              <Radio.Button value="OR">OR</Radio.Button>
            </Radio.Group>
          </div>
          {!isRoot && (
            <div className={styles.ruleGroupHeaderRight}>
              <Tooltip title={group.negated ? '取消取反' : '取反(NOT)'}>
                <button
                  className={`${styles.cardActionBtn} ${group.negated ? styles.cardActionBtnActive : ''}`}
                  onClick={() => handleToggleNegate(section, path)}
                >
                  <SwapOutlined />
                </button>
              </Tooltip>
              <Tooltip title="删除分组">
                <button className={styles.cardDeleteBtn} onClick={() => handleRemoveRuleNode(section, path)}>
                  <DeleteOutlined />
                </button>
              </Tooltip>
            </div>
          )}
        </div>
        <div
          className={`${styles.ruleGroupBody} ${dragOverTarget === groupDragId ? styles.ruleGroupDragOver : ''}`}
          onDragOver={(e) => {
            const ruleTypeKey = e.dataTransfer.types.find(t => t.startsWith('ruletype_'))
            if (ruleTypeKey) {
              const ruleType = ruleTypeKey.replace('ruletype_', '')
              if (isRuleAllowedInSection(ruleType, section)) {
                e.preventDefault(); e.stopPropagation(); e.dataTransfer.dropEffect = 'copy'; setDragOverTarget(groupDragId)
              } else { e.stopPropagation(); setDragOverTarget(null) }
            } else { e.stopPropagation(); setDragOverTarget(null) }
          }}
          onDragLeave={(e) => { e.stopPropagation(); setDragOverTarget(null) }}
          onDrop={(e) => {
            e.stopPropagation(); setDragOverTarget(null)
            const ruleType = e.dataTransfer.getData('ruleType')
            if (ruleType && isRuleAllowedInSection(ruleType, section)) handleAddRuleToGroup(section, path, ruleType)
          }}
        >
          {group.children.length === 0 ? (
            <Empty description="拖拽左侧规则组件到此处" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            group.children.map((child, idx) => renderRuleNode(child, section, [...path, idx]))
          )}
        </div>
        <div className={styles.ruleGroupFooter}>
          <Dropdown overlay={renderAddMenu(RULE_CATALOG.filter(d => isRuleAllowedInSection(d.type, section)), (type) => handleAddRuleToGroup(section, path, type))}>
            <Button size="small" type="dashed" icon={<PlusOutlined />}>
              添加规则
            </Button>
          </Dropdown>
          <Button size="small" type="dashed" onClick={() => handleAddSubGroup(section, path)}>
            + 子分组
          </Button>
        </div>
      </div>
    )
  }

  const renderAddMenu = (items: { type: string; label: string; category: string }[], onClick: (type: string) => void) => (
    <div className={styles.addMenu}>
      {items.map(item => (
        <button
          key={item.type}
          className={styles.addMenuItem}
          onClick={() => onClick(item.type)}
        >
          <span className={styles.addMenuIcon}>{CATEGORY_ICONS[item.category] || '📌'}</span>
          <span className={styles.addMenuLabel}>{item.label}</span>
        </button>
      ))}
    </div>
  )

  return (
    <div className={styles.container}>
      <div className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <span className={styles.sidebarTitle}>策略列表</span>
          <div className={styles.sidebarActions}>
            <button className={styles.aiBtn} onClick={() => setAiModalOpen(true)}>
              <BulbOutlined /> AI生成
            </button>
            <Tooltip title="导入Java策略">
              <button className={styles.codeImportBtn} onClick={() => setCodeImportModalOpen(true)}>
                <ImportOutlined />
              </button>
            </Tooltip>
            <Tooltip title="新建策略">
              <button className={styles.addBtn} onClick={() => setCreateModalOpen(true)}>
                <PlusOutlined />
              </button>
            </Tooltip>
          </div>
        </div>
        <div className={styles.strategyList}>
          {strategiesLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 100 }}>
              <Spin indicator={<LoadingOutlined style={{ fontSize: 24 }} spin />} />
            </div>
          ) : (
          <>
          {strategyList.map((s) => (
            <div
              key={s.id}
              className={strategyId === s.id ? styles.strategyItemActive : styles.strategyItem}
              onClick={() => handleLoad(s.id!)}
            >
              <Tooltip title={s.valid === false ? `无效: ${s.compile_error || '编译失败'}` : '有效'} placement="right">
                <span className={s.valid === false ? styles.statusInvalid : styles.statusValid}>
                  {s.valid === false ? <CloseCircleOutlined /> : <CheckCircleOutlined />}
                </span>
              </Tooltip>
              <span className={styles.strategyName}>{s.name}</span>
              {s.language === 'java' && <span className={styles.javaTag}>Java</span>}
              {s.created_by_role === 'root' && s.created_by !== user?.username && <span className={styles.javaTag} style={{ background: '#722ed1', color: '#fff' }}>共享</span>}
              <div className={styles.strategyMeta}>
                {isRoot && s.created_by && (
                  <span className={styles.strategyCreator}>{s.created_by}</span>
                )}
                {!isRoot && s.created_by && s.created_by !== user?.username && (
                  <span className={styles.strategyCreator}>{s.created_by}</span>
                )}
                {s.created_at && (
                  <span className={styles.strategyDate}>{new Date(s.created_at).toLocaleDateString()}</span>
                )}
              </div>
              {isOwner(s) && (
              <div className={styles.itemActions}>
                <button
                  className={styles.actionBtn}
                  onClick={(e) => { e.stopPropagation(); handleRename(s.id!) }}
                  title="重命名"
                >
                  ✎
                </button>
                <button
                  className={styles.actionBtn}
                  onClick={(e) => { e.stopPropagation(); handleDelete(s.id!) }}
                  title="删除"
                >
                  <DeleteOutlined />
                </button>
              </div>
              )}
            </div>
          ))}
          {strategyList.length === 0 && (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: 13, padding: 20 }}>
              暂无策略，点击 + 新建
            </div>
          )}
          </>
          )}
        </div>
      </div>

      <div className={styles.mainArea}>
        {loadingStrategy ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
            <Spin indicator={<LoadingOutlined style={{ fontSize: 32 }} spin />} tip="加载策略中..." />
          </div>
        ) : (
        <>
        {!javaCodeView && (
        <div className={styles.header}>
          <div className={styles.headerLeft}>
            <span className={styles.headerTitle}>可视化策略编辑器</span>
            {strategyName && <span className={styles.headerStrategyName}>{strategyName}</span>}
          </div>
          <div className={styles.headerRight}>
            <Button size="small" icon={<EyeOutlined />} onClick={() => setCodeModalOpen(true)} disabled={!strategyName.trim()}>
              查看代码
            </Button>
            <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleSave} loading={saving} disabled={!strategyName.trim() || !canEdit}>
              保存
            </Button>
          </div>
        </div>
        )}

        {!strategyName.trim() ? (
          <div className={styles.builderDisabled}>
            <Empty description="请先从左侧选择或新建一个策略" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          </div>
        ) : javaCodeView ? (
          <div className={styles.javaCodeViewArea}>
            <div className={styles.javaCodeViewHeader}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ color: '#fa8c16', fontWeight: 500 }}>Java 代码策略</span>
                {strategyName && <span className={styles.headerStrategyName}>{strategyName}</span>}
                {javaCodeEdited && <span style={{ color: '#faad14', fontSize: 12 }}>* 未保存</span>}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Button size="small" onClick={() => {
                  navigator.clipboard.writeText(javaCodeView)
                  message.success('代码已复制到剪贴板')
                }}>复制代码</Button>
                <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleJavaSave} loading={saving} disabled={!javaCodeEdited || !canEdit}>
                  保存
                </Button>
              </div>
            </div>
            <JavaEditor
              height="calc(100% - 44px)"
              value={javaCodeView}
              onChange={(v) => {
                setJavaCodeView(v || '')
                setJavaCodeEdited(true)
              }}
            />
          </div>
        ) : (
        <div className={styles.body}>
          <div className={styles.palette}>
            <div className={styles.paletteSearch}>
              <Input
                size="small"
                placeholder="搜索组件..."
                value={paletteFilter}
                onChange={(e) => setPaletteFilter(e.target.value)}
                allowClear
              />
            </div>
            <div className={styles.paletteSection}>
              <div className={styles.paletteTitle}>📊 指标组件</div>
              {filteredIndicatorCategories.map(cat => (
                <div key={cat} className={styles.paletteCategory}>
                  <div className={styles.categoryLabel}>{cat}</div>
                  <div className={styles.categoryItems}>
                    {filteredIndicatorCatalog.filter(d => d.category === cat).map(d => (
                      <button
                        key={d.type}
                        className={`${styles.paletteItem} ${d.custom ? styles.paletteItemCustom : ''}`}
                        onClick={() => handleAddIndicator(d.type)}
                        draggable
                        onDragStart={(e) => {
                          e.dataTransfer.setData('indicatorType', d.type)
                          e.dataTransfer.setData(`indicatortype_${d.type}`, '')
                          e.dataTransfer.effectAllowed = 'copy'
                        }}
                      >
                        {d.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
            <div className={styles.paletteDivider} />
            <div className={styles.paletteSection}>
              <div className={styles.paletteTitle}>🔀 规则组件</div>
              {filteredRuleCategories.map(cat => (
                <div key={cat} className={styles.paletteCategory}>
                  <div className={styles.categoryLabel}>{cat}</div>
                  <div className={styles.categoryItems}>
                    {filteredRuleCatalog.filter(d => d.category === cat).map(d => (
                      <button
                        key={d.type}
                        className={`${styles.paletteItem} ${d.custom ? styles.paletteItemCustom : ''}`}
                        draggable
                        onDragStart={(e) => {
                          e.dataTransfer.setData('ruleType', d.type)
                          e.dataTransfer.setData(`ruletype_${d.type}`, '')
                          e.dataTransfer.effectAllowed = 'copy'
                        }}
                      >
                        {d.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className={styles.builder}>
            <div
              className={`${styles.section} ${dragOverTarget === 'indicators' ? styles.sectionDragOver : ''}`}
              onDragOver={(e) => {
                const hasIndicator = e.dataTransfer.types.some(t => t.startsWith('indicatortype_'))
                if (hasIndicator) { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; setDragOverTarget('indicators') }
                else { setDragOverTarget(null) }
              }}
              onDragLeave={() => { setDragOverTarget(null) }}
              onDrop={(e) => {
                setDragOverTarget(null)
                const indicatorType = e.dataTransfer.getData('indicatorType')
                if (indicatorType) handleAddIndicator(indicatorType)
              }}
            >
              <div className={styles.sectionHeader}>
                <span className={styles.sectionTitle}>📊 指标定义</span>
                <Dropdown overlay={renderAddMenu(INDICATOR_CATALOG, handleAddIndicator)}>
                  <Button size="small" type="dashed" icon={<PlusOutlined />}>
                    添加指标
                  </Button>
                </Dropdown>
              </div>
              <div className={styles.sectionBody}>
                {config.indicators.length === 0 ? (
                  <Empty description="拖拽左侧指标组件到此处" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  <div className={styles.indicatorGrid}>
                    {config.indicators.map(renderIndicatorCard)}
                  </div>
                )}
              </div>
            </div>

            <div className={styles.rulesRow}>
              <div
                className={`${styles.section} ${dragOverTarget === 'entry' ? styles.sectionDragOver : ''}`}
                onDragOver={(e) => {
                  const ruleTypeKey = e.dataTransfer.types.find(t => t.startsWith('ruletype_'))
                  if (ruleTypeKey) {
                    const ruleType = ruleTypeKey.replace('ruletype_', '')
                    if (isRuleAllowedInSection(ruleType, 'entry')) {
                      e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; setDragOverTarget('entry')
                    } else { setDragOverTarget(null) }
                  } else { setDragOverTarget(null) }
                }}
                onDragLeave={() => { setDragOverTarget(null) }}
                onDrop={(e) => {
                  setDragOverTarget(null)
                  const ruleType = e.dataTransfer.getData('ruleType')
                  if (ruleType && isRuleAllowedInSection(ruleType, 'entry')) handleAddRuleToGroup('entry', [], ruleType)
                }}
              >
                <div className={styles.sectionHeader}>
                  <span className={styles.sectionTitle}>🟢 入场规则配置</span>
                </div>
                <div className={styles.sectionBody}>
                  {config.entryRule.kind === 'group'
                    ? renderGroupCard(config.entryRule, 'entry', [])
                    : renderRuleNode(config.entryRule, 'entry', [])
                  }
                </div>
              </div>

              <div
                className={`${styles.section} ${dragOverTarget === 'exit' ? styles.sectionDragOver : ''}`}
                onDragOver={(e) => {
                  if (e.dataTransfer.types.some(t => t.startsWith('ruletype_'))) {
                    e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; setDragOverTarget('exit')
                  } else { setDragOverTarget(null) }
                }}
                onDragLeave={() => { setDragOverTarget(null) }}
                onDrop={(e) => {
                  setDragOverTarget(null)
                  const ruleType = e.dataTransfer.getData('ruleType')
                  if (ruleType) handleAddRuleToGroup('exit', [], ruleType)
                }}
              >
                <div className={styles.sectionHeader}>
                  <span className={styles.sectionTitle}>🔴 出场规则配置</span>
                </div>
                <div className={styles.sectionBody}>
                  {config.exitRule.kind === 'group'
                    ? renderGroupCard(config.exitRule, 'exit', [])
                    : renderRuleNode(config.exitRule, 'exit', [])
                  }
                </div>
              </div>
            </div>
          </div>
        </div>
        )}
        </>
        )}
      </div>

      <Modal
        title="新建策略"
        open={createModalOpen}
        onOk={handleCreate}
        onCancel={() => { setCreateModalOpen(false); setNewName('') }}
        okText="创建"
        cancelText="取消"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
          <Input
            placeholder="策略名称"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
        </div>
      </Modal>

      <Modal
        title={<span><BulbOutlined style={{ marginRight: 8, color: '#1677ff' }} />AI智能创建策略 {aiGenerating && aiRetryCount > 0 && <span style={{ color: '#faad14', fontSize: 12 }}>(重试 {aiRetryCount}/{aiMaxRetries})</span>}</span>}
        open={aiModalOpen}
        maskClosable={false}
        keyboard={false}
        onCancel={() => {
          aiAbortRef.current?.abort()
          aiAbortRef.current = null
          setAiModalOpen(false)
          setAiGenerating(false)
          setAiBuyDesc('')
          setAiSellDesc('')
          setAiResult(null)
          setAiStrategyName('')
          setAiCode('')
          setAiThinking('')
          setAiRetryCount(0)
        }}
        width={800}
        footer={aiResult ? [
          <Button key="back" onClick={() => { setAiResult(null); setAiCode(''); setAiThinking('') }}>重新生成</Button>,
          <Button key="import" type="primary" icon={<ThunderboltOutlined />} onClick={handleAiImport} loading={aiImporting} disabled={!aiStrategyName.trim() || !aiCode.trim()}>
            导入策略
          </Button>,
        ] : [
          <Button key="cancel" onClick={() => {
            if (aiGenerating) {
              aiAbortRef.current?.abort()
              aiAbortRef.current = null
              setAiGenerating(false)
              setAiRetryCount(0)
              message.info('已取消AI生成')
            } else {
              setAiModalOpen(false)
            }
          }}>{aiGenerating ? '取消生成' : '关闭'}</Button>,
          <Button key="generate" type="primary" icon={<ThunderboltOutlined />} onClick={handleAiGenerate} loading={aiGenerating} disabled={!aiBuyDesc.trim() || !aiSellDesc.trim()}>
            {aiGenerating ? '生成中...' : 'AI生成'}
          </Button>,
        ]}
      >
        {!aiResult ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 8 }}>
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500, color: '#52c41a' }}>买入策略描述</div>
              <Input.TextArea
                rows={3}
                placeholder="例如：RSI低于30时买入，且收盘价在20日均线上方"
                value={aiBuyDesc}
                onChange={(e) => setAiBuyDesc(e.target.value)}
                disabled={aiGenerating}
              />
            </div>
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500, color: '#ff4d4f' }}>卖出策略描述</div>
              <Input.TextArea
                rows={3}
                placeholder="例如：RSI高于70时卖出，或止损5%"
                value={aiSellDesc}
                onChange={(e) => setAiSellDesc(e.target.value)}
                disabled={aiGenerating}
              />
            </div>
            {aiGenerating && (
              <div ref={aiThinkingRef} style={{
                background: '#f6f8fa',
                border: '1px solid #e8e8e8',
                borderRadius: 8,
                padding: 12,
                maxHeight: 200,
                overflow: 'auto',
                fontSize: 12,
                lineHeight: 1.6,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                fontFamily: 'monospace',
              }}>
                <div style={{ marginBottom: 4, fontWeight: 600, color: '#1677ff', fontSize: 12 }}>
                  💭 AI思考中... {aiRetryCount > 0 && <span style={{ color: '#faad14' }}>(第{aiRetryCount}次重试)</span>}
                </div>
                {aiThinking || <span style={{ color: '#999' }}>等待AI响应...</span>}
              </div>
            )}
            {!aiGenerating && (
              <div style={{ color: '#999', fontSize: 12 }}>
                提示：请尽量具体描述指标和参数，AI将自动生成Java策略代码。编译失败时会自动重试最多{aiMaxRetries}次。
              </div>
            )}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 8 }}>
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500 }}>策略名称</div>
              <Input
                value={aiStrategyName}
                onChange={(e) => setAiStrategyName(e.target.value)}
                placeholder="输入策略名称"
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontWeight: 500 }}>编译状态:</span>
              {aiResult.valid ? (
                <span style={{ color: '#52c41a' }}><CheckCircleOutlined /> 编译通过</span>
              ) : (
                <span style={{ color: '#ff4d4f' }}><CloseCircleOutlined /> 编译未通过</span>
              )}
            </div>
            {aiResult.compileError && (
              <div style={{ background: '#fff2f0', border: '1px solid #ffccc7', borderRadius: 6, padding: 8, fontSize: 12, color: '#cf1322', maxHeight: 80, overflow: 'auto' }}>
                {aiResult.compileError}
              </div>
            )}
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500 }}>生成的Java代码（可编辑）</div>
              <JavaEditor
                height="320px"
                value={aiCode}
                onChange={(v) => setAiCode(v || '')}
              />
            </div>
          </div>
        )}
      </Modal>

      <Modal
        title={<span><ImportOutlined style={{ marginRight: 8, color: '#52c41a' }} />代码导入策略</span>}
        open={codeImportModalOpen}
        onCancel={() => {
          setCodeImportModalOpen(false)
          setCodeImportName('')
          setCodeImportCode('')
          setCodeImportResult(null)
        }}
        width={800}
        footer={[
          <Button key="cancel" onClick={() => {
            setCodeImportModalOpen(false)
            setCodeImportName('')
            setCodeImportCode('')
            setCodeImportResult(null)
          }}>取消</Button>,
          <Button key="validate" icon={<CheckCircleOutlined />} onClick={handleCodeValidate} loading={codeImportValidating} disabled={!codeImportCode.trim()}>
            验证代码
          </Button>,
          <Button key="import" type="primary" icon={<ImportOutlined />} onClick={handleCodeImport} disabled={!codeImportName.trim() || !codeImportResult?.valid}>
            确认导入
          </Button>,
        ]}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 8 }}>
          <div>
            <div style={{ marginBottom: 6, fontWeight: 500 }}>策略名称</div>
            <Input
              value={codeImportName}
              onChange={(e) => setCodeImportName(e.target.value)}
              placeholder="输入策略名称"
            />
          </div>
          {codeImportResult && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontWeight: 500 }}>编译状态:</span>
              {codeImportResult.valid ? (
                <span style={{ color: '#52c41a' }}><CheckCircleOutlined /> 编译通过</span>
              ) : (
                <span style={{ color: '#ff4d4f' }}><CloseCircleOutlined /> 编译未通过</span>
              )}
            </div>
          )}
          {codeImportResult && !codeImportResult.valid && codeImportResult.compileError && (
            <div style={{ background: '#fff2f0', border: '1px solid #ffccc7', borderRadius: 6, padding: 8, fontSize: 12, color: '#cf1322', maxHeight: 80, overflow: 'auto' }}>
              {codeImportResult.compileError}
            </div>
          )}
          <div>
            <div style={{ marginBottom: 6, fontWeight: 500 }}>Java策略代码</div>
            <JavaEditor
              height="360px"
              value={codeImportCode}
              onChange={(v) => { setCodeImportCode(v || ''); setCodeImportResult(null) }}
            />
          </div>
          <div style={{ color: '#999', fontSize: 12 }}>
            提示：代码需包含 public class 声明，并提供以下任一入口：buildStrategy(BarSeries) 方法 或 继承 BaseStrategy 的 BarSeries 构造函数
          </div>
        </div>
      </Modal>

      <Modal
        title="生成的Java代码"
        open={codeModalOpen}
        onCancel={() => setCodeModalOpen(false)}
        width={1100}
        styles={{ body: { height: 520, padding: 0 } }}
        footer={[
          <Button key="close" onClick={() => setCodeModalOpen(false)}>关闭</Button>,
          <Button key="copy" type="primary" onClick={() => {
            navigator.clipboard.writeText(generatedCode)
            message.success('代码已复制到剪贴板')
          }}>复制代码</Button>,
        ]}
      >
        <JavaEditor
          height="100%"
          value={generatedCode}
          readOnly
        />
      </Modal>
    </div>
  )
}

function Dropdown({ overlay, children }: { overlay: React.ReactNode; children: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<DOMRect | null>(null)
  const [dropUp, setDropUp] = useState(false)
  const [maxHeight, setMaxHeight] = useState(400)
  const triggerRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (triggerRef.current?.contains(e.target as Node)) return
      if (menuRef.current?.contains(e.target as Node)) return
      setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const handleToggle = () => {
    if (!open && triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect()
      setPos(rect)
      const spaceBelow = window.innerHeight - rect.bottom - 8
      const spaceAbove = rect.top - 8
      if (spaceBelow < 200 && spaceAbove > spaceBelow) {
        setDropUp(true)
        setMaxHeight(Math.min(400, spaceAbove))
      } else {
        setDropUp(false)
        setMaxHeight(Math.min(400, spaceBelow))
      }
    }
    setOpen(!open)
  }

  return (
    <>
      <div ref={triggerRef} onClick={handleToggle}>{children}</div>
      {open && pos && createPortal(
        <div
          ref={menuRef}
          className={styles.dropdownContentFixed}
          style={{
            position: 'fixed',
            top: dropUp ? undefined : pos.bottom + 4,
            bottom: dropUp ? window.innerHeight - pos.top + 4 : undefined,
            left: Math.max(8, pos.left),
            zIndex: 1050,
            maxHeight,
          }}
          onClick={() => setOpen(false)}
        >
          {overlay}
        </div>,
        document.body
      )}
    </>
  )
}

export default VisualStrategyPage
