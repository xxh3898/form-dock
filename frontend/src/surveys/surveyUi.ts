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

const genericApiFieldErrorMessage = '입력값을 확인해 주세요.'

const apiFieldErrorMessages = new Map<string, string>([
  ['body:REQUIRED', '변경할 설문 정보를 입력해 주세요.'],
  ['title:REQUIRED', '제목을 입력해 주세요.'],
  ['title:INVALID_TYPE', '제목 입력값을 확인해 주세요.'],
  ['title:TOO_LONG', '제목이 너무 깁니다.'],
  ['description:INVALID_TYPE', '설명 입력값을 확인해 주세요.'],
  ['description:TOO_LONG', '설명이 너무 깁니다.'],
  [
    'privacyNotice:INVALID_TYPE',
    '개인정보 안내 입력값을 확인해 주세요.',
  ],
  ['privacyNotice:TOO_LONG', '개인정보 안내가 너무 깁니다.'],
  ['slug:REQUIRED', '예약 slug를 입력해 주세요.'],
  ['slug:INVALID_TYPE', '예약 slug 입력값을 확인해 주세요.'],
  [
    'slug:INVALID_FORMAT',
    '예약 slug는 영문 소문자, 숫자와 하이픈으로 3~64자 입력해 주세요.',
  ],
  ['slug:NOT_UNIQUE', '이미 사용 중인 예약 slug입니다.'],
  ['type:REQUIRED', '질문 유형을 선택해 주세요.'],
  ['type:INVALID_TYPE', '질문 유형을 확인해 주세요.'],
  ['type:INVALID_VALUE', '지원하는 질문 유형을 선택해 주세요.'],
  ['type:UNUSED_FIELD', '질문 유형과 설정을 확인해 주세요.'],
  ['required:REQUIRED', '필수 응답 설정을 확인해 주세요.'],
  ['required:INVALID_TYPE', '필수 응답 설정을 확인해 주세요.'],
  ['options:REQUIRED', '선택지를 확인해 주세요.'],
  ['options:INVALID_TYPE', '선택지를 확인해 주세요.'],
  ['options:TOO_FEW', '선택지를 두 개 이상 입력해 주세요.'],
  ['options:UNUSED_FIELD', '현재 질문 유형에서는 선택지를 사용할 수 없습니다.'],
  ['options[]:INVALID_TYPE', '선택지 입력값을 확인해 주세요.'],
  ['options[]:INVALID_FIELD', '선택지 입력값을 확인해 주세요.'],
  ['options[].id:INVALID_TYPE', '선택지 정보를 확인해 주세요.'],
  ['options[].id:INVALID_VALUE', '선택지 정보를 확인해 주세요.'],
  ['options[].id:DUPLICATE', '중복된 선택지를 확인해 주세요.'],
  ['options[].id:INVALID_IDENTITY', '선택지 정보가 변경됐습니다. 다시 시도해 주세요.'],
  ['options[].label:REQUIRED', '선택지 내용을 입력해 주세요.'],
  ['options[].label:INVALID_TYPE', '선택지 내용을 확인해 주세요.'],
  ['options[].label:TOO_LONG', '선택지 내용이 너무 깁니다.'],
  ['scaleMin:REQUIRED', '척도 범위를 입력해 주세요.'],
  ['scaleMin:INVALID_TYPE', '척도 범위를 확인해 주세요.'],
  ['scaleMin:INVALID_RANGE', '척도 범위를 확인해 주세요.'],
  ['scaleMin:UNUSED_FIELD', '현재 질문 유형에서는 척도 설정을 사용할 수 없습니다.'],
  ['scaleMax:REQUIRED', '척도 범위를 입력해 주세요.'],
  ['scaleMax:INVALID_TYPE', '척도 범위를 확인해 주세요.'],
  ['scaleMinLabel:REQUIRED', '척도 최솟값 설명을 확인해 주세요.'],
  ['scaleMinLabel:INVALID_TYPE', '척도 최솟값 설명을 확인해 주세요.'],
  ['scaleMinLabel:TOO_LONG', '척도 최솟값 설명이 너무 깁니다.'],
  ['scaleMaxLabel:REQUIRED', '척도 최댓값 설명을 확인해 주세요.'],
  ['scaleMaxLabel:INVALID_TYPE', '척도 최댓값 설명을 확인해 주세요.'],
  ['scaleMaxLabel:TOO_LONG', '척도 최댓값 설명이 너무 깁니다.'],
  ['numberMin:REQUIRED', '숫자 범위 설정을 확인해 주세요.'],
  ['numberMin:INVALID_TYPE', '최솟값을 숫자로 입력해 주세요.'],
  ['numberMin:INVALID_DECIMAL', '최솟값을 소수 형식으로 입력해 주세요.'],
  ['numberMin:OUT_OF_RANGE', '최솟값의 숫자 범위를 확인해 주세요.'],
  ['numberMin:INVALID_RANGE', '숫자 범위를 확인해 주세요.'],
  ['numberMin:UNUSED_FIELD', '현재 질문 유형에서는 숫자 범위를 사용할 수 없습니다.'],
  ['numberMax:REQUIRED', '숫자 범위 설정을 확인해 주세요.'],
  ['numberMax:INVALID_TYPE', '최댓값을 숫자로 입력해 주세요.'],
  ['numberMax:INVALID_DECIMAL', '최댓값을 소수 형식으로 입력해 주세요.'],
  ['numberMax:OUT_OF_RANGE', '최댓값의 숫자 범위를 확인해 주세요.'],
  ['questionIds:REQUIRED', '질문 순서를 확인해 주세요.'],
  ['questionIds:INVALID_TYPE', '질문 순서를 확인해 주세요.'],
  ['questionIds:INVALID_SET', '질문 목록이 변경됐습니다. 다시 시도해 주세요.'],
  ['questionIds[]:INVALID_TYPE', '질문 순서를 확인해 주세요.'],
  ['questionIds[]:INVALID_VALUE', '질문 순서를 확인해 주세요.'],
  ['questionIds[]:DUPLICATE', '중복된 질문을 확인해 주세요.'],
])

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
  const error = errors.find((candidate) => candidate.path === path)
  return error === undefined ? undefined : apiFieldErrorMessage(error)
}

export function apiFieldErrorMessage(error: ApiFieldError): string {
  const normalizedPath = error.path.replace(/\[\d+\]/g, '[]')
  return (
    apiFieldErrorMessages.get(`${normalizedPath}:${error.code}`) ??
    genericApiFieldErrorMessage
  )
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
