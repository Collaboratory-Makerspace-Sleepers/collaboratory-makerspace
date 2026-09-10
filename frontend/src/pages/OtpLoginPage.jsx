import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function OtpLoginPage() {
  const [step, setStep]       = useState('email') // 'email' | 'code'
  const [email, setEmail]     = useState('')
  const [code, setCode]       = useState('')
  const [error, setError]     = useState('')
  const [loading, setLoading] = useState(false)

  const { setToken } = useAuth()
  const navigate     = useNavigate()

  async function handleSend(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await fetch('/api/v1/auth/otp/send', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ email }),
      })
      if (res.status === 429) { setError('Please wait 60 seconds before requesting another code.'); return }
      if (!res.ok)            { setError('Failed to send code. Please try again.'); return }
      setStep('code')
    } catch {
      setError('Network error. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleVerify(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await fetch('/api/v1/auth/otp/verify', {
        method:      'POST',
        credentials: 'include',
        headers:     { 'Content-Type': 'application/json' },
        body:        JSON.stringify({ email, code }),
      })
      if (res.status === 401) { setError('Invalid or expired code.'); return }
      if (res.status === 403) { setError('This account has been closed.'); return }
      if (!res.ok)            { setError('Something went wrong. Please try again.'); return }
      const data = await res.json()
      setToken(data.access_token)
      navigate('/dashboard')
    } catch {
      setError('Network error. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <h1>Sign in with email</h1>

      {step === 'email' && (
        <form onSubmit={handleSend}>
          <input
            type="email"
            placeholder="your@email.com"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
            autoFocus
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Sending…' : 'Send code'}
          </button>
        </form>
      )}

      {step === 'code' && (
        <form onSubmit={handleVerify}>
          <p>Enter the 6-digit code sent to {email}</p>
          <input
            type="text"
            inputMode="numeric"
            pattern="\d{6}"
            maxLength={6}
            placeholder="000000"
            value={code}
            onChange={e => setCode(e.target.value)}
            required
            autoFocus
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Verifying…' : 'Sign in'}
          </button>
          <button type="button" onClick={() => { setStep('email'); setCode(''); setError('') }}>
            Use a different email
          </button>
        </form>
      )}

      {error && <p style={{ color: 'red' }}>{error}</p>}
    </div>
  )
}
