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
  '浠锋牸鎸囨爣': '馃挵',
  '鍧囩嚎鎸囨爣': '馃搱',
  '鎸崱鎸囨爣': '馃搳',
  '缁熻鎸囨爣': '馃搲',
  '杈呭姪鎸囨爣': '馃敡',
  'K绾垮舰鎬?: '馃暞锔?,
  '鎴愪氦閲忔寚鏍?: '馃摝',
  'Ichimoku': '鈽侊笍',
  '甯冩灄甯?: '馃幆',
  '鎸囨爣杩愮畻': '馃М',
  '姣旇緝瑙勫垯': '馃攢',
  '椋庢帶瑙勫垯': '馃洝锔?,
  '瓒嬪娍瑙勫垯': '馃搻',
  '鑼冨洿瑙勫垯': '馃搹',
  '鍏朵粬瑙勫垯': '鈿欙笍',
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
      message.warning('璇疯緭鍏ョ瓥鐣ュ悕绉?)
      return
    }
    setSaving(true)
    try {
      const code = JSON.stringify(config)
      if (strategyId) {
        const updated = await updateStrategy(strategyId, { name: strategyName.trim(), code })
        if (updated.valid) {
          message.success('淇濆瓨鎴愬姛锛岀瓥鐣ラ厤缃湁鏁?)
        } else {
          message.warning('淇濆瓨鎴愬姛锛屼絾绛栫暐閰嶇疆鏃犳晥: ' + (updated.compile_error || ''))
        }
      } else {
        const s = await createStrategy({
          name: strategyName.trim(),
          language: 'visual',
          code,
        })
        setStrategyId(s.id!)
        if (s.valid) {
          message.success('鍒涘缓鎴愬姛锛岀瓥鐣ラ厤缃湁鏁?)
        } else {
          message.warning('鍒涘缓鎴愬姛锛屼絾绛栫暐閰嶇疆鏃犳晥: ' + (s.compile_error || ''))
        }
      }
      onStrategyChanged?.()
      invalidateStrategies()
    } catch {
      message.error('淇濆瓨澶辫触')
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
        message.success('淇濆瓨鎴愬姛锛屼唬鐮佺紪璇戦€氳繃')
        setJavaCodeEdited(false)
      } else {
        message.warning('宸蹭繚瀛橈紝浣嗕唬鐮佺紪璇戞湭閫氳繃: ' + (updated.compile_error || ''))
        setJavaCodeEdited(false)
      }
      onStrategyChanged?.()
      invalidateStrategies()
    } catch {
      message.error('淇濆瓨澶辫触')
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
        message.info('宸插姞杞絁ava浠ｇ爜绛栫暐')
        return
      }
      setJavaCodeView('')
      const raw = JSON.parse(s.code)
      const parsed = migrateConfig(raw)
      setConfig(parsed)
      setStrategyId(s.id!)
      setStrategyName(s.name)
      message.success('鍔犺浇鎴愬姛')
    } catch {
      message.error('鍔犺浇绛栫暐澶辫触')
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
      message.warning('璇疯緭鍏ョ瓥鐣ュ悕绉?)
      return
    }
    try {
      const code = JSON.stringify(createDefaultConfig())
      const s = await createStrategy({
        name: newName.trim(),
        language: 'visual',
        code,
      })
      message.success('鍒涘缓鎴愬姛')
      setCreateModalOpen(false)
      setNewName('')
      onStrategyChanged?.()
      invalidateStrategies()
      handleLoad(s.id!)
    } catch {
      message.error('鍒涘缓澶辫触')
    }
  }, [newName, message, invalidateStrategies, handleLoad])

  useEffect(() => {
    if (aiThinkingRef.current) {
      aiThinkingRef.current.scrollTop = aiThinkingRef.current.scrollHeight
    }
  }, [aiThinking])

  const handleAiGenerate = useCallback(() => {
    if (!aiBuyDesc.trim() || !aiSellDesc.trim()) {
      message.warning('璇疯緭鍏ヤ拱鍏ュ拰鍗栧嚭绛栫暐鎻忚堪')
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
          message.success('AI鐢熸垚鎴愬姛锛屼唬鐮佺紪璇戦€氳繃')
        } else {
          message.warning('AI鐢熸垚瀹屾垚锛屼絾浠ｇ爜缂栬瘧鏈€氳繃锛岃妫€鏌ユ垨鎵嬪姩淇敼')
        }
      },
      (msg) => {
        if (typingTimerRef.current) {
          clearInterval(typingTimerRef.current)
          typingTimerRef.current = null
        }
        typingBufferRef.current = ''
        message.error('AI鐢熸垚澶辫触: ' + msg)
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
      message.warning('璇疯緭鍏ョ瓥鐣ュ悕绉?)
      return
    }
    if (!aiCode.trim()) {
      message.warning('娌℃湁鍙鍏ョ殑浠ｇ爜')
      return
    }
    setAiImporting(true)
    try {
      const description = `涔板叆绛栫暐: ${aiBuyDesc.trim()}\n鍗栧嚭绛栫暐: ${aiSellDesc.trim()}`
      const s = await createStrategy({
        name: aiStrategyName.trim(),
        language: 'java',
        code: aiCode.trim(),
        description,
      })
      message.success('绛栫暐瀵煎叆鎴愬姛')
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
      message.error('绛栫暐瀵煎叆澶辫触')
    } finally {
      setAiImporting(false)
    }
  }, [aiStrategyName, aiCode, message, invalidateStrategies, handleLoad])

  const handleCodeValidate = useCallback(async () => {
    if (!codeImportCode.trim()) {
      message.warning('璇疯緭鍏ava浠ｇ爜')
      return
    }
    setCodeImportValidating(true)
    setCodeImportResult(null)
    try {
      const result = await validateCode(codeImportCode.trim())
      setCodeImportResult(result)
      if (result.valid) {
        message.success('浠ｇ爜鏍￠獙閫氳繃锛屽彲浠ュ鍏?)
      } else {
        message.warning('浠ｇ爜鏍￠獙鏈€氳繃锛岃淇敼鍚庨噸鏂伴獙璇?)
      }
    } catch {
      message.error('浠ｇ爜鏍￠獙澶辫触')
    } finally {
      setCodeImportValidating(false)
    }
  }, [codeImportCode, message])

  const handleCodeImport = useCallback(async () => {
    if (!codeImportName.trim()) {
      message.warning('璇疯緭鍏ョ瓥鐣ュ悕绉?)
      return
    }
    if (!codeImportResult?.valid) {
      message.warning('璇峰厛楠岃瘉浠ｇ爜锛岀‘淇濈紪璇戦€氳繃鍚庡啀瀵煎叆')
      return
    }
    setCodeImportValidating(true)
    try {
      await createStrategy({
        name: codeImportName.trim(),
        language: 'java',
        code: codeImportCode.trim(),
      })
      message.success('浠ｇ爜瀵煎叆鎴愬姛锛岀瓥鐣ュ凡鍒涘缓')
      setCodeImportModalOpen(false)
      setCodeImportName('')
      setCodeImportCode('')
      setCodeImportResult(null)
      onStrategyChanged?.()
      invalidateStrategies()
    } catch {
      message.error('瀵煎叆澶辫触')
    } finally {
      setCodeImportValidating(false)
    }
  }, [codeImportName, codeImportCode, codeImportResult, message, invalidateStrategies])

  const handleRename = useCallback(async (id: number) => {
    const s = strategyList.find((s) => s.id === id)
    if (!s) return
    let renameValue = ''
    Modal.confirm({
      title: '閲嶅懡鍚嶇瓥鐣?,
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
                <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-secondary, #666)' }}>AI鐢熸垚鎻忚堪</span>
                <Button
                  type="link"
                  size="small"
                  icon={<CopyOutlined />}
                  style={{ padding: 0, height: 'auto', fontSize: 12 }}
                  onClick={() => {
                    navigator.clipboard.writeText(s.description!)
                    message.success('鎻忚堪宸插鍒跺埌鍓创鏉?)
                  }}
                >
                  澶嶅埗
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
      okText: '纭畾',
      cancelText: '鍙栨秷',
      onOk: async () => {
        if (!renameValue.trim()) return
        try {
          await updateStrategy(id, { name: renameValue.trim() })
          message.success('閲嶅懡鍚嶆垚鍔?)
          onStrategyChanged?.()
          invalidateStrategies()
          if (strategyId === id) {
            setStrategyName(renameValue.trim())
          }
        } catch {
          message.error('閲嶅懡鍚嶅け璐?)
        }
      },
    })
  }, [strategyList, strategyId, message, invalidateStrategies])

  const handleDelete = useCallback(async (id: number) => {
    modal.confirm({
      title: '纭鍒犻櫎',
      content: '鍒犻櫎鍚庢棤娉曟仮澶嶏紝纭畾瑕佸垹闄よ绛栫暐鍚楋紵',
      okText: '鍒犻櫎',
      okType: 'danger',
      cancelText: '鍙栨秷',
      onOk: async () => {
        try {
          await deleteStrategy(id)
          message.success('鍒犻櫎鎴愬姛')
          if (strategyId === id) {
            handleNew()
          }
          onStrategyChanged?.()
          invalidateStrategies()
        } catch {
          message.error('鍒犻櫎澶辫触')
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
          <span className={styles.cardType}>{CATEGORY_ICONS[def.category] || '馃搳'} {def.label}</span>
          <Tooltip title="鍒犻櫎鎸囨爣">
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
                placeholder="閫夋嫨鎸囨爣"
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
            {CATEGORY_ICONS[def.category] || '馃攢'} {def.label}
          </span>
          <div className={styles.cardActions}>
            <Tooltip title={leaf.negated ? '鍙栨秷鍙栧弽' : '鍙栧弽(NOT)'}>
              <button
                className={`${styles.cardActionBtn} ${leaf.negated ? styles.cardActionBtnActive : ''}`}
                onClick={() => handleToggleNegate(section, path)}
              >
                <SwapOutlined />
              </button>
            </Tooltip>
            <Tooltip title="鍒犻櫎鏉′欢">
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
                placeholder="閫夋嫨鎸囨爣"
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
              <Tooltip title={group.negated ? '鍙栨秷鍙栧弽' : '鍙栧弽(NOT)'}>
                <button
                  className={`${styles.cardActionBtn} ${group.negated ? styles.cardActionBtnActive : ''}`}
                  onClick={() => handleToggleNegate(section, path)}
                >
                  <SwapOutlined />
                </button>
              </Tooltip>
              <Tooltip title="鍒犻櫎鍒嗙粍">
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
            <Empty description="鎷栨嫿宸︿晶瑙勫垯缁勪欢鍒版澶? image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            group.children.map((child, idx) => renderRuleNode(child, section, [...path, idx]))
          )}
        </div>
        <div className={styles.ruleGroupFooter}>
          <Dropdown overlay={renderAddMenu(RULE_CATALOG.filter(d => isRuleAllowedInSection(d.type, section)), (type) => handleAddRuleToGroup(section, path, type))}>
            <Button size="small" type="dashed" icon={<PlusOutlined />}>
              娣诲姞瑙勫垯
            </Button>
          </Dropdown>
          <Button size="small" type="dashed" onClick={() => handleAddSubGroup(section, path)}>
            + 瀛愬垎缁?          </Button>
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
          <span className={styles.addMenuIcon}>{CATEGORY_ICONS[item.category] || '馃搶'}</span>
          <span className={styles.addMenuLabel}>{item.label}</span>
        </button>
      ))}
    </div>
  )

  return (
    <div className={styles.container}>
      <div className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <span className={styles.sidebarTitle}>绛栫暐鍒楄〃</span>
          <div className={styles.sidebarActions}>
            <button className={styles.aiBtn} onClick={() => setAiModalOpen(true)}>
              <BulbOutlined /> AI鐢熸垚
            </button>
            <Tooltip title="瀵煎叆Java绛栫暐">
              <button className={styles.codeImportBtn} onClick={() => setCodeImportModalOpen(true)}>
                <ImportOutlined />
              </button>
            </Tooltip>
            <Tooltip title="鏂板缓绛栫暐">
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
              <Tooltip title={s.valid === false ? `鏃犳晥: ${s.compile_error || '缂栬瘧澶辫触'}` : '鏈夋晥'} placement="right">
                <span className={s.valid === false ? styles.statusInvalid : styles.statusValid}>
                  {s.valid === false ? <CloseCircleOutlined /> : <CheckCircleOutlined />}
                </span>
              </Tooltip>
              <span className={styles.strategyName}>{s.name}</span>
              {s.language === 'java' && <span className={styles.javaTag}>Java</span>}
              {s.created_by_role === 'root' && s.created_by !== user?.username && <span className={styles.javaTag} style={{ background: '#722ed1', color: '#fff' }}>鍏变韩</span>}
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
                  title="閲嶅懡鍚?
                >
                  鉁?                </button>
                <button
                  className={styles.actionBtn}
                  onClick={(e) => { e.stopPropagation(); handleDelete(s.id!) }}
                  title="鍒犻櫎"
                >
                  <DeleteOutlined />
                </button>
              </div>
              )}
            </div>
          ))}
          {strategyList.length === 0 && (
            <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: 13, padding: 20 }}>
              鏆傛棤绛栫暐锛岀偣鍑?+ 鏂板缓
            </div>
          )}
          </>
          )}
        </div>
      </div>

      <div className={styles.mainArea}>
        {loadingStrategy ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
            <Spin indicator={<LoadingOutlined style={{ fontSize: 32 }} spin />} tip="鍔犺浇绛栫暐涓?.." />
          </div>
        ) : (
        <>
        {!javaCodeView && (
        <div className={styles.header}>
          <div className={styles.headerLeft}>
            <span className={styles.headerTitle}>鍙鍖栫瓥鐣ョ紪杈戝櫒</span>
            {strategyName && <span className={styles.headerStrategyName}>{strategyName}</span>}
          </div>
          <div className={styles.headerRight}>
            <Button size="small" icon={<EyeOutlined />} onClick={() => setCodeModalOpen(true)} disabled={!strategyName.trim()}>
              鏌ョ湅浠ｇ爜
            </Button>
            <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleSave} loading={saving} disabled={!strategyName.trim() || !canEdit}>
              淇濆瓨
            </Button>
          </div>
        </div>
        )}

        {!strategyName.trim() ? (
          <div className={styles.builderDisabled}>
            <Empty description="璇峰厛浠庡乏渚ч€夋嫨鎴栨柊寤轰竴涓瓥鐣? image={Empty.PRESENTED_IMAGE_SIMPLE} />
          </div>
        ) : javaCodeView ? (
          <div className={styles.javaCodeViewArea}>
            <div className={styles.javaCodeViewHeader}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ color: '#fa8c16', fontWeight: 500 }}>Java 浠ｇ爜绛栫暐</span>
                {strategyName && <span className={styles.headerStrategyName}>{strategyName}</span>}
                {javaCodeEdited && <span style={{ color: '#faad14', fontSize: 12 }}>* 鏈繚瀛?/span>}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Button size="small" onClick={() => {
                  navigator.clipboard.writeText(javaCodeView)
                  message.success('浠ｇ爜宸插鍒跺埌鍓创鏉?)
                }}>澶嶅埗浠ｇ爜</Button>
                <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleJavaSave} loading={saving} disabled={!javaCodeEdited || !canEdit}>
                  淇濆瓨
                </Button>
              </div>
            </div>
            <JavaEditor
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
                placeholder="鎼滅储缁勪欢..."
                value={paletteFilter}
                onChange={(e) => setPaletteFilter(e.target.value)}
                allowClear
              />
            </div>
            <div className={styles.paletteSection}>
              <div className={styles.paletteTitle}>馃搳 鎸囨爣缁勪欢</div>
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
              <div className={styles.paletteTitle}>馃攢 瑙勫垯缁勪欢</div>
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
                <span className={styles.sectionTitle}>馃搳 鎸囨爣瀹氫箟</span>
                <Dropdown overlay={renderAddMenu(INDICATOR_CATALOG, handleAddIndicator)}>
                  <Button size="small" type="dashed" icon={<PlusOutlined />}>
                    娣诲姞鎸囨爣
                  </Button>
                </Dropdown>
              </div>
              <div className={styles.sectionBody}>
                {config.indicators.length === 0 ? (
                  <Empty description="鎷栨嫿宸︿晶鎸囨爣缁勪欢鍒版澶? image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
                  <span className={styles.sectionTitle}>馃煝 鍏ュ満瑙勫垯閰嶇疆</span>
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
                  <span className={styles.sectionTitle}>馃敶 鍑哄満瑙勫垯閰嶇疆</span>
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
        title="鏂板缓绛栫暐"
        open={createModalOpen}
        onOk={handleCreate}
        onCancel={() => { setCreateModalOpen(false); setNewName('') }}
        okText="鍒涘缓"
        cancelText="鍙栨秷"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
          <Input
            placeholder="绛栫暐鍚嶇О"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
        </div>
      </Modal>

      <Modal
        title={<span><BulbOutlined style={{ marginRight: 8, color: '#1677ff' }} />AI鏅鸿兘鍒涘缓绛栫暐 {aiGenerating && aiRetryCount > 0 && <span style={{ color: '#faad14', fontSize: 12 }}>(閲嶈瘯 {aiRetryCount}/{aiMaxRetries})</span>}</span>}
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
          <Button key="back" onClick={() => { setAiResult(null); setAiCode(''); setAiThinking('') }}>閲嶆柊鐢熸垚</Button>,
          <Button key="import" type="primary" icon={<ThunderboltOutlined />} onClick={handleAiImport} loading={aiImporting} disabled={!aiStrategyName.trim() || !aiCode.trim()}>
            瀵煎叆绛栫暐
          </Button>,
        ] : [
          <Button key="cancel" onClick={() => {
            if (aiGenerating) {
              aiAbortRef.current?.abort()
              aiAbortRef.current = null
              setAiGenerating(false)
              setAiRetryCount(0)
              message.info('宸插彇娑圓I鐢熸垚')
            } else {
              setAiModalOpen(false)
            }
          }}>{aiGenerating ? '鍙栨秷鐢熸垚' : '鍏抽棴'}</Button>,
          <Button key="generate" type="primary" icon={<ThunderboltOutlined />} onClick={handleAiGenerate} loading={aiGenerating} disabled={!aiBuyDesc.trim() || !aiSellDesc.trim()}>
            {aiGenerating ? '鐢熸垚涓?..' : 'AI鐢熸垚'}
          </Button>,
        ]}
      >
        {!aiResult ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 8 }}>
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500, color: '#52c41a' }}>涔板叆绛栫暐鎻忚堪</div>
              <Input.TextArea
                rows={3}
                placeholder="渚嬪锛歊SI浣庝簬30鏃朵拱鍏ワ紝涓旀敹鐩樹环鍦?0鏃ュ潎绾夸笂鏂?
                value={aiBuyDesc}
                onChange={(e) => setAiBuyDesc(e.target.value)}
                disabled={aiGenerating}
              />
            </div>
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500, color: '#ff4d4f' }}>鍗栧嚭绛栫暐鎻忚堪</div>
              <Input.TextArea
                rows={3}
                placeholder="渚嬪锛歊SI楂樹簬70鏃跺崠鍑猴紝鎴栨鎹?%"
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
                  馃挱 AI鎬濊€冧腑... {aiRetryCount > 0 && <span style={{ color: '#faad14' }}>(绗瑊aiRetryCount}娆￠噸璇?</span>}
                </div>
                {aiThinking || <span style={{ color: '#999' }}>绛夊緟AI鍝嶅簲...</span>}
              </div>
            )}
            {!aiGenerating && (
              <div style={{ color: '#999', fontSize: 12 }}>
                鎻愮ず锛氳灏介噺鍏蜂綋鎻忚堪鎸囨爣鍜屽弬鏁帮紝AI灏嗚嚜鍔ㄧ敓鎴怞ava绛栫暐浠ｇ爜銆傜紪璇戝け璐ユ椂浼氳嚜鍔ㄩ噸璇曟渶澶歿aiMaxRetries}娆°€?              </div>
            )}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 8 }}>
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500 }}>绛栫暐鍚嶇О</div>
              <Input
                value={aiStrategyName}
                onChange={(e) => setAiStrategyName(e.target.value)}
                placeholder="杈撳叆绛栫暐鍚嶇О"
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontWeight: 500 }}>缂栬瘧鐘舵€?</span>
              {aiResult.valid ? (
                <span style={{ color: '#52c41a' }}><CheckCircleOutlined /> 缂栬瘧閫氳繃</span>
              ) : (
                <span style={{ color: '#ff4d4f' }}><CloseCircleOutlined /> 缂栬瘧鏈€氳繃</span>
              )}
            </div>
            {aiResult.compileError && (
              <div style={{ background: '#fff2f0', border: '1px solid #ffccc7', borderRadius: 6, padding: 8, fontSize: 12, color: '#cf1322', maxHeight: 80, overflow: 'auto' }}>
                {aiResult.compileError}
              </div>
            )}
            <div>
              <div style={{ marginBottom: 6, fontWeight: 500 }}>鐢熸垚鐨凧ava浠ｇ爜锛堝彲缂栬緫锛?/div>
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
        title={<span><ImportOutlined style={{ marginRight: 8, color: '#52c41a' }} />浠ｇ爜瀵煎叆绛栫暐</span>}
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
          }}>鍙栨秷</Button>,
          <Button key="validate" icon={<CheckCircleOutlined />} onClick={handleCodeValidate} loading={codeImportValidating} disabled={!codeImportCode.trim()}>
            楠岃瘉浠ｇ爜
          </Button>,
          <Button key="import" type="primary" icon={<ImportOutlined />} onClick={handleCodeImport} disabled={!codeImportName.trim() || !codeImportResult?.valid}>
            纭瀵煎叆
          </Button>,
        ]}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 8 }}>
          <div>
            <div style={{ marginBottom: 6, fontWeight: 500 }}>绛栫暐鍚嶇О</div>
            <Input
              value={codeImportName}
              onChange={(e) => setCodeImportName(e.target.value)}
              placeholder="杈撳叆绛栫暐鍚嶇О"
            />
          </div>
          {codeImportResult && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontWeight: 500 }}>缂栬瘧鐘舵€?</span>
              {codeImportResult.valid ? (
                <span style={{ color: '#52c41a' }}><CheckCircleOutlined /> 缂栬瘧閫氳繃</span>
              ) : (
                <span style={{ color: '#ff4d4f' }}><CloseCircleOutlined /> 缂栬瘧鏈€氳繃</span>
              )}
            </div>
          )}
          {codeImportResult && !codeImportResult.valid && codeImportResult.compileError && (
            <div style={{ background: '#fff2f0', border: '1px solid #ffccc7', borderRadius: 6, padding: 8, fontSize: 12, color: '#cf1322', maxHeight: 80, overflow: 'auto' }}>
              {codeImportResult.compileError}
            </div>
          )}
          <div>
            <div style={{ marginBottom: 6, fontWeight: 500 }}>Java绛栫暐浠ｇ爜</div>
            <JavaEditor
              height="360px"
              value={codeImportCode}
              onChange={(v) => { setCodeImportCode(v || ''); setCodeImportResult(null) }}
            />
          </div>
          <div style={{ color: '#999', fontSize: 12 }}>
            鎻愮ず锛氫唬鐮侀渶鍖呭惈 public class 澹版槑锛屽苟鎻愪緵浠ヤ笅浠讳竴鍏ュ彛锛歜uildStrategy(BarSeries) 鏂规硶 鎴?缁ф壙 BaseStrategy 鐨?BarSeries 鏋勯€犲嚱鏁?          </div>
        </div>
      </Modal>

      <Modal
        title="鐢熸垚鐨凧ava浠ｇ爜"
        open={codeModalOpen}
        onCancel={() => setCodeModalOpen(false)}
        width={1100}
        styles={{ body: { height: 520, padding: 0, overflow: 'hidden' } }}
        footer={[
          <Button key="close" onClick={() => setCodeModalOpen(false)}>鍏抽棴</Button>,
          <Button key="copy" type="primary" onClick={() => {
            navigator.clipboard.writeText(generatedCode)
            message.success('浠ｇ爜宸插鍒跺埌鍓创鏉?)
          }}>澶嶅埗浠ｇ爜</Button>,
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
