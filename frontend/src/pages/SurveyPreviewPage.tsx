import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { ApiError } from '../api/apiClient.ts'
import type {
  SurveyClient,
  SurveyDetail,
  SurveyQuestion,
} from '../surveys/surveyClient.ts'
import { parseSurveyId, surveyStatusLabel } from '../surveys/surveyUi.ts'

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
        관리자 미리보기를 불러오는 중…
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <main className="admin-content admin-card">
        <h2>설문을 사용할 수 없습니다</h2>
        <p>이 설문은 사용할 수 없거나 삭제됐습니다.</p>
        <Link className="text-link" to="/admin/surveys">
          설문 목록으로
        </Link>
      </main>
    )
  }

  if (state.status === 'unavailable') {
    return (
      <main className="admin-content admin-card">
        <h2>미리보기를 불러올 수 없습니다</h2>
        <p className="error-message" role="alert">
          관리자 미리보기를 불러오지 못했습니다.
        </p>
        <button
          onClick={() => {
            setState({ status: 'loading' })
            setRetryKey((value) => value + 1)
          }}
          type="button"
        >
          다시 시도
        </button>
      </main>
    )
  }

  const { survey } = state
  return (
    <main className="admin-content preview-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">관리자 미리보기 · {surveyStatusLabel(survey.status)}</p>
          <h2>{survey.title}</h2>
          <p className="field-help">
            예약 slug: <code>{survey.slug}</code> · 공개 경로는 아직 활성화되지 않았습니다.
          </p>
        </div>
        <Link className="secondary-link" to={`/admin/surveys/${survey.id}`}>
          설문 작성으로
        </Link>
      </div>

      {survey.description === null ? null : (
        <p className="preview-description">{survey.description}</p>
      )}

      {survey.questions.length === 0 ? (
        <section className="admin-card empty-state">
          <h3>미리볼 질문이 없습니다</h3>
          <p>설문을 공개하기 전에 작성 화면에서 질문을 추가하세요.</p>
        </section>
      ) : (
        <ol className="preview-questions">
          {survey.questions.map((question) => (
            <li className="preview-question admin-card" key={question.id}>
              <h3>
                {question.title}
                {question.required ? <span aria-label="필수"> *</span> : null}
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
          <h3>개인정보 안내</h3>
          <p>{survey.privacyNotice}</p>
        </section>
      )}
      <p className="preview-label">읽기 전용 관리자 미리보기 · 응답은 제출되지 않습니다.</p>
    </main>
  )
}

function QuestionPreview({ question }: { question: SurveyQuestion }) {
  switch (question.type) {
    case 'SHORT_TEXT':
      return <input aria-label={`${question.title} 미리보기`} disabled placeholder="단답 입력" />
    case 'LONG_TEXT':
      return (
        <textarea
          aria-label={`${question.title} 미리보기`}
          disabled
          placeholder="장문 입력"
          rows={4}
        />
      )
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return (
        <fieldset className="preview-options" disabled>
          <legend className="visually-hidden">{question.title} 선택지</legend>
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
          <span aria-label={`척도 ${question.scaleMin}에서 ${question.scaleMax}`}>
            {question.scaleMin} – {question.scaleMax}
          </span>
          <span>{question.scaleMaxLabel ?? question.scaleMax}</span>
        </div>
      )
    case 'NUMBER':
      return (
        <div>
          <input aria-label={`${question.title} 미리보기`} disabled inputMode="decimal" />
          <p className="field-help">
            {numberBounds(question.numberMin, question.numberMax)}
          </p>
        </div>
      )
  }
}

function numberBounds(minimum: string | null, maximum: string | null): string {
  if (minimum !== null && maximum !== null) {
    return `입력 범위: ${minimum}부터 ${maximum}`
  }
  if (minimum !== null) {
    return `최솟값: ${minimum}`
  }
  if (maximum !== null) {
    return `최댓값: ${maximum}`
  }
  return '숫자 범위 제한 없음'
}

export default SurveyPreviewPage
