export interface VisualStrategyConfig {
  version: number
  indicators: IndicatorNode[]
  entryRule: RuleNode
  exitRule: RuleNode
}

export interface IndicatorNode {
  id: string
  type: string
  params: Record<string, number>
  inputs: string[]
}

export type RuleNode = LeafRuleNode | GroupRuleNode

export interface LeafRuleNode {
  id: string
  kind: 'leaf'
  type: string
  params: Record<string, number>
  indicatorInputs: string[]
  negated?: boolean
}

export interface GroupRuleNode {
  id: string
  kind: 'group'
  combinator: 'AND' | 'OR'
  children: RuleNode[]
  negated?: boolean
}

export interface ConditionNode {
  id: string
  type: string
  params: Record<string, number>
  indicatorInputs: string[]
}

export interface ParamDef {
  name: string
  label: string
  type?: 'number'
  default: number
  min?: number
  max?: number
  step?: number
}

export interface IndicatorDef {
  type: string
  label: string
  category: string
  params: ParamDef[]
  inputCount: number
  inputLabels: string[]
  needsBarSeries: boolean
  importPath: string
  custom?: boolean
}

export interface RuleDef {
  type: string
  label: string
  category: string
  params: ParamDef[]
  indicatorInputCount: number
  indicatorInputLabels: string[]
  importPath: string
  exitOnly?: boolean
  custom?: boolean
}

