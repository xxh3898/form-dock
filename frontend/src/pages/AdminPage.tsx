import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router'

import {
  AuthApiError,
  type AuthClient,
  type Creator,
} from '../auth/authClient.ts'

type AdminPageProps = {
  client: AuthClient
}

type SessionState =
  | { status: 'loading' }
  | { status: 'authenticated'; creator: Creator }
  | { status: 'unavailable' }

function AdminPage({ client }: AdminPageProps) {
  const navigate = useNavigate()
  const navigateRef = useRef(navigate)
  const [sessionState, setSessionState] = useState<SessionState>({
    status: 'loading',
  })
  const [retryKey, setRetryKey] = useState(0)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  useEffect(() => {
    navigateRef.current = navigate
  }, [navigate])

  useEffect(() => {
    let active = true

    client.me().then(
      (creator) => {
        if (active) {
          setSessionState({ status: 'authenticated', creator })
        }
      },
      (error: unknown) => {
        if (!active) {
          return
        }
        if (error instanceof AuthApiError && error.code === 'AUTH_REQUIRED') {
          navigateRef.current('/login', { replace: true })
          return
        }
        setSessionState({ status: 'unavailable' })
      },
    )

    return () => {
      active = false
    }
  }, [client, retryKey])

  async function handleLogout() {
    if (isLoggingOut) {
      return
    }

    setLogoutError(null)
    setIsLoggingOut(true)
    try {
      await client.logout()
      navigate('/login', { replace: true })
    } catch (error) {
      if (error instanceof AuthApiError && error.code === 'AUTH_REQUIRED') {
        navigate('/login', { replace: true })
        return
      }
      setLogoutError('We could not sign you out. Try again.')
      setIsLoggingOut(false)
    }
  }

  if (sessionState.status === 'loading') {
    return (
      <main className="auth-shell">
        <section aria-live="polite" className="auth-card" role="status">
          <p className="product-name">FormDock</p>
          <p className="status-message">Checking your Creator session…</p>
        </section>
      </main>
    )
  }

  if (sessionState.status === 'unavailable') {
    return (
      <main className="auth-shell">
        <section aria-labelledby="session-error-title" className="auth-card">
          <p className="product-name">FormDock</p>
          <h1 id="session-error-title">Session unavailable</h1>
          <p className="page-description" role="alert">
            We could not verify your Creator session.
          </p>
          <button
            onClick={() => {
              setSessionState({ status: 'loading' })
              setRetryKey((value) => value + 1)
            }}
            type="button"
          >
            Try again
          </button>
        </section>
      </main>
    )
  }

  const { creator } = sessionState

  return (
    <div className="admin-shell">
      <header className="admin-header">
        <div>
          <Link className="product-name product-link" to="/admin/surveys">
            FormDock
          </Link>
          <h1>Creator administration</h1>
        </div>
        <div className="creator-session">
          <span>{creator.displayName}</span>
          <button disabled={isLoggingOut} onClick={handleLogout} type="button">
            {isLoggingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </header>

      <nav aria-label="Creator administration" className="admin-navigation">
        <Link to="/admin/surveys">Surveys</Link>
      </nav>

      {logoutError === null ? null : (
        <p className="error-message" role="alert">
          {logoutError}
        </p>
      )}

      <Outlet />
    </div>
  )
}

export default AdminPage
