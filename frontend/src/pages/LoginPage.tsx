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
        <h1 id="login-title">관리자 로그인</h1>
        <p className="page-description">
          이 FormDock에 등록된 관리자 계정으로 로그인하세요.
        </p>

        <form
          aria-describedby={errorMessage === null ? undefined : 'login-error'}
          aria-labelledby="login-title"
          className="auth-form"
          onSubmit={handleSubmit}
        >
          <label htmlFor="email">이메일</label>
          <input
            autoComplete="email"
            id="email"
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />

          <label htmlFor="password">비밀번호</label>
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
            {isSubmitting ? '로그인 중…' : '로그인'}
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
        return '이메일 또는 비밀번호가 올바르지 않습니다.'
      case 'CSRF_INVALID':
        return '보안 세션을 갱신하지 못했습니다. 다시 시도해 주세요.'
      case 'TEMPORARILY_UNAVAILABLE':
        return 'FormDock을 일시적으로 사용할 수 없습니다. 다시 시도해 주세요.'
      case 'AUTH_REQUIRED':
      case 'FORBIDDEN':
      case 'UNEXPECTED_RESPONSE':
        return '로그인하지 못했습니다. 다시 시도해 주세요.'
    }
  }

  return '로그인하지 못했습니다. 다시 시도해 주세요.'
}

export default LoginPage
