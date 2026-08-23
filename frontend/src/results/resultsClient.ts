import {
  ApiError,
  type FetchFunction,
  isRecord,
  SameOriginApiClient,
} from '../api/apiClient.ts'
import {
  questionTypes,
  type QuestionType,
  type SurveyStatus,
} from '../surveys/surveyClient.ts'

export type ResponseListItem = {
  responseId: number
  submittedAt: string
}

export type ResponsePage = {
  items: ResponseListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type SummaryQuestionBase = {
  questionId: number
  type: QuestionType
  title: string
  position: number
  answeredCount: number
}

export type CountSummaryQuestion = SummaryQuestionBase & {
  type: 'SHORT_TEXT' | 'LONG_TEXT' | 'NUMBER'
}

export type ChoiceSummaryOption = {
  optionId: number
  label: string
  position: number
  count: number
  percentage: string
}

export type ChoiceSummaryQuestion = SummaryQuestionBase & {
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE'
  options: ChoiceSummaryOption[]
}

export type ScaleSummaryBucket = {
  value: number
  count: number
  percentage: string
}

export type ScaleSummaryQuestion = SummaryQuestionBase & {
  type: 'SCALE'
  average: string | null
  distribution: ScaleSummaryBucket[]
}

export type ResponseSummaryQuestion =
  | CountSummaryQuestion
  | ChoiceSummaryQuestion
  | ScaleSummaryQuestion

export type ResponseSummary = {
  surveyId: number
  status: SurveyStatus
  totalResponses: number
  lastSubmittedAt: string | null
  questionCount: number
  questions: ResponseSummaryQuestion[]
}

export type SelectedOption = {
  id: number
  label: string
  position: number
}

export type ResponseAnswer = {
  textValue: string | null
  numericValue: string | null
  options: SelectedOption[]
}

export type ResponseDetailQuestion = {
  questionId: number
  type: QuestionType
  title: string
  description: string | null
  required: boolean
  position: number
  answer: ResponseAnswer | null
}

export type ResponseDetail = {
  responseId: number
  submittedAt: string
  questions: ResponseDetailQuestion[]
}

export type CsvDownload = {
  blob: Blob
  filename: string
}

export interface ResultsClient {
  listResponses(
    surveyId: number,
    page?: number,
    size?: number,
  ): Promise<ResponsePage>
  getSummary(surveyId: number): Promise<ResponseSummary>
  getResponseDetail(
    surveyId: number,
    responseId: number,
  ): Promise<ResponseDetail>
  downloadCsv(surveyId: number): Promise<CsvDownload>
}

export class SameOriginResultsClient implements ResultsClient {
  private readonly api: SameOriginApiClient

  constructor(fetchRequest: FetchFunction) {
    this.api = new SameOriginApiClient(fetchRequest)
  }

  async listResponses(
    surveyId: number,
    page = 0,
    size = 50,
  ): Promise<ResponsePage> {
    const path = responsePath(surveyId)
    const safePage = nonNegativeInteger(page)
    const safeSize = pageSize(size)
    return this.api.getJson(
      `${path}?page=${safePage}&size=${safeSize}`,
      parseResponsePage,
    )
  }

  async getSummary(surveyId: number): Promise<ResponseSummary> {
    return this.api.getJson(
      `${responsePath(surveyId)}/summary`,
      parseResponseSummary,
    )
  }

  async getResponseDetail(
    surveyId: number,
    responseId: number,
  ): Promise<ResponseDetail> {
    return this.api.getJson(
      `${responsePath(surveyId)}/${positiveId(responseId)}`,
      parseResponseDetail,
    )
  }

  async downloadCsv(surveyId: number): Promise<CsvDownload> {
    const safeSurveyId = positiveId(surveyId)
    const response = await this.api.getResponse(
      `${responsePath(safeSurveyId)}/export.csv`,
      'text/csv',
    )
    const contentType = response.headers.get('Content-Type')
    if (contentType === null || !contentType.toLowerCase().startsWith('text/csv')) {
      throw unexpected(response.status)
    }

    return {
      blob: await response.blob(),
      filename: csvFilename(
        response.headers.get('Content-Disposition'),
        safeSurveyId,
      ),
    }
  }
}

export function parseResponsePage(
  payload: unknown,
  status: number,
): ResponsePage {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const items = readArray(payload.items, status, parseResponseListItem)
  const page = readNonNegativeInteger(payload.page, status)
  const size = readPageSize(payload.size, status)
  const totalElements = readNonNegativeInteger(payload.totalElements, status)
  const totalPages = readNonNegativeInteger(payload.totalPages, status)
  const expectedTotalPages =
    totalElements === 0 ? 0 : Math.ceil(totalElements / size)

  if (
    totalPages !== expectedTotalPages ||
    items.length > size ||
    items.length > totalElements
  ) {
    throw unexpected(status)
  }

  items.forEach((item, index) => {
    if (index === 0) {
      return
    }
    const previous = items[index - 1]
    const previousTime = Date.parse(previous.submittedAt)
    const currentTime = Date.parse(item.submittedAt)
    if (
      previousTime < currentTime ||
      (previousTime === currentTime && previous.responseId <= item.responseId)
    ) {
      throw unexpected(status)
    }
  })

  return { items, page, size, totalElements, totalPages }
}

export function parseResponseSummary(
  payload: unknown,
  status: number,
): ResponseSummary {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const totalResponses = readNonNegativeInteger(payload.totalResponses, status)
  const lastSubmittedAt = readNullableInstant(payload.lastSubmittedAt, status)
  const questionCount = readNonNegativeInteger(payload.questionCount, status)
  const questions = readArray(payload.questions, status, parseSummaryQuestion)

  if (
    questions.length !== questionCount ||
    (totalResponses === 0 && lastSubmittedAt !== null) ||
    (totalResponses > 0 && lastSubmittedAt === null)
  ) {
    throw unexpected(status)
  }

  assertGaplessQuestionOrder(questions, status)

  return {
    surveyId: readPositiveId(payload.surveyId, status),
    status: readSurveyStatus(payload.status, status),
    totalResponses,
    lastSubmittedAt,
    questionCount,
    questions,
  }
}

export function parseResponseDetail(
  payload: unknown,
  status: number,
): ResponseDetail {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const questions = readArray(payload.questions, status, parseDetailQuestion)
  assertGaplessQuestionOrder(questions, status)

  return {
    responseId: readPositiveId(payload.responseId, status),
    submittedAt: readInstant(payload.submittedAt, status),
    questions,
  }
}

function parseResponseListItem(
  payload: unknown,
  status: number,
): ResponseListItem {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }
  return {
    responseId: readPositiveId(payload.responseId, status),
    submittedAt: readInstant(payload.submittedAt, status),
  }
}

