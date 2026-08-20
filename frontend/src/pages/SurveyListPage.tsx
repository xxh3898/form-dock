import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { ApiError } from '../api/apiClient.ts'
import type {
  SurveyClient,
  SurveyListItem,
} from '../surveys/surveyClient.ts'

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
          <p className="eyebrow">Survey Builder</p>
          <h2>Surveys</h2>
          <p className="page-description">
            Create and manage Surveys owned by this Creator.
          </p>
        </div>
        <Link className="text-link" to="/admin/surveys/new">
          Create Survey
        </Link>
      </div>

      {state.status === 'loading' ? (
        <section aria-live="polite" className="admin-card" role="status">
          Loading Surveys…
        </section>
      ) : null}

      {state.status === 'unavailable' ? (
        <section className="admin-card">
          <h3>Surveys unavailable</h3>
          <p className="error-message" role="alert">
            We could not load your Surveys.
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
        </section>
      ) : null}

      {state.status === 'ready' && state.surveys.length === 0 ? (
        <section className="admin-card empty-state">
          <h3>No Surveys yet</h3>
          <p>Create your first Survey to begin building Questions.</p>
          <Link className="text-link" to="/admin/surveys/new">
            Create Survey
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
                    {survey.status}
                  </span>
                  <h3>
                    <Link to={`/admin/surveys/${survey.id}`}>{survey.title}</Link>
                  </h3>
                </div>
                <dl className="survey-summary">
                  <div>
                    <dt>Responses</dt>
                    <dd>{survey.responseCount}</dd>
                  </div>
                  <div>
                    <dt>Updated</dt>
                    <dd>
                      <time dateTime={survey.updatedAt}>
                        {formatDate(survey.updatedAt)}
                      </time>
                    </dd>
                  </div>
                  <div>
                    <dt>Reserved slug</dt>
                    <dd>
                      <code>{survey.slug}</code>
                    </dd>
                  </div>
                </dl>
              </div>
              <div className="card-actions">
                <Link
                  className="secondary-link"
                  to={`/admin/surveys/${survey.id}`}
                >
                  Edit
                </Link>
                <Link
                  className="secondary-link"
                  to={`/admin/surveys/${survey.id}/preview`}
                >
                  Preview
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
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default SurveyListPage
