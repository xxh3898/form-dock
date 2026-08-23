import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../api/apiClient.ts'
import { SameOriginResultsClient } from './resultsClient.ts'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('SameOriginResultsClient', () => {
  it('should_parseCanonicalListSummaryAndSixTypeDetail_when_backendShapeIsValid', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(pagePayload))
      .mockResolvedValueOnce(jsonResponse(summaryPayload))
      .mockResolvedValueOnce(jsonResponse(detailPayload))
    const client = new SameOriginResultsClient(fetchRequest)

    await expect(client.listResponses(7)).resolves.toMatchObject({
      items: [{ responseId: 102 }, { responseId: 101 }, { responseId: 100 }],
      page: 0,
      size: 50,
      totalElements: 3,
      totalPages: 1,
    })
    await expect(client.getSummary(7)).resolves.toMatchObject({
      status: 'CLOSED',
      totalResponses: 3,
      questions: [
        { type: 'SHORT_TEXT', answeredCount: 2 },
        { type: 'SINGLE_CHOICE', options: [{ count: 2 }, { count: 1 }] },
        { type: 'SCALE', average: '2.50' },
        { type: 'NUMBER', answeredCount: 2 },
      ],
    })
    await expect(client.getResponseDetail(7, 102)).resolves.toMatchObject({
      responseId: 102,
      questions: [
        { type: 'SHORT_TEXT', answer: { textValue: 'exact text' } },
        { type: 'LONG_TEXT', answer: { textValue: 'line one\nline two' } },
        { type: 'SINGLE_CHOICE', answer: { options: [{ id: 21 }] } },
        {
          type: 'MULTIPLE_CHOICE',
          answer: { options: [{ id: 31 }, { id: 33 }] },
        },
        { type: 'SCALE', answer: { numericValue: '3' } },
        { type: 'NUMBER', answer: { numericValue: '-12.34' } },
        { type: 'SHORT_TEXT', answer: null },
      ],
    })

    expect(fetchRequest.mock.calls.map(([path]) => path)).toEqual([
      '/api/surveys/7/responses?page=0&size=50',
      '/api/surveys/7/responses/summary',
      '/api/surveys/7/responses/102',
    ])
    expect(requestInit(fetchRequest, 0).credentials).toBe('same-origin')
    expect(new Headers(requestInit(fetchRequest, 0).headers).get('Accept')).toBe(
      'application/json',
    )
  })

  it('should_rejectMalformedCanonicalResponses_withoutAcceptingUnsafeWireShape', async () => {
    const outOfOrder = {
      ...pagePayload,
      items: [pagePayload.items[1], pagePayload.items[0]],
      totalElements: 2,
    }
    const malformedSummary = {
      ...summaryPayload,
      questions: summaryPayload.questions.map((question, index) =>
        index === 1 && question.options !== undefined
          ? {
              ...question,
              options: [
                { ...question.options[0], percentage: 'raw percentage' },
                question.options[1],
              ],
            }
          : question,
      ),
    }
    const malformedDetail = {
      ...detailPayload,
      questions: detailPayload.questions.map((question, index) =>
        index === 0
          ? {
              ...question,
              answer: {
                textValue: 'text',
                numericValue: '1',
                options: [],
              },
            }
          : question,
      ),
    }
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(outOfOrder))
      .mockResolvedValueOnce(jsonResponse(malformedSummary))
      .mockResolvedValueOnce(jsonResponse(malformedDetail))
      .mockResolvedValueOnce(
        jsonResponse({ ...summaryPayload, lastSubmittedAt: undefined }),
      )
    const client = new SameOriginResultsClient(fetchRequest)

    await expect(client.listResponses(7)).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
    })
    await expect(client.getSummary(7)).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
    })
    await expect(client.getResponseDetail(7, 102)).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
    })
    await expect(client.getSummary(7)).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
    })
  })

  it('should_rejectInvalidIdsAndPagination_beforeSendingRequest', async () => {
    const fetchRequest = vi.fn()
    const client = new SameOriginResultsClient(fetchRequest)

    await expect(client.getSummary(0)).rejects.toBeInstanceOf(ApiError)
    await expect(client.getResponseDetail(7, Number.MAX_SAFE_INTEGER + 1)).rejects.toBeInstanceOf(
      ApiError,
    )
    await expect(client.listResponses(7, -1, 50)).rejects.toBeInstanceOf(
      ApiError,
    )
    await expect(client.listResponses(7, 0, 101)).rejects.toBeInstanceOf(
      ApiError,
    )
    expect(fetchRequest).not.toHaveBeenCalled()
  })

  it('should_preserveResponseNotFoundAsStableCode_withoutUsingRawMessage', async () => {
    const fetchRequest = vi.fn().mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'RESPONSE_NOT_FOUND',
          fieldErrors: [],
          message: 'Backend message that must not drive the UI.',
        },
        404,
      ),
    )
    const client = new SameOriginResultsClient(fetchRequest)

    await expect(client.getResponseDetail(7, 999)).rejects.toMatchObject({
      code: 'RESPONSE_NOT_FOUND',
      status: 404,
    })
  })

  it('should_downloadSameOriginCsv_withCanonicalFilenameAndSafeFallback', async () => {
    const fetchRequest = vi
      .fn()
      .mockResolvedValueOnce(
        csvResponse(
          '\ufeffresponse_id,submitted_at\r\n',
          'attachment; filename="formdock-survey-7-responses.csv"',
        ),
      )
      .mockResolvedValueOnce(
        csvResponse('safe', 'attachment; filename="../../unsafe.csv"'),
      )
    const client = new SameOriginResultsClient(fetchRequest)

    const canonical = await client.downloadCsv(7)
    const fallback = await client.downloadCsv(7)

    expect(canonical.filename).toBe('formdock-survey-7-responses.csv')
    expect(
      Array.from(new Uint8Array(await canonical.blob.arrayBuffer()).slice(0, 3)),
    ).toEqual([0xef, 0xbb, 0xbf])
    expect(await canonical.blob.text()).toBe('response_id,submitted_at\r\n')
    expect(fallback.filename).toBe('formdock-survey-7-responses.csv')
    expect(fetchRequest.mock.calls.map(([path]) => path)).toEqual([
      '/api/surveys/7/responses/export.csv',
      '/api/surveys/7/responses/export.csv',
    ])
    expect(requestInit(fetchRequest, 0).credentials).toBe('same-origin')
    expect(new Headers(requestInit(fetchRequest, 0).headers).get('Accept')).toBe(
      'text/csv',
    )
  })

  it.each([
    ['AUTH_REQUIRED', 401],
    ['SURVEY_NOT_FOUND', 404],
    ['TEMPORARILY_UNAVAILABLE', 503],
  ])(
    'should_surfaceJsonCsvError_withoutSavingErrorBodyAsFile (%s)',
    async (code, status) => {
      const blobRead = vi.spyOn(Response.prototype, 'blob')
      const fetchRequest = vi.fn().mockResolvedValueOnce(
        jsonResponse(
          {
            code,
            fieldErrors: [],
            message: 'Safe summary.',
          },
          status,
        ),
      )
      const client = new SameOriginResultsClient(fetchRequest)

      await expect(client.downloadCsv(7)).rejects.toMatchObject({
        code,
        status,
      })
      expect(blobRead).not.toHaveBeenCalled()
    },
  )
})

