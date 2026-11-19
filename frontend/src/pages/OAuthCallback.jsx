import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function OAuthCallback() {
  const { setToken } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    fetch('/api/v1/auth/token', { credentials: 'include' })
      .then(res => {
        if (res.status === 410) { navigate('/account-closed'); return null; }
        if (!res.ok) throw new Error()
        return res.json()
      })
      .then(data => {
        if (!data) return;
        setToken(data.access_token)
        navigate('/dashboard')
      })
      .catch(() => navigate('/login?error=true'))
  }, [navigate, setToken])

  return <p>Signing you in…</p>
}
