import { describe, expect, it } from 'vitest'

import type { PublicSurveyQuestion } from './publicSurveyClient.ts'
import {
  buildPublicResponseSubmission,
  createPublicAnswerState,
  firstInvalidPublicQuestion,
  publicResponseFieldFeedback,
  validatePublicAnswer,
} from './publicResponseForm.ts'

const questions: PublicSurveyQuestion[] = [
  question({ id: 10, position: 0, type: 'SHORT_TEXT', required: true }),
  question({ id: 11, position: 1, type: 'LONG_TEXT' }),
  question({
    id: 12,
    position: 2,
    type: 'SINGLE_CHOICE',
    options: [option(21, '하나', 0), option(22, '둘', 1)],
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
  }),
  question({
    id: 15,
    position: 5,
    type: 'NUMBER',
    numberMin: '-12.34',
    numberMax: '100.0000',
  }),
]

describe('public response form contract', () => {
  it('should_createTypeAppropriateEmptyState', () => {
    expect(createPublicAnswerState(questions)).toEqual({
      10: '',
      11: '',
      12: [],
      13: [],
      14: '',
      15: '',
    })
  })

  it('should_preserveAcceptedTextAndOmitOptionalUnansweredQuestions', () => {
    const state = createPublicAnswerState(questions)
    state[10] = '  원문 유지  '
    state[12] = [22]
    state[13] = [32, 31]
    state[14] = '3'
    state[15] = '-12.3400'

    const built = buildPublicResponseSubmission(
      questions,
      state,
      '550e8400-e29b-41d4-a716-446655440000',
    )

    expect(built.submission).toEqual({
      clientSubmissionId: '550e8400-e29b-41d4-a716-446655440000',
      answers: [
        { questionId: 10, textValue: '  원문 유지  ' },
        { questionId: 12, optionIds: [22] },
        { questionId: 13, optionIds: [32, 31] },
        { questionId: 14, numericValue: '3' },
        { questionId: 15, numericValue: '-12.3400' },
      ],
    })
    expect(built.questionIdsByAnswerIndex).toEqual([10, 12, 13, 14, 15])
  })

  it('should_validateUnicodeCodePoints_withoutUtf16Truncation', () => {
    const short = questions[0]

    expect(validatePublicAnswer(short, '😀'.repeat(500))).toBeNull()
    expect(validatePublicAnswer(short, '😀'.repeat(501))).toBe(
      '단답 응답은 500자 이하로 입력해 주세요.',
    )
    expect(validatePublicAnswer(short, '   ')).toBe(
      '공백만으로는 응답할 수 없습니다.',
    )
  })

  it('should_validateChoiceIdentityAndCount', () => {
    expect(validatePublicAnswer(questions[2], [21, 22])).toBe(
      '선택지를 하나만 선택해 주세요.',
    )
    expect(validatePublicAnswer(questions[3], [31, 31])).toBe(
      '같은 선택지를 중복해서 선택할 수 없습니다.',
    )
    expect(validatePublicAnswer(questions[3], [99])).toBe(
      '현재 설문에 있는 선택지를 선택해 주세요.',
    )
  })

  it('should_validateScaleAndPlainDecimalNumberWithoutNumberAuthority', () => {
    expect(validatePublicAnswer(questions[4], '3')).toBeNull()
    expect(validatePublicAnswer(questions[4], '3.0')).toBe(
      '척도 값은 표시된 정수 중에서 선택해 주세요.',
    )
    expect(validatePublicAnswer(questions[5], '-12.3400')).toBeNull()
    expect(validatePublicAnswer(questions[5], '1e2')).toBe(
      '숫자는 지수 표기 없이 입력해 주세요.',
    )
    expect(validatePublicAnswer(questions[5], '100.0001')).toBe(
      '숫자는 -12.34부터 100.0000 사이로 입력해 주세요.',
    )
    expect(validatePublicAnswer(questions[5], '0.00001')).toBe(
      '숫자는 소수점 아래 4자리까지 입력해 주세요.',
    )
  })

  it('should_findFirstInvalidQuestionInCanonicalOrder', () => {
    const state = createPublicAnswerState(questions)

    expect(firstInvalidPublicQuestion(questions, state)).toEqual({
      questionId: 10,
      message: '필수 질문에 응답해 주세요.',
    })
  })

  it('should_mapServerAnswerIndexToQuestion_withoutRenderingServerMessage', () => {
    const feedback = publicResponseFieldFeedback(
      [
        {
          path: 'answers[1].numericValue',
          code: 'OUT_OF_RANGE',
          message: 'Internal English validation detail.',
        },
      ],
      [10, 15],
      questions,
      createPublicAnswerState(questions),
    )

    expect(feedback).toEqual({
      questionId: 15,
      message: '허용된 응답 범위를 확인해 주세요.',
    })
    expect(feedback?.message).not.toContain('Internal English')
  })

  it('should_mapRequiredCompletenessAndUnknownErrorsSafely', () => {
    const state = createPublicAnswerState(questions)

    expect(
      publicResponseFieldFeedback(
        [{ path: 'answers', code: 'REQUIRED', message: 'Required.' }],
        [],
        questions,
        state,
      ),
    ).toEqual({
      questionId: 10,
      message: '필수 질문에 응답해 주세요.',
    })
    expect(
      publicResponseFieldFeedback(
        [{ path: 'future', code: 'FUTURE', message: 'Unsafe detail.' }],
        [],
        questions,
        state,
      ),
    ).toEqual({
      questionId: null,
      message: '응답 내용을 확인해 주세요.',
    })
  })
})

function question({
  id,
  position,
  type,
  required = false,
  options = [],
  scaleMin = null,
  scaleMax = null,
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
  numberMin?: string | null
  numberMax?: string | null
}): PublicSurveyQuestion {
  return {
    id,
    type,
    title: `질문 ${id}`,
    description: null,
    required,
    position,
    scaleMin,
    scaleMax,
    scaleMinLabel: null,
    scaleMaxLabel: null,
    numberMin,
    numberMax,
    options,
  }
}

function option(id: number, label: string, position: number) {
  return { id, label, position }
}
