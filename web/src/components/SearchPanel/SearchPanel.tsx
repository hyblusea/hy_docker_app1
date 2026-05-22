import { useState, useRef, useCallback, forwardRef, useImperativeHandle } from 'react'
import { AutoComplete, Select, DatePicker, Button } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import styles from './SearchPanel.module.css'
import { searchStocks } from '../../api/stock'
import type { StockBasic, SearchQuery } from '../../types'

const PERIODS = [
  { value: 'day', label: '日K' },
  { value: 'week', label: '周K' },
  { value: 'month', label: '月K' },
]

export interface SearchPanelHandle {
  selectAndQuery: (tsCode: string, name: string) => void
}

interface SearchPanelProps {
  onSearch: (query: SearchQuery) => void
  loading?: boolean
}

const SearchPanel = forwardRef<SearchPanelHandle, SearchPanelProps>(({ onSearch, loading }, ref) => {
  const [selectedStock, setSelectedStock] = useState<StockBasic | null>(null)
  const [inputValue, setInputValue] = useState('')
  const [options, setOptions] = useState<{ value: string; label: string }[]>([])
  const [period, setPeriod] = useState('day')
  const [startDate, setStartDate] = useState<Dayjs | null>(dayjs().subtract(3, 'year'))
  const [endDate, setEndDate] = useState<Dayjs | null>(dayjs())
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const stockMapRef = useRef<Map<string, StockBasic>>(new Map())

  const doQuery = useCallback(
    (stock: StockBasic) => {
      onSearch({
        tsCode: stock.ts_code || stock.symbol || '',
        name: stock.name || '',
        period,
        startDate: startDate ? startDate.format('YYYYMMDD') : '',
        endDate: endDate ? endDate.format('YYYYMMDD') : '',
      })
    },
    [onSearch, period, startDate, endDate],
  )

  useImperativeHandle(ref, () => ({
    selectAndQuery: (tsCode: string, name: string) => {
      const display = `${name} (${tsCode})`
      setInputValue(display)
      const stock: StockBasic = {
        ts_code: tsCode,
        symbol: tsCode.split('.')[0],
        name,
        area: '',
        industry: '',
        cnspell: '',
        market: '',
        list_date: '',
        act_name: '',
        act_ent_type: '',
        market_value: null,
        market_value_circulating: null,
        total_shares: null,
        circulating_shares: null,
      }
      setSelectedStock(stock)
      setOptions([])
      doQuery(stock)
    },
  }), [doQuery])

  const handleSearch = useCallback((value: string) => {
    setInputValue(value)
    if (!value || value.length < 1) {
      setSelectedStock(null)
      setOptions([])
      stockMapRef.current.clear()
      return
    }
    if (selectedStock && `${selectedStock.name} (${selectedStock.ts_code})` === value) return
    setSelectedStock(null)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
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
        stockMapRef.current = map
        setOptions(opts)
      } catch {
        setOptions([])
        stockMapRef.current.clear()
      }
    }, 300)
  }, [selectedStock])

  const handleSelect = useCallback((value: string) => {
    setInputValue(value)
    const stock = stockMapRef.current.get(value)
    if (stock) {
      setSelectedStock(stock)
    }
    setOptions([])
  }, [])

  const handleQuery = () => {
    if (!selectedStock) return
    doQuery(selectedStock)
  }

  const canSearch = !!selectedStock && !!startDate && !!endDate

  return (
    <div className={styles.panel}>
      <div className={styles.row}>
        <div className={styles.fieldGroup}>
          <span className={styles.label}>股票</span>
          <AutoComplete
            className={styles.stockInput}
            value={inputValue}
            options={options}
            onSearch={handleSearch}
            onSelect={handleSelect}
            placeholder="输入股票代码或名称"
            allowClear
            onClear={() => {
              setInputValue('')
              setSelectedStock(null)
              setOptions([])
              stockMapRef.current.clear()
            }}
          />
        </div>

        <div className={styles.fieldGroup}>
          <span className={styles.label}>周期</span>
          <Select
            className={styles.periodSelect}
            value={period}
            onChange={setPeriod}
            options={PERIODS}
          />
        </div>

        <div className={styles.fieldGroup}>
          <span className={styles.label}>开始</span>
          <DatePicker
            className={styles.datePicker}
            value={startDate}
            onChange={setStartDate}
            format="YYYY-MM-DD"
            placeholder="开始日期"
          />
        </div>

        <div className={styles.fieldGroup}>
          <span className={styles.label}>结束</span>
          <DatePicker
            className={styles.datePicker}
            value={endDate}
            onChange={setEndDate}
            format="YYYY-MM-DD"
            placeholder="结束日期"
          />
        </div>

        <Button
          type="primary"
          icon={<SearchOutlined />}
          className={styles.searchBtn}
          onClick={handleQuery}
          loading={loading}
          disabled={!canSearch}
        >
          查询
        </Button>
      </div>
    </div>
  )
})

export default SearchPanel
