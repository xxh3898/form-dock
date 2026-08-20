import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../api/apiClient.ts'
import {
  SameOriginSurveyClient,
  type QuestionWriteInput,
} from './surveyClient.ts'

const detailPayload = {
  id: 7,
  title: 'Research survey',
  description: 'Description',
  slug: 'research-survey',
  privacyNotice: null,
  status: 'DRAFT',
  openedAt: null,
  closedAt: null,
  createdAt: '2026-08-20T00:00:00Z',
  updatedAt: '2026-08-20T00:10:00Z',
  responseCount: 0,
  structureLocked: false,
  questions: [
    {
      id: 12,
      type: 'NUMBER',
      title: 'Budget',
      description: null,
      required: true,
      position: 0,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: '0.0001',
      numberMax: '999999999999999.9999',
      options: [],
    },
  ],
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('SameOriginSurveyClient', () => {
  it('should_parseCanonicalListAndDetail_when_backendShapeIsValid', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse([
          {
            id: 7,
            title: 'Research survey',
            status: 'DRAFT',
            slug: 'research-survey',
            responseCount: 0,
            updatedAt: '2026-08-20T00:10:00Z',
          },
        ]),
      )
      .mockResolvedValueOnce(jsonResponse(detailPayload))
    const client = new SameOriginSurveyClient(fetchRequest)

    await expect(client.listSurveys()).resolves.toMatchObject([
      { id: 7, responseCount: 0, status: 'DRAFT' },
    ])
    await expect(client.getSurvey(7)).resolves.toMatchObject({
      questions: [
        {
          numberMin: '0.0001',
          numberMax: '999999999999999.9999',
          type: 'NUMBER',
        },
      ],
    })

    expect(fetchRequest.mock.calls.map(([path]) => path)).toEqual([
      '/api/surveys',
      '/api/surveys/7',
    ])
    expect(requestInit(fetchRequest, 0).credentials).toBe('same-origin')
  })

  it('should_rejectMalformedCanonicalDetail_withoutAcceptingPartialDto', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ ...detailPayload, responseCount: '0' }),
      )
    const client = new SameOriginSurveyClient(fetchRequest)

    await expect(client.getSurvey(7)).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
      status: 200,
    })
  })

  it('should_useMemoryCsrfAndRetryExactlyOnce_when_questionMutationTokenIsStale', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('stale')))
      .mockResolvedValueOnce(apiError('CSRF_INVALID', 403))
      .mockResolvedValueOnce(jsonResponse(csrfPayload('fresh')))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
    const client = new SameOriginSurveyClient(fetchRequest)
    const input: QuestionWriteInput = {
      type: 'NUMBER',
      title: 'Budget',
      description: null,
      required: true,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: '0.0001',
      numberMax: '999999999999999.9999',
      options: [],
    }

    await expect(client.createQuestion(7, input)).resolves.toMatchObject({
      id: 7,
    })

    expect(fetchRequest).toHaveBeenCalledTimes(4)
    expect(fetchRequest.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/csrf',
      '/api/surveys/7/questions',
      '/api/auth/csrf',
      '/api/surveys/7/questions',
    ])
    expect(
      new Headers(requestInit(fetchRequest, 1).headers).get('X-CSRF-TOKEN'),
    ).toBe('stale')
    expect(
      new Headers(requestInit(fetchRequest, 3).headers).get('X-CSRF-TOKEN'),
    ).toBe('fresh')
    expect(JSON.parse(requestInit(fetchRequest, 3).body as string)).toEqual(
      input,
    )
  })

  it('should_preserveStableCodeStatusAndFieldErrors_when_validationFails', async () => {
    const fieldErrors = [
      { path: 'options[0].label', code: 'REQUIRED', message: 'Required.' },
    ]
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('token')))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: 'QUESTION_INVALID_CONFIGURATION',
            message: 'Safe summary.',
            fieldErrors,
          },
          400,
        ),
      )
    const client = new SameOriginSurveyClient(fetchRequest)

    let caught: unknown
    try {
      await client.createQuestion(7, {
        type: 'SINGLE_CHOICE',
        title: 'Choice',
        description: null,
        required: false,
        scaleMin: null,
        scaleMax: null,
        scaleMinLabel: null,
        scaleMaxLabel: null,
        numberMin: null,
        numberMax: null,
        options: [{ label: '' }, { label: 'Two' }],
      })
    } catch (error) {
      caught = error
    }

    expect(caught).toBeInstanceOf(ApiError)
    expect(caught).toMatchObject({
      code: 'QUESTION_INVALID_CONFIGURATION',
      fieldErrors,
      status: 400,
    })
  })

  it('should_acceptOnly204_when_deletingAndMapNetworkFailureSafely', async () => {
    const deleteFetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('token')))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const deleteClient = new SameOriginSurveyClient(deleteFetch)

    await expect(deleteClient.deleteSurvey(7)).resolves.toBeUndefined()
    expect(deleteFetch.mock.calls[1][0]).toBe('/api/surveys/7')
    expect(requestInit(deleteFetch, 1).method).toBe('DELETE')

    const failedClient = new SameOriginSurveyClient(
      vi.fn().mockRejectedValue(new TypeError('network down')),
    )
    await expect(failedClient.listSurveys()).rejects.toMatchObject({
      code: 'TEMPORARILY_UNAVAILABLE',
      status: 0,
    })
  })

  it('should_callEveryBuilderMutationUsingCanonicalPathsAndBodies', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(csrfPayload('token')))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = new SameOriginSurveyClient(fetchRequest)
    const question: QuestionWriteInput = {
      type: 'SHORT_TEXT',
      title: 'Question',
      description: null,
      required: false,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: null,
      numberMax: null,
      options: [],
    }

    await client.createSurvey({
      title: 'Survey',
      description: null,
      privacyNotice: null,
      slug: null,
    })
    await client.updateSurvey(7, { title: 'Updated' })
    await client.duplicateSurvey(7)
    await client.openSurvey(7)
    await client.closeSurvey(7)
    await client.updateQuestion(7, 12, question)
    await client.reorderQuestions(7, [12])
    await client.deleteQuestion(7, 12)

    expect(fetchRequest.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/csrf',
      '/api/surveys',
      '/api/surveys/7',
      '/api/surveys/7/duplicate',
      '/api/surveys/7/open',
      '/api/surveys/7/close',
      '/api/surveys/7/questions/12',
      '/api/surveys/7/questions/reorder',
      '/api/surveys/7/questions/12',
    ])
    expect(requestInit(fetchRequest, 1).method).toBe('POST')
    expect(requestInit(fetchRequest, 2).method).toBe('PATCH')
    expect(JSON.parse(requestInit(fetchRequest, 7).body as string)).toEqual({
      questionIds: [12],
    })
    expect(requestInit(fetchRequest, 8).method).toBe('DELETE')
  })

  it('should_surfaceAuthRequired_withoutPersistingTransportState', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(apiError('AUTH_REQUIRED', 401))
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const client = new SameOriginSurveyClient(fetchRequest)

    await expect(client.listSurveys()).rejects.toMatchObject({
      code: 'AUTH_REQUIRED',
      status: 401,
    })
    expect(storageWrite).not.toHaveBeenCalled()
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
