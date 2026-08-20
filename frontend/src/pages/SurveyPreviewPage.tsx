import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { ApiError } from '../api/apiClient.ts'
import type {
  SurveyClient,
  SurveyDetail,
  SurveyQuestion,
} from '../surveys/surveyClient.ts'
import { parseSurveyId } from '../surveys/surveyUi.ts'

type SurveyPreviewPageProps = {
  client: SurveyClient
}

type PreviewState =
  | { status: 'loading' }
  | { status: 'ready'; survey: SurveyDetail }
  | { status: 'not-found' }
  | { status: 'unavailable' }

function SurveyPreviewPage({ client }: SurveyPreviewPageProps) {
  const navigate = useNavigate()
  const surveyId = parseSurveyId(useParams().surveyId)
  const [state, setState] = useState<PreviewState>(() =>
    surveyId === null ? { status: 'not-found' } : { status: 'loading' },
  )
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    if (surveyId === null) {
      return
    }
    let active = true
    client.getSurvey(surveyId).then(
      (survey) => {
        if (active) {
          setState({ status: 'ready', survey })
        }
      },
      (error: unknown) => {
        if (!active) {
          return
        }
        if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
          navigate('/login', { replace: true })
          return
        }
        setState(
          error instanceof ApiError && error.code === 'SURVEY_NOT_FOUND'
            ? { status: 'not-found' }
            : { status: 'unavailable' },
        )
      },
    )
    return () => {
      active = false
    }
  }, [client, navigate, retryKey, surveyId])

  if (state.status === 'loading') {
    return (
      <main aria-live="polite" className="admin-content admin-card" role="status">
        Loading Admin Preview…
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <main className="admin-content admin-card">
        <h2>Survey unavailable</h2>
        <p>This Survey is unavailable or has been deleted.</p>
        <Link className="text-link" to="/admin/surveys">
          Back to Surveys
        </Link>
      </main>
    )
  }

  if (state.status === 'unavailable') {
    return (
      <main className="admin-content admin-card">
        <h2>Preview unavailable</h2>
        <p className="error-message" role="alert">
          We could not load this Admin Preview.
        </p>
        <button
          onClick={() => {
            setState({ status: 'loading' })
            setRetryKey((value) => value + 1)
          }}
          type="button"
        >
          Try again
        </button>
      </main>
    )
  }

  const { survey } = state
  return (
    <main className="admin-content preview-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Admin Preview · {survey.status}</p>
          <h2>{survey.title}</h2>
          <p className="field-help">
            Reserved slug: <code>{survey.slug}</code> · No public route is active.
          </p>
        </div>
        <Link className="secondary-link" to={`/admin/surveys/${survey.id}`}>
          Back to Builder
        </Link>
      </div>

      {survey.description === null ? null : (
        <p className="preview-description">{survey.description}</p>
      )}

      {survey.questions.length === 0 ? (
        <section className="admin-card empty-state">
          <h3>No Questions to preview</h3>
          <p>Add a Question in the Builder before opening this Survey.</p>
        </section>
      ) : (
        <ol className="preview-questions">
          {survey.questions.map((question) => (
            <li className="preview-question admin-card" key={question.id}>
              <h3>
                {question.title}
                {question.required ? <span aria-label="required"> *</span> : null}
              </h3>
              {question.description === null ? null : (
                <p>{question.description}</p>
              )}
              <QuestionPreview question={question} />
            </li>
          ))}
        </ol>
      )}

      {survey.privacyNotice === null ? null : (
        <section className="privacy-notice">
          <h3>Privacy notice</h3>
          <p>{survey.privacyNotice}</p>
        </section>
      )}
      <p className="preview-label">Read-only Admin Preview · Responses are not submitted.</p>
    </main>
  )
}

function QuestionPreview({ question }: { question: SurveyQuestion }) {
  switch (question.type) {
    case 'SHORT_TEXT':
      return <input aria-label={`${question.title} preview`} disabled placeholder="Short answer" />
    case 'LONG_TEXT':
      return (
        <textarea
          aria-label={`${question.title} preview`}
          disabled
          placeholder="Long answer"
          rows={4}
        />
      )
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return (
        <fieldset className="preview-options" disabled>
          <legend className="visually-hidden">{question.title} options</legend>
          {question.options.map((option) => (
            <label key={option.id}>
              <input
                name={`preview-question-${question.id}`}
                type={question.type === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
              />
              {option.label}
            </label>
          ))}
        </fieldset>
      )
    case 'SCALE':
      return (
        <div className="scale-preview">
          <span>{question.scaleMinLabel ?? question.scaleMin}</span>
          <span aria-label={`Scale ${question.scaleMin} to ${question.scaleMax}`}>
            {question.scaleMin} – {question.scaleMax}
          </span>
          <span>{question.scaleMaxLabel ?? question.scaleMax}</span>
        </div>
      )
    case 'NUMBER':
      return (
        <div>
          <input aria-label={`${question.title} preview`} disabled inputMode="decimal" />
          <p className="field-help">
            {numberBounds(question.numberMin, question.numberMax)}
          </p>
        </div>
      )
  }
}

function numberBounds(minimum: string | null, maximum: string | null): string {
  if (minimum !== null && maximum !== null) {
    return `Allowed range: ${minimum} to ${maximum}`
  }
  if (minimum !== null) {
    return `Minimum: ${minimum}`
  }
  if (maximum !== null) {
    return `Maximum: ${maximum}`
  }
  return 'No numeric bounds'
}

export default SurveyPreviewPage
