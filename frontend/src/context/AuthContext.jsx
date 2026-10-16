import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

const AuthContext = createContext(null)

const STORAGE_KEY = 'auth_token'

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(() => localStorage.getItem(STORAGE_KEY))
  const [user, setUser] = useState(null)
  const [permissions, setPermissions] = useState([])
  const [authError, setAuthError] = useState("")
  // Tracks which token the profile below belongs to, so "still loading" is
  // derived rather than flipped inside the effect (a synchronous setState in an
  // effect body triggers a cascading re-render).
  const [loadedForToken, setLoadedForToken] = useState(null)

  const loadingProfile = !!token && loadedForToken !== token

  function setToken(value) {
    setAuthError("")
    if (value) {
      localStorage.setItem(STORAGE_KEY, value)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
    setTokenState(value)
    if (!value) {
      setUser(null)
      setPermissions([])
    }
  }

  const authFetch = useCallback((url, options = {}) => {
    return fetch(url, {
      ...options,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    })
  }, [token])

  // Profile and permission codes are fetched separately: /me returns role codes,
  // /me/permissions returns the flattened permission set those roles grant.
  // Gating UI on permissions (not role names) keeps it correct when an admin
  // reassigns a role's permissions through the admin API.
  useEffect(() => {
    if (!token) return

    let cancelled = false

    const bearer = { Authorization: `Bearer ${token}` }

    const readResponse = async (response) => ({
      ok: response.ok,
      status: response.status,
      body: response.ok ? await response.json().catch(() => null) : null,
    })

    Promise.all([
      fetch('/api/v1/users/me', { credentials: 'include', headers: bearer }).then(readResponse),
      fetch('/api/v1/users/me/permissions', { credentials: 'include', headers: bearer }).then(readResponse),
    ]).then(([profile, perms]) => {
        if (cancelled) return

        if (!profile.ok && (profile.status === 401 || profile.status === 403)) {
          localStorage.removeItem(STORAGE_KEY)
          setTokenState(null)
          setUser(null)
          setPermissions([])
          setAuthError(`The server rejected this sign-in (HTTP ${profile.status}). Please sign in again.`)
          return
        }

        if (!profile.ok || !profile.body) {
          setLoadedForToken(token)
          return
        }

        setUser(profile.body)
        setPermissions(perms.ok && Array.isArray(perms.body) ? perms.body : [])
        setLoadedForToken(token)
      })
      .catch(() => {
        if (!cancelled) setLoadedForToken(token)
      })

    return () => { cancelled = true }
  }, [token])

  const hasPermission = useCallback(
    (code) => permissions.includes(code),
    [permissions],
  )

  const hasAnyPermission = useCallback(
    (codes) => codes.some((code) => permissions.includes(code)),
    [permissions],
  )

  const value = useMemo(
    () => ({
      token,
      setToken,
      authFetch,
      isAuthenticated: !!token,
      authError,
      user,
      roles: user?.roles ?? [],
      permissions,
      loadingProfile,
      hasPermission,
      hasAnyPermission,
    }),
    [token, authFetch, user, permissions, loadingProfile, authError, hasPermission, hasAnyPermission],
  )

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
