import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { ApiError } from '../api/apiClient.ts'
import type {
  ResponseAnswer,
  ResponseDetail,
  ResponseDetailQuestion,
  ResultsClient,
} from '../results/resultsClient.ts'
import {
  formatResultTimestamp,
  parsePositiveRouteId,
} from '../results/resultsUi.ts'
import { questionTypeLabel } from '../surveys/surveyUi.ts'

type SurveyResponseDetailPageProps = {
  client: ResultsClient
}

type DetailState =
  | { status: 'loading' }
  | { status: 'ready'; detail: ResponseDetail }
  | { status: 'survey-not-found' }
  | { status: 'response-not-found' }
  | { status: 'unavailable' }

function SurveyResponseDetailPage({ client }: SurveyResponseDetailPageProps) {
  const { responseId, surveyId } = useParams()
  return (
    <SurveyResponseDetailContent
      client={client}
      key={`${surveyId ?? 'invalid'}:${responseId ?? 'invalid'}`}
      routeResponseId={responseId}
      routeSurveyId={surveyId}
    />
  )
}

function SurveyResponseDetailContent({
  client,
  routeResponseId,
  routeSurveyId,
}: SurveyResponseDetailPageProps & {
  routeResponseId: string | undefined
  routeSurveyId: string | undefined
}) {
  const navigate = useNavigate()
  const surveyId = parsePositiveRouteId(routeSurveyId)
  const responseId = parsePositiveRouteId(routeResponseId)
  const hasValidRoute = surveyId !== null && responseId !== null
  const [state, setState] = useState<DetailState>(() =>
    hasValidRoute ? { status: 'loading' } : { status: 'response-not-found' },
  )
  const [retryKey, setRetryKey] = useState(0)
  const headingRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (state.status === 'ready') {
      headingRef.current?.focus()
    }
  }, [state.status])

  useEffect(() => {
    if (surveyId === null || responseId === null) {
      return
    }

    let active = true
    client.getResponseDetail(surveyId, responseId).then(
      (detail) => {
        if (active) {
          setState({ status: 'ready', detail })
        }
      },
      (error: unknown) => {
        if (!active) {
          return
        }
        if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
          navigate('/login', { replace: true })
        } else if (
          error instanceof ApiError &&
          error.code === 'SURVEY_NOT_FOUND'
        ) {
          setState({ status: 'survey-not-found' })
        } else if (
          error instanceof ApiError &&
          error.code === 'RESPONSE_NOT_FOUND'
        ) {
          setState({ status: 'response-not-found' })
        } else {
          setState({ status: 'unavailable' })
        }
      },
    )

    return () => {
      active = false
    }
  }, [client, navigate, responseId, retryKey, surveyId])

  if (state.status === 'loading') {
    return (
      <main className="admin-content results-shell">
        <section aria-live="polite" className="admin-card" role="status">
          개별 응답을 불러오는 중…
        </section>
      </main>
    )
  }

  if (state.status === 'survey-not-found') {
    return (
      <DetailStateCard
        description="이 설문은 사용할 수 없거나 삭제됐습니다."
        overviewPath={null}
        title="설문을 찾을 수 없습니다"
      />
    )
  }

  if (state.status === 'response-not-found') {
    return (
      <DetailStateCard
        description="이 설문에서 해당 응답을 찾을 수 없습니다."
        overviewPath={
          surveyId === null
            ? null
            : `/admin/surveys/${surveyId}/responses`
        }
        title="응답을 찾을 수 없습니다"
      />
    )
  }

  if (state.status === 'unavailable') {
    return (
      <main className="admin-content results-shell">
        <section className="admin-card">
          <h2>개별 응답을 불러올 수 없습니다</h2>
          <p className="error-message" role="alert">
            FormDock을 일시적으로 사용할 수 없습니다.
          </p>
          <div className="form-actions">
            <button
              onClick={() => {
                setState({ status: 'loading' })
                setRetryKey((value) => value + 1)
              }}
              type="button"
            >
              다시 시도
            </button>
            {surveyId === null ? null : (
              <Link
                className="secondary-link"
                to={`/admin/surveys/${surveyId}/responses`}
              >
                응답 결과
              </Link>
            )}
          </div>
        </section>
      </main>
    )
  }

  const { detail } = state

  return (
    <main className="admin-content results-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">개별 응답</p>
          <h2 ref={headingRef} tabIndex={-1}>
            응답 #{detail.responseId}
          </h2>
          <p className="page-description">
            제출 시각:{' '}
            <time dateTime={detail.submittedAt}>
              {formatResultTimestamp(detail.submittedAt)}
            </time>
          </p>
        </div>
        <div className="card-actions">
          <Link
            className="secondary-link"
            to={`/admin/surveys/${surveyId}/responses`}
          >
            응답 결과
          </Link>
          <Link className="secondary-link" to="/admin/surveys">
            설문 목록
          </Link>
        </div>
      </div>

      {detail.questions.length === 0 ? (
        <section className="admin-card empty-state">
          이 설문에는 표시할 질문이 없습니다.
        </section>
      ) : (
        <ol className="response-detail-list">
          {detail.questions.map((question) => (
            <li className="admin-card" key={question.questionId}>
              <ResponseQuestion question={question} />
            </li>
          ))}
        </ol>
      )}
    </main>
  )
}

function ResponseQuestion({ question }: { question: ResponseDetailQuestion }) {
  return (
    <article className="response-detail-question">
      <header>
        <p className="eyebrow">{questionTypeLabel(question.type)}</p>
        <h3>{question.title}</h3>
        {question.description === null ? null : (
          <p className="question-summary">{question.description}</p>
        )}
        <p className="preview-label">
          {question.required ? '필수 질문' : '선택 질문'}
        </p>
      </header>
      <div className="response-answer">
        {question.answer === null ? (
          <p className="unanswered-value">응답 없음</p>
        ) : (
          <AnswerValue answer={question.answer} type={question.type} />
        )}
      </div>
    </article>
  )
}

function AnswerValue({
  answer,
  type,
}: {
  answer: ResponseAnswer
  type: ResponseDetailQuestion['type']
}) {
  if (type === 'SHORT_TEXT' || type === 'LONG_TEXT') {
    return <p className="response-text-value">{answer.textValue}</p>
  }

  if (type === 'NUMBER' || type === 'SCALE') {
    return <p className="response-numeric-value">{answer.numericValue}</p>
  }

  return (
    <ul className="selected-option-list">
      {answer.options.map((option) => (
        <li key={option.id}>{option.label}</li>
      ))}
    </ul>
  )
}

function DetailStateCard({
  description,
  overviewPath,
  title,
}: {
  description: string
  overviewPath: string | null
  title: string
}) {
  return (
    <main className="admin-content results-shell">
      <section className="admin-card">
        <h2>{title}</h2>
        <p className="error-message" role="alert">
          {description}
        </p>
        <div className="form-actions">
          {overviewPath === null ? null : (
            <Link className="secondary-link" to={overviewPath}>
              응답 결과
            </Link>
          )}
          <Link className="secondary-link" to="/admin/surveys">
            설문 목록
          </Link>
        </div>
      </section>
    </main>
  )
}

export default SurveyResponseDetailPage
