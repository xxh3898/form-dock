import { describe, expect, it } from 'vitest'

import { questionTypeLabel, surveyStatusLabel } from './surveyUi.ts'

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
})
