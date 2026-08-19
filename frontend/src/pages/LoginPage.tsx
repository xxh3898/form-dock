import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router'

import { AuthApiError, type AuthClient } from '../auth/authClient.ts'

type LoginPageProps = {
  client: AuthClient
}

function LoginPage({ client }: LoginPageProps) {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSubmitting) {
      return
    }

    setErrorMessage(null)
    setIsSubmitting(true)

    try {
      await client.login(email, password)
      setPassword('')
      navigate('/admin', { replace: true })
    } catch (error) {
      setErrorMessage(loginErrorMessage(error))
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-shell">
      <section aria-labelledby="login-title" className="auth-card">
        <p className="product-name">FormDock</p>
        <h1 id="login-title">Creator sign in</h1>
        <p className="page-description">
          Sign in with the Creator account provisioned for this FormDock
          instance.
        </p>

        <form
          aria-describedby={errorMessage === null ? undefined : 'login-error'}
          aria-labelledby="login-title"
          className="auth-form"
          onSubmit={handleSubmit}
        >
          <label htmlFor="email">Email</label>
          <input
            autoComplete="email"
            id="email"
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />

          <label htmlFor="password">Password</label>
          <input
            autoComplete="current-password"
            id="password"
            name="password"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />

          {errorMessage === null ? null : (
            <p className="error-message" id="login-error" role="alert">
              {errorMessage}
            </p>
          )}

          <button disabled={isSubmitting} type="submit">
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  )
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    switch (error.code) {
      case 'AUTH_INVALID_CREDENTIALS':
        return 'The email or password is incorrect.'
      case 'CSRF_INVALID':
        return 'Your security session could not be refreshed. Try again.'
      case 'TEMPORARILY_UNAVAILABLE':
        return 'FormDock is temporarily unavailable. Try again.'
      case 'AUTH_REQUIRED':
      case 'FORBIDDEN':
      case 'UNEXPECTED_RESPONSE':
        return 'We could not sign you in. Try again.'
    }
  }

  return 'We could not sign you in. Try again.'
}

export default LoginPage
