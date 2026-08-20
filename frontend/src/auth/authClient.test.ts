import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthApiError, SameOriginAuthClient } from './authClient.ts'

const creatorPayload = {
  id: 1,
  email: 'creator@example.test',
  displayName: 'Local Creator',
  role: 'ADMIN',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('SameOriginAuthClient', () => {
  it('should_refreshCsrfAcrossLoginAndLogout_when_authStateChanges', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('before-login')))
      .mockResolvedValueOnce(jsonResponse(creatorPayload))
      .mockResolvedValueOnce(jsonResponse(csrfPayload('after-login')))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(csrfPayload('after-logout')))
    const client = new SameOriginAuthClient(fetchRequest)

    await expect(
      client.login('creator@example.test', 'local-password'),
    ).resolves.toEqual(creatorPayload)
    await expect(client.logout()).resolves.toBeUndefined()

    expect(fetchRequest).toHaveBeenCalledTimes(5)
    expect(fetchRequest.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/csrf',
      '/api/auth/login',
      '/api/auth/csrf',
      '/api/auth/logout',
      '/api/auth/csrf',
    ])

    const loginInit = requestInit(fetchRequest, 1)
    expect(loginInit.credentials).toBe('same-origin')
    expect(loginInit.cache).toBe('no-store')
    expect(new Headers(loginInit.headers).get('X-CSRF-TOKEN')).toBe(
      'before-login',
    )
    expect(JSON.parse(loginInit.body as string)).toEqual({
      email: 'creator@example.test',
      password: 'local-password',
    })

    const logoutInit = requestInit(fetchRequest, 3)
    expect(new Headers(logoutInit.headers).get('X-CSRF-TOKEN')).toBe(
      'after-login',
    )
  })

  it('should_refreshAndRetryOnce_when_csrfIsStale', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('stale')))
      .mockResolvedValueOnce(apiError('CSRF_INVALID', 403))
      .mockResolvedValueOnce(jsonResponse(csrfPayload('fresh')))
      .mockResolvedValueOnce(jsonResponse(creatorPayload))
      .mockResolvedValueOnce(jsonResponse(csrfPayload('after-login')))
    const client = new SameOriginAuthClient(fetchRequest)

    await expect(
      client.login('creator@example.test', 'local-password'),
    ).resolves.toEqual(creatorPayload)

    expect(fetchRequest).toHaveBeenCalledTimes(5)
    expect(
      new Headers(requestInit(fetchRequest, 1).headers).get('X-CSRF-TOKEN'),
    ).toBe('stale')
    expect(
      new Headers(requestInit(fetchRequest, 3).headers).get('X-CSRF-TOKEN'),
    ).toBe('fresh')
  })

  it('should_stopAfterOneRetry_when_refreshedCsrfIsRejected', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('stale')))
      .mockResolvedValueOnce(apiError('CSRF_INVALID', 403))
      .mockResolvedValueOnce(jsonResponse(csrfPayload('also-stale')))
      .mockResolvedValueOnce(apiError('CSRF_INVALID', 403))
    const client = new SameOriginAuthClient(fetchRequest)

    await expect(
      client.login('creator@example.test', 'local-password'),
    ).rejects.toMatchObject({ code: 'CSRF_INVALID', status: 403 })
    expect(fetchRequest).toHaveBeenCalledTimes(4)
  })

  it('should_mapStableCode_withoutUsingServerMessageAsErrorText', async () => {
    const sensitiveServerMessage = 'wrong password was local-password'
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('token')))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: 'AUTH_INVALID_CREDENTIALS',
            message: sensitiveServerMessage,
            fieldErrors: [],
          },
          401,
        ),
      )
    const client = new SameOriginAuthClient(fetchRequest)

    let caughtError: unknown
    try {
      await client.login('creator@example.test', 'local-password')
    } catch (error) {
      caughtError = error
    }

    expect(caughtError).toBeInstanceOf(AuthApiError)
    expect(caughtError).toMatchObject({
      code: 'AUTH_INVALID_CREDENTIALS',
      status: 401,
    })
    expect((caughtError as Error).message).not.toContain(sensitiveServerMessage)
  })

  it('should_mapNetworkFailureToTemporarilyUnavailable', async () => {
    const fetchRequest = vi.fn().mockRejectedValue(new TypeError('network down'))
    const client = new SameOriginAuthClient(fetchRequest)

    await expect(client.me()).rejects.toMatchObject({
      code: 'TEMPORARILY_UNAVAILABLE',
      status: 0,
    })
  })

  it('should_mapAnonymousMeUsingStableAuthRequiredCode', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(apiError('AUTH_REQUIRED', 401))
    const client = new SameOriginAuthClient(fetchRequest)

    await expect(client.me()).rejects.toMatchObject({
      code: 'AUTH_REQUIRED',
      status: 401,
    })
  })
})

function csrfPayload(token: string) {
  return { headerName: 'X-CSRF-TOKEN', token }
}

function apiError(code: string, status: number) {
  return jsonResponse({ code, fieldErrors: [], message: 'safe summary' }, status)
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status,
  })
}

function requestInit(fetchRequest: ReturnType<typeof vi.fn>, call: number) {
  return fetchRequest.mock.calls[call][1] as RequestInit
}
