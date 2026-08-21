import { type FetchFunction, isRecord } from '../api/apiClient.ts'

export const publicQuestionTypes = [
  'SHORT_TEXT',
  'LONG_TEXT',
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'SCALE',
  'NUMBER',
] as const

export type PublicQuestionType = (typeof publicQuestionTypes)[number]

export type PublicQuestionOption = {
  id: number
  label: string
  position: number
}

export type PublicSurveyQuestion = {
  id: number
  type: PublicQuestionType
  title: string
  description: string | null
  required: boolean
  position: number
  scaleMin: number | null
  scaleMax: number | null
  scaleMinLabel: string | null
  scaleMaxLabel: string | null
  numberMin: string | null
  numberMax: string | null
  options: PublicQuestionOption[]
}

export type PublicSurvey = {
  slug: string
  title: string
  description: string | null
  privacyNotice: string | null
  questions: PublicSurveyQuestion[]
}

export type PublicResponseAnswer =
  | { questionId: number; textValue: string }
  | { questionId: number; optionIds: number[] }
  | { questionId: number; numericValue: string }

export type PublicResponseSubmission = {
  clientSubmissionId: string
  answers: PublicResponseAnswer[]
}

export type PublicResponseReceipt = {
  responseId: number
  submittedAt: string
  replayed: boolean
}

export type PublicApiFieldError = {
  path: string
  code: string
  message: string
}

export type PublicApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'SURVEY_NOT_FOUND'
  | 'SURVEY_NOT_OPEN'
  | 'RESPONSE_INVALID'
  | 'RESPONSE_DUPLICATE_CONFLICT'
  | 'RESPONSE_PAYLOAD_TOO_LARGE'
  | 'RATE_LIMITED'
  | 'TEMPORARILY_UNAVAILABLE'
  | 'UNEXPECTED_RESPONSE'

export class PublicApiError extends Error {
  readonly code: PublicApiErrorCode
  readonly status: number
  readonly fieldErrors: PublicApiFieldError[]

