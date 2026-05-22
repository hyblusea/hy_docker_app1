import { StockOutlined, SunOutlined, MoonOutlined, UserOutlined, LogoutOutlined, TeamOutlined, BarChartOutlined, HomeOutlined, SlidersOutlined, DatabaseOutlined, LineChartOutlined, ExperimentOutlined, EyeOutlined, FundOutlined, RobotOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import styles from './Header.module.css'

interface HeaderProps {
  theme: 'light' | 'dark'
  onToggleTheme: () => void
}

const Header = ({ theme, onToggleTheme }: HeaderProps) => {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout, isRoot } = useAuth()
  const isHome = location.pathname === '/'
  const isVisualStrategy = location.pathname === '/strategy-visual'
  const isStrategySelect = location.pathname === '/strategy-select'
  const isStrategyAnalysis = location.pathname === '/strategy-analysis'
  const isFactorMining = location.pathname === '/factor-mining'
  const isBacktest = location.pathname === '/backtest'
  const isTrack = location.pathname === '/track'
  const isDataManagement = location.pathname === '/data-management'
  const isUserManage = location.pathname === '/user-manage'
  const isPrediction = location.pathname === '/prediction'

  const handleLogout = async () => {
    await logout()
  }

  return (
    <header className={styles.header}>
      <div className={styles.left}>
        <div className={styles.logo} onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
          <StockOutlined className={styles.logoIcon} />
          <span>TradingX</span>
        </div>
        <nav className={styles.menu}>
          <button
            className={isHome ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/')}
          >
            <HomeOutlined style={{ marginRight: 4 }} />
            首页
          </button>
          <button
            className={isVisualStrategy ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/strategy-visual')}
          >
            <SlidersOutlined style={{ marginRight: 4 }} />
            策略管理
          </button>
          <button
            className={isStrategySelect ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/strategy-select')}
          >
            <BarChartOutlined style={{ marginRight: 4 }} />
            策略选股
          </button>
          <button
            className={isStrategyAnalysis ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/strategy-analysis')}
          >
            <LineChartOutlined style={{ marginRight: 4 }} />
            策略分析
          </button>
          <button
            className={isFactorMining ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/factor-mining')}
          >
            <ExperimentOutlined style={{ marginRight: 4 }} />
            因子挖掘
          </button>
          <button
            className={isBacktest ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/backtest')}
          >
            <FundOutlined style={{ marginRight: 4 }} />
            策略回测
          </button>
          <button
            className={isTrack ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/track')}
          >
            <EyeOutlined style={{ marginRight: 4 }} />
            跟踪
          </button>
          <button
            className={isPrediction ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/prediction')}
          >
            <RobotOutlined style={{ marginRight: 4 }} />
            模型管理
          </button>
          {isRoot && (
            <button
              className={isDataManagement ? styles.menuItemActive : styles.menuItem}
              onClick={() => navigate('/data-management')}
            >
              <DatabaseOutlined style={{ marginRight: 4 }} />
              数据管理
            </button>
          )}
          {isRoot && (
            <button
              className={isUserManage ? styles.menuItemActive : styles.menuItem}
              onClick={() => navigate('/user-manage')}
            >
              <TeamOutlined style={{ marginRight: 4 }} />
              用户管理
            </button>
          )}
        </nav>
      </div>
      <div className={styles.right}>
        <button
          className={styles.themeBtn}
          onClick={onToggleTheme}
          title={theme === 'light' ? '切换深色模式' : '切换浅色模式'}
        >
          {theme === 'light' ? <MoonOutlined /> : <SunOutlined />}
        </button>
        <div className={styles.userInfo}>
          <div className={styles.avatar}>
            <UserOutlined />
          </div>
          <span className={styles.username}>{user?.username}</span>
        </div>
        <button className={styles.logoutBtn} onClick={handleLogout} title="退出登录">
          <LogoutOutlined />
        </button>
      </div>
    </header>
  )
}

export default Header
