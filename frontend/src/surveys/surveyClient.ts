import {
  ApiError,
  type FetchFunction,
  isRecord,
  SameOriginApiClient,
} from '../api/apiClient.ts'

export const questionTypes = [
  'SHORT_TEXT',
  'LONG_TEXT',
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'SCALE',
  'NUMBER',
] as const

export type QuestionType = (typeof questionTypes)[number]
export type SurveyStatus = 'DRAFT' | 'OPEN' | 'CLOSED'

export type SurveyListItem = {
  id: number
  title: string
  status: SurveyStatus
  slug: string
  responseCount: number
  updatedAt: string
}

export type QuestionOption = {
  id: number
  label: string
  position: number
}

export type SurveyQuestion = {
  id: number
  type: QuestionType
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
  options: QuestionOption[]
}

export type SurveyDetail = {
  id: number
  title: string
  description: string | null
  slug: string
  privacyNotice: string | null
  status: SurveyStatus
  openedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
  responseCount: number
  structureLocked: boolean
  questions: SurveyQuestion[]
}

export type SurveyCreateInput = {
  title: string
  description: string | null
  privacyNotice: string | null
  slug?: string | null
}

export type SurveyPatchInput = Partial<SurveyCreateInput>

export type QuestionOptionInput = {
  id?: number
  label: string
}

export type QuestionWriteInput = {
  type: QuestionType
  title: string
  description: string | null
  required: boolean
  scaleMin: number | null
  scaleMax: number | null
  scaleMinLabel: string | null
  scaleMaxLabel: string | null
  numberMin: string | null
  numberMax: string | null
  options: QuestionOptionInput[]
}

export interface SurveyClient {
  listSurveys(): Promise<SurveyListItem[]>
  createSurvey(input: SurveyCreateInput): Promise<SurveyDetail>
  getSurvey(surveyId: number): Promise<SurveyDetail>
  updateSurvey(
    surveyId: number,
    input: SurveyPatchInput,
  ): Promise<SurveyDetail>
  deleteSurvey(surveyId: number): Promise<void>
  duplicateSurvey(surveyId: number): Promise<SurveyDetail>
  openSurvey(surveyId: number): Promise<SurveyDetail>
  closeSurvey(surveyId: number): Promise<SurveyDetail>
  createQuestion(
    surveyId: number,
    input: QuestionWriteInput,
  ): Promise<SurveyDetail>
  updateQuestion(
    surveyId: number,
    questionId: number,
    input: QuestionWriteInput,
  ): Promise<SurveyDetail>
  deleteQuestion(surveyId: number, questionId: number): Promise<void>
  reorderQuestions(
    surveyId: number,
    questionIds: number[],
  ): Promise<SurveyDetail>
}

export class SameOriginSurveyClient implements SurveyClient {
  private readonly api: SameOriginApiClient

  constructor(fetchRequest: FetchFunction) {
    this.api = new SameOriginApiClient(fetchRequest)
  }

  async listSurveys(): Promise<SurveyListItem[]> {
    return this.api.getJson('/api/surveys', parseSurveyList)
  }

  async createSurvey(input: SurveyCreateInput): Promise<SurveyDetail> {
    return this.api.postJson('/api/surveys', input, parseSurveyDetail)
  }

  async getSurvey(surveyId: number): Promise<SurveyDetail> {
    return this.api.getJson(surveyPath(surveyId), parseSurveyDetail)
  }

  async updateSurvey(
    surveyId: number,
    input: SurveyPatchInput,
  ): Promise<SurveyDetail> {
    return this.api.patchJson(surveyPath(surveyId), input, parseSurveyDetail)
  }

  async deleteSurvey(surveyId: number): Promise<void> {
    await this.api.delete(surveyPath(surveyId))
  }

  async duplicateSurvey(surveyId: number): Promise<SurveyDetail> {
    return this.api.postJson(
      `${surveyPath(surveyId)}/duplicate`,
      undefined,
      parseSurveyDetail,
    )
  }

  async openSurvey(surveyId: number): Promise<SurveyDetail> {
    return this.api.postJson(
      `${surveyPath(surveyId)}/open`,
      undefined,
      parseSurveyDetail,
    )
  }

  async closeSurvey(surveyId: number): Promise<SurveyDetail> {
    return this.api.postJson(
      `${surveyPath(surveyId)}/close`,
      undefined,
      parseSurveyDetail,
    )
  }

  async createQuestion(
    surveyId: number,
    input: QuestionWriteInput,
  ): Promise<SurveyDetail> {
    return this.api.postJson(
      `${surveyPath(surveyId)}/questions`,
      input,
      parseSurveyDetail,
    )
  }

  async updateQuestion(
    surveyId: number,
    questionId: number,
    input: QuestionWriteInput,
  ): Promise<SurveyDetail> {
    return this.api.patchJson(
      `${surveyPath(surveyId)}/questions/${positiveId(questionId)}`,
      input,
      parseSurveyDetail,
    )
  }

  async deleteQuestion(surveyId: number, questionId: number): Promise<void> {
    await this.api.delete(
      `${surveyPath(surveyId)}/questions/${positiveId(questionId)}`,
    )
  }

