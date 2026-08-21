import { ApiError, type ApiFieldError } from '../api/apiClient.ts'
import type { QuestionType, SurveyStatus } from './surveyClient.ts'

const surveyStatusLabels: Record<SurveyStatus, string> = {
  DRAFT: '초안',
  OPEN: '공개',
  CLOSED: '마감',
}

const questionTypeLabels: Record<QuestionType, string> = {
  SHORT_TEXT: '단답형',
  LONG_TEXT: '장문형',
  SINGLE_CHOICE: '단일 선택',
  MULTIPLE_CHOICE: '복수 선택',
  SCALE: '척도',
  NUMBER: '숫자',
}

export function surveyStatusLabel(status: SurveyStatus): string {
  return surveyStatusLabels[status]
}

export function questionTypeLabel(type: QuestionType): string {
  return questionTypeLabels[type]
}

export function parseSurveyId(value: string | undefined): number | null {
  if (value === undefined || !/^[1-9][0-9]*$/.test(value)) {
    return null
  }
  const surveyId = Number(value)
  return Number.isSafeInteger(surveyId) ? surveyId : null
}

export function fieldMessage(
  errors: ApiFieldError[],
  path: string,
): string | undefined {
  return errors.find((error) => error.path === path)?.message
}

export function surveyErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'SURVEY_SLUG_CONFLICT':
        return '이미 사용 중인 예약 slug입니다. 다른 slug를 선택해 주세요.'
      case 'SURVEY_SLUG_IMMUTABLE':
        return '설문을 한 번이라도 공개한 뒤에는 예약 slug를 변경할 수 없습니다.'
      case 'SURVEY_DELETE_REQUIRES_CLOSED':
        return '설문을 마감한 뒤 삭제해 주세요.'
      case 'SURVEY_STATE_CONFLICT':
        return '설문 상태가 변경됐습니다. 새로고침한 뒤 가능한 작업을 다시 시도해 주세요.'
      case 'SURVEY_INVALID_STRUCTURE':
        return '설문을 공개하기 전에 유효한 질문을 하나 이상 추가해 주세요.'
      case 'SURVEY_STRUCTURE_LOCKED':
        return '기존 응답으로 질문 구조가 잠겼습니다. 편집 가능한 복사본을 만들려면 설문을 복제하세요.'
      case 'QUESTION_NOT_FOUND':
        return '해당 질문을 더 이상 사용할 수 없습니다. 설문 작성 화면을 새로고침했습니다.'
      case 'QUESTION_INVALID_CONFIGURATION':
      case 'VALIDATION_FAILED':
        return '표시된 항목을 확인한 뒤 다시 시도해 주세요.'
      case 'CSRF_INVALID':
        return '보안 토큰을 갱신하지 못했습니다. 다시 시도해 주세요.'
      case 'TEMPORARILY_UNAVAILABLE':
        return 'FormDock을 일시적으로 사용할 수 없습니다. 다시 시도해 주세요.'
      case 'SURVEY_NOT_FOUND':
        return '이 설문은 사용할 수 없거나 삭제됐습니다.'
      case 'AUTH_REQUIRED':
      case 'AUTH_INVALID_CREDENTIALS':
      case 'FORBIDDEN':
      case 'UNEXPECTED_RESPONSE':
        return '요청을 안전하게 처리하지 못했습니다. 다시 시도해 주세요.'
    }
  }
  return '요청을 안전하게 처리하지 못했습니다. 다시 시도해 주세요.'
}

export function nullableText(value: string): string | null {
  return value.trim().length === 0 ? null : value
}
