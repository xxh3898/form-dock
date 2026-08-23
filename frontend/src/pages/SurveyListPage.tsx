import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { ApiError } from '../api/apiClient.ts'
import type {
  SurveyClient,
  SurveyListItem,
} from '../surveys/surveyClient.ts'
import { surveyStatusLabel } from '../surveys/surveyUi.ts'

type SurveyListPageProps = {
  client: SurveyClient
}

type ListState =
  | { status: 'loading' }
  | { status: 'ready'; surveys: SurveyListItem[] }
  | { status: 'unavailable' }

function SurveyListPage({ client }: SurveyListPageProps) {
  const navigate = useNavigate()
  const [state, setState] = useState<ListState>({ status: 'loading' })
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    client.listSurveys().then(
      (surveys) => {
        if (active) {
          setState({ status: 'ready', surveys })
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
        setState({ status: 'unavailable' })
      },
    )
    return () => {
      active = false
    }
  }, [client, navigate, retryKey])

  return (
    <main className="admin-content">
      <div className="page-header">
        <div>
          <p className="eyebrow">설문 제작</p>
          <h2>설문</h2>
          <p className="page-description">
            이 관리자가 소유한 설문을 만들고 관리합니다.
          </p>
        </div>
        <Link className="text-link" to="/admin/surveys/new">
          설문 만들기
        </Link>
      </div>

      {state.status === 'loading' ? (
        <section aria-live="polite" className="admin-card" role="status">
          설문을 불러오는 중…
        </section>
      ) : null}

      {state.status === 'unavailable' ? (
        <section className="admin-card">
          <h3>설문을 불러올 수 없습니다</h3>
          <p className="error-message" role="alert">
            설문 목록을 불러오지 못했습니다.
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
        </section>
      ) : null}

      {state.status === 'ready' && state.surveys.length === 0 ? (
        <section className="admin-card empty-state">
          <h3>아직 설문이 없습니다</h3>
          <p>첫 설문을 만들고 질문 작성을 시작하세요.</p>
          <Link className="text-link" to="/admin/surveys/new">
            설문 만들기
          </Link>
        </section>
      ) : null}

      {state.status === 'ready' && state.surveys.length > 0 ? (
        <ul className="survey-list">
          {state.surveys.map((survey) => (
            <li className="survey-card" key={survey.id}>
              <div className="survey-card-main">
                <div>
                  <span className={`status-badge status-${survey.status.toLowerCase()}`}>
                    {surveyStatusLabel(survey.status)}
                  </span>
                  <h3>
                    <Link to={`/admin/surveys/${survey.id}`}>{survey.title}</Link>
                  </h3>
                </div>
                <dl className="survey-summary">
                  <div>
                    <dt>응답</dt>
                    <dd>{survey.responseCount}</dd>
                  </div>
                  <div>
                    <dt>수정일</dt>
                    <dd>
                      <time dateTime={survey.updatedAt}>
                        {formatDate(survey.updatedAt)}
                      </time>
                    </dd>
                  </div>
                  <div>
                    <dt>예약 slug</dt>
                    <dd>
                      <code>{survey.slug}</code>
                    </dd>
                  </div>
                </dl>
              </div>
              <div className="card-actions">
                <Link
                  className="secondary-link"
                  to={`/admin/surveys/${survey.id}/responses`}
                >
                  응답 보기
                </Link>
                <Link
                  className="secondary-link"
                  to={`/admin/surveys/${survey.id}`}
                >
                  편집
                </Link>
                <Link
                  className="secondary-link"
                  to={`/admin/surveys/${survey.id}/preview`}
                >
                  미리보기
                </Link>
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </main>
  )
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default SurveyListPage
