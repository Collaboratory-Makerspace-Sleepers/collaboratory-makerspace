import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function OAuthCallback() {
  const { setToken } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    fetch('/api/v1/auth/token', { credentials: 'include' })
      .then(res => {
        if (!res.ok) throw new Error()
        return res.json()
      })
      .then(({ access_token }) => {
        setToken(access_token)
        const redirect = sessionStorage.getItem('redirectAfterLogin') ?? '/dashboard'
        sessionStorage.removeItem('redirectAfterLogin')
        navigate(redirect, { replace: true })
      })
      .catch(() => navigate('/signin?error=true'))
  }, [])

  return <p>Signing you in…</p>
}
