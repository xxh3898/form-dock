import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  PublicApiError,
  SameOriginPublicSurveyClient,
} from './publicSurveyClient.ts'

const publicSurveyPayload = {
  slug: 'project-research',
  title: '프로젝트 설문',
  description: '응답 안내',
  privacyNotice: null,
  questions: [
    question({ id: 10, position: 0, type: 'SHORT_TEXT' }),
    question({ id: 11, position: 1, type: 'LONG_TEXT' }),
    question({
      id: 12,
      position: 2,
      type: 'SINGLE_CHOICE',
      options: [option(21, '첫 번째', 0), option(22, '두 번째', 1)],
    }),
    question({
      id: 13,
      position: 3,
      type: 'MULTIPLE_CHOICE',
      options: [option(31, '가', 0), option(32, '나', 1)],
    }),
    question({
      id: 14,
      position: 4,
      type: 'SCALE',
      scaleMin: 1,
      scaleMax: 5,
      scaleMinLabel: '낮음',
      scaleMaxLabel: '높음',
    }),
    question({
      id: 15,
      position: 5,
      type: 'NUMBER',
      numberMin: '-12.34',
      numberMax: '999999999999999.9999',
    }),
  ],
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('SameOriginPublicSurveyClient', () => {
  it('should_parseStrictRespondentDto_withoutAdminFields', async () => {
    const fetchRequest = vi.fn().mockResolvedValue(jsonResponse(publicSurveyPayload))
    const client = new SameOriginPublicSurveyClient(fetchRequest)

    await expect(client.getSurvey('project-research')).resolves.toEqual(
      publicSurveyPayload,
    )

    expect(fetchRequest).toHaveBeenCalledOnce()
    expect(fetchRequest.mock.calls[0][0]).toBe(
      '/api/public/surveys/project-research',
    )
    const init = requestInit(fetchRequest, 0)
    expect(init.method).toBe('GET')
    expect(init.credentials).toBe('same-origin')
    expect(init.cache).toBe('no-store')
    expect(new Headers(init.headers).get('Accept')).toBe('application/json')
  })

  it('should_rejectMalformedOrExpandedPublicDto', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ ...publicSurveyPayload, id: 7 }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          ...publicSurveyPayload,
          questions: [
            question({ id: 10, position: 1, type: 'SHORT_TEXT' }),
          ],
        }),
      )
    const client = new SameOriginPublicSurveyClient(fetchRequest)

    await expect(client.getSurvey('project-research')).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
      status: 200,
    })
    await expect(client.getSurvey('project-research')).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
      status: 200,
    })
  })

  it('should_acceptCanonicalPublicDtoWithNoQuestions', async () => {
    const emptySurvey = { ...publicSurveyPayload, questions: [] }
    const client = new SameOriginPublicSurveyClient(
      vi.fn().mockResolvedValue(jsonResponse(emptySurvey)),
    )

    await expect(client.getSurvey('project-research')).resolves.toEqual(
      emptySurvey,
    )
  })

  it('should_submitExactJson_withoutFetchingCreatorCsrf', async () => {
    const fetchRequest = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          responseId: 9001,
          submittedAt: '2026-08-21T00:00:00Z',
          replayed: false,
        },
        201,
      ),
    )
    const client = new SameOriginPublicSurveyClient(fetchRequest)
    const input = {
      clientSubmissionId: '550e8400-e29b-41d4-a716-446655440000',
      answers: [{ questionId: 10, textValue: '  원문 유지  ' }],
    }

    await expect(
      client.submitResponse('project-research', input),
    ).resolves.toEqual({
      responseId: 9001,
      submittedAt: '2026-08-21T00:00:00Z',
      replayed: false,
    })

    expect(fetchRequest).toHaveBeenCalledOnce()
    const [path, rawInit] = fetchRequest.mock.calls[0]
    const init = rawInit as RequestInit
    expect(path).toBe('/api/public/surveys/project-research/responses')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('same-origin')
    expect(new Headers(init.headers).get('Content-Type')).toBe(
      'application/json',
    )
    expect(JSON.parse(init.body as string)).toEqual(input)
    expect(path).not.toBe('/api/auth/csrf')
  })

  it('should_acceptCanonicalReplayAndRejectStatusBodyMismatch', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          responseId: 9001,
          submittedAt: '2026-08-21T00:00:00Z',
          replayed: true,
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          responseId: 9002,
          submittedAt: '2026-08-21T00:00:00Z',
          replayed: false,
        }),
      )
    const client = new SameOriginPublicSurveyClient(fetchRequest)
    const input = {
      clientSubmissionId: '550e8400-e29b-41d4-a716-446655440000',
      answers: [],
    }

    await expect(
      client.submitResponse('project-research', input),
    ).resolves.toMatchObject({ replayed: true })
    await expect(
      client.submitResponse('project-research', input),
    ).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
      status: 200,
    })
  })

  it('should_preserveStableErrorCodeAndFieldErrors_withoutTrustingMessage', async () => {
    const fieldErrors = [
      {
        path: 'answers[0].textValue',
        code: 'TOO_LONG',
        message: 'Text answer exceeds the allowed length.',
      },
    ]
    const fetchRequest = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          code: 'RESPONSE_INVALID',
          message: 'Do not render this server message.',
          fieldErrors,
        },
        400,
      ),
    )
    const client = new SameOriginPublicSurveyClient(fetchRequest)

    let caught: unknown
    try {
      await client.submitResponse('project-research', {
        clientSubmissionId: '550e8400-e29b-41d4-a716-446655440000',
        answers: [],
      })
    } catch (error) {
      caught = error
    }

    expect(caught).toBeInstanceOf(PublicApiError)
    expect(caught).toMatchObject({
      code: 'RESPONSE_INVALID',
      fieldErrors,
      status: 400,
    })
    expect((caught as Error).message).not.toContain('Do not render')
  })

  it('should_mapNetworkAndMalformedErrorResponsesToSafeClientErrors', async () => {
    const networkClient = new SameOriginPublicSurveyClient(
      vi.fn().mockRejectedValue(new TypeError('internal network detail')),
    )
    await expect(networkClient.getSurvey('project-research')).rejects.toMatchObject({
      code: 'TEMPORARILY_UNAVAILABLE',
      status: 0,
    })

    const malformedClient = new SameOriginPublicSurveyClient(
      vi.fn().mockResolvedValue(jsonResponse({ message: 'unsafe detail' }, 503)),
    )
    await expect(malformedClient.getSurvey('project-research')).rejects.toMatchObject({
      code: 'TEMPORARILY_UNAVAILABLE',
      status: 503,
    })
  })
})

function question({
  id,
  position,
  type,
  options = [],
  scaleMin = null,
  scaleMax = null,
  scaleMinLabel = null,
  scaleMaxLabel = null,
  numberMin = null,
  numberMax = null,
}: {
  id: number
  position: number
  type: string
  options?: unknown[]
  scaleMin?: number | null
  scaleMax?: number | null
  scaleMinLabel?: string | null
  scaleMaxLabel?: string | null
  numberMin?: string | null
  numberMax?: string | null
}) {
  return {
    id,
    type,
    title: `질문 ${id}`,
    description: null,
    required: false,
    position,
    scaleMin,
    scaleMax,
    scaleMinLabel,
    scaleMaxLabel,
    numberMin,
    numberMax,
    options,
  }
}

function option(id: number, label: string, position: number) {
  return { id, label, position }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status,
  })
}

function requestInit(fetchRequest: ReturnType<typeof vi.fn>, index: number) {
  return fetchRequest.mock.calls[index][1] as RequestInit
}
