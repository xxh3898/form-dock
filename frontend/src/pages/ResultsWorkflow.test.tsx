import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import App from '../App.tsx'
import { ApiError } from '../api/apiClient.ts'
import {
  AuthApiError,
  type AuthClient,
  type Creator,
} from '../auth/authClient.ts'
import type {
  ResponseDetail,
  ResponsePage,
  ResponseSummary,
  ResultsClient,
} from '../results/resultsClient.ts'
import type {
  SurveyClient,
  SurveyDetail,
} from '../surveys/surveyClient.ts'

const originalCreateObjectUrl = Object.getOwnPropertyDescriptor(
  URL,
  'createObjectURL',
)
const originalRevokeObjectUrl = Object.getOwnPropertyDescriptor(
  URL,
  'revokeObjectURL',
)

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  restoreUrlMethod('createObjectURL', originalCreateObjectUrl)
  restoreUrlMethod('revokeObjectURL', originalRevokeObjectUrl)
})

describe('Phase 4-D Results workflow', () => {
  it('should_renderOverviewSummaryAndNewestFirstResponseList_withSemanticTables', async () => {
    renderAt('/admin/surveys/7/responses')

    const heading = await screen.findByRole('heading', { name: '응답 결과' })
    await waitFor(() => expect(heading).toHaveFocus())
    expect(screen.getByText('마감')).toBeInTheDocument()
    expect(screen.getAllByText('2026. 8. 23. 오전 12:02')).toHaveLength(2)
    expect(screen.getByText('150.00%')).toBeInTheDocument()
    expect(screen.getByText('Zero option')).toBeInTheDocument()
    expect(screen.getByText('평균: 2.50')).toBeInTheDocument()
    expect(screen.getAllByText('응답 수만 집계합니다.')).toHaveLength(3)

    const responseList = screen
      .getByRole('heading', { name: '개별 응답' })
      .closest('section')
    expect(responseList).not.toBeNull()
    const rows = within(responseList as HTMLElement).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('#102')
    expect(rows[2]).toHaveTextContent('#101')
    expect(
      within(responseList as HTMLElement).getAllByRole('link', {
        name: '응답 보기',
      })[0],
    ).toHaveAttribute('href', '/admin/surveys/7/responses/102')
    expect(
      within(responseList as HTMLElement).getByRole('region', {
        name: '개별 응답 표',
      }),
    ).toBeInTheDocument()
  })

  it('should_renderZeroResponseAsNormalEmptyState_withNoLastSubmission', async () => {
    const results = createResultsClient({
      getSummary: vi.fn(async () => ({
        ...summary,
        totalResponses: 0,
        lastSubmittedAt: null,
      })),
      listResponses: vi.fn(async () => ({
        items: [],
        page: 0,
        size: 50,
        totalElements: 0,
        totalPages: 0,
      })),
    })

    renderAt('/admin/surveys/7/responses', results)

    expect(await screen.findByText('아직 제출된 응답이 없습니다')).toBeInTheDocument()
    const overview = screen
      .getByRole('heading', { name: '요약' })
      .closest('section')
    expect(overview).not.toBeNull()
    expect(within(overview as HTMLElement).getByText('없음')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: '응답 페이지' })).not.toBeInTheDocument()
  })

  it('should_preventDuplicatePaginationAndRecoverFromOutOfRangePage', async () => {
    let resolveSecondPage: ((page: ResponsePage) => void) | undefined
    const listResponses = vi
      .fn()
      .mockResolvedValueOnce({
        ...firstPage,
        totalElements: 51,
        totalPages: 2,
      })
      .mockImplementationOnce(
        () =>
          new Promise<ResponsePage>((resolve) => {
            resolveSecondPage = resolve
          }),
      )
    renderAt(
      '/admin/surveys/7/responses',
      createResultsClient({ listResponses }),
    )

    const next = await screen.findByRole('button', { name: '다음' })
    fireEvent.click(next)
    fireEvent.click(next)

    expect(listResponses).toHaveBeenCalledTimes(2)
    expect(listResponses).toHaveBeenLastCalledWith(7, 1, 50)
    expect(next).toBeDisabled()

    await act(async () => {
      resolveSecondPage?.({
        items: [],
        page: 1,
        size: 50,
        totalElements: 51,
        totalPages: 2,
      })
    })

    expect(screen.getByText('이 페이지에는 응답이 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이전' })).toBeEnabled()
    expect(screen.getByText('2 / 2 페이지')).toBeInTheDocument()
  })

  it('should_renderSixTypesUnansweredAndExactMultilineText_withoutMutationControls', async () => {
    renderAt('/admin/surveys/7/responses/102')

    expect(
      await screen.findByRole('heading', { name: '응답 #102' }),
    ).toHaveFocus()
    const multiline = document.querySelectorAll('.response-text-value')[1]
    expect(multiline).toHaveTextContent('<strong>원문</strong> 둘째 줄')
    expect(multiline.textContent).toBe('<strong>원문</strong>\n둘째 줄')
    expect(multiline.querySelector('strong')).toBeNull()
    expect(screen.getByText('-12.34')).toBeInTheDocument()
    expect(screen.getByText('응답 없음')).toBeInTheDocument()

    const multipleArticle = screen
      .getByRole('heading', { name: '복수 선택 답변' })
      .closest('article')
    expect(multipleArticle).not.toBeNull()
    expect(
      within(multipleArticle as HTMLElement)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['첫 번째', '세 번째'])
    expect(screen.queryByRole('button', { name: /삭제|편집|제외/ })).not.toBeInTheDocument()
  })

  it('should_keepCsvSingleFlightAndRevokeObjectUrl_afterSuccessfulDownload', async () => {
    let resolveDownload:
      | ((download: { blob: Blob; filename: string }) => void)
      | undefined
    const downloadCsv = vi.fn(
      () =>
        new Promise<{ blob: Blob; filename: string }>((resolve) => {
          resolveDownload = resolve
        }),
    )
    const createObjectUrl = vi.fn(() => 'blob:formdock-result')
    const revokeObjectUrl = vi.fn()
    defineUrlMethod('createObjectURL', createObjectUrl)
    defineUrlMethod('revokeObjectURL', revokeObjectUrl)
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderAt(
      '/admin/surveys/7/responses',
      createResultsClient({ downloadCsv }),
    )

    const button = await screen.findByRole('button', { name: 'CSV 다운로드' })
    fireEvent.click(button)
    fireEvent.click(button)

    expect(downloadCsv).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'CSV 준비 중…' })).toBeDisabled()

    const blob = new Blob(['csv'], { type: 'text/csv' })
    await act(async () => {
      resolveDownload?.({
        blob,
        filename: 'formdock-survey-7-responses.csv',
      })
    })

    expect(createObjectUrl).toHaveBeenCalledWith(blob)
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:formdock-result')
    expect(screen.getByRole('button', { name: 'CSV 다운로드' })).toBeEnabled()
  })

  it('should_keepCurrentResultsPage_whenCsvDownloadFailsSafely', async () => {
    const downloadCsv = vi.fn(async () => {
      throw new ApiError('TEMPORARILY_UNAVAILABLE', 503)
    })
    renderAt(
      '/admin/surveys/7/responses',
      createResultsClient({ downloadCsv }),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'CSV 다운로드' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'CSV 파일을 내려받지 못했습니다.',
    )
    expect(screen.getByRole('heading', { name: '개별 응답' })).toBeInTheDocument()
    expect(document.querySelector('a[download]')).toBeNull()
  })

  it('should_showStableSafeStatesAndRetry_withoutRawBackendMessage', async () => {
    const getSummary = vi
      .fn()
      .mockRejectedValueOnce(new ApiError('TEMPORARILY_UNAVAILABLE', 503))
      .mockResolvedValueOnce(summary)
    const results = createResultsClient({ getSummary })
    renderAt('/admin/surveys/7/responses', results)

    expect(
      await screen.findByRole('heading', {
        name: '응답 결과를 불러올 수 없습니다',
      }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(
      await screen.findByRole('heading', { name: '응답 결과' }),
    ).toBeInTheDocument()
    expect(getSummary).toHaveBeenCalledTimes(2)
    expect(screen.queryByText(/FormDock API request failed/)).not.toBeInTheDocument()
  })

  it('should_concealMissingSurveyAndResponse_andBlockMalformedRouteRequests', async () => {
    const missingSurvey = createResultsClient({
      getSummary: vi.fn(async () => {
        throw new ApiError('SURVEY_NOT_FOUND', 404)
      }),
    })
    const first = renderAt('/admin/surveys/7/responses', missingSurvey)
    expect(
      await screen.findByRole('heading', { name: '설문을 찾을 수 없습니다' }),
    ).toBeInTheDocument()
    first.unmount()

    const missingResponse = createResultsClient({
      getResponseDetail: vi.fn(async () => {
        throw new ApiError('RESPONSE_NOT_FOUND', 404)
      }),
    })
    const second = renderAt('/admin/surveys/7/responses/999', missingResponse)
    expect(
      await screen.findByRole('heading', { name: '응답을 찾을 수 없습니다' }),
    ).toBeInTheDocument()
    second.unmount()

    const malformed = createResultsClient()
    renderAt('/admin/surveys/not-an-id/responses/0', malformed)
    expect(
      await screen.findByRole('heading', { name: '응답을 찾을 수 없습니다' }),
    ).toBeInTheDocument()
    expect(malformed.getResponseDetail).not.toHaveBeenCalled()
  })

  it('should_useSharedAdminGuardAndSurveyListNavigation_withoutPublicStateLeak', async () => {
    const results = createResultsClient()
    const anonymous = createAuthClient({
      me: vi.fn(async () => {
        throw new AuthApiError('AUTH_REQUIRED', 401)
      }),
    })
    const first = renderAt('/admin/surveys/7/responses', results, anonymous)

    expect(
      await screen.findByRole('heading', { name: '관리자 로그인' }),
    ).toBeInTheDocument()
    expect(results.getSummary).not.toHaveBeenCalled()
    first.unmount()

    renderAt('/admin/surveys', results)
    const link = await screen.findByRole('link', { name: '응답 보기' })
    expect(link).toHaveAttribute('href', '/admin/surveys/7/responses')
    fireEvent.click(link)
    expect(
      await screen.findByRole('heading', { name: '응답 결과' }),
    ).toBeInTheDocument()
  })
})

