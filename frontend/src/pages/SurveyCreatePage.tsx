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
        { path: 'title', code: 'REQUIRED', message: '제목을 입력해 주세요.' },
      ])
      setErrorMessage('표시된 항목을 확인한 뒤 다시 시도해 주세요.')
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
          <p className="eyebrow">설문 제작</p>
          <h2>설문 만들기</h2>
          <p className="page-description">
            먼저 설문 정보를 입력하세요. 질문은 다음 화면에서 추가합니다.
          </p>
        </div>
      </div>

      <form className="admin-card form-grid" onSubmit={handleSubmit}>
        <label htmlFor="survey-title">제목</label>
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

        <label htmlFor="survey-description">설명 (선택)</label>
        <textarea
          id="survey-description"
          onChange={(event) => setDescription(event.target.value)}
          rows={5}
          value={description}
        />

        <label htmlFor="survey-privacy">개인정보 안내 (선택)</label>
        <textarea
          id="survey-privacy"
          onChange={(event) => setPrivacyNotice(event.target.value)}
          rows={4}
          value={privacyNotice}
        />

        <label htmlFor="survey-slug">예약 slug (선택)</label>
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
            비워 두면 예약 slug를 자동으로 만듭니다. 공개 설문 경로는 아직 활성화되지 않았습니다.
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
            {isSubmitting ? '만드는 중…' : '설문 만들기'}
          </button>
          <Link className="secondary-link" to="/admin/surveys">
            취소
          </Link>
        </div>
      </form>
    </main>
  )
}

export default SurveyCreatePage
