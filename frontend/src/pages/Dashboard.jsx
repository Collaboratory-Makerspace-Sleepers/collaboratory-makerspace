import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Dashboard() {
  const { setToken } = useAuth()
  const navigate = useNavigate()

  function handleSignOut() {
    setToken(null)
    navigate('/signin', { replace: true })
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-white shadow px-8 py-4 flex justify-between items-center">
        <span className="text-xl font-bold text-gray-800">Collaboratory Makerspace</span>
        <button
          onClick={handleSignOut}
          className="text-sm text-gray-500 hover:text-red-500 transition-colors"
        >
          Sign Out
        </button>
      </nav>

      <main className="max-w-5xl mx-auto px-8 py-12">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Dashboard</h1>
        <p className="text-gray-500 mb-10">Welcome back!</p>
      </main>
    </div>
  )
}

function DashboardCard({ title, description, icon }) {
  return (
    <div className="bg-white rounded-2xl shadow p-6 flex flex-col gap-2 hover:shadow-md transition-shadow cursor-pointer">
      <span className="text-3xl">{icon}</span>
      <h2 className="text-lg font-semibold text-gray-800">{title}</h2>
      <p className="text-sm text-gray-500">{description}</p>
    </div>
  )
}
