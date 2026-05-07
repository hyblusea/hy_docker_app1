import { useState, useMemo } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Input, Button, App } from 'antd'
import { StockOutlined, CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons'
import { useAuth } from '../contexts/AuthContext'
import { register } from '../api/auth'
import styles from './LoginPage.module.css'

const EMAIL_PATTERN = /^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/

const PWD_RULES = [
  { key: 'length', label: '鑷冲皯6浣?, test: (p: string) => p.length >= 6 },
  { key: 'letter', label: '鍖呭惈瀛楁瘝', test: (p: string) => /[a-zA-Z]/.test(p) },
  { key: 'digit', label: '鍖呭惈鏁板瓧', test: (p: string) => /\d/.test(p) },
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
      setError('璇疯緭鍏ョ敤鎴峰悕鍜屽瘑鐮?)
      return
    }
    setLoading(true)
    try {
      await login(username.trim(), password)
      message.success('鐧诲綍鎴愬姛')
    } catch (e: any) {
      setError(e.message || '鐧诲綍澶辫触')
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async () => {
    setError('')
    if (!username.trim()) {
      setError('璇疯緭鍏ラ偖绠卞湴鍧€')
      return
    }
    const emailPattern = EMAIL_PATTERN
    if (!emailPattern.test(username.trim())) {
      setError('鐢ㄦ埛鍚嶅繀椤绘槸鏈夋晥鐨勯偖绠卞湴鍧€')
      return
    }
    const pwdPattern = /^(?=.*[a-zA-Z])(?=.*\d).{6,}$/
    if (!pwdPattern.test(password)) {
      setError('瀵嗙爜蹇呴』澶т簬绛変簬6浣嶏紝涓斿繀椤诲寘鍚瓧姣嶅拰鏁板瓧')
      return
    }
    if (password !== confirmPassword) {
      setError('涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?)
      return
    }
    setLoading(true)
    try {
      await register(username.trim(), password)
      message.success('娉ㄥ唽鎴愬姛锛岃绛夊緟绠＄悊鍛樺鏍?)
      setTab('login')
      setPassword('')
      setConfirmPassword('')
    } catch (e: any) {
      setError(e.message || '娉ㄥ唽澶辫触')
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
        <div className={styles.subtitle}>绛栫暐鍥炴祴骞冲彴</div>

        <div className={styles.tabBar}>
          <button
            className={tab === 'login' ? styles.tabActive : styles.tab}
            onClick={() => { setTab('login'); setError('') }}
          >
            鐧诲綍
          </button>
          <button
            className={tab === 'register' ? styles.tabActive : styles.tab}
            onClick={() => { setTab('register'); setError('') }}
          >
            娉ㄥ唽
          </button>
        </div>

        <div className={styles.form} onKeyDown={handleKeyDown}>
          <Input
            size="large"
            placeholder={tab === 'login' ? '鐢ㄦ埛鍚?/ 閭' : '閭鍦板潃'}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            type={tab === 'register' ? 'email' : 'text'}
          />
          {emailValid !== null && (
            <div className={`${styles.inlineHint} ${emailValid ? styles.inlineHintPass : styles.inlineHintFail}`}>
              {emailValid ? <CheckCircleFilled /> : <CloseCircleFilled />}
              {emailValid ? '閭鏍煎紡姝ｇ‘' : '璇疯緭鍏ユ湁鏁堢殑閭鍦板潃'}
            </div>
          )}
          <Input.Password
            size="large"
            placeholder="瀵嗙爜"
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
              placeholder="纭瀵嗙爜"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          )}
          {confirmValid !== null && (
            <div className={`${styles.inlineHint} ${confirmValid ? styles.inlineHintPass : styles.inlineHintFail}`}>
              {confirmValid ? <CheckCircleFilled /> : <CloseCircleFilled />}
              {confirmValid ? '瀵嗙爜涓€鑷? : '涓ゆ杈撳叆鐨勫瘑鐮佷笉涓€鑷?}
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
            {tab === 'login' ? '鐧?褰? : '娉?鍐?}
          </Button>
        </div>

        {tab === 'register' && (
          <div className={styles.footer}>
            娉ㄥ唽鍚庨渶绠＄悊鍛樺鏍搁€氳繃鎵嶈兘鐧诲綍
          </div>
        )}
      </div>
      <div className={styles.version}>v0.0.1</div>
    </div>
  )
}

export default LoginPage
