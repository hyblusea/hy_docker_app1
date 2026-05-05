import { useEffect, useRef, useMemo, useState, useCallback } from 'react'
import { BarChartOutlined } from '@ant-design/icons'
import { Checkbox, Radio, Tooltip } from 'antd'
import * as echarts from 'echarts'
import styles from './DataPanel.module.css'
import type { DailyQuote, BacktestSignal } from '../../types'

interface DataPanelProps {
  data: DailyQuote[]
  stockName: string
  signals?: BacktestSignal[]
}

type SubIndicator = 'NONE' | 'MACD' | 'KDJ'

interface OverlayState {
  ma5: boolean
  ma10: boolean
  ma20: boolean
  ma60: boolean
  ma120: boolean
  boll: boolean
  bull9: boolean
  bear9: boolean
}

function getCSSVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function formatVol(val: number): string {
  if (val >= 10000) return (val / 10000).toFixed(0) + '亿'
  if (val >= 1) return val.toFixed(0) + '万'
  return (val * 10000).toFixed(0)
}

function calcSMA(closes: number[], period: number): (number | null)[] {
  const result: (number | null)[] = []
  for (let i = 0; i < closes.length; i++) {
    if (i < period - 1) {
      result.push(null)
    } else {
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) sum += closes[j]
      result.push(sum / period)
    }
  }
  return result
}

function calcEMA(closes: number[], period: number): (number | null)[] {
  const result: (number | null)[] = []
  const k = 2 / (period + 1)
  for (let i = 0; i < closes.length; i++) {
    if (i < period - 1) {
      result.push(null)
    } else if (i === period - 1) {
      let sum = 0
      for (let j = 0; j < period; j++) sum += closes[j]
      result.push(sum / period)
    } else {
      const prev = result[i - 1]
      result.push(prev !== null ? closes[i] * k + prev * (1 - k) : null)
    }
  }
  return result
}

function calcBoll(closes: number[], period: number = 20, mult: number = 2) {
  const mid = calcSMA(closes, period)
  const upper: (number | null)[] = []
  const lower: (number | null)[] = []
  for (let i = 0; i < closes.length; i++) {
    if (mid[i] === null) {
      upper.push(null)
      lower.push(null)
    } else {
      let sumSq = 0
      for (let j = i - period + 1; j <= i; j++) {
        sumSq += (closes[j] - mid[i]!) ** 2
      }
      const std = Math.sqrt(sumSq / period)
      upper.push(mid[i]! + mult * std)
      lower.push(mid[i]! - mult * std)
    }
  }
  return { mid, upper, lower }
}

function calcMACD(closes: number[], fast = 12, slow = 26, signal = 9) {
  const emaFast = calcEMA(closes, fast)
  const emaSlow = calcEMA(closes, slow)
  const dif: (number | null)[] = []
  for (let i = 0; i < closes.length; i++) {
    if (emaFast[i] !== null && emaSlow[i] !== null) {
      dif.push(emaFast[i]! - emaSlow[i]!)
    } else {
      dif.push(null)
    }
  }
  const validDif = dif.filter(v => v !== null) as number[]
  const deaRaw = calcEMA(validDif, signal)
  const dea: (number | null)[] = []
  let vi = 0
  for (let i = 0; i < closes.length; i++) {
    if (dif[i] !== null) {
      dea.push(deaRaw[vi] ?? null)
      vi++
    } else {
      dea.push(null)
    }
  }
  const histogram: (number | null)[] = []
  for (let i = 0; i < closes.length; i++) {
    if (dif[i] !== null && dea[i] !== null) {
      histogram.push(2 * (dif[i]! - dea[i]!))
    } else {
      histogram.push(null)
    }
  }
  return { dif, dea, histogram }
}

