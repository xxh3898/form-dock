export type Creator = {
  id: number
  email: string
  displayName: string
  role: 'ADMIN'
}

export type AuthErrorCode =
  | 'AUTH_INVALID_CREDENTIALS'
  | 'AUTH_REQUIRED'
  | 'CSRF_INVALID'
  | 'FORBIDDEN'
  | 'TEMPORARILY_UNAVAILABLE'
  | 'UNEXPECTED_RESPONSE'

export class AuthApiError extends Error {
  readonly code: AuthErrorCode
  readonly status: number

  constructor(code: AuthErrorCode, status: number) {
    super(`Authentication request failed (${code}).`)
    this.name = 'AuthApiError'
    this.code = code
    this.status = status
  }
}

export interface AuthClient {
  login(email: string, password: string): Promise<Creator>
  me(): Promise<Creator>
  logout(): Promise<void>
}

type CsrfToken = {
  token: string
  headerName: string
}

type FetchFunction = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response>

const knownErrorCodes = new Set<AuthErrorCode>([
  'AUTH_INVALID_CREDENTIALS',
  'AUTH_REQUIRED',
  'CSRF_INVALID',
  'FORBIDDEN',
  'TEMPORARILY_UNAVAILABLE',
])

export class SameOriginAuthClient implements AuthClient {
  private csrfToken: CsrfToken | null = null
  private readonly fetchRequest: FetchFunction

  constructor(fetchRequest: FetchFunction) {
    this.fetchRequest = fetchRequest
  }

  async login(email: string, password: string): Promise<Creator> {
    const response = await this.requestWithCsrf('/api/auth/login', {
      body: JSON.stringify({ email, password }),
      method: 'POST',
    })
    const creator = await parseCreator(response)
    await this.refreshCsrfAfterAuthStateChange()
    return creator
  }

  async me(): Promise<Creator> {
    const response = await this.request('/api/auth/me', { method: 'GET' })
    return parseCreator(response)
  }

  async logout(): Promise<void> {
    await this.requestWithCsrf('/api/auth/logout', { method: 'POST' })
    await this.refreshCsrfAfterAuthStateChange()
  }

  private async requestWithCsrf(
    path: '/api/auth/login' | '/api/auth/logout',
    init: RequestInit,
  ): Promise<Response> {
    let csrfToken = await this.getCsrfToken()

    for (let attempt = 0; attempt < 2; attempt += 1) {
      const headers = new Headers(init.headers)
      headers.set(csrfToken.headerName, csrfToken.token)

      try {
        return await this.request(path, { ...init, headers })
      } catch (error) {
        if (
          attempt === 0 &&
          error instanceof AuthApiError &&
          error.code === 'CSRF_INVALID'
        ) {
          this.csrfToken = null
          csrfToken = await this.getCsrfToken()
          continue
        }

        if (error instanceof AuthApiError && error.code === 'CSRF_INVALID') {
          this.csrfToken = null
        }
        throw error
      }
    }

    throw new AuthApiError('CSRF_INVALID', 403)
  }

  private async refreshCsrfAfterAuthStateChange(): Promise<void> {
    this.csrfToken = null
    try {
      await this.getCsrfToken()
    } catch {
      // The auth transition already succeeded. Keep no stale token; the next
      // unsafe request will obtain a fresh token before it is sent.
      this.csrfToken = null
    }
  }

  private async getCsrfToken(): Promise<CsrfToken> {
    if (this.csrfToken !== null) {
      return this.csrfToken
    }

    const response = await this.request('/api/auth/csrf', { method: 'GET' })
    const csrfToken = await parseCsrfToken(response)
    this.csrfToken = csrfToken
    return csrfToken
  }

  private async request(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (init.body !== undefined) {
      headers.set('Content-Type', 'application/json')
    }

    let response: Response
    try {
      response = await this.fetchRequest(path, {
        ...init,
        cache: 'no-store',
        credentials: 'same-origin',
        headers,
      })
    } catch {
      throw new AuthApiError('TEMPORARILY_UNAVAILABLE', 0)
    }

    if (!response.ok) {
      throw await parseApiError(response)
    }

    return response
  }
}

async function parseCreator(response: Response): Promise<Creator> {
  const payload = await parseJson(response)
  if (
    !isRecord(payload) ||
    typeof payload.id !== 'number' ||
    !Number.isSafeInteger(payload.id) ||
    payload.id < 1 ||
    typeof payload.email !== 'string' ||
    typeof payload.displayName !== 'string' ||
    payload.role !== 'ADMIN'
  ) {
    throw new AuthApiError('UNEXPECTED_RESPONSE', response.status)
  }

  return {
    id: payload.id,
    email: payload.email,
    displayName: payload.displayName,
    role: payload.role,
  }
}

async function parseCsrfToken(response: Response): Promise<CsrfToken> {
  const payload = await parseJson(response)
  if (
    !isRecord(payload) ||
    typeof payload.token !== 'string' ||
    payload.token.length === 0 ||
    typeof payload.headerName !== 'string' ||
    payload.headerName.length === 0
  ) {
    throw new AuthApiError('UNEXPECTED_RESPONSE', response.status)
  }

  return { token: payload.token, headerName: payload.headerName }
}

async function parseApiError(response: Response): Promise<AuthApiError> {
  let code: unknown
  try {
    const payload: unknown = await response.json()
    code = isRecord(payload) ? payload.code : undefined
  } catch {
    code = undefined
  }

  if (typeof code === 'string' && knownErrorCodes.has(code as AuthErrorCode)) {
    return new AuthApiError(code as AuthErrorCode, response.status)
  }

  if (response.status >= 500) {
    return new AuthApiError('TEMPORARILY_UNAVAILABLE', response.status)
  }

  return new AuthApiError('UNEXPECTED_RESPONSE', response.status)
}

async function parseJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    throw new AuthApiError('UNEXPECTED_RESPONSE', response.status)
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export const authClient = new SameOriginAuthClient((input, init) =>
  fetch(input, init),
)
