import type {
  PublicApiFieldError,
  PublicResponseSubmission,
  PublicSurveyQuestion,
} from './publicSurveyClient.ts'

export type PublicAnswerDraft = string | number[]
export type PublicAnswerState = Record<number, PublicAnswerDraft>

export type BuiltPublicResponseSubmission = {
  submission: PublicResponseSubmission
  questionIdsByAnswerIndex: number[]
}

export type PublicQuestionFeedback = {
  questionId: number | null
  message: string
}

const plainDecimalPattern = /^-?[0-9]+(?:\.[0-9]+)?$/

export function createPublicAnswerState(
  questions: PublicSurveyQuestion[],
): PublicAnswerState {
  return Object.fromEntries(
    questions.map((question) => [
      question.id,
      question.type === 'SINGLE_CHOICE' ||
      question.type === 'MULTIPLE_CHOICE'
        ? []
        : '',
    ]),
  )
}

export function validatePublicAnswer(
  question: PublicSurveyQuestion,
  draft: PublicAnswerDraft,
): string | null {
  const unanswered =
    (typeof draft === 'string' && draft.length === 0) ||
    (Array.isArray(draft) && draft.length === 0)
  if (unanswered) {
    return question.required ? '필수 질문에 응답해 주세요.' : null
  }

  switch (question.type) {
    case 'SHORT_TEXT':
      return validateText(draft, 500, '단답')
    case 'LONG_TEXT':
      return validateText(draft, 5000, '장문')
    case 'SINGLE_CHOICE':
      return validateChoice(question, draft, true)
    case 'MULTIPLE_CHOICE':
      return validateChoice(question, draft, false)
    case 'SCALE':
      return validateScale(question, draft)
    case 'NUMBER':
      return validateNumber(question, draft)
  }
}

export function firstInvalidPublicQuestion(
  questions: PublicSurveyQuestion[],
  state: PublicAnswerState,
): { questionId: number; message: string } | null {
  for (const question of questions) {
    const message = validatePublicAnswer(question, state[question.id])
    if (message !== null) {
      return { questionId: question.id, message }
    }
  }
  return null
}

export function buildPublicResponseSubmission(
  questions: PublicSurveyQuestion[],
  state: PublicAnswerState,
  clientSubmissionId: string,
): BuiltPublicResponseSubmission {
  const invalid = firstInvalidPublicQuestion(questions, state)
  if (invalid !== null) {
    throw new Error(`Invalid public answer state for question ${invalid.questionId}.`)
  }

  const answers: PublicResponseSubmission['answers'] = []
  const questionIdsByAnswerIndex: number[] = []
  for (const question of questions) {
    const draft = state[question.id]
    if (
      (typeof draft === 'string' && draft.length === 0) ||
      (Array.isArray(draft) && draft.length === 0)
    ) {
      continue
    }

    questionIdsByAnswerIndex.push(question.id)
    if (question.type === 'SHORT_TEXT' || question.type === 'LONG_TEXT') {
      answers.push({ questionId: question.id, textValue: draft as string })
    } else if (
      question.type === 'SINGLE_CHOICE' ||
      question.type === 'MULTIPLE_CHOICE'
    ) {
      answers.push({ questionId: question.id, optionIds: [...(draft as number[])] })
    } else {
      answers.push({ questionId: question.id, numericValue: draft as string })
    }
  }

  return {
    submission: { clientSubmissionId, answers },
    questionIdsByAnswerIndex,
  }
}

export function publicResponseFieldFeedback(
  fieldErrors: PublicApiFieldError[],
  questionIdsByAnswerIndex: number[],
  questions: PublicSurveyQuestion[],
  state: PublicAnswerState,
): PublicQuestionFeedback | null {
  const first = fieldErrors[0]
  if (first === undefined) {
    return null
  }

  const answerPath = /^answers\[(\d+)\](?:\.|$)/.exec(first.path)
  if (answerPath !== null) {
    const questionId = questionIdsByAnswerIndex[Number(answerPath[1])] ?? null
    return {
      questionId,
      message: publicResponseFieldErrorMessage(first),
    }
  }

  if (first.path === 'answers' && first.code === 'REQUIRED') {
    const invalid = firstInvalidPublicQuestion(questions, state)
    if (invalid !== null) {
      return invalid
    }
  }

  return {
    questionId: null,
    message: publicResponseFieldErrorMessage(first),
  }
}

