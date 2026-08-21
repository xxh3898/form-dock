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
import { questionTypeLabel } from '../surveys/surveyUi.ts'

type QuestionEditorProps = {
  question?: SurveyQuestion
  pending: boolean
  apiFieldErrors: ApiFieldError[]
  onCancel(): void
  onSave(input: QuestionWriteInput): void
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
        <legend>{question === undefined ? '질문 추가' : '질문 편집'}</legend>

        <div className="form-grid">
          <label htmlFor={`${editorId}-type`}>질문 유형</label>
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
                {questionTypeLabel(type)}
              </option>
            ))}
          </select>

          <label htmlFor={`${editorId}-title`}>질문 제목</label>
          <input
            aria-invalid={hasError(errors, 'title')}
            id={`${editorId}-title`}
            onChange={(event) =>
              setForm((current) => ({ ...current, title: event.target.value }))
            }
            required
            value={form.title}
          />

          <label htmlFor={`${editorId}-description`}>설명 (선택)</label>
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
            필수 응답
          </label>
        </div>

        {isChoice(form.type) ? (
          <fieldset className="option-editor">
            <legend>선택지</legend>
            {form.options.map((option, index) => (
              <div className="option-row" key={option.key}>
                <label htmlFor={`${editorId}-option-${option.key}`}>
                  선택지 {index + 1}
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
                    aria-label={`선택지 ${index + 1} 위로 이동`}
                    disabled={index === 0}
                    onClick={() =>
                      setForm((current) =>
                        moveQuestionOption(current, index, -1),
                      )
                    }
                    type="button"
                  >
                    위로
                  </button>
                  <button
                    aria-label={`선택지 ${index + 1} 아래로 이동`}
                    disabled={index === form.options.length - 1}
                    onClick={() =>
                      setForm((current) =>
                        moveQuestionOption(current, index, 1),
                      )
                    }
                    type="button"
                  >
                    아래로
                  </button>
                  <button
                    aria-label={`선택지 ${index + 1} 삭제`}
                    disabled={form.options.length <= 2}
                    onClick={() =>
                      setForm((current) =>
                        removeQuestionOption(current, index),
                      )
                    }
                    type="button"
                  >
                    삭제
                  </button>
                </div>
              </div>
            ))}
            <button
              className="secondary-button"
              onClick={() => setForm(addQuestionOption)}
              type="button"
            >
              선택지 추가
            </button>
          </fieldset>
        ) : null}

        {form.type === 'SCALE' ? (
          <fieldset className="type-configuration">
            <legend>척도 설정</legend>
            <div className="two-column-fields">
              <label>
                최솟값
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
                최댓값
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
                최솟값 설명 (선택)
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
                최댓값 설명 (선택)
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
            <legend>숫자 범위</legend>
            <div className="two-column-fields">
              <label>
                최솟값 (선택)
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
                최댓값 (선택)
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
            {pending ? '저장 중…' : '질문 저장'}
          </button>
          <button
            className="secondary-button"
            disabled={pending}
            onClick={onCancel}
            type="button"
          >
            취소
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
