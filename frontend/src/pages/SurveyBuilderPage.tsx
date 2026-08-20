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
  surveyErrorMessage,
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
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([])
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

  async function handleMutationError(error: unknown): Promise<void> {
    if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
      navigate('/login', { replace: true })
      return
    }
    if (error instanceof ApiError && error.code === 'SURVEY_NOT_FOUND') {
      navigate('/admin/surveys', { replace: true })
      return
    }

    if (error instanceof ApiError) {
      setFieldErrors(error.fieldErrors)
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
          if (error.code === 'SURVEY_STRUCTURE_LOCKED') {
            setQuestionEditor(null)
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
      setFieldErrors([
        { path: 'title', code: 'REQUIRED', message: 'Title is required.' },
      ])
      setErrorMessage('Review the highlighted fields and try again.')
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
      setNotice('No metadata changes to save.')
      setErrorMessage(null)
      setFieldErrors([])
      return
    }

    setPendingAction('metadata')
    clearFeedback()
    try {
      const updated = await client.updateSurvey(state.survey.id, patch)
      applyCanonical(updated)
      setNotice('Survey metadata saved.')
    } catch (error) {
      await handleMutationError(error)
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
      setNotice(action === 'open' ? 'Survey opened.' : 'Survey closed.')
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
      setNotice('Editable DRAFT copy created without Responses.')
      navigate(`/admin/surveys/${duplicate.id}`, {
        replace: false,
        state: { notice: 'Editable DRAFT copy created without Responses.' },
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
    if (!window.confirm(`Delete “${state.survey.title}”? This cannot be restored in V1.`)) {
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
    clearFeedback()
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
          ? 'Question added.'
          : 'Question saved.',
      )
    } catch (error) {
      await handleMutationError(error)
    } finally {
      setPendingAction(null)
    }
  }

  async function handleQuestionDelete(question: SurveyQuestion) {
    if (state.status !== 'ready' || pendingAction !== null) {
      return
    }
    if (!window.confirm(`Delete Question “${question.title}”?`)) {
      return
    }
    setPendingAction('question-delete')
    clearFeedback()
    try {
      await client.deleteQuestion(state.survey.id, question.id)
      const refreshed = await client.getSurvey(state.survey.id)
      applyCanonical(refreshed)
      setQuestionEditor(null)
      setNotice('Question deleted.')
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
      setNotice('Question order saved.')
    } catch (error) {
      await handleMutationError(error)
    } finally {
      setPendingAction(null)
    }
  }

  function clearFeedback() {
    setNotice(null)
    setErrorMessage(null)
    setFieldErrors([])
  }

  if (state.status === 'loading') {
    return (
      <main aria-live="polite" className="admin-content admin-card" role="status">
        Loading Survey Builder…
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
        <h2>Builder unavailable</h2>
        <p className="error-message" role="alert">
          We could not load this Survey.
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
  const selectedQuestion =
    questionEditor?.kind === 'edit'
      ? survey.questions.find(
          (question) => question.id === questionEditor.questionId,
        )
      : undefined
  const locked = survey.structureLocked
  const pending = pendingAction !== null
  const titleError = fieldMessage(fieldErrors, 'title')
  const slugError = fieldMessage(fieldErrors, 'slug')

  return (
    <main className="admin-content builder-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Survey Builder</p>
          <h2>{survey.title}</h2>
          <div className="status-line">
            <span className={`status-badge status-${survey.status.toLowerCase()}`}>
              {survey.status}
            </span>
            <span>{survey.responseCount} Responses</span>
            <span>
              Reserved slug: <code>{survey.slug}</code>
            </span>
          </div>
        </div>
        <div className="card-actions">
          <Link
            className="secondary-link"
            to={`/admin/surveys/${survey.id}/preview`}
          >
            Admin Preview
          </Link>
          <Link className="secondary-link" to="/admin/surveys">
            Back to Surveys
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
            <h3 id="lifecycle-title">Lifecycle</h3>
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
                {pendingAction === 'close' ? 'Closing…' : 'Close'}
              </button>
            ) : (
              <button
                disabled={pending}
                onClick={() => void handleLifecycle('open')}
                type="button"
              >
                {pendingAction === 'open' ? 'Opening…' : 'Open'}
              </button>
            )}
            <button
              className="secondary-button"
              disabled={pending}
              onClick={() => void handleDuplicate()}
              type="button"
            >
              {pendingAction === 'duplicate' ? 'Duplicating…' : 'Duplicate'}
            </button>
            {survey.status === 'OPEN' ? null : (
              <button
                className="danger-button"
                disabled={pending}
                onClick={() => void handleDelete()}
                type="button"
              >
                {pendingAction === 'delete' ? 'Deleting…' : 'Delete'}
              </button>
            )}
          </div>
        </div>
      </section>

      <section aria-labelledby="metadata-title" className="admin-card">
        <h3 id="metadata-title">Survey metadata</h3>
        <form className="form-grid" onSubmit={handleMetadataSave}>
          <label htmlFor="builder-title">Title</label>
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

          <label htmlFor="builder-description">Description (optional)</label>
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

          <label htmlFor="builder-privacy">Privacy notice (optional)</label>
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

          <label htmlFor="builder-slug">Reserved slug</label>
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
                ? 'Editable until the first OPEN. This is not a public link.'
                : 'Immutable after the first OPEN. This is not a public link.'}
            </p>
          ) : (
            <p className="field-error" id="builder-slug-error">
              {slugError}
            </p>
          )}

          <div className="form-actions">
            <button disabled={pending} type="submit">
              {pendingAction === 'metadata' ? 'Saving…' : 'Save metadata'}
            </button>
          </div>
        </form>
      </section>

      <section aria-labelledby="questions-title" className="admin-card question-section">
        <div className="section-header">
          <div>
            <h3 id="questions-title">Questions</h3>
            <p>{survey.questions.length} total · ordered from top to bottom</p>
          </div>
          {locked ? null : (
            <button
              disabled={pending || questionEditor !== null}
              onClick={() => {
                clearFeedback()
                setQuestionEditor({ kind: 'create' })
              }}
              type="button"
            >
              Add Question
            </button>
          )}
        </div>

        {locked ? (
          <div className="locked-notice" role="status">
            <h4>Question structure is locked</h4>
            <p>
              Existing Responses lock Question semantics. Lifecycle status did not
              cause this lock. Duplicate this Survey to create an editable DRAFT
              without Responses.
            </p>
            <button
              className="secondary-button"
              disabled={pending}
              onClick={() => void handleDuplicate()}
              type="button"
            >
              Duplicate Survey
            </button>
          </div>
        ) : null}

        {questionEditor?.kind === 'create' ? (
          <QuestionEditor
            apiFieldErrors={fieldErrors}
            onCancel={() => {
              setQuestionEditor(null)
              setFieldErrors([])
            }}
            onSave={(input) => void handleQuestionSave(input)}
            pending={pendingAction === 'question'}
          />
        ) : null}

        {survey.questions.length === 0 ? (
          <div className="empty-state">
            <h4>No Questions yet</h4>
            <p>Add at least one valid Question before opening this Survey.</p>
          </div>
        ) : (
          <ol className="question-list">
            {survey.questions.map((question, index) => (
              <li className="question-card" key={question.id}>
                <div className="question-card-header">
                  <div>
                    <p className="eyebrow">
                      {questionTypeLabel(question)} · Question {index + 1}
                    </p>
                    <h4>{question.title}</h4>
                    {question.required ? <span>Required</span> : <span>Optional</span>}
                  </div>
                  {locked ? null : (
                    <div className="compact-actions">
                      <button
                        aria-label={`Move ${question.title} up`}
                        className="secondary-button"
                        disabled={pending || index === 0}
                        onClick={() => void handleQuestionMove(index, -1)}
                        type="button"
                      >
                        Up
                      </button>
                      <button
                        aria-label={`Move ${question.title} down`}
                        className="secondary-button"
                        disabled={pending || index === survey.questions.length - 1}
                        onClick={() => void handleQuestionMove(index, 1)}
                        type="button"
                      >
                        Down
                      </button>
                      <button
                        className="secondary-button"
                        disabled={pending || questionEditor !== null}
                        onClick={() => {
                          clearFeedback()
                          setQuestionEditor({
                            kind: 'edit',
                            questionId: question.id,
                          })
                        }}
                        type="button"
                      >
                        Edit
                      </button>
                      <button
                        aria-label={`Delete ${question.title}`}
                        className="danger-button"
                        disabled={pending}
                        onClick={() => void handleQuestionDelete(question)}
                        type="button"
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </div>
                <QuestionSummary question={question} />

                {selectedQuestion?.id === question.id ? (
                  <QuestionEditor
                    apiFieldErrors={fieldErrors}
                    key={question.id}
                    onCancel={() => {
                      setQuestionEditor(null)
                      setFieldErrors([])
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
    return 'Add a valid Question, then open this Survey when it is ready.'
  }
  if (survey.status === 'OPEN') {
    return 'This Survey is open. Close it before deleting; Question structure locks only after a Response exists.'
  }
  return 'This Survey is closed and may be reopened. Its original first-open time is preserved.'
}

function questionTypeLabel(question: SurveyQuestion): string {
  return question.type
    .toLowerCase()
    .split('_')
    .map((part) => part[0]?.toUpperCase() + part.slice(1))
    .join(' ')
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
        Scale {question.scaleMin}–{question.scaleMax}
        {question.scaleMinLabel === null ? '' : ` · ${question.scaleMinLabel}`}
        {question.scaleMaxLabel === null ? '' : ` → ${question.scaleMaxLabel}`}
      </p>
    )
  }
  if (question.type === 'NUMBER') {
    return (
      <p className="question-summary">
        Number bounds: {question.numberMin ?? 'none'} to {question.numberMax ?? 'none'}
      </p>
    )
  }
  return question.description === null ? null : (
    <p className="question-summary">{question.description}</p>
  )
}

export default SurveyBuilderPage
