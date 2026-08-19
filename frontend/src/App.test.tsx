import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import App from './App.tsx'
import {
  AuthApiError,
  type AuthClient,
  type Creator,
} from './auth/authClient.ts'

const creator: Creator = {
  id: 1,
  email: 'creator@example.test',
  displayName: 'Local Creator',
  role: 'ADMIN',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('Creator routes', () => {
  it('should_renderAccessibleLogin_when_routeIsLogin', () => {
    renderRoute('/login', createClient())

    expect(
      screen.getByRole('heading', { name: 'Creator sign in' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute(
      'autocomplete',
      'email',
    )
    expect(screen.getByLabelText('Password')).toHaveAttribute(
      'autocomplete',
      'current-password',
    )
    expect(screen.queryByText(/sign up|reset password|oauth/i)).not.toBeInTheDocument()
  })

  it('should_redirectAnonymousRootToLogin_withoutProtectedContentFlash', async () => {
    const client = createClient({
      me: vi.fn(async () => {
        throw new AuthApiError('AUTH_REQUIRED', 401)
      }),
    })

    renderRoute('/', client)

    expect(
      screen.queryByRole('heading', { name: 'Creator administration' }),
    ).not.toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { name: 'Creator sign in' }),
    ).toBeInTheDocument()
  })

  it('should_restoreCreatorShell_when_sessionIsAuthenticated', async () => {
    const me = vi.fn(async () => creator)
    renderRoute('/admin', createClient({ me }))

    expect(
      await screen.findByRole('heading', { name: 'Creator administration' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Local Creator')).toBeInTheDocument()
    expect(screen.getByText('creator@example.test')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(me).toHaveBeenCalledOnce()
  })

  it('should_navigateToAdmin_when_loginSucceeds', async () => {
    const login = vi.fn(async () => creator)
    const me = vi.fn(async () => creator)
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const password = 'local-password-value'
    renderRoute('/login', createClient({ login, me }))

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'Creator@Example.test' },
    })
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: password },
    })
    fireEvent.submit(screen.getByRole('form', { name: 'Creator sign in' }))

    expect(
      await screen.findByRole('heading', { name: 'Creator administration' }),
    ).toBeInTheDocument()
    expect(login).toHaveBeenCalledWith('Creator@Example.test', password)
    expect(me).toHaveBeenCalledOnce()
    expect(screen.queryByDisplayValue(password)).not.toBeInTheDocument()
    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('should_showGenericCredentialError_when_loginIsRejected', async () => {
    const password = 'not-a-real-secret'
    const login = vi.fn(async () => {
      throw new AuthApiError('AUTH_INVALID_CREDENTIALS', 401)
    })
    renderRoute('/login', createClient({ login }))

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'unknown@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: password },
    })
    fireEvent.submit(screen.getByRole('form', { name: 'Creator sign in' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The email or password is incorrect.')
    expect(alert).not.toHaveTextContent(password)
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('should_showSafeUnavailableError_when_authServiceIsTransientlyUnavailable', async () => {
    const login = vi.fn(async () => {
      throw new AuthApiError('TEMPORARILY_UNAVAILABLE', 503)
    })
    renderRoute('/login', createClient({ login }))

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'creator@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'unavailable-password' },
    })
    fireEvent.submit(screen.getByRole('form', { name: 'Creator sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'FormDock is temporarily unavailable. Try again.',
    )
  })

  it('should_showSafeCsrfError_when_refreshedTokenIsRejected', async () => {
    const login = vi.fn(async () => {
      throw new AuthApiError('CSRF_INVALID', 403)
    })
    renderRoute('/login', createClient({ login }))

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'creator@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'csrf-password' },
    })
    fireEvent.submit(screen.getByRole('form', { name: 'Creator sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Your security session could not be refreshed. Try again.',
    )
  })

  it('should_preventDuplicateSubmission_when_loginIsPending', async () => {
    let resolveLogin: ((value: Creator) => void) | undefined
    const login = vi.fn(
      () =>
        new Promise<Creator>((resolve) => {
          resolveLogin = resolve
        }),
    )
    renderRoute('/login', createClient({ login, me: vi.fn(async () => creator) }))

    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'creator@example.test' },
    })
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'pending-password' },
    })
    const form = screen.getByRole('form', { name: 'Creator sign in' })
    fireEvent.submit(form)
    fireEvent.submit(form)

    expect(login).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled()

    await act(async () => {
      resolveLogin?.(creator)
    })
  })

  it('should_returnToLogin_withoutDuplicateRequest_when_logoutSucceeds', async () => {
    let resolveLogout: (() => void) | undefined
    const logout = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveLogout = resolve
        }),
    )
    renderRoute('/admin', createClient({ logout }))

    await screen.findByRole('heading', { name: 'Creator administration' })
    const button = screen.getByRole('button', { name: 'Sign out' })
    fireEvent.click(button)
    fireEvent.click(button)

    expect(logout).toHaveBeenCalledOnce()
    expect(
      screen.getByRole('button', { name: 'Signing out…' }),
    ).toBeDisabled()

    await act(async () => {
      resolveLogout?.()
    })

    expect(
      await screen.findByRole('heading', { name: 'Creator sign in' }),
    ).toBeInTheDocument()
  })

  it('should_hideProtectedContent_when_sessionCheckIsUnavailable', async () => {
    const me = vi.fn(async () => {
      throw new AuthApiError('TEMPORARILY_UNAVAILABLE', 503)
    })
    renderRoute('/admin', createClient({ me }))

    expect(
      await screen.findByRole('heading', { name: 'Session unavailable' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Creator administration' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('creator@example.test')).not.toBeInTheDocument()
  })

  it('should_renderMinimalNotFound_when_routeIsUnknown', () => {
    renderRoute('/not-a-formdock-route', createClient())

    expect(
      screen.getByRole('heading', { name: 'Page not found' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/survey/i)).not.toBeInTheDocument()
  })
})

function renderRoute(path: string, client: AuthClient) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App client={client} />
    </MemoryRouter>,
  )
}

function createClient(overrides: Partial<AuthClient> = {}): AuthClient {
  return {
    login: vi.fn(async () => creator),
    logout: vi.fn(async () => undefined),
    me: vi.fn(async () => creator),
    ...overrides,
  }
}
