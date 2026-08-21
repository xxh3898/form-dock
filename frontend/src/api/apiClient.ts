export type ApiErrorCode =
  | 'AUTH_INVALID_CREDENTIALS'
  | 'AUTH_REQUIRED'
  | 'CSRF_INVALID'
  | 'FORBIDDEN'
  | 'VALIDATION_FAILED'
  | 'SURVEY_NOT_FOUND'
  | 'SURVEY_STATE_CONFLICT'
  | 'SURVEY_STRUCTURE_LOCKED'
  | 'SURVEY_SLUG_CONFLICT'
  | 'SURVEY_SLUG_IMMUTABLE'
  | 'SURVEY_DELETE_REQUIRES_CLOSED'
  | 'SURVEY_INVALID_STRUCTURE'
  | 'QUESTION_NOT_FOUND'
  | 'QUESTION_INVALID_CONFIGURATION'
  | 'TEMPORARILY_UNAVAILABLE'
  | 'UNEXPECTED_RESPONSE'

export type ApiFieldError = {
  path: string
  code: string
  message: string
}

export class ApiError extends Error {
  readonly code: ApiErrorCode
  readonly status: number
  readonly fieldErrors: ApiFieldError[]

  constructor(
    code: ApiErrorCode,
    status: number,
    fieldErrors: ApiFieldError[] = [],
  ) {
    super(`FormDock API request failed (${code}).`)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export type FetchFunction = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response>

export type PayloadParser<T> = (payload: unknown, status: number) => T

type CsrfToken = {
  token: string
  headerName: string
}

const knownErrorCodes = new Set<ApiErrorCode>([
  'AUTH_INVALID_CREDENTIALS',
  'AUTH_REQUIRED',
  'CSRF_INVALID',
  'FORBIDDEN',
  'VALIDATION_FAILED',
  'SURVEY_NOT_FOUND',
  'SURVEY_STATE_CONFLICT',
  'SURVEY_STRUCTURE_LOCKED',
  'SURVEY_SLUG_CONFLICT',
  'SURVEY_SLUG_IMMUTABLE',
  'SURVEY_DELETE_REQUIRES_CLOSED',
  'SURVEY_INVALID_STRUCTURE',
  'QUESTION_NOT_FOUND',
  'QUESTION_INVALID_CONFIGURATION',
  'TEMPORARILY_UNAVAILABLE',
])

export class SameOriginApiClient {
  private csrfToken: CsrfToken | null = null
  private readonly fetchRequest: FetchFunction

  constructor(fetchRequest: FetchFunction) {
    this.fetchRequest = fetchRequest
  }

  async getJson<T>(path: string, parser: PayloadParser<T>): Promise<T> {
    const response = await this.request(path, { method: 'GET' })
    return this.parseJsonResponse(response, parser)
  }

  async postJson<T>(
    path: string,
    body: unknown | undefined,
    parser: PayloadParser<T>,
  ): Promise<T> {
    const response = await this.requestWithCsrf(path, {
      body: body === undefined ? undefined : JSON.stringify(body),
      method: 'POST',
    })
    return this.parseJsonResponse(response, parser)
  }

  async patchJson<T>(
    path: string,
    body: unknown,
    parser: PayloadParser<T>,
  ): Promise<T> {
    const response = await this.requestWithCsrf(path, {
      body: JSON.stringify(body),
      method: 'PATCH',
    })
    return this.parseJsonResponse(response, parser)
  }

  async postEmpty(path: string): Promise<void> {
    const response = await this.requestWithCsrf(path, { method: 'POST' })
    if (response.status !== 204) {
      throw new ApiError('UNEXPECTED_RESPONSE', response.status)
    }
  }

  async delete(path: string): Promise<void> {
    const response = await this.requestWithCsrf(path, { method: 'DELETE' })
    if (response.status !== 204) {
      throw new ApiError('UNEXPECTED_RESPONSE', response.status)
    }
  }

  clearCsrfToken(): void {
    this.csrfToken = null
  }

  async refreshCsrfToken(): Promise<void> {
    this.clearCsrfToken()
    await this.getCsrfToken()
  }

  private async requestWithCsrf(
    path: string,
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
          error instanceof ApiError &&
          error.code === 'CSRF_INVALID'
        ) {
          this.clearCsrfToken()
          csrfToken = await this.getCsrfToken()
          continue
        }

        if (error instanceof ApiError && error.code === 'CSRF_INVALID') {
          this.clearCsrfToken()
        }
        throw error
      }
    }

    throw new ApiError('CSRF_INVALID', 403)
  }

  private async getCsrfToken(): Promise<CsrfToken> {
    if (this.csrfToken !== null) {
      return this.csrfToken
    }

    const response = await this.request('/api/auth/csrf', { method: 'GET' })
    const token = await this.parseJsonResponse(response, parseCsrfToken)
    this.csrfToken = token
    return token
  }

  private async request(path: string, init: RequestInit): Promise<Response> {
    assertRelativeApiPath(path)

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
      throw new ApiError('TEMPORARILY_UNAVAILABLE', 0)
    }

    if (!response.ok) {
      throw await parseApiError(response)
    }

    return response
  }

  private async parseJsonResponse<T>(
    response: Response,
    parser: PayloadParser<T>,
  ): Promise<T> {
    let payload: unknown
    try {
      payload = await response.json()
    } catch {
      throw new ApiError('UNEXPECTED_RESPONSE', response.status)
    }

    try {
      return parser(payload, response.status)
    } catch (error) {
      if (error instanceof ApiError) {
        throw error
      }
      throw new ApiError('UNEXPECTED_RESPONSE', response.status)
    }
  }
}

function assertRelativeApiPath(path: string): void {
  if (!path.startsWith('/api/') || path.startsWith('//')) {
    throw new ApiError('UNEXPECTED_RESPONSE', 0)
  }
}

function parseCsrfToken(payload: unknown, status: number): CsrfToken {
  if (
    !isRecord(payload) ||
    typeof payload.token !== 'string' ||
    payload.token.length === 0 ||
    typeof payload.headerName !== 'string' ||
    payload.headerName.length === 0
  ) {
    throw new ApiError('UNEXPECTED_RESPONSE', status)
  }

  return { token: payload.token, headerName: payload.headerName }
}

async function parseApiError(response: Response): Promise<ApiError> {
  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  if (isRecord(payload) && typeof payload.code === 'string') {
    const code = payload.code as ApiErrorCode
    if (knownErrorCodes.has(code)) {
      const fieldErrors = parseFieldErrors(payload.fieldErrors)
      if (fieldErrors !== null) {
        return new ApiError(code, response.status, fieldErrors)
      }
    }
  }

  if (response.status >= 500) {
    return new ApiError('TEMPORARILY_UNAVAILABLE', response.status)
  }

  return new ApiError('UNEXPECTED_RESPONSE', response.status)
}

function parseFieldErrors(value: unknown): ApiFieldError[] | null {
  if (!Array.isArray(value)) {
    return null
  }

  const fieldErrors: ApiFieldError[] = []
  for (const item of value) {
    if (
      !isRecord(item) ||
      typeof item.path !== 'string' ||
      typeof item.code !== 'string' ||
      typeof item.message !== 'string'
    ) {
      return null
    }
    fieldErrors.push({
      path: item.path,
      code: item.code,
      message: item.message,
    })
  }
  return fieldErrors
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