function calcKDJ(closes: number[], highs: number[], lows: number[], n = 9, m1 = 3, m2 = 3) {
  const kArr: (number | null)[] = []
  const dArr: (number | null)[] = []
  const jArr: (number | null)[] = []
  let prevK = 50
  let prevD = 50
  for (let i = 0; i < closes.length; i++) {
    if (i < n - 1) {
      kArr.push(null)
      dArr.push(null)
      jArr.push(null)
    } else {
      let lowN = Infinity
      let highN = -Infinity
      for (let j = i - n + 1; j <= i; j++) {
        if (lows[j] < lowN) lowN = lows[j]
        if (highs[j] > highN) highN = highs[j]
      }
      const rsv = highN === lowN ? 50 : ((closes[i] - lowN) / (highN - lowN)) * 100
      const k = (2 / m1) * prevK + (1 / m1) * rsv
      const d = (2 / m2) * prevD + (1 / m2) * k
      const j = 3 * k - 2 * d
      kArr.push(k)
      dArr.push(d)
      jArr.push(j)
      prevK = k
      prevD = d
    }
  }
  return { k: kArr, d: dArr, j: jArr }
}

function calcNineTurns(closes: number[], lows: number[], highs: number[]) {
  const lookback = 4
  const threshold = 9
  const bullCounts: number[] = []
  const bearCounts: number[] = []
  for (let i = 0; i < closes.length; i++) {
    if (i < lookback) {
      bullCounts.push(0)
      bearCounts.push(0)
    } else {
      const bullMet = closes[i] > closes[i - lookback]
      const bearMet = closes[i] < closes[i - lookback]
      const prevBull = bullCounts[i - 1]
      const prevBear = bearCounts[i - 1]
      bullCounts.push(bullMet ? prevBull + 1 : 0)
      bearCounts.push(bearMet ? prevBear + 1 : 0)
    }
  }
  const bullData: any[] = []
  const bearData: any[] = []
  for (let i = 0; i < closes.length; i++) {
    if (bullCounts[i] >= 1 && bullCounts[i] <= threshold) {
      bullData.push({
        value: [i, lows[i], bullCounts[i]],
        label: {
          show: true,
          position: 'bottom',
          formatter: String(bullCounts[i]),
          color: '#ef5350',
          fontSize: bullCounts[i] >= threshold ? 12 : 9,
          fontWeight: bullCounts[i] >= threshold ? 'bold' : 'normal',
        },
      })
    }
    if (bearCounts[i] >= 1 && bearCounts[i] <= threshold) {
      bearData.push({
        value: [i, highs[i], bearCounts[i]],
        label: {
          show: true,
          position: 'top',
          formatter: String(bearCounts[i]),
          color: '#26a69a',
          fontSize: bearCounts[i] >= threshold ? 12 : 9,
          fontWeight: bearCounts[i] >= threshold ? 'bold' : 'normal',
        },
      })
    }
  }
  return { bullData, bearData }
}

const MA_COLORS: Record<string, string> = {
  ma5: '#e6a23c',
  ma10: '#409eff',
  ma20: '#f56c6c',
  ma60: '#67c23a',
  ma120: '#b37feb',
}

