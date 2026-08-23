import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { ApiError } from '../api/apiClient.ts'
import type {
  ChoiceSummaryQuestion,
  ResponsePage,
  ResponseSummary,
  ResponseSummaryQuestion,
  ResultsClient,
  ScaleSummaryQuestion,
} from '../results/resultsClient.ts'
import {
  formatResultTimestamp,
  parsePositiveRouteId,
  saveCsvDownload,
} from '../results/resultsUi.ts'
import {
  questionTypeLabel,
  surveyStatusLabel,
} from '../surveys/surveyUi.ts'

type SurveyResultsPageProps = {
  client: ResultsClient
}

type ResultsState =
  | { status: 'loading' }
  | { status: 'ready'; summary: ResponseSummary; page: ResponsePage }
  | { status: 'not-found' }
  | { status: 'unavailable' }

function SurveyResultsPage({ client }: SurveyResultsPageProps) {
  const { surveyId } = useParams()
  return (
    <SurveyResultsContent
      client={client}
      key={surveyId ?? 'invalid'}
      routeSurveyId={surveyId}
    />
  )
}

function SurveyResultsContent({
  client,
  routeSurveyId,
}: SurveyResultsPageProps & { routeSurveyId: string | undefined }) {
  const navigate = useNavigate()
  const surveyId = parsePositiveRouteId(routeSurveyId)
  const [state, setState] = useState<ResultsState>(() =>
    surveyId === null ? { status: 'not-found' } : { status: 'loading' },
  )
  const [retryKey, setRetryKey] = useState(0)
  const [isPageLoading, setIsPageLoading] = useState(false)
  const [isDownloading, setIsDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const activeRef = useRef(true)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const pageRequestRef = useRef(0)
  const pagePendingRef = useRef(false)
  const downloadPendingRef = useRef(false)

  useEffect(() => {
    return () => {
      activeRef.current = false
      pageRequestRef.current += 1
    }
  }, [])

  useEffect(() => {
    if (state.status === 'ready') {
      headingRef.current?.focus()
    }
  }, [state.status])

  useEffect(() => {
    if (surveyId === null) {
      return
    }

    let active = true
    pageRequestRef.current += 1
    pagePendingRef.current = false
    Promise.all([
      client.getSummary(surveyId),
      client.listResponses(surveyId, 0, 50),
    ]).then(
      ([summary, page]) => {
        if (active) {
          setState({ status: 'ready', summary, page })
        }
      },
      (error: unknown) => {
        if (active) {
          handleLoadError(error, navigate, setState)
        }
      },
    )

    return () => {
      active = false
    }
  }, [client, navigate, retryKey, surveyId])

  async function loadPage(page: number) {
    if (
      surveyId === null ||
      state.status !== 'ready' ||
      pagePendingRef.current
    ) {
      return
    }

    pagePendingRef.current = true
    const requestId = pageRequestRef.current + 1
    pageRequestRef.current = requestId
    setIsPageLoading(true)
    try {
      const nextPage = await client.listResponses(surveyId, page, 50)
      if (activeRef.current && pageRequestRef.current === requestId) {
        setState((current) =>
          current.status === 'ready'
            ? { ...current, page: nextPage }
            : current,
        )
      }
    } catch (error) {
      if (activeRef.current && pageRequestRef.current === requestId) {
        handleLoadError(error, navigate, setState)
      }
    } finally {
      if (activeRef.current && pageRequestRef.current === requestId) {
        pagePendingRef.current = false
        setIsPageLoading(false)
      }
    }
  }

  async function downloadCsv() {
    if (surveyId === null || downloadPendingRef.current) {
      return
    }

    downloadPendingRef.current = true
    setIsDownloading(true)
    setDownloadError(null)
    try {
      const download = await client.downloadCsv(surveyId)
      if (activeRef.current) {
        saveCsvDownload(download)
      }
    } catch (error) {
      if (!activeRef.current) {
        return
      }
      if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
        navigate('/login', { replace: true })
      } else if (error instanceof ApiError && error.code === 'SURVEY_NOT_FOUND') {
        setState({ status: 'not-found' })
      } else {
        setDownloadError(
          'CSV 파일을 내려받지 못했습니다. 잠시 후 다시 시도해 주세요.',
        )
      }
    } finally {
      downloadPendingRef.current = false
      if (activeRef.current) {
        setIsDownloading(false)
      }
    }
  }

  if (state.status === 'loading') {
    return (
      <main className="admin-content results-shell">
        <section aria-live="polite" className="admin-card" role="status">
          응답 결과를 불러오는 중…
        </section>
      </main>
    )
  }

  if (state.status === 'not-found') {
    return (
      <ResultStateCard
        description="이 설문은 사용할 수 없거나 삭제됐습니다."
        title="설문을 찾을 수 없습니다"
      />
    )
  }

  if (state.status === 'unavailable') {
    return (
      <main className="admin-content results-shell">
        <section className="admin-card">
          <h2>응답 결과를 불러올 수 없습니다</h2>
          <p className="error-message" role="alert">
            FormDock을 일시적으로 사용할 수 없습니다.
          </p>
          <div className="form-actions">
            <button
              onClick={() => {
                setState({ status: 'loading' })
                setDownloadError(null)
                setRetryKey((value) => value + 1)
              }}
              type="button"
            >
              다시 시도
            </button>
            <Link className="secondary-link" to="/admin/surveys">
              설문 목록
            </Link>
          </div>
        </section>
      </main>
    )
  }

  const { summary, page } = state
  const outOfRange = page.totalElements > 0 && page.items.length === 0

  return (
    <main className="admin-content results-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">설문 결과</p>
          <h2 ref={headingRef} tabIndex={-1}>
            응답 결과
          </h2>
          <p className="page-description">
            설문 #{summary.surveyId}의 요약과 개별 응답을 확인합니다.
          </p>
        </div>
        <div className="card-actions">
          <Link className="secondary-link" to={`/admin/surveys/${surveyId}`}>
            설문 관리
          </Link>
          <Link className="secondary-link" to="/admin/surveys">
            설문 목록
          </Link>
        </div>
      </div>

      <section aria-labelledby="result-overview-title" className="admin-card">
        <div className="section-header">
          <div>
            <h3 id="result-overview-title">요약</h3>
            <p>현재 설문 상태와 누적 응답 현황입니다.</p>
          </div>
          <span className={`status-badge status-${summary.status.toLowerCase()}`}>
            {surveyStatusLabel(summary.status)}
          </span>
        </div>
        <dl className="result-overview">
          <div>
            <dt>전체 응답</dt>
            <dd>{summary.totalResponses}</dd>
          </div>
          <div>
            <dt>마지막 제출</dt>
            <dd>
              {summary.lastSubmittedAt === null ? (
                '없음'
              ) : (
                <time dateTime={summary.lastSubmittedAt}>
                  {formatResultTimestamp(summary.lastSubmittedAt)}
                </time>
              )}
            </dd>
          </div>
          <div>
            <dt>질문 수</dt>
            <dd>{summary.questionCount}</dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="question-summary-title" className="question-section">
        <div className="section-header">
          <div>
            <h3 id="question-summary-title">질문별 요약</h3>
            <p>현재 질문 순서와 서버 집계 결과를 표시합니다.</p>
          </div>
        </div>
        {summary.questions.length === 0 ? (
          <div className="admin-card empty-state">요약할 질문이 없습니다.</div>
        ) : (
          <ol className="result-summary-list">
            {summary.questions.map((question) => (
              <li className="admin-card" key={question.questionId}>
                <QuestionSummary question={question} />
              </li>
            ))}
          </ol>
        )}
      </section>

      <section aria-labelledby="response-list-title" className="admin-card">
        <div className="section-header">
          <div>
            <h3 id="response-list-title">개별 응답</h3>
            <p>최신 제출 순으로 표시합니다.</p>
          </div>
          <button
            disabled={isDownloading}
            onClick={downloadCsv}
            type="button"
          >
            {isDownloading ? 'CSV 준비 중…' : 'CSV 다운로드'}
          </button>
        </div>

        {downloadError === null ? null : (
          <p className="error-message" role="alert">
            {downloadError}
          </p>
        )}

        {page.totalElements === 0 ? (
          <div className="empty-state result-empty-state">
            <h4>아직 제출된 응답이 없습니다</h4>
            <p>새 응답이 제출되면 이 목록에 표시됩니다.</p>
          </div>
        ) : null}

        {outOfRange ? (
          <div className="empty-state result-empty-state" role="status">
            <h4>이 페이지에는 응답이 없습니다</h4>
            <p>이전 페이지로 돌아가 응답을 계속 확인해 주세요.</p>
          </div>
        ) : null}

        {page.items.length > 0 ? (
          <div
            aria-label="개별 응답 표"
            className="result-table-scroll"
            role="region"
            tabIndex={0}
          >
            <table className="result-table">
              <thead>
                <tr>
                  <th scope="col">응답</th>
                  <th scope="col">제출 시각</th>
                  <th scope="col">상세</th>
                </tr>
              </thead>
              <tbody>
                {page.items.map((item) => (
                  <tr key={item.responseId}>
                    <th scope="row">#{item.responseId}</th>
                    <td>
                      <time dateTime={item.submittedAt}>
                        {formatResultTimestamp(item.submittedAt)}
                      </time>
                    </td>
                    <td>
                      <Link
                        className="secondary-link"
                        to={`/admin/surveys/${surveyId}/responses/${item.responseId}`}
                      >
                        응답 보기
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {page.totalElements > 0 ? (
          <nav aria-label="응답 페이지" className="result-pagination">
            <button
              className="secondary-button"
              disabled={isPageLoading || page.page === 0}
              onClick={() => loadPage(page.page - 1)}
              type="button"
            >
              이전
            </button>
            <span aria-live="polite" role="status">
              {page.page + 1} / {Math.max(page.totalPages, 1)} 페이지
            </span>
            <button
              className="secondary-button"
              disabled={
                isPageLoading ||
                page.totalPages === 0 ||
                page.page + 1 >= page.totalPages
              }
              onClick={() => loadPage(page.page + 1)}
              type="button"
            >
              다음
            </button>
          </nav>
        ) : null}
      </section>
    </main>
  )
}

function QuestionSummary({ question }: { question: ResponseSummaryQuestion }) {
  return (
    <article className="result-question-summary">
      <div className="question-card-header">
        <div>
          <p className="eyebrow">{questionTypeLabel(question.type)}</p>
          <h4>{question.title}</h4>
        </div>
        <p>응답 {question.answeredCount}</p>
      </div>
      {question.type === 'SINGLE_CHOICE' ||
      question.type === 'MULTIPLE_CHOICE' ? (
        <ChoiceSummary question={question} />
      ) : null}
      {question.type === 'SCALE' ? (
        <ScaleSummary question={question} />
      ) : null}
      {question.type === 'SHORT_TEXT' ||
      question.type === 'LONG_TEXT' ||
      question.type === 'NUMBER' ? (
        <p className="question-summary">응답 수만 집계합니다.</p>
      ) : null}
    </article>
  )
}

function ChoiceSummary({ question }: { question: ChoiceSummaryQuestion }) {
  return (
    <div
      aria-label={`${question.title} 선택지 요약`}
      className="result-table-scroll"
      role="region"
      tabIndex={0}
    >
      <table className="result-table compact-table">
        <thead>
          <tr>
            <th scope="col">선택지</th>
            <th scope="col">응답 수</th>
            <th scope="col">비율</th>
          </tr>
        </thead>
        <tbody>
          {question.options.map((option) => (
            <tr key={option.optionId}>
              <th scope="row">{option.label}</th>
              <td>{option.count}</td>
              <td>{option.percentage}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ScaleSummary({ question }: { question: ScaleSummaryQuestion }) {
  return (
    <>
      <p className="question-summary">
        평균: {question.average === null ? '없음' : question.average}
      </p>
      <div
        aria-label={`${question.title} 척도 분포`}
        className="result-table-scroll"
        role="region"
        tabIndex={0}
      >
        <table className="result-table compact-table">
          <thead>
            <tr>
              <th scope="col">값</th>
              <th scope="col">응답 수</th>
              <th scope="col">비율</th>
            </tr>
          </thead>
          <tbody>
            {question.distribution.map((bucket) => (
              <tr key={bucket.value}>
                <th scope="row">{bucket.value}</th>
                <td>{bucket.count}</td>
                <td>{bucket.percentage}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

function ResultStateCard({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <main className="admin-content results-shell">
      <section className="admin-card">
        <h2>{title}</h2>
        <p className="error-message" role="alert">
          {description}
        </p>
        <Link className="secondary-link" to="/admin/surveys">
          설문 목록
        </Link>
      </section>
    </main>
  )
}

function handleLoadError(
  error: unknown,
  navigate: ReturnType<typeof useNavigate>,
  setState: (state: ResultsState) => void,
): void {
  if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
    navigate('/login', { replace: true })
    return
  }
  if (error instanceof ApiError && error.code === 'SURVEY_NOT_FOUND') {
    setState({ status: 'not-found' })
    return
  }
  setState({ status: 'unavailable' })
}

export default SurveyResultsPage
