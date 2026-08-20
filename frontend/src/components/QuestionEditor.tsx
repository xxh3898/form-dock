import { type FormEvent, useId, useState } from 'react'

import type { ApiFieldError } from '../api/apiClient.ts'
import {
  addQuestionOption,
  buildQuestionInput,
  changeQuestionType,
  emptyQuestionForm,
  isChoice,
  moveQuestionOption,
  questionFormFrom,
  removeQuestionOption,
  type QuestionFormState,
  updateQuestionOption,
} from '../surveys/questionForm.ts'
import {
  questionTypes,
  type QuestionType,
  type QuestionWriteInput,
  type SurveyQuestion,
} from '../surveys/surveyClient.ts'

type QuestionEditorProps = {
  question?: SurveyQuestion
  pending: boolean
  apiFieldErrors: ApiFieldError[]
  onCancel(): void
  onSave(input: QuestionWriteInput): void
}

const typeLabels: Record<QuestionType, string> = {
  SHORT_TEXT: 'Short text',
  LONG_TEXT: 'Long text',
  SINGLE_CHOICE: 'Single choice',
  MULTIPLE_CHOICE: 'Multiple choice',
  SCALE: 'Scale',
  NUMBER: 'Number',
}

function QuestionEditor({
  question,
  pending,
  apiFieldErrors,
  onCancel,
  onSave,
}: QuestionEditorProps) {
  const editorId = useId()
  const [form, setForm] = useState<QuestionFormState>(() =>
    question === undefined ? emptyQuestionForm() : questionFormFrom(question),
  )
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({})

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const result = buildQuestionInput(form)
    if (result.input === null) {
      setClientErrors(result.errors)
      return
    }
    setClientErrors({})
    onSave(result.input)
  }

  const errors = [
    ...Object.entries(clientErrors).map(([path, message]) => ({
      code: 'CLIENT_VALIDATION',
      message,
      path,
    })),
    ...apiFieldErrors,
  ]

  return (
    <form className="question-editor" onSubmit={handleSubmit}>
      <fieldset disabled={pending}>
        <legend>{question === undefined ? 'Add question' : 'Edit question'}</legend>

        <div className="form-grid">
          <label htmlFor={`${editorId}-type`}>Question type</label>
          <select
            id={`${editorId}-type`}
            onChange={(event) =>
              setForm((current) =>
                changeQuestionType(current, event.target.value as QuestionType),
              )
            }
            value={form.type}
          >
            {questionTypes.map((type) => (
              <option key={type} value={type}>
                {typeLabels[type]}
              </option>
            ))}
          </select>

          <label htmlFor={`${editorId}-title`}>Question title</label>
          <input
            aria-invalid={hasError(errors, 'title')}
            id={`${editorId}-title`}
            onChange={(event) =>
              setForm((current) => ({ ...current, title: event.target.value }))
            }
            required
            value={form.title}
          />

          <label htmlFor={`${editorId}-description`}>Description (optional)</label>
          <textarea
            id={`${editorId}-description`}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                description: event.target.value,
              }))
            }
            rows={3}
            value={form.description}
          />

          <label className="checkbox-label">
            <input
              checked={form.required}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  required: event.target.checked,
                }))
              }
              type="checkbox"
            />
            Required response
          </label>
        </div>

        {isChoice(form.type) ? (
          <fieldset className="option-editor">
            <legend>Options</legend>
            {form.options.map((option, index) => (
              <div className="option-row" key={option.key}>
                <label htmlFor={`${editorId}-option-${option.key}`}>
                  Option {index + 1}
                </label>
                <input
                  aria-invalid={hasError(errors, `options[${index}].label`)}
                  id={`${editorId}-option-${option.key}`}
                  onChange={(event) =>
                    setForm((current) =>
                      updateQuestionOption(current, index, event.target.value),
                    )
                  }
                  value={option.label}
                />
                <div className="compact-actions">
                  <button
                    aria-label={`Move option ${index + 1} up`}
                    disabled={index === 0}
                    onClick={() =>
                      setForm((current) =>
                        moveQuestionOption(current, index, -1),
                      )
                    }
                    type="button"
                  >
                    Up
                  </button>
                  <button
                    aria-label={`Move option ${index + 1} down`}
                    disabled={index === form.options.length - 1}
                    onClick={() =>
                      setForm((current) =>
                        moveQuestionOption(current, index, 1),
                      )
                    }
                    type="button"
                  >
                    Down
                  </button>
                  <button
                    aria-label={`Remove option ${index + 1}`}
                    disabled={form.options.length <= 2}
                    onClick={() =>
                      setForm((current) =>
                        removeQuestionOption(current, index),
                      )
                    }
                    type="button"
                  >
                    Remove
                  </button>
                </div>
              </div>
            ))}
            <button
              className="secondary-button"
              onClick={() => setForm(addQuestionOption)}
              type="button"
            >
              Add option
            </button>
          </fieldset>
        ) : null}

        {form.type === 'SCALE' ? (
          <fieldset className="type-configuration">
            <legend>Scale configuration</legend>
            <div className="two-column-fields">
              <label>
                Minimum
                <input
                  inputMode="numeric"
                  max="9"
                  min="1"
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      scaleMin: event.target.value,
                    }))
                  }
                  type="number"
                  value={form.scaleMin}
                />
              </label>
              <label>
                Maximum
                <input
                  inputMode="numeric"
                  max="10"
                  min="2"
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      scaleMax: event.target.value,
                    }))
                  }
                  type="number"
                  value={form.scaleMax}
                />
              </label>
              <label>
                Minimum label (optional)
                <input
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      scaleMinLabel: event.target.value,
                    }))
                  }
                  value={form.scaleMinLabel}
                />
              </label>
              <label>
                Maximum label (optional)
                <input
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      scaleMaxLabel: event.target.value,
                    }))
                  }
                  value={form.scaleMaxLabel}
                />
              </label>
            </div>
          </fieldset>
        ) : null}

        {form.type === 'NUMBER' ? (
          <fieldset className="type-configuration">
            <legend>Number bounds</legend>
            <div className="two-column-fields">
              <label>
                Minimum (optional)
                <input
                  aria-invalid={hasError(errors, 'numberMin')}
                  inputMode="decimal"
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      numberMin: event.target.value,
                    }))
                  }
                  placeholder="0.0000"
                  type="text"
                  value={form.numberMin}
                />
              </label>
              <label>
                Maximum (optional)
                <input
                  aria-invalid={hasError(errors, 'numberMax')}
                  inputMode="decimal"
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      numberMax: event.target.value,
                    }))
                  }
                  placeholder="100.0000"
                  type="text"
                  value={form.numberMax}
                />
              </label>
            </div>
          </fieldset>
        ) : null}

        {errors.length === 0 ? null : (
          <ul className="error-list" role="alert">
            {errors.map((error, index) => (
              <li key={`${error.path}-${error.code}-${index}`}>
                {error.message}
              </li>
            ))}
          </ul>
        )}

        <div className="form-actions">
          <button disabled={pending} type="submit">
            {pending ? 'Saving…' : 'Save question'}
          </button>
          <button
            className="secondary-button"
            disabled={pending}
            onClick={onCancel}
            type="button"
          >
            Cancel
          </button>
        </div>
      </fieldset>
    </form>
  )
}

function hasError(errors: ApiFieldError[], path: string): boolean {
  return errors.some((error) => error.path === path)
}

export default QuestionEditor