function renderAt(
  path: string,
  results: ResultsClient = createResultsClient(),
  auth: AuthClient = createAuthClient(),
  surveys: SurveyClient = createSurveyClient(),
) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App client={auth} results={results} surveys={surveys} />
    </MemoryRouter>,
  )
}

function createResultsClient(
  overrides: Partial<ResultsClient> = {},
): ResultsClient {
  return {
    downloadCsv: vi.fn(async () => ({
      blob: new Blob(['csv'], { type: 'text/csv' }),
      filename: 'formdock-survey-7-responses.csv',
    })),
    getResponseDetail: vi.fn(async () => detail),
    getSummary: vi.fn(async () => summary),
    listResponses: vi.fn(async () => firstPage),
    ...overrides,
  }
}

function createAuthClient(overrides: Partial<AuthClient> = {}): AuthClient {
  return {
    login: vi.fn(async () => creator),
    logout: vi.fn(async () => undefined),
    me: vi.fn(async () => creator),
    ...overrides,
  }
}

function createSurveyClient(
  overrides: Partial<SurveyClient> = {},
): SurveyClient {
  return {
    closeSurvey: vi.fn(async () => survey),
    createQuestion: vi.fn(async () => survey),
    createSurvey: vi.fn(async () => survey),
    deleteQuestion: vi.fn(async () => undefined),
    deleteSurvey: vi.fn(async () => undefined),
    duplicateSurvey: vi.fn(async () => survey),
    getSurvey: vi.fn(async () => survey),
    listSurveys: vi.fn(async () => [
      {
        id: survey.id,
        title: survey.title,
        status: survey.status,
        slug: survey.slug,
        responseCount: survey.responseCount,
        updatedAt: survey.updatedAt,
      },
    ]),
    openSurvey: vi.fn(async () => survey),
    reorderQuestions: vi.fn(async () => survey),
    updateQuestion: vi.fn(async () => survey),
    updateSurvey: vi.fn(async () => survey),
    ...overrides,
  }
}

