import { type FormEvent, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'

import { ApiError, type ApiFieldError } from '../api/apiClient.ts'
import QuestionEditor from '../components/QuestionEditor.tsx'
import type {
  QuestionWriteInput,
  SurveyClient,
  SurveyDetail,
  SurveyPatchInput,
  SurveyQuestion,
} from '../surveys/surveyClient.ts'
import {
  fieldMessage,
  nullableText,
  parseSurveyId,
  questionTypeLabel,
  surveyErrorMessage,
  surveyStatusLabel,
} from '../surveys/surveyUi.ts'

type SurveyBuilderPageProps = {
  client: SurveyClient
}

type BuilderState =
  | { status: 'loading' }
  | { status: 'ready'; survey: SurveyDetail }
  | { status: 'not-found' }
  | { status: 'unavailable' }

type MetadataState = {
  title: string
  description: string
  privacyNotice: string
  slug: string
}

type QuestionEditorState =
  | { kind: 'create' }
  | { kind: 'edit'; questionId: number }
  | null

function SurveyBuilderPage({ client }: SurveyBuilderPageProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const surveyId = parseSurveyId(useParams().surveyId)
  const [state, setState] = useState<BuilderState>(() =>
    surveyId === null ? { status: 'not-found' } : { status: 'loading' },
  )
  const [metadata, setMetadata] = useState<MetadataState>(emptyMetadata)
  const [retryKey, setRetryKey] = useState(0)
  const [pendingAction, setPendingAction] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(() =>
    locationNotice(location.state),
  )
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [metadataFieldErrors, setMetadataFieldErrors] =
    useState<ApiFieldError[]>([])
  const [questionFieldErrors, setQuestionFieldErrors] =
    useState<ApiFieldError[]>([])
  const [questionEditor, setQuestionEditor] =
    useState<QuestionEditorState>(null)

  useEffect(() => {
    if (surveyId === null) {
      return
    }
    let active = true
    client.getSurvey(surveyId).then(
      (survey) => {
        if (active) {
          setState({ status: 'ready', survey })
          setMetadata(metadataFrom(survey))
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

  function applyCanonical(survey: SurveyDetail): void {
    setState({ status: 'ready', survey })
    setMetadata(metadataFrom(survey))
  }

  async function handleMutationError(
    error: unknown,
    fieldErrorScope: 'metadata' | 'question' | 'none' = 'none',
  ): Promise<void> {
    if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
      navigate('/login', { replace: true })
      return
    }
    if (error instanceof ApiError && error.code === 'SURVEY_NOT_FOUND') {
      navigate('/admin/surveys', { replace: true })
      return
    }

    if (error instanceof ApiError) {
      if (fieldErrorScope === 'metadata') {
        setMetadataFieldErrors(error.fieldErrors)
      } else if (fieldErrorScope === 'question') {
        setQuestionFieldErrors(error.fieldErrors)
      }
      if (
        surveyId !== null &&
        [
          'SURVEY_STRUCTURE_LOCKED',
          'SURVEY_STATE_CONFLICT',
          'SURVEY_DELETE_REQUIRES_CLOSED',
          'SURVEY_SLUG_IMMUTABLE',
          'QUESTION_NOT_FOUND',
        ].includes(error.code)
      ) {
        try {
          const refreshed = await client.getSurvey(surveyId)
          applyCanonical(refreshed)
          if (fieldErrorScope === 'metadata') {
            setMetadataFieldErrors([])
          }
          if (
            error.code === 'SURVEY_STRUCTURE_LOCKED' ||
            error.code === 'QUESTION_NOT_FOUND'
          ) {
            setQuestionEditor(null)
            setQuestionFieldErrors([])
          }
        } catch (refreshError) {
          if (
            refreshError instanceof ApiError &&
            refreshError.code === 'AUTH_REQUIRED'
          ) {
            navigate('/login', { replace: true })
            return
          }
          if (
            refreshError instanceof ApiError &&
            refreshError.code === 'SURVEY_NOT_FOUND'
          ) {
            navigate('/admin/surveys', { replace: true })
            return
          }
        }
      }
    }
    setErrorMessage(surveyErrorMessage(error))
  }

  async function handleMetadataSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }

    const title = metadata.title.trim()
    if (title.length === 0) {
      setMetadataFieldErrors([
        { path: 'title', code: 'REQUIRED', message: '제목을 입력해 주세요.' },
      ])
      setErrorMessage('표시된 항목을 확인한 뒤 다시 시도해 주세요.')
      return
    }

    const patch: SurveyPatchInput = {}
    if (title !== state.survey.title) {
      patch.title = title
    }
    const description = nullableText(metadata.description)
    if (description !== state.survey.description) {
      patch.description = description
    }
    const privacyNotice = nullableText(metadata.privacyNotice)
    if (privacyNotice !== state.survey.privacyNotice) {
      patch.privacyNotice = privacyNotice
    }
    if (canEditSlug(state.survey)) {
      const slug = metadata.slug.trim()
      if (slug !== state.survey.slug) {
        patch.slug = slug
      }
    }

    if (Object.keys(patch).length === 0) {
      setNotice('저장할 설문 정보 변경이 없습니다.')
      setErrorMessage(null)
      setMetadataFieldErrors([])
      return
    }

    setPendingAction('metadata')
    clearFeedback('metadata')
    try {
      const updated = await client.updateSurvey(state.survey.id, patch)
      applyCanonical(updated)
      setNotice('설문 정보를 저장했습니다.')
    } catch (error) {
      await handleMutationError(error, 'metadata')
    } finally {
      setPendingAction(null)
    }
  }

  async function handleLifecycle(action: 'open' | 'close') {
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }
    setPendingAction(action)
    clearFeedback()
    try {
      const updated =
        action === 'open'
          ? await client.openSurvey(state.survey.id)
          : await client.closeSurvey(state.survey.id)
      applyCanonical(updated)
      setNotice(action === 'open' ? '설문을 공개했습니다.' : '설문을 마감했습니다.')
    } catch (error) {
      await handleMutationError(error)
    } finally {
      setPendingAction(null)
    }
  }

  async function handleDuplicate() {
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }
    setPendingAction('duplicate')
    clearFeedback()
    try {
      const duplicate = await client.duplicateSurvey(state.survey.id)
      setPendingAction(null)
      applyCanonical(duplicate)
      setNotice('응답 없이 편집 가능한 초안 복사본을 만들었습니다.')
      navigate(`/admin/surveys/${duplicate.id}`, {
        replace: false,
        state: { notice: '응답 없이 편집 가능한 초안 복사본을 만들었습니다.' },
      })
    } catch (error) {
      await handleMutationError(error)
      setPendingAction(null)
    }
  }

  async function handleDelete() {
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }
    if (!window.confirm(`“${state.survey.title}” 설문을 삭제할까요? V1에서는 복원할 수 없습니다.`)) {
      return
    }
    setPendingAction('delete')
    clearFeedback()
    try {
      await client.deleteSurvey(state.survey.id)
      navigate('/admin/surveys', { replace: true })
    } catch (error) {
      await handleMutationError(error)
      setPendingAction(null)
    }
  }

  async function handleQuestionSave(input: QuestionWriteInput) {
    if (
      state.status !== 'ready' ||
      pendingAction !== null ||
      questionEditor === null
    ) {
      return
    }
    setPendingAction('question')
    clearFeedback('question')
    try {
      const updated =
        questionEditor.kind === 'create'
          ? await client.createQuestion(state.survey.id, input)
          : await client.updateQuestion(
              state.survey.id,
              questionEditor.questionId,
              input,
            )
      applyCanonical(updated)
      setQuestionEditor(null)
      setNotice(
        questionEditor.kind === 'create'
          ? '질문을 추가했습니다.'
          : '질문을 저장했습니다.',
      )
    } catch (error) {
      await handleMutationError(error, 'question')
    } finally {
      setPendingAction(null)
    }
  }

  async function handleQuestionDelete(question: SurveyQuestion) {
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }
    if (!window.confirm(`“${question.title}” 질문을 삭제할까요?`)) {
      return
    }
    setPendingAction('question-delete')
    clearFeedback()
    try {
      await client.deleteQuestion(state.survey.id, question.id)
      const refreshed = await client.getSurvey(state.survey.id)
      applyCanonical(refreshed)
      setQuestionEditor(null)
      setQuestionFieldErrors([])
      setNotice('질문을 삭제했습니다.')
    } catch (error) {
      await handleMutationError(error)
    } finally {
      setPendingAction(null)
    }
  }

  async function handleQuestionMove(index: number, direction: -1 | 1) {
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }
    const destination = index + direction
    if (destination < 0 || destination >= state.survey.questions.length) {
      return
    }
    const orderedIds = state.survey.questions.map((question) => question.id)
    const current = orderedIds[index]
    const target = orderedIds[destination]
    if (current === undefined || target === undefined) {
      return
    }
    orderedIds[index] = target
    orderedIds[destination] = current

    setPendingAction('question-reorder')
    clearFeedback()
    try {
      const updated = await client.reorderQuestions(
        state.survey.id,
        orderedIds,
      )
      applyCanonical(updated)
      setNotice('질문 순서를 저장했습니다.')
    } catch (error) {
      await handleMutationError(error)
    } finally {
      setPendingAction(null)
    }
  }

  function clearFeedback(
    fieldErrorScope: 'metadata' | 'question' | 'none' = 'none',
  ) {
    setNotice(null)
    setErrorMessage(null)
    if (fieldErrorScope === 'metadata') {
      setMetadataFieldErrors([])
    } else if (fieldErrorScope === 'question') {
      setQuestionFieldErrors([])
    }
  }

  if (state.status === 'loading') {
    return (
      <main aria-live="polite" className="admin-content admin-card" role="status">
        설문 작성 화면을 불러오는 중…
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
        <h2>설문 작성 화면을 불러올 수 없습니다</h2>
        <p className="error-message" role="alert">
          이 설문을 불러오지 못했습니다.
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
  const selectedQuestion =
    questionEditor?.kind === 'edit'
      ? survey.questions.find(
          (question) => question.id === questionEditor.questionId,
        )
      : undefined
  const locked = survey.structureLocked
  const pending = pendingAction !== null
  const titleError = fieldMessage(metadataFieldErrors, 'title')
  const slugError = fieldMessage(metadataFieldErrors, 'slug')

  return (
    <main className="admin-content builder-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">설문 작성</p>
          <h2>{survey.title}</h2>
          <div className="status-line">
            <span className={`status-badge status-${survey.status.toLowerCase()}`}>
              {surveyStatusLabel(survey.status)}
            </span>
            <span>응답 {survey.responseCount}개</span>
            <span>
              예약 slug: <code>{survey.slug}</code>
            </span>
          </div>
        </div>
        <div className="card-actions">
          <Link
            className="secondary-link"
            to={`/admin/surveys/${survey.id}/preview`}
          >
            관리자 미리보기
          </Link>
          <Link className="secondary-link" to="/admin/surveys">
            설문 목록으로
          </Link>
        </div>
      </div>

      {notice === null ? null : (
        <p aria-live="polite" className="success-message" role="status">
          {notice}
        </p>
      )}
      {errorMessage === null ? null : (
        <p className="error-message" role="alert">
          {errorMessage}
        </p>
      )}

      <section aria-labelledby="lifecycle-title" className="admin-card">
        <div className="section-header">
          <div>
            <h3 id="lifecycle-title">공개 상태</h3>
            <p>
              {lifecycleDescription(survey)}
            </p>
          </div>
          <div className="card-actions">
            {survey.status === 'OPEN' ? (
              <button
                disabled={pending}
                onClick={() => void handleLifecycle('close')}
                type="button"
              >
                {pendingAction === 'close' ? '마감 중…' : '마감'}
              </button>
            ) : (
              <button
                disabled={pending}
                onClick={() => void handleLifecycle('open')}
                type="button"
              >
                {pendingAction === 'open' ? '공개 중…' : '공개'}
              </button>
            )}
            <button
              className="secondary-button"
              disabled={pending}
              onClick={() => void handleDuplicate()}
              type="button"
            >
              {pendingAction === 'duplicate' ? '복제 중…' : '복제'}
            </button>
            {survey.status === 'OPEN' ? null : (
              <button
                className="danger-button"
                disabled={pending}
                onClick={() => void handleDelete()}
                type="button"
              >
                {pendingAction === 'delete' ? '삭제 중…' : '삭제'}
              </button>
            )}
          </div>
        </div>
      </section>

      <section aria-labelledby="metadata-title" className="admin-card">
        <h3 id="metadata-title">설문 정보</h3>
        <form className="form-grid" onSubmit={handleMetadataSave}>
          <label htmlFor="builder-title">제목</label>
          <input
            aria-describedby={titleError === undefined ? undefined : 'builder-title-error'}
            aria-invalid={titleError !== undefined}
            id="builder-title"
            onChange={(event) =>
              setMetadata((current) => ({
                ...current,
                title: event.target.value,
              }))
            }
            required
            value={metadata.title}
          />
          {titleError === undefined ? null : (
            <p className="field-error" id="builder-title-error">
              {titleError}
            </p>
          )}

          <label htmlFor="builder-description">설명 (선택)</label>
          <textarea
            id="builder-description"
            onChange={(event) =>
              setMetadata((current) => ({
                ...current,
                description: event.target.value,
              }))
            }
            rows={4}
            value={metadata.description}
          />

          <label htmlFor="builder-privacy">개인정보 안내 (선택)</label>
          <textarea
            id="builder-privacy"
            onChange={(event) =>
              setMetadata((current) => ({
                ...current,
                privacyNotice: event.target.value,
              }))
            }
            rows={3}
            value={metadata.privacyNotice}
          />

          <label htmlFor="builder-slug">예약 slug</label>
          <input
            aria-describedby={
              slugError === undefined ? 'builder-slug-help' : 'builder-slug-error'
            }
            aria-invalid={slugError !== undefined}
            disabled={!canEditSlug(survey)}
            id="builder-slug"
            onChange={(event) =>
              setMetadata((current) => ({
                ...current,
                slug: event.target.value,
              }))
            }
            value={metadata.slug}
          />
          {slugError === undefined ? (
            <p className="field-help" id="builder-slug-help">
              {canEditSlug(survey)
                ? '처음 공개하기 전까지 편집할 수 있습니다. 공개 링크는 아닙니다.'
                : '처음 공개한 뒤에는 변경할 수 없습니다. 공개 링크는 아닙니다.'}
            </p>
          ) : (
            <p className="field-error" id="builder-slug-error">
              {slugError}
            </p>
          )}

          <div className="form-actions">
            <button disabled={pending} type="submit">
              {pendingAction === 'metadata' ? '저장 중…' : '설문 정보 저장'}
            </button>
          </div>
        </form>
      </section>

      <section aria-labelledby="questions-title" className="admin-card question-section">
        <div className="section-header">
          <div>
            <h3 id="questions-title">질문</h3>
            <p>총 {survey.questions.length}개 · 위에서 아래 순서</p>
          </div>
          {locked ? null : (
            <button
              disabled={pending || questionEditor !== null}
              onClick={() => {
                clearFeedback('question')
                setQuestionEditor({ kind: 'create' })
              }}
              type="button"
            >
              질문 추가
            </button>
          )}
        </div>

        {locked ? (
          <div className="locked-notice" role="status">
            <h4>질문 구조가 잠겼습니다</h4>
            <p>
              기존 응답이 질문 의미를 잠갔습니다. 공개 상태 때문에 잠긴 것은
              아닙니다. 응답 없는 편집 가능한 초안이 필요하면 이 설문을
              복제하세요.
            </p>
            <button
              className="secondary-button"
              disabled={pending}
              onClick={() => void handleDuplicate()}
              type="button"
            >
              설문 복제
            </button>
          </div>
        ) : null}

        {questionEditor?.kind === 'create' ? (
          <QuestionEditor
            apiFieldErrors={questionFieldErrors}
            onCancel={() => {
              setQuestionEditor(null)
              setQuestionFieldErrors([])
            }}
            onSave={(input) => void handleQuestionSave(input)}
            pending={pendingAction === 'question'}
          />
        ) : null}

        {survey.questions.length === 0 ? (
          <div className="empty-state">
            <h4>아직 질문이 없습니다</h4>
            <p>설문을 공개하기 전에 유효한 질문을 하나 이상 추가하세요.</p>
          </div>
        ) : (
          <ol className="question-list">
            {survey.questions.map((question, index) => (
              <li className="question-card" key={question.id}>
                <div className="question-card-header">
                  <div>
                    <p className="eyebrow">
                      {questionTypeLabel(question.type)} · 질문 {index + 1}
                    </p>
                    <h4>{question.title}</h4>
                    {question.required ? <span>필수</span> : <span>선택</span>}
                  </div>
                  {locked ? null : (
                    <div className="compact-actions">
                      <button
                        aria-label={`${question.title} 위로 이동`}
                        className="secondary-button"
                        disabled={pending || index === 0}
                        onClick={() => void handleQuestionMove(index, -1)}
                        type="button"
                      >
                        위로
                      </button>
                      <button
                        aria-label={`${question.title} 아래로 이동`}
                        className="secondary-button"
                        disabled={pending || index === survey.questions.length - 1}
                        onClick={() => void handleQuestionMove(index, 1)}
                        type="button"
                      >
                        아래로
                      </button>
                      <button
                        className="secondary-button"
                        disabled={pending || questionEditor !== null}
                        onClick={() => {
                          clearFeedback('question')
                          setQuestionEditor({
                            kind: 'edit',
                            questionId: question.id,
                          })
                        }}
                        type="button"
                      >
                        편집
                      </button>
                      <button
                        aria-label={`${question.title} 삭제`}
                        className="danger-button"
                        disabled={pending}
                        onClick={() => void handleQuestionDelete(question)}
                        type="button"
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </div>
                <QuestionSummary question={question} />

                {selectedQuestion?.id === question.id ? (
                  <QuestionEditor
                    apiFieldErrors={questionFieldErrors}
                    key={question.id}
                    onCancel={() => {
                      setQuestionEditor(null)
                      setQuestionFieldErrors([])
                    }}
                    onSave={(input) => void handleQuestionSave(input)}
                    pending={pendingAction === 'question'}
                    question={question}
                  />
                ) : null}
              </li>
            ))}
          </ol>
        )}
      </section>
    </main>
  )
}

function metadataFrom(survey: SurveyDetail): MetadataState {
  return {
    title: survey.title,
    description: survey.description ?? '',
    privacyNotice: survey.privacyNotice ?? '',
    slug: survey.slug,
  }
}

function emptyMetadata(): MetadataState {
  return { title: '', description: '', privacyNotice: '', slug: '' }
}

function locationNotice(value: unknown): string | null {
  if (
    typeof value === 'object' &&
    value !== null &&
    'notice' in value &&
    typeof value.notice === 'string'
  ) {
    return value.notice
  }
  return null
}

function canEditSlug(survey: SurveyDetail): boolean {
  return survey.status === 'DRAFT' && survey.openedAt === null
}

function lifecycleDescription(survey: SurveyDetail): string {
  if (survey.status === 'DRAFT') {
    return '유효한 질문을 추가한 뒤 준비되면 설문을 공개하세요.'
  }
  if (survey.status === 'OPEN') {
    return '이 설문은 공개 중입니다. 삭제하려면 먼저 마감하세요. 질문 구조는 응답이 생긴 뒤에만 잠깁니다.'
  }
  return '이 설문은 마감됐으며 다시 공개할 수 있습니다. 최초 공개 시각은 유지됩니다.'
}

function QuestionSummary({ question }: { question: SurveyQuestion }) {
  if (
    question.type === 'SINGLE_CHOICE' ||
    question.type === 'MULTIPLE_CHOICE'
  ) {
    return (
      <ol className="option-summary">
        {question.options.map((option) => (
          <li key={option.id}>{option.label}</li>
        ))}
      </ol>
    )
  }
  if (question.type === 'SCALE') {
    return (
      <p className="question-summary">
        척도 {question.scaleMin}–{question.scaleMax}
        {question.scaleMinLabel === null ? '' : ` · ${question.scaleMinLabel}`}
        {question.scaleMaxLabel === null ? '' : ` → ${question.scaleMaxLabel}`}
      </p>
    )
  }
  if (question.type === 'NUMBER') {
    return (
      <p className="question-summary">
        숫자 범위: {question.numberMin ?? '없음'}부터 {question.numberMax ?? '없음'}
      </p>
    )
  }
  return question.description === null ? null : (
    <p className="question-summary">{question.description}</p>
  )
}

export default SurveyBuilderPage
