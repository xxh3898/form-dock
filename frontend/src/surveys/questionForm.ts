import type {
  QuestionType,
  QuestionWriteInput,
  SurveyQuestion,
} from './surveyClient.ts'

export type QuestionFormOption = {
  key: string
  id?: number
  label: string
}

export type QuestionFormState = {
  type: QuestionType
  title: string
  description: string
  required: boolean
  scaleMin: string
  scaleMax: string
  scaleMinLabel: string
  scaleMaxLabel: string
  numberMin: string
  numberMax: string
  options: QuestionFormOption[]
}

export type QuestionFormResult =
  | { input: QuestionWriteInput; errors: null }
  | { input: null; errors: Record<string, string> }

let nextOptionKey = 1

export function emptyQuestionForm(
  type: QuestionType = 'SHORT_TEXT',
): QuestionFormState {
  return {
    type,
    title: '',
    description: '',
    required: false,
    scaleMin: type === 'SCALE' ? '1' : '',
    scaleMax: type === 'SCALE' ? '5' : '',
    scaleMinLabel: '',
    scaleMaxLabel: '',
    numberMin: '',
    numberMax: '',
    options: isChoice(type) ? [newOption(), newOption()] : [],
  }
}

export function questionFormFrom(
  question: SurveyQuestion,
): QuestionFormState {
  return {
    type: question.type,
    title: question.title,
    description: question.description ?? '',
    required: question.required,
    scaleMin: question.scaleMin?.toString() ?? '',
    scaleMax: question.scaleMax?.toString() ?? '',
    scaleMinLabel: question.scaleMinLabel ?? '',
    scaleMaxLabel: question.scaleMaxLabel ?? '',
    numberMin: question.numberMin ?? '',
    numberMax: question.numberMax ?? '',
    options: question.options.map((option) => ({
      id: option.id,
      key: `option-${option.id}`,
      label: option.label,
    })),
  }
}

export function changeQuestionType(
  state: QuestionFormState,
  type: QuestionType,
): QuestionFormState {
  const next = emptyQuestionForm(type)
  return {
    ...next,
    title: state.title,
    description: state.description,
    required: state.required,
  }
}

export function addQuestionOption(
  state: QuestionFormState,
): QuestionFormState {
  return { ...state, options: [...state.options, newOption()] }
}

export function updateQuestionOption(
  state: QuestionFormState,
  index: number,
  label: string,
): QuestionFormState {
  return {
    ...state,
    options: state.options.map((option, current) =>
      current === index ? { ...option, label } : option,
    ),
  }
}

export function removeQuestionOption(
  state: QuestionFormState,
  index: number,
): QuestionFormState {
  if (state.options.length <= 2) {
    return state
  }
  return {
    ...state,
    options: state.options.filter((_, current) => current !== index),
  }
}

export function moveQuestionOption(
  state: QuestionFormState,
  index: number,
  direction: -1 | 1,
): QuestionFormState {
  const destination = index + direction
  if (destination < 0 || destination >= state.options.length) {
    return state
  }
  const options = [...state.options]
  const current = options[index]
  const target = options[destination]
  if (current === undefined || target === undefined) {
    return state
  }
  options[index] = target
  options[destination] = current
  return { ...state, options }
}

export function buildQuestionInput(
  state: QuestionFormState,
): QuestionFormResult {
  const errors: Record<string, string> = {}
  const title = state.title.trim()
  if (title.length === 0) {
    errors.title = 'Question title is required.'
  }

  if (isChoice(state.type)) {
    if (state.options.length < 2) {
      errors.options = 'Choice questions require at least two options.'
    }
    state.options.forEach((option, index) => {
      if (option.label.trim().length === 0) {
        errors[`options[${index}].label`] = 'Option label is required.'
      }
    })
  }

  const scaleMin = integerOrNull(state.scaleMin)
  const scaleMax = integerOrNull(state.scaleMax)
  if (state.type === 'SCALE') {
    if (
      scaleMin === null ||
      scaleMax === null ||
      scaleMin < 1 ||
      scaleMin >= scaleMax ||
      scaleMax > 10
    ) {
      errors.scale = 'Scale must use integer bounds from 1 to 10 with min below max.'
    }
  }

  if (state.type === 'NUMBER') {
    if (!isDecimalOrBlank(state.numberMin)) {
      errors.numberMin = 'Minimum must be an exponent-free decimal.'
    }
    if (!isDecimalOrBlank(state.numberMax)) {
      errors.numberMax = 'Maximum must be an exponent-free decimal.'
    }
  }

  if (Object.keys(errors).length > 0) {
    return { errors, input: null }
  }

  return {
    errors: null,
    input: {
      type: state.type,
      title,
      description: optionalText(state.description),
      required: state.required,
      scaleMin: state.type === 'SCALE' ? scaleMin : null,
      scaleMax: state.type === 'SCALE' ? scaleMax : null,
      scaleMinLabel:
        state.type === 'SCALE' ? optionalText(state.scaleMinLabel) : null,
      scaleMaxLabel:
        state.type === 'SCALE' ? optionalText(state.scaleMaxLabel) : null,
      numberMin: state.type === 'NUMBER' ? decimalOrNull(state.numberMin) : null,
      numberMax: state.type === 'NUMBER' ? decimalOrNull(state.numberMax) : null,
      options: isChoice(state.type)
        ? state.options.map((option) => ({
            ...(option.id === undefined ? {} : { id: option.id }),
            label: option.label.trim(),
          }))
        : [],
    },
  }
}

export function isChoice(type: QuestionType): boolean {
  return type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE'
}

function newOption(): QuestionFormOption {
  const key = `new-option-${nextOptionKey}`
  nextOptionKey += 1
  return { key, label: '' }
}

function optionalText(value: string): string | null {
  return value.trim().length === 0 ? null : value
}

function decimalOrNull(value: string): string | null {
  const normalized = value.trim()
  return normalized.length === 0 ? null : normalized
}

function integerOrNull(value: string): number | null {
  const normalized = value.trim()
  if (!/^-?[0-9]+$/.test(normalized)) {
    return null
  }
  const parsed = Number(normalized)
  return Number.isSafeInteger(parsed) ? parsed : null
}

function isDecimalOrBlank(value: string): boolean {
  const normalized = value.trim()
  return normalized.length === 0 || /^-?[0-9]+(?:\.[0-9]+)?$/.test(normalized)
}
