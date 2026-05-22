import { useState, useMemo } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Input, Button, App } from 'antd'
import { StockOutlined, CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { register } from '../api/auth'
import styles from './LoginPage.module.css'

const EMAIL_PATTERN = /^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/

const PWD_RULES = [
  { key: 'length', label: '至少6位', test: (p: string) => p.length >= 6 },
  { key: 'letter', label: '包含字母', test: (p: string) => /[a-zA-Z]/.test(p) },
  { key: 'digit', label: '包含数字', test: (p: string) => /\d/.test(p) },
]

const LoginPage = () => {
  const { login } = useAuth()
  const { message } = App.useApp()
  const [tab, setTab] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const pwdChecks = useMemo(() => {
    if (tab !== 'register' || !password) return null
    return PWD_RULES.map(r => ({ ...r, passed: r.test(password) }))
  }, [tab, password])

  const pwdStrength = useMemo(() => {
    if (!pwdChecks) return 0
    return pwdChecks.filter(r => r.passed).length
  }, [pwdChecks])

  const emailValid = useMemo(() => {
    if (tab !== 'register' || !username.trim()) return null
    return EMAIL_PATTERN.test(username.trim())
  }, [tab, username])

  const confirmValid = useMemo(() => {
    if (tab !== 'register' || !confirmPassword) return null
    return password === confirmPassword
  }, [tab, password, confirmPassword])

  const handleLogin = async () => {
    setError('')
    if (!username.trim() || !password) {
      setError('请输入用户名和密码')
      return
    }
    setLoading(true)
    try {
      await login(username.trim(), password)
    } catch (e: any) {
      setError(e.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async () => {
    setError('')
    if (!username.trim()) {
      setError('请输入邮箱地址')
      return
    }
    const emailPattern = EMAIL_PATTERN
    if (!emailPattern.test(username.trim())) {
      setError('用户名必须是有效的邮箱地址')
      return
    }
    const pwdPattern = /^(?=.*[a-zA-Z])(?=.*\d).{6,}$/
    if (!pwdPattern.test(password)) {
      setError('密码必须大于等于6位，且必须包含字母和数字')
      return
    }
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }
    setLoading(true)
    try {
      await register(username.trim(), password)
      message.success('注册成功，请等待管理员审核')
      setTab('login')
      setPassword('')
      setConfirmPassword('')
    } catch (e: any) {
      setError(e.message || '注册失败')
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: ReactKeyboardEvent) => {
    if (e.key === 'Enter') {
      tab === 'login' ? handleLogin() : handleRegister()
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.logo}>
          <StockOutlined className={styles.logoIcon} />
          <span>TradingX</span>
        </div>
        <div className={styles.subtitle}>策略回测平台</div>

        <div className={styles.tabBar}>
          <button
            className={tab === 'login' ? styles.tabActive : styles.tab}
            onClick={() => { setTab('login'); setError('') }}
          >
            登录
          </button>
          <button
            className={tab === 'register' ? styles.tabActive : styles.tab}
            onClick={() => { setTab('register'); setError('') }}
          >
            注册
          </button>
        </div>

        <div className={styles.form} onKeyDown={handleKeyDown}>
          <Input
            size="large"
            placeholder={tab === 'login' ? '用户名 / 邮箱' : '邮箱地址'}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            type={tab === 'register' ? 'email' : 'text'}
          />
          {emailValid !== null && (
            <div className={`${styles.inlineHint} ${emailValid ? styles.inlineHintPass : styles.inlineHintFail}`}>
              {emailValid ? <CheckCircleFilled /> : <CloseCircleFilled />}
              {emailValid ? '邮箱格式正确' : '请输入有效的邮箱地址'}
            </div>
          )}
          <Input.Password
            size="large"
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {pwdChecks && (
            <div className={styles.pwdStrength}>
              <div className={styles.pwdStrengthBar}>
                <div
                  className={styles.pwdStrengthFill}
                  style={{
                    width: `${(pwdStrength / PWD_RULES.length) * 100}%`,
                    backgroundColor: pwdStrength === PWD_RULES.length ? '#52c41a' : pwdStrength >= 2 ? '#faad14' : '#ff4d4f',
                  }}
                />
              </div>
              <div className={styles.pwdRules}>
                {pwdChecks.map(r => (
                  <span key={r.key} className={`${styles.pwdRule} ${r.passed ? styles.pwdRulePass : styles.pwdRuleFail}`}>
                    {r.passed ? <CheckCircleFilled /> : <CloseCircleFilled />}
                    {r.label}
                  </span>
                ))}
              </div>
            </div>
          )}
          {tab === 'register' && (
            <Input.Password
              size="large"
              placeholder="确认密码"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          )}
          {confirmValid !== null && (
            <div className={`${styles.inlineHint} ${confirmValid ? styles.inlineHintPass : styles.inlineHintFail}`}>
              {confirmValid ? <CheckCircleFilled /> : <CloseCircleFilled />}
              {confirmValid ? '密码一致' : '两次输入的密码不一致'}
            </div>
          )}
          <div className={styles.errorMsg}>{error}</div>
          <Button
            type="primary"
            size="large"
            block
            loading={loading}
            onClick={tab === 'login' ? handleLogin : handleRegister}
          >
            {tab === 'login' ? '登 录' : '注 册'}
          </Button>
        </div>

        {tab === 'register' && (
          <div className={styles.footer}>
            注册后需管理员审核通过才能登录
          </div>
        )}
      </div>
      <div className={styles.version}>v0.0.1</div>
    </div>
  )
}

export default LoginPage