function publicResponseFieldErrorMessage(error: PublicApiFieldError): string {
  switch (error.code) {
    case 'REQUIRED':
      return '필수 질문에 응답해 주세요.'
    case 'TOO_LONG':
      return '응답 글자 수를 확인해 주세요.'
    case 'INVALID_NUMBER':
      return '숫자 응답 형식을 확인해 주세요.'
    case 'OUT_OF_RANGE':
      return '허용된 응답 범위를 확인해 주세요.'
    case 'INVALID_COUNT':
      return '선택한 항목 수를 확인해 주세요.'
    case 'UNKNOWN_OPTION':
    case 'UNKNOWN_QUESTION':
      return '설문 내용이 변경됐습니다. 응답을 다시 확인해 주세요.'
    case 'DUPLICATE':
      return '중복된 응답 항목을 확인해 주세요.'
    case 'INVALID_REPRESENTATION':
    case 'INVALID_TEXT':
      return '이 질문의 응답 형식을 확인해 주세요.'
    default:
      return '응답 내용을 확인해 주세요.'
  }
}

function validateText(
  draft: PublicAnswerDraft,
  maximumCodePoints: number,
  label: string,
): string | null {
  if (typeof draft !== 'string') {
    return '텍스트 응답을 확인해 주세요.'
  }
  if (draft.trim().length === 0) {
    return '공백만으로는 응답할 수 없습니다.'
  }
  if (Array.from(draft).length > maximumCodePoints) {
    return `${label} 응답은 ${maximumCodePoints}자 이하로 입력해 주세요.`
  }
  return null
}

function validateChoice(
  question: PublicSurveyQuestion,
  draft: PublicAnswerDraft,
  single: boolean,
): string | null {
  if (!Array.isArray(draft)) {
    return '선택한 응답을 확인해 주세요.'
  }
  if (single && draft.length !== 1) {
    return '선택지를 하나만 선택해 주세요.'
  }
  if (!single && draft.length < 1) {
    return '선택지를 하나 이상 선택해 주세요.'
  }
  if (new Set(draft).size !== draft.length) {
    return '같은 선택지를 중복해서 선택할 수 없습니다.'
  }
  const optionIds = new Set(question.options.map((option) => option.id))
  if (draft.some((optionId) => !optionIds.has(optionId))) {
    return '현재 설문에 있는 선택지를 선택해 주세요.'
  }
  return null
}

function validateScale(
  question: PublicSurveyQuestion,
  draft: PublicAnswerDraft,
): string | null {
  if (typeof draft !== 'string' || !/^-?[0-9]+$/.test(draft)) {
    return '척도 값은 표시된 정수 중에서 선택해 주세요.'
  }
  const value = BigInt(draft)
  if (
    question.scaleMin === null ||
    question.scaleMax === null ||
    value < BigInt(question.scaleMin) ||
    value > BigInt(question.scaleMax)
  ) {
    return '표시된 척도 범위 안에서 선택해 주세요.'
  }
  return null
}

function validateNumber(
  question: PublicSurveyQuestion,
  draft: PublicAnswerDraft,
): string | null {
  if (typeof draft !== 'string' || !plainDecimalPattern.test(draft)) {
    return '숫자는 지수 표기 없이 입력해 주세요.'
  }

  const unsigned = draft.startsWith('-') ? draft.slice(1) : draft
  const [integerPart, fractionPart = ''] = unsigned.split('.')
  if (fractionPart.length > 4) {
    return '숫자는 소수점 아래 4자리까지 입력해 주세요.'
  }
  const significantInteger = integerPart.replace(/^0+/, '')
  if (significantInteger.length > 15) {
    return '숫자는 허용된 자릿수 안에서 입력해 주세요.'
  }

  if (
    (question.numberMin !== null &&
      comparePlainDecimals(draft, question.numberMin) < 0) ||
    (question.numberMax !== null &&
      comparePlainDecimals(draft, question.numberMax) > 0)
  ) {
    return numberRangeMessage(question.numberMin, question.numberMax)
  }
  return null
}

function comparePlainDecimals(left: string, right: string): number {
  const leftDecimal = parsePlainDecimal(left)
  const rightDecimal = parsePlainDecimal(right)
  const scale = Math.max(leftDecimal.scale, rightDecimal.scale)
  const leftValue =
    leftDecimal.value * 10n ** BigInt(scale - leftDecimal.scale)
  const rightValue =
    rightDecimal.value * 10n ** BigInt(scale - rightDecimal.scale)
  return leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0
}

function parsePlainDecimal(value: string): { value: bigint; scale: number } {
  const negative = value.startsWith('-')
  const unsigned = negative ? value.slice(1) : value
  const [integerPart, fractionPart = ''] = unsigned.split('.')
  const magnitude = BigInt(`${integerPart}${fractionPart}`)
  return {
    value: negative ? -magnitude : magnitude,
    scale: fractionPart.length,
  }
}

function numberRangeMessage(
  minimum: string | null,
  maximum: string | null,
): string {
  if (minimum !== null && maximum !== null) {
    return `숫자는 ${minimum}부터 ${maximum} 사이로 입력해 주세요.`
  }
  if (minimum !== null) {
    return `숫자는 ${minimum} 이상으로 입력해 주세요.`
  }
  if (maximum !== null) {
    return `숫자는 ${maximum} 이하로 입력해 주세요.`
  }
  return '숫자 응답을 확인해 주세요.'
}