  constructor(
    code: PublicApiErrorCode,
    status: number,
    fieldErrors: PublicApiFieldError[] = [],
  ) {
    super(`FormDock public API request failed (${code}).`)
    this.name = 'PublicApiError'
    this.code = code
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export interface PublicSurveyClient {
  getSurvey(slug: string): Promise<PublicSurvey>
  submitResponse(
    slug: string,
    submission: PublicResponseSubmission,
  ): Promise<PublicResponseReceipt>
}

const knownErrorCodes = new Set<PublicApiErrorCode>([
  'VALIDATION_FAILED',
  'SURVEY_NOT_FOUND',
  'SURVEY_NOT_OPEN',
  'RESPONSE_INVALID',
  'RESPONSE_DUPLICATE_CONFLICT',
  'RESPONSE_PAYLOAD_TOO_LARGE',
  'RATE_LIMITED',
  'TEMPORARILY_UNAVAILABLE',
])

const rootFields = [
  'slug',
  'title',
  'description',
  'privacyNotice',
  'questions',
] as const
const questionFields = [
  'id',
  'type',
  'title',
  'description',
  'required',
  'position',
  'scaleMin',
  'scaleMax',
  'scaleMinLabel',
  'scaleMaxLabel',
  'numberMin',
  'numberMax',
  'options',
] as const
const optionFields = ['id', 'label', 'position'] as const
const receiptFields = ['responseId', 'submittedAt', 'replayed'] as const

export class SameOriginPublicSurveyClient implements PublicSurveyClient {
  private readonly fetchRequest: FetchFunction

  constructor(fetchRequest: FetchFunction) {
    this.fetchRequest = fetchRequest
  }

  async getSurvey(slug: string): Promise<PublicSurvey> {
    const response = await this.request(publicSurveyPath(slug), {
      method: 'GET',
    })
    return parseJson(response, parsePublicSurvey)
  }

  async submitResponse(
    slug: string,
    submission: PublicResponseSubmission,
  ): Promise<PublicResponseReceipt> {
    const response = await this.request(`${publicSurveyPath(slug)}/responses`, {
      body: JSON.stringify(submission),
      method: 'POST',
    })
    return parseJson(response, parsePublicResponseReceipt)
  }

  private async request(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (init.body !== undefined) {
      headers.set('Content-Type', 'application/json')
    }

    let response: Response
    try {
      response = await this.fetchRequest(path, {
        ...init,
        cache: 'no-store',
        credentials: 'same-origin',
        headers,
      })
    } catch {
      throw new PublicApiError('TEMPORARILY_UNAVAILABLE', 0)
    }

    if (!response.ok) {
      throw await parsePublicApiError(response)
    }
    return response
  }
}

export function parsePublicSurvey(payload: unknown, status: number): PublicSurvey {
  if (!isRecord(payload) || !hasExactFields(payload, rootFields)) {
    throw unexpected(status)
  }

  const questions = parseArray(payload.questions, status, parseQuestion)
  const questionIds = new Set<number>()
  questions.forEach((question, index) => {
    if (question.position !== index || questionIds.has(question.id)) {
      throw unexpected(status)
    }
    questionIds.add(question.id)
  })

  const slug = readString(payload.slug, status)
  if (!/^[a-z0-9](?:[a-z0-9-]{1,62}[a-z0-9])$/.test(slug)) {
    throw unexpected(status)
  }

  return {
    slug,
    title: readBoundedNonBlankString(payload.title, 200, status),
    description: readNullableBoundedString(payload.description, 5000, status),
    privacyNotice: readNullableBoundedString(
      payload.privacyNotice,
      5000,
      status,
    ),
    questions,
  }
}

function parseQuestion(payload: unknown, status: number): PublicSurveyQuestion {
  if (!isRecord(payload) || !hasExactFields(payload, questionFields)) {
    throw unexpected(status)
  }

  const type = readQuestionType(payload.type, status)
  const options = parseArray(payload.options, status, parseOption)
  const optionIds = new Set<number>()
  options.forEach((option, index) => {
    if (option.position !== index || optionIds.has(option.id)) {
      throw unexpected(status)
    }
    optionIds.add(option.id)
  })

  const question: PublicSurveyQuestion = {
    id: readPositiveId(payload.id, status),
    type,
    title: readBoundedNonBlankString(payload.title, 500, status),
    description: readNullableBoundedString(payload.description, 2000, status),
    required: readBoolean(payload.required, status),
    position: readNonNegativeInteger(payload.position, status),
    scaleMin: readNullableInteger(payload.scaleMin, status),
    scaleMax: readNullableInteger(payload.scaleMax, status),
    scaleMinLabel: readNullableBoundedString(payload.scaleMinLabel, 100, status),
    scaleMaxLabel: readNullableBoundedString(payload.scaleMaxLabel, 100, status),
    numberMin: readNullableDecimal(payload.numberMin, status),
    numberMax: readNullableDecimal(payload.numberMax, status),
    options,
  }
  validateQuestionConfiguration(question, status)
  return question
}

function parseOption(payload: unknown, status: number): PublicQuestionOption {
  if (!isRecord(payload) || !hasExactFields(payload, optionFields)) {
    throw unexpected(status)
  }
  return {
    id: readPositiveId(payload.id, status),
    label: readBoundedNonBlankString(payload.label, 500, status),
    position: readNonNegativeInteger(payload.position, status),
  }
}

function parsePublicResponseReceipt(
  payload: unknown,
  status: number,
): PublicResponseReceipt {
  if (
    !isRecord(payload) ||
    !hasExactFields(payload, receiptFields) ||
    (status !== 200 && status !== 201)
  ) {
    throw unexpected(status)
  }
  const replayed = readBoolean(payload.replayed, status)
  if ((status === 200) !== replayed) {
    throw unexpected(status)
  }
  return {
    responseId: readPositiveId(payload.responseId, status),
    submittedAt: readInstant(payload.submittedAt, status),
    replayed,
  }
}

async function parseJson<T>(
  response: Response,
  parser: (payload: unknown, status: number) => T,
): Promise<T> {
  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    throw unexpected(response.status)
  }
  try {
    return parser(payload, response.status)
  } catch (error) {
    if (error instanceof PublicApiError) {
      throw error
    }
    throw unexpected(response.status)
  }
}

async function parsePublicApiError(response: Response): Promise<PublicApiError> {
  let payload: unknown
  try {
    payload = await response.json()
  } catch {
    payload = null
  }

  if (
    isRecord(payload) &&
    typeof payload.code === 'string' &&
    knownErrorCodes.has(payload.code as PublicApiErrorCode)
  ) {
    const fieldErrors = parseFieldErrors(payload.fieldErrors)
    if (fieldErrors !== null) {
      return new PublicApiError(
        payload.code as PublicApiErrorCode,
        response.status,
        fieldErrors,
      )
    }
  }
  return response.status >= 500
    ? new PublicApiError('TEMPORARILY_UNAVAILABLE', response.status)
    : unexpected(response.status)
}

function parseFieldErrors(value: unknown): PublicApiFieldError[] | null {
  if (!Array.isArray(value)) {
    return null
  }
  const fieldErrors: PublicApiFieldError[] = []
  for (const item of value) {
    if (
      !isRecord(item) ||
      typeof item.path !== 'string' ||
      typeof item.code !== 'string' ||
      typeof item.message !== 'string'
    ) {
      return null
    }
    fieldErrors.push({
      path: item.path,
      code: item.code,
      message: item.message,
    })
  }
  return fieldErrors
}

function validateQuestionConfiguration(
  question: PublicSurveyQuestion,
  status: number,
): void {
  const hasScale =
    question.scaleMin !== null ||
    question.scaleMax !== null ||
    question.scaleMinLabel !== null ||
    question.scaleMaxLabel !== null
  const hasNumber = question.numberMin !== null || question.numberMax !== null

  if (
    question.type === 'SINGLE_CHOICE' ||
    question.type === 'MULTIPLE_CHOICE'
  ) {
    if (question.options.length < 2 || hasScale || hasNumber) {
      throw unexpected(status)
    }
    return
  }
  if (question.options.length !== 0) {
    throw unexpected(status)
  }
  if (question.type === 'SCALE') {
    if (
      question.scaleMin === null ||
      question.scaleMax === null ||
      question.scaleMin < 1 ||
      question.scaleMin >= question.scaleMax ||
      question.scaleMax > 10 ||
      hasNumber
    ) {
      throw unexpected(status)
    }
    return
  }
  if (question.type === 'NUMBER') {
    if (hasScale) {
      throw unexpected(status)
    }
    return
  }
  if (hasScale || hasNumber) {
    throw unexpected(status)
  }
}

function publicSurveyPath(slug: string): string {
  if (!/^[a-z0-9](?:[a-z0-9-]{1,62}[a-z0-9])$/.test(slug)) {
    throw unexpected(0)
  }
  return `/api/public/surveys/${encodeURIComponent(slug)}`
}

function hasExactFields(
  value: Record<string, unknown>,
  expected: readonly string[],
): boolean {
  const actual = Object.keys(value).sort()
  const canonical = [...expected].sort()
  return (
    actual.length === canonical.length &&
    actual.every((field, index) => field === canonical[index])
  )
}

function parseArray<T>(
  value: unknown,
  status: number,
  parser: (item: unknown, status: number) => T,
): T[] {
  if (!Array.isArray(value)) {
    throw unexpected(status)
  }
  return value.map((item) => parser(item, status))
}

function readQuestionType(value: unknown, status: number): PublicQuestionType {
  if (
    typeof value === 'string' &&
    publicQuestionTypes.includes(value as PublicQuestionType)
  ) {
    return value as PublicQuestionType
  }
  throw unexpected(status)
}

function readPositiveId(value: unknown, status: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1) {
    throw unexpected(status)
  }
  return value
}

