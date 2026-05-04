import { useState, useCallback } from 'react'
import { ConfigProvider, App as AntApp, Spin, theme as antdTheme } from 'antd'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import zhCN from 'antd/locale/zh_CN'
import Header from './components/Header/Header'
import HomePage, { defaultHomePageState, type HomePageState } from './pages/HomePage'
import VisualStrategyPage from './pages/VisualStrategyPage'
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