const DataPanel = ({ data, stockName, signals = [] }: DataPanelProps) => {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstanceRef = useRef<echarts.ECharts | null>(null)
  const zoomStateRef = useRef<{ start: number; end: number } | null>(null)
  const isFirstRenderRef = useRef(true)
  const [overlay, setOverlay] = useState<OverlayState>({ ma5: true, ma10: true, ma20: true, ma60: false, ma120: false, boll: false, bull9: false, bear9: false })
  const [subIndicator, setSubIndicator] = useState<SubIndicator>('MACD')

  const sortedData = useMemo(() => {
    return [...data].sort((a, b) => a.trade_date.localeCompare(b.trade_date))
  }, [data])

  const indicatorData = useMemo(() => {
    const closes = sortedData.map(d => d.close)
    const highs = sortedData.map(d => d.high)
    const lows = sortedData.map(d => d.low)
    return {
      ma5: calcSMA(closes, 5),
      ma10: calcSMA(closes, 10),
      ma20: calcSMA(closes, 20),
      ma60: calcSMA(closes, 60),
      ma120: calcSMA(closes, 120),
      boll: calcBoll(closes),
      macd: calcMACD(closes),
      kdj: calcKDJ(closes, highs, lows),
      nineTurns: calcNineTurns(closes, lows, highs),
    }
  }, [sortedData])

  useEffect(() => {
    const el = chartRef.current
    if (!el) return
    const chart = echarts.init(el)
    chartInstanceRef.current = chart

    chart.on('datazoom', (_params: any) => {
      const opt = chart.getOption()
      if (opt?.dataZoom && Array.isArray(opt.dataZoom) && opt.dataZoom.length > 0) {
        const dz = opt.dataZoom[0] as any
        if (dz.start !== undefined && dz.end !== undefined) {
          zoomStateRef.current = { start: dz.start, end: dz.end }
        }
      }
    })

    const ro = new ResizeObserver(() => chart.resize())
    ro.observe(el)

    const onWinResize = () => chart.resize()
    window.addEventListener('resize', onWinResize)

    return () => {
      window.removeEventListener('resize', onWinResize)
      ro.disconnect()
      chart.dispose()
      chartInstanceRef.current = null
    }
  }, [])

  const buildOption = useCallback((): echarts.EChartsOption | null => {
    if (sortedData.length === 0) return null

    const dates = sortedData.map((d) => d.trade_date)
    const kData = sortedData.map((d) => [d.open, d.close, d.low, d.high])
    const volumes = sortedData.map((d) => d.vol / 10000)

    const upColor = '#ef5350'
    const downColor = '#26a69a'
    const axisColor = '#ccc'
    const splitColor = '#e8e8e8'
    const labelColor = '#888'

    const bgCard = getCSSVar('--bg-card') || '#fff'
    const textPrimary = getCSSVar('--text-primary') || '#333'
    const accentColor = getCSSVar('--accent-color') || '#1890ff'

    const markPoints: any[] = []
    if (signals.length > 0) {
      signals.forEach((signal) => {
        const idx = dates.indexOf(signal.trade_date.replace(/-/g, ''))
        if (idx !== -1) {
          const bar = sortedData[idx]
          const isBuy = signal.type === 'BUY'
          const markerY = isBuy ? bar.low : bar.high
          markPoints.push({
            name: signal.type,
            coord: [idx, markerY],
            value: signal.type,
            symbol: 'pin',
            symbolSize: 12,
            symbolRotate: isBuy ? 180 : 0,
            symbolOffset: isBuy ? [0, '50%'] : [0, '-100%'],
            itemStyle: { color: isBuy ? '#ef5350' : '#26a69a' },
            label: {
              show: true,
              position: isBuy ? 'bottom' : 'top',
              formatter: isBuy ? '买' : '卖',
              fontSize: 10,
              fontWeight: 'bold',
              color: isBuy ? '#ef5350' : '#26a69a',
            },
          })
        }
      })
    }

    const totalBars = sortedData.length
    const MAX_VISIBLE = 500
    const defaultZoomEnd = 100
    const defaultZoomStart = totalBars > MAX_VISIBLE
      ? ((totalBars - MAX_VISIBLE) / totalBars) * 100
      : 0

    const saved = zoomStateRef.current
    const zoomStart = saved ? saved.start : defaultZoomStart
    const zoomEnd = saved ? saved.end : defaultZoomEnd

    const hasSub = subIndicator !== 'NONE'

    const grids: any[] = hasSub
      ? [
          { left: '4%', right: '2%', top: '2%', height: '48%' },
          { left: '4%', right: '2%', top: '57%', height: '10%' },
          { left: '4%', right: '2%', top: '73%', height: '16%' },
        ]
      : [
          { left: '4%', right: '2%', top: '2%', height: '68%' },
          { left: '4%', right: '2%', top: '78%', height: '10%' },
        ]

    const sliderTop = hasSub ? '93%' : '93%'

    const xAxes: any[] = hasSub
      ? [
          {
            type: 'category', data: dates, scale: true, boundaryGap: false,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor },
            splitLine: { show: false },
            min: 'dataMin', max: 'dataMax',
          },
          {
            type: 'category', gridIndex: 1, data: dates,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { show: false },
            splitLine: { show: false },
          },
          {
            type: 'category', gridIndex: 2, data: dates,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { show: false },
            splitLine: { show: false },
          },
        ]
      : [
          {
            type: 'category', data: dates, scale: true, boundaryGap: false,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor },
            splitLine: { show: false },
            min: 'dataMin', max: 'dataMax',
          },
          {
            type: 'category', gridIndex: 1, data: dates,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { show: false },
            splitLine: { show: false },
          },
        ]

    const yAxes: any[] = hasSub
      ? [
          {
            scale: true,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor, formatter: (v: number) => v.toFixed(2) },
            splitLine: { lineStyle: { color: splitColor } },
          },
          {
            scale: true, gridIndex: 1, splitNumber: 2,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor, formatter: (v: number) => formatVol(v) },
            splitLine: { show: false },
          },
          {
            scale: true, gridIndex: 2, splitNumber: 3,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor, fontSize: 10, formatter: (v: number) => v.toFixed(2) },
            splitLine: { lineStyle: { color: splitColor, type: 'dashed' } },
          },
        ]
      : [
          {
            scale: true,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor, formatter: (v: number) => v.toFixed(2) },
            splitLine: { lineStyle: { color: splitColor } },
          },
          {
            scale: true, gridIndex: 1, splitNumber: 2,
            axisLine: { lineStyle: { color: axisColor } },
            axisLabel: { color: labelColor, formatter: (v: number) => formatVol(v) },
            splitLine: { show: false },
          },
        ]

    const zoomXIndices = hasSub ? [0, 1, 2] : [0, 1]

    const seriesList: any[] = [
      {
        name: '日K',
        type: 'candlestick',
        data: kData,
        itemStyle: {
          color: upColor,
          color0: downColor,
          borderColor: upColor,
          borderColor0: downColor,
        },
        markPoint: {
          data: markPoints,
          animation: false,
        },
      },
    ]

    if (overlay.ma5) {
      seriesList.push({
        name: 'MA5', type: 'line', data: indicatorData.ma5,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: MA_COLORS.ma5 },
      })
    }
    if (overlay.ma10) {
      seriesList.push({
        name: 'MA10', type: 'line', data: indicatorData.ma10,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: MA_COLORS.ma10 },
      })
    }
    if (overlay.ma20) {
      seriesList.push({
        name: 'MA20', type: 'line', data: indicatorData.ma20,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: MA_COLORS.ma20 },
      })
    }
    if (overlay.ma60) {
      seriesList.push({
        name: 'MA60', type: 'line', data: indicatorData.ma60,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: MA_COLORS.ma60 },
      })
    }
    if (overlay.ma120) {
      seriesList.push({
        name: 'MA120', type: 'line', data: indicatorData.ma120,
        smooth: true, symbol: 'none', lineStyle: { width: 1, color: MA_COLORS.ma120 },
      })
    }
    if (overlay.boll) {
      seriesList.push(
        {
          name: 'BOLL中轨', type: 'line', data: indicatorData.boll.mid,
          smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#e6a23c', type: 'dashed' },
        },
        {
          name: 'BOLL上轨', type: 'line', data: indicatorData.boll.upper,
          smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#f56c6c', type: 'dashed' },
        },
        {
          name: 'BOLL下轨', type: 'line', data: indicatorData.boll.lower,
          smooth: true, symbol: 'none', lineStyle: { width: 1, color: '#67c23a', type: 'dashed' },
        },
      )
    }

    if (overlay.bull9) {
      seriesList.push({
        name: '涨九转',
        type: 'scatter',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: indicatorData.nineTurns.bullData,
        symbolSize: 1,
      })
    }
    if (overlay.bear9) {
      seriesList.push({
        name: '跌九转',
        type: 'scatter',
        xAxisIndex: 0,
        yAxisIndex: 0,
        data: indicatorData.nineTurns.bearData,
        symbolSize: 1,
      })
    }

    seriesList.push({
      name: '成交量',
      type: 'bar',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: volumes,
      itemStyle: {
        color: (params: any) => {
          const idx = params.dataIndex
          const close = sortedData[idx]?.close ?? 0
          const open = sortedData[idx]?.open ?? 0
          return close >= open ? upColor : downColor
        },
      },
      markPoint: {
        data: [{ name: 'VOL', x: 5, y: 5, symbol: 'none', label: { show: true, formatter: 'VOL', color: labelColor, fontSize: 10, fontWeight: 500 } }],
        animation: false,
      },
    })

    if (hasSub && subIndicator === 'MACD') {
      seriesList.push(
        {
          name: 'DIF', type: 'line', xAxisIndex: 2, yAxisIndex: 2,
          data: indicatorData.macd.dif, symbol: 'none',
          lineStyle: { width: 1, color: '#e6a23c' },
        },
        {
          name: 'DEA', type: 'line', xAxisIndex: 2, yAxisIndex: 2,
          data: indicatorData.macd.dea, symbol: 'none',
          lineStyle: { width: 1, color: '#409eff' },
        },
        {
          name: 'MACD', type: 'bar', xAxisIndex: 2, yAxisIndex: 2,
          data: indicatorData.macd.histogram,
          itemStyle: {
            color: (params: any) => {
              const val = params.value
              return val >= 0 ? '#ef5350' : '#26a69a'
            },
          },
          markPoint: {
            data: [{ name: 'MACD', x: 5, y: 5, symbol: 'none', label: { show: true, formatter: 'MACD(12,26,9)', color: labelColor, fontSize: 10, fontWeight: 500 } }],
            animation: false,
          },
        },
      )
    }

    if (hasSub && subIndicator === 'KDJ') {
      seriesList.push(
        {
          name: 'K', type: 'line', xAxisIndex: 2, yAxisIndex: 2,
          data: indicatorData.kdj.k, symbol: 'none',
          lineStyle: { width: 1, color: '#e6a23c' },
        },
        {
          name: 'D', type: 'line', xAxisIndex: 2, yAxisIndex: 2,
          data: indicatorData.kdj.d, symbol: 'none',
          lineStyle: { width: 1, color: '#409eff' },
        },
        {
          name: 'J', type: 'line', xAxisIndex: 2, yAxisIndex: 2,
          data: indicatorData.kdj.j, symbol: 'none',
          lineStyle: { width: 1, color: '#f56c6c' },
          markPoint: {
            data: [{ name: 'KDJ', x: 5, y: 5, symbol: 'none', label: { show: true, formatter: 'KDJ(9,3,3)', color: labelColor, fontSize: 10, fontWeight: 500 } }],
            animation: false,
          },
        },
      )
    }

    return {
      backgroundColor: 'transparent',
      animation: false,
      title: stockName ? {
        text: stockName,
        right: '3%',
        top: 0,
        textStyle: {
          color: labelColor,
          fontSize: 13,
          fontWeight: 'normal',
        },
      } : undefined,
      axisPointer: {
        link: [{ xAxisIndex: 'all' }],
        label: { show: false },
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross', label: { show: false } },
        backgroundColor: bgCard,
        borderColor: axisColor,
        textStyle: { color: textPrimary, fontSize: 12 },
        formatter: (params: any) => {
          if (!Array.isArray(params)) return ''
          let html = `<div style="font-weight:600;margin-bottom:4px">${params[0]?.axisValueLabel ?? ''}</div>`
          params.forEach((p: any) => {
            if (p.seriesType === 'candlestick') {
              const d = p.data
              if (Array.isArray(d)) {
                html += `${p.marker} ${p.seriesName} 开${d[1]} 收${d[2]} 低${d[3]} 高${d[4]}<br/>`
              }
            } else if (p.seriesName === '成交量') {
              html += `${p.marker} ${p.seriesName} ${formatVol(p.value)}<br/>`
            } else if (p.seriesType === 'line') {
              const val = p.value !== null && p.value !== undefined ? p.value.toFixed(2) : '-'
              html += `${p.marker} ${p.seriesName} ${val}<br/>`
            }
          })
          return html
        },
      },
      legend: {
        show: true,
        top: 0,
        left: 'center',
        textStyle: { color: labelColor, fontSize: 11 },
        itemWidth: 14,
        itemHeight: 10,
        data: seriesList.filter(s => s.type === 'line').map(s => s.name),
      },
      grid: grids,
      xAxis: xAxes,
      yAxis: yAxes,
      dataZoom: [
        { type: 'inside', xAxisIndex: zoomXIndices, start: zoomStart, end: zoomEnd },
        {
          show: true,
          xAxisIndex: zoomXIndices,
          type: 'slider',
          top: sliderTop,
          start: zoomStart,
          end: zoomEnd,
          height: 22,
          borderColor: axisColor,
          fillerColor: 'rgba(24,144,255,0.25)',
          handleStyle: { color: accentColor },
          textStyle: { color: labelColor },
          showDetail: false,
        },
      ],
      series: seriesList,
    }
  }, [sortedData, signals, stockName, overlay, subIndicator, indicatorData])

  useEffect(() => {
    const chart = chartInstanceRef.current
    if (!chart) return
    const option = buildOption()
    if (option) {
      chart.setOption(option, { notMerge: true })
    } else {
      chart.clear()
    }
  }, [buildOption])

  useEffect(() => {
    if (data.length > 0) {
      zoomStateRef.current = null
      isFirstRenderRef.current = true
    }
  }, [data])

  const handleOverlayChange = (key: keyof OverlayState, checked: boolean) => {
    setOverlay(prev => ({ ...prev, [key]: checked }))
  }

  return (
    <div className={styles.panel}>
      {data.length > 0 && (
        <div className={styles.indicatorToolbar}>
          <div className={styles.toolbarGroup}>
            <span className={styles.toolbarLabel}>叠加指标:</span>
            <Checkbox
              checked={overlay.ma5}
              onChange={e => handleOverlayChange('ma5', e.target.checked)}
            >
              <span style={{ color: MA_COLORS.ma5 }}>MA5</span>
            </Checkbox>
            <Checkbox
              checked={overlay.ma10}
              onChange={e => handleOverlayChange('ma10', e.target.checked)}
            >
              <span style={{ color: MA_COLORS.ma10 }}>MA10</span>
            </Checkbox>
            <Checkbox
              checked={overlay.ma20}
              onChange={e => handleOverlayChange('ma20', e.target.checked)}
            >
              <span style={{ color: MA_COLORS.ma20 }}>MA20</span>
            </Checkbox>
            <Checkbox
              checked={overlay.ma60}
              onChange={e => handleOverlayChange('ma60', e.target.checked)}
            >
              <span style={{ color: MA_COLORS.ma60 }}>MA60</span>
            </Checkbox>
            <Checkbox
              checked={overlay.ma120}
              onChange={e => handleOverlayChange('ma120', e.target.checked)}
            >
              <span style={{ color: MA_COLORS.ma120 }}>MA120</span>
            </Checkbox>
            <Tooltip title="布林带(20,2)">
              <Checkbox
                checked={overlay.boll}
                onChange={e => handleOverlayChange('boll', e.target.checked)}
              >
                <span style={{ color: '#e6a23c' }}>BOLL</span>
              </Checkbox>
            </Tooltip>
            <Tooltip title="看涨九转: 连续9天收盘价>4天前收盘价 ★=完美九转">
              <Checkbox
                checked={overlay.bull9}
                onChange={e => handleOverlayChange('bull9', e.target.checked)}
              >
                <span style={{ color: '#ef5350' }}>涨九转</span>
              </Checkbox>
            </Tooltip>
            <Tooltip title="看跌九转: 连续9天收盘价<4天前收盘价 ★=完美九转">
              <Checkbox
                checked={overlay.bear9}
                onChange={e => handleOverlayChange('bear9', e.target.checked)}
              >
                <span style={{ color: '#26a69a' }}>跌九转</span>
              </Checkbox>
            </Tooltip>
          </div>
          <div className={styles.toolbarGroup}>
            <span className={styles.toolbarLabel}>副图指标:</span>
            <Radio.Group
              size="small"
              value={subIndicator}
              onChange={e => setSubIndicator(e.target.value)}
              optionType="button"
              buttonStyle="solid"
            >
              <Radio.Button value="MACD">MACD</Radio.Button>
              <Radio.Button value="KDJ">KDJ</Radio.Button>
              <Radio.Button value="NONE">无</Radio.Button>
            </Radio.Group>
          </div>
        </div>
      )}
      <div className={styles.chartArea}>
        <div ref={chartRef} className={styles.chartContainer} />
        {data.length === 0 && (
          <div className={styles.emptyOverlay}>
            <BarChartOutlined className={styles.emptyIcon} />
            <span>请输入股票代码并点击查询，查看K线数据</span>
          </div>
        )}
      </div>
    </div>
  )
}

export default DataPanel
