import {
  ApiError,
  type ApiErrorCode,
  type FetchFunction,
  isRecord,
  SameOriginApiClient,
} from '../api/apiClient.ts'

export { ApiError as AuthApiError } from '../api/apiClient.ts'

export type Creator = {
  id: number
  email: string
  displayName: string
  role: 'ADMIN'
}

export type AuthErrorCode = ApiErrorCode

export interface AuthClient {
  login(email: string, password: string): Promise<Creator>
  me(): Promise<Creator>
  logout(): Promise<void>
}

export class SameOriginAuthClient implements AuthClient {
  private readonly api: SameOriginApiClient

  constructor(fetchRequest: FetchFunction) {
    this.api = new SameOriginApiClient(fetchRequest)
  }

  async login(email: string, password: string): Promise<Creator> {
    const creator = await this.api.postJson(
      '/api/auth/login',
      { email, password },
      parseCreator,
    )
    await this.refreshCsrfAfterAuthStateChange()
    return creator
  }

  async me(): Promise<Creator> {
    return this.api.getJson('/api/auth/me', parseCreator)
  }

  async logout(): Promise<void> {
    await this.api.postEmpty('/api/auth/logout')
    await this.refreshCsrfAfterAuthStateChange()
  }

  private async refreshCsrfAfterAuthStateChange(): Promise<void> {
    try {
      await this.api.refreshCsrfToken()
    } catch {
      // The auth transition already succeeded. Keep no stale token; the next
      // unsafe request will obtain a fresh token before it is sent.
      this.api.clearCsrfToken()
    }
  }
}

function parseCreator(payload: unknown, status: number): Creator {
  if (
    !isRecord(payload) ||
    typeof payload.id !== 'number' ||
    !Number.isSafeInteger(payload.id) ||
    payload.id < 1 ||
    typeof payload.email !== 'string' ||
    typeof payload.displayName !== 'string' ||
    payload.role !== 'ADMIN'
  ) {
    throw new ApiError('UNEXPECTED_RESPONSE', status)
  }

  return {
    id: payload.id,
    email: payload.email,
    displayName: payload.displayName,
    role: payload.role,
  }
}

export const authClient = new SameOriginAuthClient((input, init) =>
  fetch(input, init),
)