function readNonNegativeInteger(value: unknown, status: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw unexpected(status)
  }
  return value
}

function readNullableInteger(value: unknown, status: number): number | null {
  if (value === null) {
    return null
  }
  if (typeof value !== 'number' || !Number.isSafeInteger(value)) {
    throw unexpected(status)
  }
  return value
}

function readString(value: unknown, status: number): string {
  if (typeof value !== 'string') {
    throw unexpected(status)
  }
  return value
}

function readBoundedNonBlankString(
  value: unknown,
  maximumCodePoints: number,
  status: number,
): string {
  const text = readString(value, status)
  const length = Array.from(text).length
  if (text.trim().length === 0 || length > maximumCodePoints) {
    throw unexpected(status)
  }
  return text
}

function readNullableBoundedString(
  value: unknown,
  maximumCodePoints: number,
  status: number,
): string | null {
  if (value === null) {
    return null
  }
  const text = readString(value, status)
  if (Array.from(text).length > maximumCodePoints) {
    throw unexpected(status)
  }
  return text
}

function readNullableDecimal(value: unknown, status: number): string | null {
  if (value === null) {
    return null
  }
  const decimal = readString(value, status)
  if (!/^-?[0-9]+(?:\.[0-9]+)?$/.test(decimal)) {
    throw unexpected(status)
  }
  return decimal
}

function readBoolean(value: unknown, status: number): boolean {
  if (typeof value !== 'boolean') {
    throw unexpected(status)
  }
  return value
}

function readInstant(value: unknown, status: number): string {
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) {
    throw unexpected(status)
  }
  return value
}

function unexpected(status: number): PublicApiError {
  return new PublicApiError('UNEXPECTED_RESPONSE', status)
}

export const publicSurveyClient = new SameOriginPublicSurveyClient(
  (input, init) => fetch(input, init),
)