  async reorderQuestions(
    surveyId: number,
    questionIds: number[],
  ): Promise<SurveyDetail> {
    return this.api.postJson(
      `${surveyPath(surveyId)}/questions/reorder`,
      { questionIds: questionIds.map(positiveId) },
      parseSurveyDetail,
    )
  }
}

export function parseSurveyList(
  payload: unknown,
  status: number,
): SurveyListItem[] {
  if (!Array.isArray(payload)) {
    throw unexpected(status)
  }
  return payload.map((item) => parseSurveyListItem(item, status))
}

export function parseSurveyDetail(
  payload: unknown,
  status: number,
): SurveyDetail {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const questions = parseArray(payload.questions, status, parseQuestion)
  questions.forEach((question, index) => {
    if (question.position !== index) {
      throw unexpected(status)
    }
  })

  return {
    id: readPositiveId(payload.id, status),
    title: readString(payload.title, status),
    description: readNullableString(payload.description, status),
    slug: readString(payload.slug, status),
    privacyNotice: readNullableString(payload.privacyNotice, status),
    status: readStatus(payload.status, status),
    openedAt: readNullableInstant(payload.openedAt, status),
    closedAt: readNullableInstant(payload.closedAt, status),
    createdAt: readInstant(payload.createdAt, status),
    updatedAt: readInstant(payload.updatedAt, status),
    responseCount: readNonNegativeInteger(payload.responseCount, status),
    structureLocked: readBoolean(payload.structureLocked, status),
    questions,
  }
}

function parseSurveyListItem(payload: unknown, status: number): SurveyListItem {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }
  return {
    id: readPositiveId(payload.id, status),
    title: readString(payload.title, status),
    status: readStatus(payload.status, status),
    slug: readString(payload.slug, status),
    responseCount: readNonNegativeInteger(payload.responseCount, status),
    updatedAt: readInstant(payload.updatedAt, status),
  }
}

function parseQuestion(payload: unknown, status: number): SurveyQuestion {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }

  const type = readQuestionType(payload.type, status)
  const options = parseArray(payload.options, status, parseOption)
  options.forEach((option, index) => {
    if (option.position !== index) {
      throw unexpected(status)
    }
  })

  const question: SurveyQuestion = {
    id: readPositiveId(payload.id, status),
    type,
    title: readString(payload.title, status),
    description: readNullableString(payload.description, status),
    required: readBoolean(payload.required, status),
    position: readNonNegativeInteger(payload.position, status),
    scaleMin: readNullableInteger(payload.scaleMin, status),
    scaleMax: readNullableInteger(payload.scaleMax, status),
    scaleMinLabel: readNullableString(payload.scaleMinLabel, status),
    scaleMaxLabel: readNullableString(payload.scaleMaxLabel, status),
    numberMin: readNullableDecimal(payload.numberMin, status),
    numberMax: readNullableDecimal(payload.numberMax, status),
    options,
  }

  validateQuestionConfiguration(question, status)
  return question
}

function parseOption(payload: unknown, status: number): QuestionOption {
  if (!isRecord(payload)) {
    throw unexpected(status)
  }
  return {
    id: readPositiveId(payload.id, status),
    label: readString(payload.label, status),
    position: readNonNegativeInteger(payload.position, status),
  }
}

function validateQuestionConfiguration(
  question: SurveyQuestion,
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

function surveyPath(surveyId: number): string {
  return `/api/surveys/${positiveId(surveyId)}`
}

function positiveId(value: number): number {
  if (!Number.isSafeInteger(value) || value < 1) {
    throw unexpected(0)
  }
  return value
}

function readPositiveId(value: unknown, status: number): number {
  if (
    typeof value !== 'number' ||
    !Number.isSafeInteger(value) ||
    value < 1
  ) {
    throw unexpected(status)
  }
  return value
}

function readNonNegativeInteger(value: unknown, status: number): number {
  if (
    typeof value !== 'number' ||
    !Number.isSafeInteger(value) ||
    value < 0
  ) {
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

function readNullableString(value: unknown, status: number): string | null {
  return value === null ? null : readString(value, status)
}

function readBoolean(value: unknown, status: number): boolean {
  if (typeof value !== 'boolean') {
    throw unexpected(status)
  }
  return value
}

function readStatus(value: unknown, status: number): SurveyStatus {
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
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) {
    throw unexpected(status)
  }
  return value
}

function readNullableInstant(value: unknown, status: number): string | null {
  return value === null ? null : readInstant(value, status)
}

function readNullableDecimal(value: unknown, status: number): string | null {
  if (value === null) {
    return null
  }
  if (typeof value !== 'string' || !/^-?[0-9]+(?:\.[0-9]+)?$/.test(value)) {
    throw unexpected(status)
  }
  return value
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

function unexpected(status: number): ApiError {
  return new ApiError('UNEXPECTED_RESPONSE', status)
}

export const surveyClient = new SameOriginSurveyClient((input, init) =>
  fetch(input, init),
)