function parseSummaryQuestion(
  payload: unknown,
  status: number,
): ResponseSummaryQuestion {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const type = readQuestionType(payload.type, status)
  const base = {
    questionId: readPositiveId(payload.questionId, status),
    type,
    title: readString(payload.title, status),
    position: readNonNegativeInteger(payload.position, status),
    answeredCount: readNonNegativeInteger(payload.answeredCount, status),
  }

  if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
    const options = readArray(payload.options, status, parseSummaryOption)
    if (options.length < 2) {
      throw unexpected(status)
    }
    const optionIds = new Set<number>()
    options.forEach((option, index) => {
      if (option.position !== index || optionIds.has(option.optionId)) {
        throw unexpected(status)
      }
      optionIds.add(option.optionId)
    })
    return { ...base, type, options }
  }

  if (type === 'SCALE') {
    const average = readNullableFixedDecimal(payload.average, status)
    const distribution = readArray(
      payload.distribution,
      status,
      parseScaleBucket,
    )
    if (distribution.length < 2) {
      throw unexpected(status)
    }
    distribution.forEach((bucket, index) => {
      if (
        index > 0 &&
        bucket.value !== distribution[index - 1].value + 1
      ) {
        throw unexpected(status)
      }
    })
    if (base.answeredCount === 0 && average !== null) {
      throw unexpected(status)
    }
    return { ...base, type, average, distribution }
  }

  return { ...base, type }
}