export const INDICATOR_CATALOG: IndicatorDef[] = [
  { type: 'ClosePriceIndicator', label: '收盘价', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.ClosePriceIndicator' },
  { type: 'OpenPriceIndicator', label: '开盘价', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.OpenPriceIndicator' },
  { type: 'HighPriceIndicator', label: '最高价', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.HighPriceIndicator' },
  { type: 'LowPriceIndicator', label: '最低价', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.LowPriceIndicator' },
  { type: 'VolumeIndicator', label: '成交量', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.VolumeIndicator' },
  { type: 'TypicalPriceIndicator', label: '典型价格', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.TypicalPriceIndicator' },
  { type: 'MedianPriceIndicator', label: '中间价格', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.MedianPriceIndicator' },
  { type: 'TRIndicator', label: '真实波幅', category: '价格指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.TRIndicator' },

  { type: 'SMAIndicator', label: 'SMA简单移动平均', category: '均线指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.averages.SMAIndicator' },
  { type: 'EMAIndicator', label: 'EMA指数移动平均', category: '均线指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.averages.EMAIndicator' },
  { type: 'WMAIndicator', label: 'WMA加权移动平均', category: '均线指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.averages.WMAIndicator' },
  { type: 'LWMAIndicator', label: 'LWMA线性加权移动平均', category: '均线指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.averages.LWMAIndicator' },

  { type: 'RSIIndicator', label: 'RSI相对强弱', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 14, min: 2, max: 100 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.RSIIndicator' },
  { type: 'MACDIndicator', label: 'MACD', category: '振荡指标', params: [{ name: 'shortBarCount', label: '短周期', default: 12, min: 2, max: 100 }, { name: 'longBarCount', label: '长周期', default: 26, min: 2, max: 200 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.MACDIndicator' },
  { type: 'ROCIndicator', label: 'ROC变动率', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 12, min: 2, max: 100 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.ROCIndicator' },
  { type: 'PPOIndicator', label: 'PPO价格震荡', category: '振荡指标', params: [{ name: 'shortBarCount', label: '短周期', default: 12, min: 2, max: 100 }, { name: 'longBarCount', label: '长周期', default: 26, min: 2, max: 200 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.PPOIndicator' },
  { type: 'CCIIndicator', label: 'CCI商品通道', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.CCIIndicator' },
  { type: 'ATRIndicator', label: 'ATR真实波幅均值', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 14, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ATRIndicator' },
  { type: 'WilliamsRIndicator', label: '威廉指标', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 14, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.WilliamsRIndicator' },
  { type: 'DPOIndicator', label: 'DPO去趋势价格', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.DPOIndicator' },
  { type: 'ChopIndicator', label: 'Chop指数', category: '振荡指标', params: [{ name: 'timeFrame', label: '周期', default: 14, min: 2, max: 100 }, { name: 'scaleUpTo', label: '缩放因子', default: 100, min: 1, max: 1000 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ChopIndicator' },
  { type: 'StochasticOscillatorKIndicator', label: 'KDJ-K值', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 14, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.StochasticOscillatorKIndicator' },
  { type: 'StochasticOscillatorDIndicator', label: 'KDJ-D值', category: '振荡指标', params: [{ name: 'barCount', label: '周期', default: 3, min: 2, max: 100 }], inputCount: 1, inputLabels: ['K值指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.StochasticOscillatorDIndicator' },
  { type: 'ParabolicSarIndicator', label: '抛物线SAR', category: '振荡指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ParabolicSarIndicator' },

  { type: 'StandardDeviationIndicator', label: '标准差', category: '统计指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.statistics.StandardDeviationIndicator' },
  { type: 'VarianceIndicator', label: '方差', category: '统计指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.statistics.VarianceIndicator' },
  { type: 'CovarianceIndicator', label: '协方差', category: '统计指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 2, inputLabels: ['指标A', '指标B'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.statistics.CovarianceIndicator' },
  { type: 'CorrelationCoefficientIndicator', label: '相关系数', category: '统计指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 2, inputLabels: ['指标A', '指标B'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator' },
  { type: 'SimpleLinearRegressionIndicator', label: '线性回归', category: '统计指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 500 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator' },

  { type: 'ConstantIndicator', label: '常量值', category: '辅助指标', params: [{ name: 'value', label: '常量值', default: 0, min: -100000, max: 100000, step: 0.01 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.helpers.ConstantIndicator' },
  { type: 'PreviousValueIndicator', label: '前N期值', category: '辅助指标', params: [{ name: 'n', label: '回看期数', default: 1, min: 1, max: 100 }], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.helpers.PreviousValueIndicator' },

  { type: 'BullishEngulfingIndicator', label: '看涨吞没', category: 'K线形态', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.candles.BullishEngulfingIndicator' },
  { type: 'BearishEngulfingIndicator', label: '看跌吞没', category: 'K线形态', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.candles.BearishEngulfingIndicator' },
  { type: 'HammerIndicator', label: '锤子线', category: 'K线形态', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.candles.HammerIndicator' },
  { type: 'DojiIndicator', label: '十字星', category: 'K线形态', params: [{ name: 'barCount', label: '参考周期', default: 10, min: 2, max: 100 }, { name: 'factor', label: '因子', default: 0.1, min: 0.01, max: 1, step: 0.01 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.candles.DojiIndicator' },
  { type: 'MorningStarIndicator', label: '晨星', category: 'K线形态', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.candles.MorningStarIndicator' },
  { type: 'EveningStarIndicator', label: '暮星', category: 'K线形态', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.candles.EveningStarIndicator' },

  { type: 'OnBalanceVolumeIndicator', label: 'OBV能量潮', category: '成交量指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator' },
  { type: 'ChaikinMoneyFlowIndicator', label: 'CMF蔡金资金流', category: '成交量指标', params: [{ name: 'barCount', label: '周期', default: 20, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator' },
  { type: 'MoneyFlowIndexIndicator', label: 'MFI资金流量', category: '成交量指标', params: [{ name: 'barCount', label: '周期', default: 14, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator' },
  { type: 'AccumulationDistributionIndicator', label: 'AD累积分布', category: '成交量指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.volume.AccumulationDistributionIndicator' },
  { type: 'NVIIndicator', label: 'NVI负量指标', category: '成交量指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.volume.NVIIndicator' },
  { type: 'PVIIndicator', label: 'PVI正量指标', category: '成交量指标', params: [], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.volume.PVIIndicator' },

  { type: 'IchimokuTenkanSenIndicator', label: 'Ichimoku转换线', category: 'Ichimoku', params: [{ name: 'barCount', label: '转换线周期', default: 9, min: 2, max: 100 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator' },
  { type: 'IchimokuKijunSenIndicator', label: 'Ichimoku基准线', category: 'Ichimoku', params: [{ name: 'barCount', label: '基准线周期', default: 26, min: 2, max: 200 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator' },
  { type: 'IchimokuSenkouSpanAIndicator', label: 'Ichimoku先行带A', category: 'Ichimoku', params: [{ name: 'barCountTenkan', label: '转换线周期', default: 9, min: 2, max: 100 }, { name: 'barCountKijun', label: '基准线周期', default: 26, min: 2, max: 200 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator' },
  { type: 'IchimokuSenkouSpanBIndicator', label: 'Ichimoku先行带B', category: 'Ichimoku', params: [{ name: 'barCount', label: '先行带B周期', default: 52, min: 2, max: 200 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator' },
  { type: 'IchimokuChikouSpanIndicator', label: 'Ichimoku延迟线', category: 'Ichimoku', params: [{ name: 'barCount', label: '延迟线周期', default: 26, min: 2, max: 200 }], inputCount: 0, inputLabels: [], needsBarSeries: true, importPath: 'org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator' },

  { type: 'BollingerBandsMiddleIndicator', label: '布林带中轨', category: '布林带', params: [], inputCount: 1, inputLabels: ['源指标'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator' },
  { type: 'BollingerBandsUpperIndicator', label: '布林带上轨', category: '布林带', params: [], inputCount: 1, inputLabels: ['布林带中轨'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator' },
  { type: 'BollingerBandsLowerIndicator', label: '布林带下轨', category: '布林带', params: [], inputCount: 1, inputLabels: ['布林带中轨'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator' },

  { type: 'DifferenceIndicator', label: '指标差(A-B)', category: '指标运算', params: [], inputCount: 2, inputLabels: ['指标A', '指标B'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.helpers.DifferenceIndicator' },
  { type: 'SumIndicator', label: '指标和(A+B)', category: '指标运算', params: [], inputCount: 2, inputLabels: ['指标A', '指标B'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.helpers.SumIndicator' },
  { type: 'CombineIndicatorPlus', label: '指标加法', category: '指标运算', params: [], inputCount: 2, inputLabels: ['指标A', '指标B'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.helpers.CombineIndicator' },
  { type: 'CombineIndicatorMultiply', label: '指标乘法', category: '指标运算', params: [], inputCount: 2, inputLabels: ['指标A', '指标B'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.helpers.CombineIndicator' },
  { type: 'CombineIndicatorDivide', label: '指标除法', category: '指标运算', params: [], inputCount: 2, inputLabels: ['指标A(分子)', '指标B(分母)'], needsBarSeries: false, importPath: 'org.ta4j.core.indicators.helpers.CombineIndicator' },
]

export const RULE_CATALOG: RuleDef[] = [
  { type: 'CrossedUpIndicatorRule', label: '上穿', category: '比较规则', params: [], indicatorInputCount: 2, indicatorInputLabels: ['指标A(上穿方)', '指标B(被穿方)'], importPath: 'org.ta4j.core.rules.CrossedUpIndicatorRule' },
  { type: 'CrossedDownIndicatorRule', label: '下穿', category: '比较规则', params: [], indicatorInputCount: 2, indicatorInputLabels: ['指标A(下穿方)', '指标B(被穿方)'], importPath: 'org.ta4j.core.rules.CrossedDownIndicatorRule' },
  { type: 'OverIndicatorRule', label: '高于', category: '比较规则', params: [], indicatorInputCount: 2, indicatorInputLabels: ['指标A', '指标B(A>B)'], importPath: 'org.ta4j.core.rules.OverIndicatorRule' },
  { type: 'UnderIndicatorRule', label: '低于', category: '比较规则', params: [], indicatorInputCount: 2, indicatorInputLabels: ['指标A', '指标B(A<B)'], importPath: 'org.ta4j.core.rules.UnderIndicatorRule' },

  { type: 'StopLossRule', label: '止损', category: '风控规则', params: [{ name: 'lossPercentage', label: '止损比例(%)', default: 5, min: 0.1, max: 100, step: 0.1 }], indicatorInputCount: 1, indicatorInputLabels: ['参考指标'], importPath: 'org.ta4j.core.rules.StopLossRule', exitOnly: true },
  { type: 'StopGainRule', label: '止盈', category: '风控规则', params: [{ name: 'gainPercentage', label: '止盈比例(%)', default: 10, min: 0.1, max: 1000, step: 0.1 }], indicatorInputCount: 1, indicatorInputLabels: ['参考指标'], importPath: 'org.ta4j.core.rules.StopGainRule', exitOnly: true },
  { type: 'TrailingStopLossRule', label: '追踪止损', category: '风控规则', params: [{ name: 'lossPercentage', label: '止损比例(%)', default: 5, min: 0.1, max: 100, step: 0.1 }], indicatorInputCount: 1, indicatorInputLabels: ['参考指标'], importPath: 'org.ta4j.core.rules.TrailingStopLossRule', exitOnly: true },

  { type: 'IsRisingRule', label: '持续上升', category: '趋势规则', params: [{ name: 'barCount', label: '持续周期', default: 3, min: 1, max: 100 }], indicatorInputCount: 1, indicatorInputLabels: ['指标'], importPath: 'org.ta4j.core.rules.IsRisingRule' },
  { type: 'IsFallingRule', label: '持续下降', category: '趋势规则', params: [{ name: 'barCount', label: '持续周期', default: 3, min: 1, max: 100 }], indicatorInputCount: 1, indicatorInputLabels: ['指标'], importPath: 'org.ta4j.core.rules.IsFallingRule' },
  { type: 'IsHighestRule', label: '区间最高', category: '趋势规则', params: [{ name: 'barCount', label: '区间周期', default: 20, min: 2, max: 500 }], indicatorInputCount: 1, indicatorInputLabels: ['指标'], importPath: 'org.ta4j.core.rules.IsHighestRule' },
  { type: 'IsLowestRule', label: '区间最低', category: '趋势规则', params: [{ name: 'barCount', label: '区间周期', default: 20, min: 2, max: 500 }], indicatorInputCount: 1, indicatorInputLabels: ['指标'], importPath: 'org.ta4j.core.rules.IsLowestRule' },

  { type: 'InPipeRule', label: '区间内', category: '范围规则', params: [{ name: 'lower', label: '下限值', default: 0, step: 0.01 }, { name: 'upper', label: '上限值', default: 100, step: 0.01 }], indicatorInputCount: 1, indicatorInputLabels: ['指标'], importPath: 'org.ta4j.core.rules.InPipeRule' },
  { type: 'MaxTradeBarCountRule', label: '时间止损', category: '风控规则', params: [{ name: 'maxBarCount', label: '最大持仓天数', default: 5, min: 1, max: 100 }], indicatorInputCount: 0, indicatorInputLabels: [], importPath: 'com.tradingx.rules.MaxTradeBarCountRule', exitOnly: true, custom: true },
  { type: 'BooleanIndicatorRule', label: '布尔指标', category: '其他规则', params: [], indicatorInputCount: 1, indicatorInputLabels: ['布尔指标'], importPath: 'org.ta4j.core.rules.BooleanIndicatorRule' },
]

const INDICATOR_DEF_MAP = new Map(INDICATOR_CATALOG.map(d => [d.type, d]))
const RULE_DEF_MAP = new Map(RULE_CATALOG.map(d => [d.type, d]))

export function getIndicatorDef(type: string): IndicatorDef | undefined {
  return INDICATOR_DEF_MAP.get(type)
}

export function getRuleDef(type: string): RuleDef | undefined {
  return RULE_DEF_MAP.get(type)
}

let idCounter = 0
export function nextId(prefix: string): string {
  return `${prefix}_${++idCounter}_${Date.now().toString(36)}`
}

export function getIndicatorLabel(ind: IndicatorNode): string {
  const def = getIndicatorDef(ind.type)
  if (!def) return ind.type
  if (def.params.length === 0) return def.label
  const paramStr = def.params.map(p => ind.params[p.name] ?? p.default).join(', ')
  return `${def.label}(${paramStr})`
}

export function getRuleLabel(node: RuleNode): string {
  if (node.kind === 'group') {
    const childLabels = node.children.map(c => getRuleLabel(c))
    const label = node.children.length === 0 ? '(空)' : childLabels.join(` ${node.combinator} `)
    return node.negated ? `NOT(${label})` : `(${label})`
  }
  const def = getRuleDef(node.type)
  const base = def ? def.label : node.type
  return node.negated ? `NOT(${base})` : base
}

function migrateConditionsToRule(conditions: ConditionNode[], combinator: 'AND' | 'OR'): RuleNode {
  if (conditions.length === 0) {
    return { id: nextId('group'), kind: 'group', combinator, children: [] }
  }
  if (conditions.length === 1) {
    const c = conditions[0]
    return { id: c.id, kind: 'leaf', type: c.type, params: c.params, indicatorInputs: c.indicatorInputs }
  }
  return {
    id: nextId('group'),
    kind: 'group',
    combinator,
    children: conditions.map(c => ({
      id: c.id,
      kind: 'leaf' as const,
      type: c.type,
      params: c.params,
      indicatorInputs: c.indicatorInputs,
    })),
  }
}

export function migrateConfig(raw: any): VisualStrategyConfig {
  if (raw.entryRule && raw.exitRule) {
    return raw as VisualStrategyConfig
  }
  return {
    version: raw.version || 1,
    indicators: raw.indicators || [],
    entryRule: migrateConditionsToRule(
      raw.entryConditions || [],
      raw.entryCombinator || 'AND'
    ),
    exitRule: migrateConditionsToRule(
      raw.exitConditions || [],
      raw.exitCombinator || 'OR'
    ),
  }
}

export function createDefaultConfig(): VisualStrategyConfig {
  return {
    version: 1,
    indicators: [],
    entryRule: {
      id: nextId('group'),
      kind: 'group',
      combinator: 'AND',
      children: [],
    },
    exitRule: {
      id: nextId('group'),
      kind: 'group',
      combinator: 'OR',
      children: [],
    },
  }
}

function collectRuleImports(node: RuleNode, imports: Set<string>) {
  if (node.kind === 'leaf') {
    const def = getRuleDef(node.type)
    if (def) imports.add(def.importPath)
    if (node.negated) imports.add('org.ta4j.core.rules.NotRule')
  } else {
    if (node.children.length === 0) {
      imports.add('org.ta4j.core.rules.BooleanRule')
    } else if (node.children.length > 1) {
      imports.add(`org.ta4j.core.rules.${node.combinator}Rule`)
    }
    if (node.negated) imports.add('org.ta4j.core.rules.NotRule')
    for (const child of node.children) {
      collectRuleImports(child, imports)
    }
  }
}

function generateRuleJava(node: RuleNode, varPrefix: string, counter: { val: number }): string[] {
  const lines: string[] = []

  if (node.kind === 'leaf') {
    const def = getRuleDef(node.type)
    if (!def) return lines
    const ruleVar = `${varPrefix}${counter.val++}`
    const args: string[] = []
    for (const inputId of node.indicatorInputs) {
      args.push(inputId)
    }
    for (const p of def.params) {
      const val = node.params[p.name] ?? p.default
      if (['StopLossRule', 'StopGainRule', 'TrailingStopLossRule'].includes(node.type)) {
        args.push(`series.numFactory().numOf(${val})`)
      } else if (node.type === 'InPipeRule') {
        args.push(`series.numFactory().numOf(${val})`)
      } else {
        args.push(String(val))
      }
    }
    lines.push(`        // ${def.label}${node.negated ? ' (NOT)' : ''}`)
    lines.push(`        Rule ${ruleVar} = new ${node.type}(${args.join(', ')});`)
    if (node.negated) {
      const negVar = `${varPrefix}${counter.val++}`
      lines.push(`        Rule ${negVar} = new NotRule(${ruleVar});`)
    }
    return lines
  }

  const childVars: string[] = []
  for (const child of node.children) {
    const startCount = counter.val
    const childLines = generateRuleJava(child, varPrefix, counter)
    lines.push(...childLines)
    const lastVar = node.negated && child.negated
      ? `${varPrefix}${counter.val - 1}`
      : child.kind === 'leaf' && !child.negated
        ? `${varPrefix}${startCount}`
        : `${varPrefix}${counter.val - 1}`
    childVars.push(lastVar)
  }

  if (node.children.length === 0) {
    lines.push(`        // 空规则(false)`)
    lines.push(`        Rule ${varPrefix}${counter.val++} = new BooleanRule(false);`)
  } else if (node.children.length === 1) {
    // already handled by child
  } else {
    const combinedVar = `${varPrefix}${counter.val++}`
    const refs = childVars.join(', ')
    lines.push(`        // ${node.combinator}组合${node.negated ? ' (NOT)' : ''}`)
    lines.push(`        Rule ${combinedVar} = new ${node.combinator}Rule(${refs});`)
  }

  return lines
}

export function generateJavaCode(config: VisualStrategyConfig, className: string): string {
  const imports = new Set<string>()
  imports.add('org.ta4j.core.BarSeries')
  imports.add('org.ta4j.core.BaseStrategy')
  imports.add('org.ta4j.core.Rule')
  imports.add('org.ta4j.core.Strategy')
  imports.add('org.ta4j.core.indicators.Indicator')
  imports.add('org.ta4j.core.num.Num')

  for (const ind of config.indicators) {
    const def = getIndicatorDef(ind.type)
    if (def) imports.add(def.importPath)
  }
  collectRuleImports(config.entryRule, imports)
  collectRuleImports(config.exitRule, imports)

  const importLines = Array.from(imports).sort().map(p => `import ${p};`).join('\n')

  const varLines: string[] = []
  for (const ind of config.indicators) {
    const def = getIndicatorDef(ind.type)
    if (!def) continue
    const varName = ind.id
    const paramArgs: string[] = []
    if (def.needsBarSeries) paramArgs.push('series')
    for (const inputId of ind.inputs) {
      paramArgs.push(inputId)
    }
    for (const p of def.params) {
      const val = ind.params[p.name] ?? p.default
      paramArgs.push(String(val))
    }
    const label = getIndicatorLabel(ind)
    varLines.push(`        // ${label}`)
    varLines.push(`        ${ind.type}<Num> ${varName} = new ${ind.type}<>(${paramArgs.join(', ')});`)
  }

  const entryLines = generateRuleJava(config.entryRule, 'entry', { val: 0 })
  const exitLines = generateRuleJava(config.exitRule, 'exit', { val: 0 })

  return `package com.tradingx.strategy;

${importLines}

/**
 * ${className} - 由可视化策略编辑器自动生成
 */
public class ${className} {

    public static BaseStrategy buildStrategy(BarSeries series) {
        // ===== 指标定义 =====
${varLines.join('\n')}

        // ===== 入场规则 =====
${entryLines.join('\n')}

        // ===== 出场规则 =====
${exitLines.join('\n')}

        return new BaseStrategy(entryRule, exitRule);
    }
}
`
}
