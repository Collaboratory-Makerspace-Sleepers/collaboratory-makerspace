import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function RequireAuth({ children }) {
  const { isAuthenticated, sessionExpired } = useAuth()
  const location = useLocation()

  if (isAuthenticated) return children

  const to = sessionExpired ? '/signin?reason=expired' : '/signin'
  return <Navigate to={to} state={{ from: location }} replace />
}
