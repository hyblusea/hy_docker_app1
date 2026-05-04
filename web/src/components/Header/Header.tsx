import { StockOutlined, SunOutlined, MoonOutlined, UserOutlined, LogoutOutlined, TeamOutlined } from '@ant-design/icons'
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
  const isUserManage = location.pathname === '/user-manage'

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
            首页
          </button>
          <button
            className={isVisualStrategy ? styles.menuItemActive : styles.menuItem}
            onClick={() => navigate('/strategy-visual')}
          >
            策略管理
          </button>
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
