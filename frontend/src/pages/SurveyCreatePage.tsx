import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { ApiError, type ApiFieldError } from '../api/apiClient.ts'
import type { SurveyClient } from '../surveys/surveyClient.ts'
import {
  fieldMessage,
  nullableText,
  surveyErrorMessage,
} from '../surveys/surveyUi.ts'

type SurveyCreatePageProps = {
  client: SurveyClient
}

function SurveyCreatePage({ client }: SurveyCreatePageProps) {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [privacyNotice, setPrivacyNotice] = useState('')
  const [slug, setSlug] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSubmitting) {
      return
    }
    if (title.trim().length === 0) {
      setFieldErrors([
        { path: 'title', code: 'REQUIRED', message: 'Title is required.' },
      ])
      setErrorMessage('Review the highlighted fields and try again.')
      return
    }

    setIsSubmitting(true)
    setErrorMessage(null)
    setFieldErrors([])
    try {
      const survey = await client.createSurvey({
        title: title.trim(),
        description: nullableText(description),
        privacyNotice: nullableText(privacyNotice),
        slug: nullableText(slug),
      })
      navigate(`/admin/surveys/${survey.id}`, { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'AUTH_REQUIRED') {
        navigate('/login', { replace: true })
        return
      }
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
      }
      setErrorMessage(surveyErrorMessage(error))
      setIsSubmitting(false)
    }
  }

  const titleError = fieldMessage(fieldErrors, 'title')
  const slugError = fieldMessage(fieldErrors, 'slug')

  return (
    <main className="admin-content narrow-content">
      <div className="page-header">
        <div>
          <p className="eyebrow">Survey Builder</p>
          <h2>Create Survey</h2>
          <p className="page-description">
            Start with metadata. Questions are added in the Builder next.
          </p>
        </div>
      </div>

      <form className="admin-card form-grid" onSubmit={handleSubmit}>
        <label htmlFor="survey-title">Title</label>
        <input
          aria-describedby={titleError === undefined ? undefined : 'survey-title-error'}
          aria-invalid={titleError !== undefined}
          id="survey-title"
          onChange={(event) => setTitle(event.target.value)}
          required
          value={title}
        />
        {titleError === undefined ? null : (
          <p className="field-error" id="survey-title-error">
            {titleError}
          </p>
        )}

        <label htmlFor="survey-description">Description (optional)</label>
        <textarea
          id="survey-description"
          onChange={(event) => setDescription(event.target.value)}
          rows={5}
          value={description}
        />

        <label htmlFor="survey-privacy">Privacy notice (optional)</label>
        <textarea
          id="survey-privacy"
          onChange={(event) => setPrivacyNotice(event.target.value)}
          rows={4}
          value={privacyNotice}
        />

        <label htmlFor="survey-slug">Reserved slug (optional)</label>
        <input
          aria-describedby={slugError === undefined ? 'survey-slug-help' : 'survey-slug-error'}
          aria-invalid={slugError !== undefined}
          id="survey-slug"
          onChange={(event) => setSlug(event.target.value)}
          placeholder="generated-from-title"
          value={slug}
        />
        {slugError === undefined ? (
          <p className="field-help" id="survey-slug-help">
            Leave blank to generate a reserved slug. Public Survey routes are not active.
          </p>
        ) : (
          <p className="field-error" id="survey-slug-error">
            {slugError}
          </p>
        )}

        {errorMessage === null ? null : (
          <p className="error-message" role="alert">
            {errorMessage}
          </p>
        )}

        <div className="form-actions">
          <button disabled={isSubmitting} type="submit">
            {isSubmitting ? 'Creating…' : 'Create Survey'}
          </button>
          <Link className="secondary-link" to="/admin/surveys">
            Cancel
          </Link>
        </div>
      </form>
    </main>
  )
}

export default SurveyCreatePage