function parseSummaryOption(
  payload: unknown,
  status: number,
): ChoiceSummaryOption {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }
  return {
    optionId: readPositiveId(payload.optionId, status),
    label: readString(payload.label, status),
    position: readNonNegativeInteger(payload.position, status),
    count: readNonNegativeInteger(payload.count, status),
    percentage: readPercentage(payload.percentage, status),
  }
}

function parseScaleBucket(
  payload: unknown,
  status: number,
): ScaleSummaryBucket {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }
  return {
    value: readInteger(payload.value, status),
    count: readNonNegativeInteger(payload.count, status),
    percentage: readPercentage(payload.percentage, status),
  }
}

function parseDetailQuestion(
  payload: unknown,
  status: number,
): ResponseDetailQuestion {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const type = readQuestionType(payload.type, status)
  const required = readBoolean(payload.required, status)
  const answer =
    payload.answer === null
      ? null
      : parseAnswer(payload.answer, type, status)

  if (required && answer === null) {
    throw unexpected(status)
  }

  return {
    questionId: readPositiveId(payload.questionId, status),
    type,
    title: readString(payload.title, status),
    description: readNullableString(payload.description, status),
    required,
    position: readNonNegativeInteger(payload.position, status),
    answer,
  }
}

function parseAnswer(
  payload: unknown,
  type: QuestionType,
  status: number,
): ResponseAnswer {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const textValue = readNullableString(payload.textValue, status)
  const numericValue = readNullableCanonicalDecimal(
    payload.numericValue,
    status,
  )
  const options = readArray(payload.options, status, parseSelectedOption)

  if (type === 'SHORT_TEXT' || type === 'LONG_TEXT') {
    if (textValue === null || numericValue !== null || options.length !== 0) {
      throw unexpected(status)
    }
  } else if (type === 'SCALE' || type === 'NUMBER') {
    if (textValue !== null || numericValue === null || options.length !== 0) {
      throw unexpected(status)
    }
  } else {
    if (textValue !== null || numericValue !== null || options.length === 0) {
      throw unexpected(status)
    }
    if (type === 'SINGLE_CHOICE' && options.length !== 1) {
      throw unexpected(status)
    }
    const optionIds = new Set<number>()
    options.forEach((option, index) => {
      if (
        optionIds.has(option.id) ||
        (index > 0 && option.position <= options[index - 1].position)
      ) {
        throw unexpected(status)
      }
      optionIds.add(option.id)
    })
  }

  return { textValue, numericValue, options }
}

function parseSelectedOption(
  payload: unknown,
  status: number,
): SelectedOption {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }
  return {
    id: readPositiveId(payload.id, status),
    label: readString(payload.label, status),
    position: readNonNegativeInteger(payload.position, status),
  }
}

function assertGaplessQuestionOrder(
  questions: Array<{ questionId: number; position: number }>,
  status: number,
): void {
  const questionIds = new Set<number>()
  questions.forEach((question, index) => {
    if (question.position !== index || questionIds.has(question.questionId)) {
      throw unexpected(status)
    }
    questionIds.add(question.questionId)
  })
}

function responsePath(surveyId: number): string {
  return `/api/surveys/${positiveId(surveyId)}/responses`
}

function positiveId(value: number): number {
  if (!Number.isSafeInteger(value) || value < 1) {
    throw unexpected(0)
  }
  return value
}

function nonNegativeInteger(value: number): number {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw unexpected(0)
  }
  return value
}

