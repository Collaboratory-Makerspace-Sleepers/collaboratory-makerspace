import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  return isAuthenticated
    ? children
    : <Navigate to="/signin" state={{ from: location }} replace />
}
