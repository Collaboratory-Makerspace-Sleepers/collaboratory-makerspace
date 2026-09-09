import { Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import OAuthCallback from './pages/OAuthCallback'
import Dashboard from './pages/Dashboard'
import RequireAuth from './components/RequireAuth'

export default function App() {
  return (
    <Routes>
      <Route path="/login"          element={<LoginPage />} />
      <Route path="/oauth-callback" element={<OAuthCallback />} />
      <Route path="/dashboard"      element={
        <RequireAuth>
          <Dashboard />
        </RequireAuth>
      } />
      <Route path="/" element={<LoginPage />} />
    </Routes>
  )
}
