import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'

import type {
  PublicResponseReceipt,
  PublicSurvey,
  PublicSurveyClient,
  PublicSurveyQuestion,
} from '../public/publicSurveyClient.ts'
import { PublicApiError } from '../public/publicSurveyClient.ts'
import PublicSurveyPage from './PublicSurveyPage.tsx'

const submissionId = '550e8400-e29b-41d4-a716-446655440000'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('PublicSurveyPage', () => {
  it('should_renderLoadingThenRespondentSafeIntro', async () => {
    let resolveSurvey: ((survey: PublicSurvey) => void) | undefined
    const getSurvey = vi.fn(
      () =>
        new Promise<PublicSurvey>((resolve) => {
          resolveSurvey = resolve
        }),
    )
    renderPage(createClient({ getSurvey }))

    expect(screen.getByRole('status')).toHaveTextContent(
      '설문을 불러오는 중',
    )

    await act(async () => {
      resolveSurvey?.(surveyFixture)
    })

    expect(
      screen.getByRole('heading', { name: '프로젝트 경험 설문' }),
    ).toBeInTheDocument()
    expect(screen.getByText('참여 전 안내입니다.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '개인정보 안내' })).toBeInTheDocument()
    expect(screen.getByText('응답은 익명으로 수집됩니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '설문 시작' })).toBeEnabled()
  })

  it('should_renderAllSixTypesStepByStepAndSubmitExactPayload', async () => {
    const submitResponse = vi.fn(async () => receipt(false))
    renderPage(createClient({ submitResponse }))

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))

    expect(screen.getByText('질문 1 / 6')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('value', '1')
    fireEvent.change(screen.getByLabelText('단답 응답'), {
      target: { value: '  원문 유지  ' },
    })
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    expect(screen.getByRole('heading', { name: /장문 질문/ })).toHaveFocus()
    fireEvent.change(screen.getByLabelText('장문 응답'), {
      target: { value: '장문 응답' },
    })
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    fireEvent.click(screen.getByLabelText('두 번째'))
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    fireEvent.click(screen.getByLabelText('가'))
    fireEvent.click(screen.getByLabelText('나'))
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    fireEvent.click(screen.getByLabelText('3'))
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    fireEvent.change(screen.getByLabelText('숫자 응답'), {
      target: { value: '-12.3400' },
    })
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

    expect(
      await screen.findByRole('heading', { name: '응답이 제출되었습니다' }),
    ).toBeInTheDocument()
    expect(submitResponse).toHaveBeenCalledWith('project-experience', {
      clientSubmissionId: submissionId,
      answers: [
        { questionId: 10, textValue: '  원문 유지  ' },
        { questionId: 11, textValue: '장문 응답' },
        { questionId: 12, optionIds: [22] },
        { questionId: 13, optionIds: [31, 32] },
        { questionId: 14, numericValue: '3' },
        { questionId: 15, numericValue: '-12.3400' },
      ],
    })
    expect(screen.queryByText(/responseId|9001/i)).not.toBeInTheDocument()
  })

  it('should_keepCurrentStepAndFocusInvalidRequiredAnswer', async () => {
    renderPage(createClient())

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
    expect(screen.getByRole('group', { name: '단답 질문' })).toHaveAttribute(
      'aria-required',
      'true',
    )
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    expect(screen.getByRole('alert')).toHaveTextContent(
      '필수 질문에 응답해 주세요.',
    )
    expect(screen.getByRole('heading', { name: /단답 질문/ })).toHaveFocus()
    expect(screen.getByText('질문 1 / 6')).toBeInTheDocument()
  })

  it('should_showIdenticalUnavailableStateForPublicGet404', async () => {
    renderPage(
      createClient({
        getSurvey: vi.fn(async () => {
          throw new PublicApiError('SURVEY_NOT_FOUND', 404)
        }),
      }),
    )

    expect(
      await screen.findByRole('heading', { name: '설문을 사용할 수 없습니다' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('should_retrySafeLoadFailure', async () => {
    const getSurvey = vi
      .fn()
      .mockRejectedValueOnce(
        new PublicApiError('TEMPORARILY_UNAVAILABLE', 503),
      )
      .mockResolvedValueOnce(surveyFixture)
    renderPage(createClient({ getSurvey }))

    expect(
      await screen.findByRole('heading', { name: '설문을 불러올 수 없습니다' }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(
      await screen.findByRole('heading', { name: '프로젝트 경험 설문' }),
    ).toBeInTheDocument()
    expect(getSurvey).toHaveBeenCalledTimes(2)
  })

  it('should_reuseOneMemoryOnlySubmissionIdForTransientRetry', async () => {
    const oneQuestionSurvey = surveyWithQuestions([
      question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
    ])
    const submitResponse = vi
      .fn()
      .mockRejectedValueOnce(
        new PublicApiError('TEMPORARILY_UNAVAILABLE', 503),
      )
      .mockResolvedValueOnce(receipt(false))
    const idFactory = vi.fn(() => submissionId)
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    renderPage(createClient({ submitResponse, getSurvey: vi.fn(async () => oneQuestionSurvey) }), idFactory)

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
    fireEvent.change(screen.getByLabelText('단답 응답'), {
      target: { value: '응답' },
    })
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '같은 응답으로 안전하게 다시 시도할 수 있습니다.',
    )
    fireEvent.click(screen.getByRole('button', { name: '같은 응답 다시 제출' }))

    expect(
      await screen.findByRole('heading', { name: '응답이 제출되었습니다' }),
    ).toBeInTheDocument()
    expect(idFactory).toHaveBeenCalledOnce()
    expect(submitResponse).toHaveBeenCalledTimes(2)
    expect(submitResponse.mock.calls[0][1].clientSubmissionId).toBe(submissionId)
    expect(submitResponse.mock.calls[1][1].clientSubmissionId).toBe(submissionId)
    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('should_reuseSubmissionIdAfterRateLimitAndAnswerEdit', async () => {
    const oneQuestionSurvey = surveyWithQuestions([
      question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
    ])
    const submitResponse = vi
      .fn()
      .mockRejectedValueOnce(new PublicApiError('RATE_LIMITED', 429))
      .mockResolvedValueOnce(receipt(false))
    renderPage(
      createClient({
        getSurvey: vi.fn(async () => oneQuestionSurvey),
        submitResponse,
      }),
    )

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
    const input = screen.getByLabelText('단답 응답')
    fireEvent.change(input, { target: { value: '첫 응답' } })
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('요청이 많습니다.')
    fireEvent.change(input, { target: { value: '수정한 응답' } })
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

    expect(
      await screen.findByRole('heading', { name: '응답이 제출되었습니다' }),
    ).toBeInTheDocument()
    expect(submitResponse.mock.calls[0][1].clientSubmissionId).toBe(submissionId)
    expect(submitResponse.mock.calls[1][1]).toEqual({
      clientSubmissionId: submissionId,
      answers: [{ questionId: 10, textValue: '수정한 응답' }],
    })
  })

  it('should_preventDuplicateSubmitWhileRequestIsPending', async () => {
    let resolveSubmission: ((value: PublicResponseReceipt) => void) | undefined
    const oneQuestionSurvey = surveyWithQuestions([
      question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
    ])
    const submitResponse = vi.fn(
      () =>
        new Promise<PublicResponseReceipt>((resolve) => {
          resolveSubmission = resolve
        }),
    )
    renderPage(
      createClient({
        getSurvey: vi.fn(async () => oneQuestionSurvey),
        submitResponse,
      }),
    )

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
    fireEvent.change(screen.getByLabelText('단답 응답'), {
      target: { value: '응답' },
    })
    const submit = screen.getByRole('button', { name: '응답 제출' })
    fireEvent.click(submit)
    fireEvent.click(submit)

    expect(submitResponse).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: '제출 중…' })).toBeDisabled()

    await act(async () => {
      resolveSubmission?.(receipt(false))
    })
  })

  it('should_moveToRelevantQuestionForResponseInvalid_withoutRawServerMessage', async () => {
    const twoQuestionSurvey = surveyWithQuestions([
      question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
      question({ id: 15, position: 1, type: 'NUMBER' }),
    ])
    const submitResponse = vi.fn(async () => {
      throw new PublicApiError('RESPONSE_INVALID', 400, [
        {
          path: 'answers[0].textValue',
          code: 'INVALID_TEXT',
          message: 'English backend detail must not be shown.',
        },
      ])
    })
    renderPage(
      createClient({
        getSurvey: vi.fn(async () => twoQuestionSurvey),
        submitResponse,
      }),
    )

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
    fireEvent.change(screen.getByLabelText('단답 응답'), {
      target: { value: '응답' },
    })
    fireEvent.click(screen.getByRole('button', { name: '다음 질문' }))
    fireEvent.change(screen.getByLabelText('숫자 응답'), {
      target: { value: '10' },
    })
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

    expect(
      await screen.findByText('이 질문의 응답 형식을 확인해 주세요.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /단답 질문/ })).toHaveFocus()
    expect(screen.queryByText(/English backend detail/)).not.toBeInTheDocument()
  })

  it.each([
    ['SURVEY_NOT_OPEN', 409, '설문 응답이 마감되었습니다'],
    ['RESPONSE_DUPLICATE_CONFLICT', 409, '응답을 안전하게 확인할 수 없습니다'],
    ['SURVEY_NOT_FOUND', 404, '설문을 사용할 수 없습니다'],
  ] as const)(
    'should_renderStableTerminalStateFor_%s',
    async (code, status, heading) => {
      const oneQuestionSurvey = surveyWithQuestions([
        question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
      ])
      renderPage(
        createClient({
          getSurvey: vi.fn(async () => oneQuestionSurvey),
          submitResponse: vi.fn(async () => {
            throw new PublicApiError(code, status)
          }),
        }),
      )

      await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
      fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
      fireEvent.change(screen.getByLabelText('단답 응답'), {
        target: { value: '응답' },
      })
      fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

      expect(
        await screen.findByRole('heading', { name: heading }),
      ).toBeInTheDocument()
    },
  )

  it('should_preserveFormForPayloadTooLargeAndAcceptReplayCompletion', async () => {
    const oneQuestionSurvey = surveyWithQuestions([
      question({ id: 10, position: 0, type: 'LONG_TEXT', required: true }),
    ])
    const submitResponse = vi
      .fn()
      .mockRejectedValueOnce(
        new PublicApiError('RESPONSE_PAYLOAD_TOO_LARGE', 413),
      )
      .mockResolvedValueOnce(receipt(true))
    renderPage(
      createClient({
        getSurvey: vi.fn(async () => oneQuestionSurvey),
        submitResponse,
      }),
    )

    await screen.findByRole('heading', { name: '프로젝트 경험 설문' })
    fireEvent.click(screen.getByRole('button', { name: '설문 시작' }))
    fireEvent.change(screen.getByLabelText('장문 응답'), {
      target: { value: '보존해야 할 응답' },
    })
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '응답 전체 크기가 너무 큽니다.',
    )
    expect(screen.getByLabelText('장문 응답')).toHaveValue('보존해야 할 응답')
    fireEvent.click(screen.getByRole('button', { name: '응답 제출' }))
    expect(
      await screen.findByRole('heading', { name: '응답이 제출되었습니다' }),
    ).toBeInTheDocument()
  })
})

function renderPage(
  client: PublicSurveyClient,
  submissionIdFactory: () => string = () => submissionId,
) {
  return render(
    <MemoryRouter initialEntries={['/s/project-experience']}>
      <Routes>
        <Route
          element={
            <PublicSurveyPage
              client={client}
              submissionIdFactory={submissionIdFactory}
            />
          }
          path="/s/:slug"
        />
      </Routes>
    </MemoryRouter>,
  )
}

function createClient(
  overrides: Partial<PublicSurveyClient> = {},
): PublicSurveyClient {
  return {
    getSurvey: vi.fn(async () => surveyFixture),
    submitResponse: vi.fn(async () => receipt(false)),
    ...overrides,
  }
}

function receipt(replayed: boolean): PublicResponseReceipt {
  return {
    responseId: 9001,
    submittedAt: '2026-08-21T00:00:00Z',
    replayed,
  }
}

function surveyWithQuestions(questions: PublicSurveyQuestion[]): PublicSurvey {
  return { ...surveyFixture, questions }
}

const surveyFixture: PublicSurvey = {
  slug: 'project-experience',
  title: '프로젝트 경험 설문',
  description: '참여 전 안내입니다.',
  privacyNotice: '응답은 익명으로 수집됩니다.',
  questions: [
    question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
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
      numberMax: '100.0000',
    }),
  ],
}

function question({
  id,
  position,
  type,
  required = false,
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
  type: PublicSurveyQuestion['type']
  required?: boolean
  options?: PublicSurveyQuestion['options']
  scaleMin?: number | null
  scaleMax?: number | null
  scaleMinLabel?: string | null
  scaleMaxLabel?: string | null
  numberMin?: string | null
  numberMax?: string | null
}): PublicSurveyQuestion {
  return {
    id,
    type,
    title: `${type === 'SHORT_TEXT' ? '단답' : type === 'LONG_TEXT' ? '장문' : type} 질문`,
    description: null,
    required,
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