function pageSize(value: number): number {
  if (!Number.isSafeInteger(value) || value < 1 || value > 100) {
    throw unexpected(0)
  }
  return value
}

function readPositiveId(value: unknown, status: number): number {
  const parsed = readInteger(value, status)
  if (parsed < 1) {
    throw unexpected(status)
  }
  return parsed
}

function readNonNegativeInteger(value: unknown, status: number): number {
  const parsed = readInteger(value, status)
  if (parsed < 0) {
    throw unexpected(status)
  }
  return parsed
}

function readPageSize(value: unknown, status: number): number {
  const parsed = readInteger(value, status)
  if (parsed < 1 || parsed > 100) {
    throw unexpected(status)
  }
  return parsed
}

function readInteger(value: unknown, status: number): number {
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

function readNullableString(value: unknown, status: number): string | null {
  return value === null ? null : readString(value, status)
}

function readBoolean(value: unknown, status: number): boolean {
  if (typeof value !== 'boolean') {
    throw unexpected(status)
  }
  return value
}

function readSurveyStatus(value: unknown, status: number): SurveyStatus {
  if (value === 'DRAFT' || value === 'OPEN' || value === 'CLOSED') {
    return value
  }
  throw unexpected(status)
}

function readQuestionType(value: unknown, status: number): QuestionType {
  if (
    typeof value === 'string' &&
    questionTypes.includes(value as QuestionType)
  ) {
    return value as QuestionType
  }
  throw unexpected(status)
}

function readInstant(value: unknown, status: number): string {
  if (
    typeof value !== 'string' ||
    !value.endsWith('Z') ||
    Number.isNaN(Date.parse(value))
  ) {
    throw unexpected(status)
  }
  return value
}

function readNullableInstant(value: unknown, status: number): string | null {
  return value === null ? null : readInstant(value, status)
}

function readPercentage(value: unknown, status: number): string {
  if (typeof value !== 'string' || !/^[0-9]+\.[0-9]{2}$/.test(value)) {
    throw unexpected(status)
  }
  return value
}

function readNullableFixedDecimal(
  value: unknown,
  status: number,
): string | null {
  if (value === null) {
    return null
  }
  if (typeof value !== 'string' || !/^-?[0-9]+\.[0-9]{2}$/.test(value)) {
    throw unexpected(status)
  }
  return value
}

function readNullableCanonicalDecimal(
  value: unknown,
  status: number,
): string | null {
  if (value === null) {
    return null
  }
  if (
    typeof value !== 'string' ||
    value === '-0' ||
    !/^-?(?:0|[1-9][0-9]*)(?:\.[0-9]*[1-9])?$/.test(value)
  ) {
    throw unexpected(status)
  }
  return value
}

function readArray<T>(
  value: unknown,
  status: number,
  parser: (item: unknown, status: number) => T,
): T[] {
  if (!Array.isArray(value)) {
    throw unexpected(status)
  }
  return value.map((item) => parser(item, status))
}

function csvFilename(
  contentDisposition: string | null,
  surveyId: number,
): string {
  const fallback = `formdock-survey-${surveyId}-responses.csv`
  if (contentDisposition === null) {
    return fallback
  }

  const match = /(?:^|;)\s*filename=(?:"([^"]+)"|([^;]+))/i.exec(
    contentDisposition,
  )
  const candidate = (match?.[1] ?? match?.[2])?.trim()
  if (
    candidate === undefined ||
    !/^[A-Za-z0-9][A-Za-z0-9._-]{0,126}\.csv$/i.test(candidate) ||
    candidate.includes('..')
  ) {
    return fallback
  }
  return candidate
}

function unexpected(status: number): ApiError {
  return new ApiError('UNEXPECTED_RESPONSE', status)
}

export const resultsClient = new SameOriginResultsClient((input, init) =>
  fetch(input, init),
)
