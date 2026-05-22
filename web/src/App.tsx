import { useState, useCallback, useEffect } from 'react'
import { ConfigProvider, App as AntApp, Spin, theme as antdTheme } from 'antd'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import zhCN from 'antd/locale/zh_CN'
import Header from './components/Header/Header'
import HomePage, { defaultHomePageState, type HomePageState } from './pages/HomePage'
import VisualStrategyPage from './pages/VisualStrategyPage'
import StrategySelectPage, { defaultStrategySelectPageState, type StrategySelectPageState } from './pages/StrategySelectPage'
import StrategyAnalysisPage, { defaultStrategyAnalysisPageState, type StrategyAnalysisPageState } from './pages/StrategyAnalysisPage'
import FactorMiningPage, { defaultFactorMiningPageState, type FactorMiningPageState } from './pages/FactorMiningPage'
import BacktestPage, { defaultBacktestPageState, type BacktestPageState } from './pages/BacktestPage'
import DataManagementPage from './pages/DataManagementPage'
import TrackStockPage from './pages/TrackStockPage'
import PredictionPage from './pages/PredictionPage'
import LoginPage from './pages/LoginPage'
import UserManagePage from './pages/UserManagePage'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { useTheme } from './hooks/useTheme'
import { clearStrategiesCache, clearValidStrategiesCache } from './hooks/useStrategies'
import styles from './App.module.css'

const ProtectedRoutes = () => {
  const { user, loading } = useAuth()
  const { theme, toggleTheme } = useTheme()

  const [homeState, setHomeState] = useState<HomePageState>(defaultHomePageState)
  const handleHomeStateChange = useCallback((patch: Partial<HomePageState>) => {
    setHomeState(prev => ({ ...prev, ...patch }))
  }, [])

  const [strategySelectState, setStrategySelectState] = useState<StrategySelectPageState>(defaultStrategySelectPageState)
  const handleStrategySelectStateChange = useCallback((patch: Partial<StrategySelectPageState>) => {
    setStrategySelectState(prev => ({ ...prev, ...patch }))
  }, [])

  const [strategyAnalysisState, setStrategyAnalysisState] = useState<StrategyAnalysisPageState>(defaultStrategyAnalysisPageState)
  const handleStrategyAnalysisStateChange = useCallback((patch: Partial<StrategyAnalysisPageState>) => {
    setStrategyAnalysisState(prev => ({ ...prev, ...patch }))
  }, [])

  const [factorMiningState, setFactorMiningState] = useState<FactorMiningPageState>(defaultFactorMiningPageState)
  const handleFactorMiningStateChange = useCallback((patch: Partial<FactorMiningPageState>) => {
    setFactorMiningState(prev => ({ ...prev, ...patch }))
  }, [])

  const [backtestState, setBacktestState] = useState<BacktestPageState>(defaultBacktestPageState)
  const handleBacktestStateChange = useCallback((patch: Partial<BacktestPageState>) => {
    setBacktestState(prev => ({ ...prev, ...patch }))
  }, [])

  useEffect(() => {
    if (user) {
      setHomeState(defaultHomePageState)
      setStrategySelectState(defaultStrategySelectPageState)
      setStrategyAnalysisState(defaultStrategyAnalysisPageState)
      setFactorMiningState(defaultFactorMiningPageState)
      setBacktestState(defaultBacktestPageState)
    }
  }, [user])

  const handleStrategyChanged = useCallback(() => {
    clearStrategiesCache()
    clearValidStrategiesCache()
  }, [])

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (!user) {
    return <LoginPage />
  }

  return (
    <div className={styles.app} data-theme={theme}>
      <Header theme={theme} onToggleTheme={toggleTheme} />
      <main className={styles.main}>
        <Routes>
          <Route path="/" element={<HomePage state={homeState} onStateChange={handleHomeStateChange} />} />
          <Route path="/strategy-visual" element={<VisualStrategyPage onStrategyChanged={handleStrategyChanged} />} />
          <Route path="/strategy-select" element={<StrategySelectPage state={strategySelectState} onStateChange={handleStrategySelectStateChange} />} />
          <Route path="/strategy-analysis" element={<StrategyAnalysisPage state={strategyAnalysisState} onStateChange={handleStrategyAnalysisStateChange} />} />
          <Route path="/factor-mining" element={<FactorMiningPage state={factorMiningState} onStateChange={handleFactorMiningStateChange} />} />
          <Route path="/backtest" element={<BacktestPage state={backtestState} onStateChange={handleBacktestStateChange} />} />
          <Route path="/data-management" element={<DataManagementPage />} />
          <Route path="/track" element={<TrackStockPage />} />
          <Route path="/prediction" element={<PredictionPage />} />
          <Route path="/user-manage" element={<UserManagePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

const App = () => {
  const { theme } = useTheme()
  const algorithm = theme === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm

  return (
    <BrowserRouter>
      <ConfigProvider
        locale={zhCN}
        theme={{
          algorithm,
          token: {
            colorPrimary: '#1677ff',
            borderRadius: 8,
          },
        }}
      >
        <AntApp>
          <AuthProvider>
            <ProtectedRoutes />
          </AuthProvider>
        </AntApp>
      </ConfigProvider>
    </BrowserRouter>
  )
}

export default App
