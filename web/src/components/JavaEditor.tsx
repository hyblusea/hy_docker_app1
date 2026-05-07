import { useMemo } from 'react'
import CodeMirror from '@uiw/react-codemirror'
import { java } from '@codemirror/lang-java'
import { oneDark } from '@codemirror/theme-one-dark'
import { autocompletion } from '@codemirror/autocomplete'
import { EditorView } from '@codemirror/view'
import { EditorState } from '@codemirror/state'

const tradingKeywords = [
  'BarSeries', 'BaseStrategy', 'Rule', 'Strategy', 'Indicator', 'Num',
  'ClosePriceIndicator', 'OpenPriceIndicator', 'HighPriceIndicator', 'LowPriceIndicator',
  'VolumeIndicator', 'SMAIndicator', 'EMAIndicator', 'WMAIndicator',
  'RSIIndicator', 'MACDIndicator', 'StochasticOscillatorKIndicator',
  'StochasticOscillatorDIndicator', 'BollingerBandsUpperIndicator',
  'BollingerBandsLowerIndicator', 'BollingerBandsMiddleIndicator',
  'ATRIndicator', 'CCIIndicator', 'ADXIndicator', 'ROCIndicator',
  'StandardDeviationIndicator', 'VarianceIndicator', 'DifferenceIndicator',
  'PreviousValueIndicator', 'SumIndicator', 'MultiplyIndicator',
  'CrossedUpIndicatorRule', 'CrossedDownIndicatorRule',
  'OverIndicatorRule', 'UnderIndicatorRule',
  'IsRisingRule', 'IsFallingRule',
  'BooleanRule', 'NotRule', 'ANDRule', 'ORRule',
  'StopLossRule', 'StopGainRule', 'TrailingStopLossRule',
  'InPipeRule', 'InSlopeRule', 'MaxTradeBarCountRule',
]

const tradingCompletions = (context: any) => {
  const word = context.matchBefore(/[\w.]*/)
  if (!word || (word.from === word.to && !context.explicit)) return null
  const options = [
    ...tradingKeywords.map(k => ({ label: k, type: 'class' })),
    { label: 'buildStrategy', type: 'function', detail: 'BarSeries → Strategy' },
    { label: 'getNum', type: 'function', detail: '() → Num' },
    { label: 'getValue', type: 'function', detail: '() → double' },
    { label: 'getBarCount', type: 'function', detail: '() → int' },
    { label: 'getClosePrice', type: 'function', detail: '() → Num' },
    { label: 'getOpenPrice', type: 'function', detail: '() → Num' },
    { label: 'getHighPrice', type: 'function', detail: '() → Num' },
    { label: 'getLowPrice', type: 'function', detail: '() → Num' },
    { label: 'getVolume', type: 'function', detail: '() → Num' },
  ]
  return {
    from: word.from,
    options,
    filter: true,
  }
}

const tradingTheme = EditorView.baseTheme({
  '&': {
    fontSize: '13px',
    height: '100%',
  },
  '.cm-scroller': {
    overflow: 'auto',
  },
  '.cm-content': {
    minHeight: '100%',
  },
  '.cm-gutters': {
    backgroundColor: '#1e1e1e',
    borderRight: '1px solid #333',
  },
  '.cm-foldGutter': {
    width: '16px',
  },
  '.cm-foldGutter .cm-gutterElement': {
    cursor: 'pointer',
    color: '#636d83',
    textAlign: 'center',
  },
  '.cm-foldGutter .cm-gutterElement:hover': {
    color: '#c8c8c8',
  },
  '&dark .cm-activeLineGutter': {
    backgroundColor: '#2a2d2e',
  },
  '&dark .cm-activeLine': {
    backgroundColor: '#2a2d2e',
  },
})

interface JavaEditorProps {
  value: string
  onChange?: (value: string) => void
  height?: string
  readOnly?: boolean
  className?: string
}

export default function JavaEditor({ value, onChange, height = '100%', readOnly = false, className }: JavaEditorProps) {
  const extensions = useMemo(() => [
    java(),
    autocompletion({ override: [tradingCompletions] }),
    tradingTheme,
    EditorView.lineWrapping,
    ...(readOnly ? [EditorView.editable.of(false), EditorState.readOnly.of(true)] : []),
  ], [readOnly])

  return (
    <div style={{ height, display: 'flex', flexDirection: 'column' }}>
      <CodeMirror
        value={value}
        height="100%"
        theme={oneDark}
        extensions={extensions}
        onChange={onChange}
        className={className}
        basicSetup={{
          lineNumbers: true,
          highlightActiveLineGutter: true,
          highlightActiveLine: true,
          foldGutter: true,
          bracketMatching: true,
          closeBrackets: true,
          autocompletion: false,
          indentOnInput: true,
        }}
      />
    </div>
  )
}