function defineUrlMethod(name: string, value: unknown) {
  Object.defineProperty(URL, name, { configurable: true, value })
}

function restoreUrlMethod(
  name: string,
  descriptor: PropertyDescriptor | undefined,
) {
  if (descriptor === undefined) {
    Reflect.deleteProperty(URL, name)
  } else {
    Object.defineProperty(URL, name, descriptor)
  }
}

const creator: Creator = {
  id: 1,
  email: 'creator@example.test',
  displayName: 'Local Creator',
  role: 'ADMIN',
}

const survey: SurveyDetail = {
  id: 7,
  title: 'Research survey',
  description: null,
  slug: 'research-survey',
  privacyNotice: null,
  status: 'CLOSED',
  openedAt: '2026-08-20T00:00:00Z',
  closedAt: '2026-08-23T00:03:00Z',
  createdAt: '2026-08-20T00:00:00Z',
  updatedAt: '2026-08-23T00:03:00Z',
  responseCount: 3,
  structureLocked: true,
  questions: [],
}

const firstPage: ResponsePage = {
  items: [
    { responseId: 102, submittedAt: '2026-08-23T00:02:00Z' },
    { responseId: 101, submittedAt: '2026-08-23T00:01:00Z' },
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 1,
}

const summary: ResponseSummary = {
  surveyId: 7,
  status: 'CLOSED',
  totalResponses: 2,
  lastSubmittedAt: '2026-08-23T00:02:00Z',
  questionCount: 6,
  questions: [
    {
      questionId: 10,
      type: 'SHORT_TEXT',
      title: '단답형 요약',
      position: 0,
      answeredCount: 2,
    },
    {
      questionId: 11,
      type: 'LONG_TEXT',
      title: '장문형 요약',
      position: 1,
      answeredCount: 2,
    },
    {
      questionId: 12,
      type: 'SINGLE_CHOICE',
      title: '단일 선택 요약',
      position: 2,
      answeredCount: 3,
      options: [
        { optionId: 21, label: 'Alpha', position: 0, count: 3, percentage: '100.00' },
        { optionId: 22, label: 'Zero option', position: 1, count: 0, percentage: '0.00' },
      ],
    },
    {
      questionId: 13,
      type: 'MULTIPLE_CHOICE',
      title: '복수 선택 요약',
      position: 3,
      answeredCount: 2,
      options: [
        { optionId: 31, label: 'First', position: 0, count: 2, percentage: '100.00' },
        { optionId: 32, label: 'Second', position: 1, count: 3, percentage: '150.00' },
      ],
    },
    {
      questionId: 14,
      type: 'SCALE',
      title: '척도 요약',
      position: 4,
      answeredCount: 2,
      average: '2.50',
      distribution: [
        { value: 1, count: 0, percentage: '0.00' },
        { value: 2, count: 1, percentage: '50.00' },
        { value: 3, count: 1, percentage: '50.00' },
      ],
    },
    {
      questionId: 15,
      type: 'NUMBER',
      title: '숫자 요약',
      position: 5,
      answeredCount: 2,
    },
  ],
}

const detail: ResponseDetail = {
  responseId: 102,
  submittedAt: '2026-08-23T00:02:00Z',
  questions: [
    detailQuestion(10, 0, 'SHORT_TEXT', {
      textValue: '짧은 응답',
      numericValue: null,
      options: [],
    }),
    detailQuestion(11, 1, 'LONG_TEXT', {
      textValue: '<strong>원문</strong>\n둘째 줄',
      numericValue: null,
      options: [],
    }),
    detailQuestion(12, 2, 'SINGLE_CHOICE', {
      textValue: null,
      numericValue: null,
      options: [{ id: 21, label: 'Alpha', position: 0 }],
    }),
    detailQuestion(13, 3, 'MULTIPLE_CHOICE', {
      textValue: null,
      numericValue: null,
      options: [
        { id: 31, label: '첫 번째', position: 0 },
        { id: 33, label: '세 번째', position: 2 },
      ],
    }),
    detailQuestion(14, 4, 'SCALE', {
      textValue: null,
      numericValue: '3',
      options: [],
    }),
    detailQuestion(15, 5, 'NUMBER', {
      textValue: null,
      numericValue: '-12.34',
      options: [],
    }),
    {
      ...detailQuestion(16, 6, 'SHORT_TEXT', null),
      required: false,
    },
  ],
}

function detailQuestion(
  questionId: number,
  position: number,
  type: ResponseDetail['questions'][number]['type'],
  answer: ResponseDetail['questions'][number]['answer'],
) {
  return {
    questionId,
    type,
    title: `${type === 'MULTIPLE_CHOICE' ? '복수 선택' : type} 답변`,
    description: null,
    required: true,
    position,
    answer,
  }
}