const pagePayload = {
  items: [
    { responseId: 102, submittedAt: '2026-08-23T00:02:00Z' },
    { responseId: 101, submittedAt: '2026-08-23T00:01:00Z' },
    { responseId: 100, submittedAt: '2026-08-23T00:01:00Z' },
  ],
  page: 0,
  size: 50,
  totalElements: 3,
  totalPages: 1,
}

const summaryPayload = {
  surveyId: 7,
  status: 'CLOSED',
  totalResponses: 3,
  lastSubmittedAt: '2026-08-23T00:02:00Z',
  questionCount: 4,
  questions: [
    {
      questionId: 10,
      type: 'SHORT_TEXT',
      title: 'Short',
      position: 0,
      answeredCount: 2,
    },
    {
      questionId: 11,
      type: 'SINGLE_CHOICE',
      title: 'Choice',
      position: 1,
      answeredCount: 3,
      options: [
        {
          optionId: 21,
          label: 'Alpha',
          position: 0,
          count: 2,
          percentage: '66.67',
        },
        {
          optionId: 22,
          label: 'Beta',
          position: 1,
          count: 1,
          percentage: '33.33',
        },
      ],
    },
    {
      questionId: 12,
      type: 'SCALE',
      title: 'Scale',
      position: 2,
      answeredCount: 2,
      average: '2.50',
      distribution: [
        { value: 1, count: 0, percentage: '0.00' },
        { value: 2, count: 1, percentage: '50.00' },
        { value: 3, count: 1, percentage: '50.00' },
      ],
    },
    {
      questionId: 13,
      type: 'NUMBER',
      title: 'Number',
      position: 3,
      answeredCount: 2,
    },
  ],
}

const detailPayload = {
  responseId: 102,
  submittedAt: '2026-08-23T00:02:00Z',
  questions: [
    question(10, 0, 'SHORT_TEXT', {
      textValue: 'exact text',
      numericValue: null,
      options: [],
    }),
    question(11, 1, 'LONG_TEXT', {
      textValue: 'line one\nline two',
      numericValue: null,
      options: [],
    }),
    question(12, 2, 'SINGLE_CHOICE', {
      textValue: null,
      numericValue: null,
      options: [{ id: 21, label: 'Alpha', position: 0 }],
    }),
    question(13, 3, 'MULTIPLE_CHOICE', {
      textValue: null,
      numericValue: null,
      options: [
        { id: 31, label: 'First', position: 0 },
        { id: 33, label: 'Third', position: 2 },
      ],
    }),
    question(14, 4, 'SCALE', {
      textValue: null,
      numericValue: '3',
      options: [],
    }),
    question(15, 5, 'NUMBER', {
      textValue: null,
      numericValue: '-12.34',
      options: [],
    }),
    {
      ...question(16, 6, 'SHORT_TEXT', null),
      required: false,
    },
  ],
}

function question(
  questionId: number,
  position: number,
  type: string,
  answer: unknown,
) {
  return {
    questionId,
    type,
    title: `${type} question`,
    description: null,
    required: true,
    position,
    answer,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status,
  })
}

function csvResponse(body: string, contentDisposition: string) {
  return new Response(body, {
    headers: {
      'Content-Disposition': contentDisposition,
      'Content-Type': 'text/csv; charset=UTF-8',
    },
    status: 200,
  })
}

function requestInit(fetchRequest: ReturnType<typeof vi.fn>, call: number) {
  return fetchRequest.mock.calls[call][1] as RequestInit
}
