import { describe, expect, it } from 'vitest'

import {
  apiFieldErrorMessage,
  fieldMessage,
  questionTypeLabel,
  surveyStatusLabel,
} from './surveyUi.ts'

describe('Survey UI labels', () => {
  it('should_mapCanonicalSurveyStatusesToKoreanLabels', () => {
    expect(surveyStatusLabel('DRAFT')).toBe('초안')
    expect(surveyStatusLabel('OPEN')).toBe('공개')
    expect(surveyStatusLabel('CLOSED')).toBe('마감')
  })

  it('should_mapCanonicalQuestionTypesToKoreanLabels', () => {
    expect(questionTypeLabel('SHORT_TEXT')).toBe('단답형')
    expect(questionTypeLabel('LONG_TEXT')).toBe('장문형')
    expect(questionTypeLabel('SINGLE_CHOICE')).toBe('단일 선택')
    expect(questionTypeLabel('MULTIPLE_CHOICE')).toBe('복수 선택')
    expect(questionTypeLabel('SCALE')).toBe('척도')
    expect(questionTypeLabel('NUMBER')).toBe('숫자')
  })

  it('should_mapKnownApiFieldErrorWithoutExposingServerMessage', () => {
    const error = {
      path: 'title',
      code: 'REQUIRED',
      message: 'Title is required.',
    }

    expect(apiFieldErrorMessage(error)).toBe('제목을 입력해 주세요.')
    expect(fieldMessage([error], 'title')).toBe('제목을 입력해 주세요.')
    expect(apiFieldErrorMessage(error)).not.toContain('Title is required.')
  })

  it('should_useSafeKoreanFallbackForUnknownApiFieldError', () => {
    const error = {
      path: 'futureField',
      code: 'FUTURE_CODE',
      message: 'Sensitive server implementation detail.',
    }

    expect(apiFieldErrorMessage(error)).toBe('입력값을 확인해 주세요.')
    expect(apiFieldErrorMessage(error)).not.toContain(error.message)
  })
})
