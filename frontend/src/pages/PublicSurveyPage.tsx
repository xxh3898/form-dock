import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'

import {
  buildPublicResponseSubmission,
  createPublicAnswerState,
  firstInvalidPublicQuestion,
  type PublicAnswerDraft,
  type PublicAnswerState,
  type PublicQuestionFeedback,
  publicResponseFieldFeedback,
  validatePublicAnswer,
} from '../public/publicResponseForm.ts'
import {
  PublicApiError,
  publicSurveyClient,
  type PublicSurvey,
  type PublicSurveyClient,
  type PublicSurveyQuestion,
} from '../public/publicSurveyClient.ts'

type PublicSurveyPageProps = {
  client?: PublicSurveyClient
  submissionIdFactory?: () => string
}

type LoadState =
  | { status: 'loading' }
  | { status: 'ready'; survey: PublicSurvey }
  | { status: 'unavailable' }
  | { status: 'error' }

type TerminalState = 'completed' | 'unavailable' | 'not-open' | 'conflict'

function PublicSurveyPage({
  client = publicSurveyClient,
  submissionIdFactory = createSubmissionId,
}: PublicSurveyPageProps) {
  const slug = useParams().slug
  const [loadState, setLoadState] = useState<LoadState>({ status: 'loading' })
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [answers, setAnswers] = useState<PublicAnswerState>({})
  const [clientSubmissionId, setClientSubmissionId] = useState<string | null>(
    null,
  )
  const [stepIndex, setStepIndex] = useState(-1)
  const [questionFeedback, setQuestionFeedback] =
    useState<PublicQuestionFeedback | null>(null)
  const [submissionMessage, setSubmissionMessage] = useState<string | null>(
    null,
  )
  const [retryableSubmission, setRetryableSubmission] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [terminal, setTerminal] = useState<TerminalState | null>(null)
  const [focusRequest, setFocusRequest] = useState(0)
  const questionHeadingRef = useRef<HTMLHeadingElement>(null)
  const submittingRef = useRef(false)

  useEffect(() => {
    if (slug === undefined) {
      return
    }
    let active = true
    client.getSurvey(slug).then(
      (survey) => {
        if (!active) {
          return
        }
        setAnswers(createPublicAnswerState(survey.questions))
        setClientSubmissionId(submissionIdFactory())
        setStepIndex(-1)
        setQuestionFeedback(null)
        setSubmissionMessage(null)
        setRetryableSubmission(false)
        setTerminal(null)
        setLoadState({ status: 'ready', survey })
      },
      (error: unknown) => {
        if (!active) {
          return
        }
        setLoadState(
          error instanceof PublicApiError && error.code === 'SURVEY_NOT_FOUND'
            ? { status: 'unavailable' }
            : { status: 'error' },
        )
      },
    )
    return () => {
      active = false
    }
  }, [client, loadAttempt, slug, submissionIdFactory])

  useEffect(() => {
    if (stepIndex >= 0 && terminal === null) {
      questionHeadingRef.current?.focus()
    }
  }, [focusRequest, stepIndex, terminal])

  if (loadState.status === 'loading') {
    return (
      <PublicShell>
        <p aria-live="polite" className="status-message" role="status">
          설문을 불러오는 중…
        </p>
      </PublicShell>
    )
  }

  if (loadState.status === 'unavailable' || terminal === 'unavailable') {
    return (
      <PublicState
        description="이 설문은 현재 참여할 수 없습니다."
        title="설문을 사용할 수 없습니다"
      />
    )
  }

  if (loadState.status === 'error') {
    return (
      <PublicState
        description="네트워크 또는 서버 상태를 확인한 뒤 다시 시도해 주세요."
        title="설문을 불러올 수 없습니다"
      >
        <button
          onClick={() => {
            setLoadState({ status: 'loading' })
            setLoadAttempt((attempt) => attempt + 1)
          }}
          type="button"
        >
          다시 시도
        </button>
      </PublicState>
    )
  }

  const { survey } = loadState
  if (terminal === 'completed') {
    return (
      <PublicState
        description="소중한 응답이 안전하게 접수되었습니다."
        title="응답이 제출되었습니다"
      />
    )
  }
  if (terminal === 'not-open') {
    return (
      <PublicState
        description="작성한 내용은 이 화면에만 남아 있지만 더 이상 제출할 수 없습니다."
        title="설문 응답이 마감되었습니다"
      />
    )
  }
  if (terminal === 'conflict') {
    return (
      <PublicState
        description="같은 제출 식별자로 다른 응답이 확인됐습니다. 반복 제출하지 말고 설문 관리자에게 문의해 주세요."
        title="응답을 안전하게 확인할 수 없습니다"
      />
    )
  }

  if (stepIndex < 0) {
    return (
      <PublicShell>
        <header className="public-intro">
          <p className="product-name">FormDock</p>
          <h1>{survey.title}</h1>
          {survey.description === null ? null : <p>{survey.description}</p>}
        </header>
        {survey.privacyNotice === null ? null : (
          <section className="public-privacy">
            <h2>개인정보 안내</h2>
            <p>{survey.privacyNotice}</p>
          </section>
        )}
        <button
          onClick={() => {
            setStepIndex(0)
            setFocusRequest((request) => request + 1)
          }}
          type="button"
        >
          설문 시작
        </button>
      </PublicShell>
    )
  }

  const question = survey.questions[stepIndex]
  const questionError =
    questionFeedback?.questionId === question.id
      ? questionFeedback.message
      : null

  const moveToQuestion = (questionId: number, message: string) => {
    const targetIndex = survey.questions.findIndex(
      (candidate) => candidate.id === questionId,
    )
    if (targetIndex >= 0) {
      setStepIndex(targetIndex)
    }
    setQuestionFeedback({ questionId, message })
    setFocusRequest((request) => request + 1)
  }

  const moveNext = () => {
    const message = validatePublicAnswer(question, answers[question.id])
    if (message !== null) {
      moveToQuestion(question.id, message)
      return
    }
    setQuestionFeedback(null)
    setSubmissionMessage(null)
    setRetryableSubmission(false)
    setStepIndex((index) => index + 1)
    setFocusRequest((request) => request + 1)
  }

  const submit = async () => {
    if (submittingRef.current || clientSubmissionId === null || slug === undefined) {
      return
    }
    const invalid = firstInvalidPublicQuestion(survey.questions, answers)
    if (invalid !== null) {
      moveToQuestion(invalid.questionId, invalid.message)
      return
    }

    const built = buildPublicResponseSubmission(
      survey.questions,
      answers,
      clientSubmissionId,
    )
    submittingRef.current = true
    setSubmitting(true)
    setQuestionFeedback(null)
    setSubmissionMessage(null)
    setRetryableSubmission(false)
    try {
      await client.submitResponse(slug, built.submission)
      setTerminal('completed')
    } catch (error) {
      handleSubmissionError(
        error,
        built.questionIdsByAnswerIndex,
        survey,
        answers,
        setQuestionFeedback,
        setStepIndex,
        setFocusRequest,
        setSubmissionMessage,
        setRetryableSubmission,
        setTerminal,
      )
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <PublicShell>
      <header className="public-progress-header">
        <p>{survey.title}</p>
        <p aria-live="polite">질문 {stepIndex + 1} / {survey.questions.length}</p>
      </header>
      <progress
        aria-label="설문 진행률"
        max={survey.questions.length}
        value={stepIndex + 1}
      />
      <QuestionStep
        draft={answers[question.id]}
        error={questionError}
        headingRef={questionHeadingRef}
        onChange={(draft) => {
          setAnswers((current) => ({ ...current, [question.id]: draft }))
          if (questionFeedback?.questionId === question.id) {
            setQuestionFeedback(null)
          }
          setSubmissionMessage(null)
          setRetryableSubmission(false)
        }}
        question={question}
      />
      {questionFeedback?.questionId === null ? (
        <p className="field-error" role="alert">
          {questionFeedback.message}
        </p>
      ) : null}
      {submissionMessage === null ? null : (
        <p className="field-error" role="alert">
          {submissionMessage}
        </p>
      )}
      <div className="public-actions">
        <button
          className="secondary-button"
          disabled={submitting}
          onClick={() => {
            setQuestionFeedback(null)
            setSubmissionMessage(null)
            setRetryableSubmission(false)
            setStepIndex((index) => index - 1)
            setFocusRequest((request) => request + 1)
          }}
          type="button"
        >
          이전 질문
        </button>
        {stepIndex < survey.questions.length - 1 ? (
          <button disabled={submitting} onClick={moveNext} type="button">
            다음 질문
          </button>
        ) : (
          <button disabled={submitting} onClick={submit} type="button">
            {submitting
              ? '제출 중…'
              : retryableSubmission
                ? '같은 응답 다시 제출'
                : '응답 제출'}
          </button>
        )}
      </div>
    </PublicShell>
  )
}

function QuestionStep({
  question,
  draft,
  error,
  onChange,
  headingRef,
}: {
  question: PublicSurveyQuestion
  draft: PublicAnswerDraft
  error: string | null
  onChange: (draft: PublicAnswerDraft) => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  const descriptionId = `public-question-${question.id}-description`
  const helpId = `public-question-${question.id}-help`
  const errorId = `public-question-${question.id}-error`
  const describedBy = [
    question.description === null ? null : descriptionId,
    question.type === 'SHORT_TEXT' || question.type === 'LONG_TEXT'
      ? helpId
      : null,
    error === null ? null : errorId,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <section className="public-question-step">
      <p className="eyebrow">
        {question.required ? '필수 응답' : '선택 응답'}
      </p>
      <h2 ref={headingRef} tabIndex={-1}>
        {question.title}
      </h2>
      {question.description === null ? null : (
        <p id={descriptionId}>{question.description}</p>
      )}
      <fieldset
        aria-describedby={describedBy || undefined}
        aria-required={question.required}
      >
        <legend className="visually-hidden">{question.title}</legend>
        <QuestionControl
          describedBy={describedBy || undefined}
          draft={draft}
          invalid={error !== null}
          onChange={onChange}
          question={question}
        />
      </fieldset>
      {question.type === 'SHORT_TEXT' || question.type === 'LONG_TEXT' ? (
        <p className="field-help" id={helpId}>
          {Array.from(draft as string).length} / {question.type === 'SHORT_TEXT' ? 500 : 5000}자
        </p>
      ) : null}
      {error === null ? null : (
        <p className="field-error" id={errorId} role="alert">
          {error}
        </p>
      )}
    </section>
  )
}

function QuestionControl({
  question,
  draft,
  invalid,
  describedBy,
  onChange,
}: {
  question: PublicSurveyQuestion
  draft: PublicAnswerDraft
  invalid: boolean
  describedBy: string | undefined
  onChange: (draft: PublicAnswerDraft) => void
}) {
  switch (question.type) {
    case 'SHORT_TEXT':
      return (
        <label>
          <span>단답 응답</span>
          <input
            aria-describedby={describedBy}
            aria-invalid={invalid}
            onChange={(event) => onChange(event.target.value)}
            type="text"
            value={draft as string}
          />
        </label>
      )
    case 'LONG_TEXT':
      return (
        <label>
          <span>장문 응답</span>
          <textarea
            aria-describedby={describedBy}
            aria-invalid={invalid}
            onChange={(event) => onChange(event.target.value)}
            rows={7}
            value={draft as string}
          />
        </label>
      )
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return (
        <div className="public-options">
          {question.options.map((option) => {
            const selected = (draft as number[]).includes(option.id)
            return (
              <label key={option.id}>
                <input
                  aria-describedby={describedBy}
                  aria-invalid={invalid}
                  checked={selected}
                  name={`public-question-${question.id}`}
                  onChange={() => {
                    if (question.type === 'SINGLE_CHOICE') {
                      onChange([option.id])
                    } else {
                      onChange(
                        selected
                          ? (draft as number[]).filter((id) => id !== option.id)
                          : [...(draft as number[]), option.id],
                      )
                    }
                  }}
                  type={question.type === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
                />
                <span>{option.label}</span>
              </label>
            )
          })}
        </div>
      )
    case 'SCALE':
      return (
        <div className="public-scale">
          {scaleValues(question).map((value) => (
            <label key={value}>
              <input
                aria-describedby={describedBy}
                aria-invalid={invalid}
                checked={draft === String(value)}
                name={`public-question-${question.id}`}
                onChange={() => onChange(String(value))}
                type="radio"
              />
              <span>{value}</span>
              {value === question.scaleMin && question.scaleMinLabel !== null ? (
                <small>{question.scaleMinLabel}</small>
              ) : null}
              {value === question.scaleMax && question.scaleMaxLabel !== null ? (
                <small>{question.scaleMaxLabel}</small>
              ) : null}
            </label>
          ))}
        </div>
      )
    case 'NUMBER':
      return (
        <>
          <label htmlFor={`public-question-${question.id}-number`}>
            숫자 응답
          </label>
          <input
            aria-describedby={describedBy}
            aria-invalid={invalid}
            id={`public-question-${question.id}-number`}
            inputMode="decimal"
            onChange={(event) => onChange(event.target.value)}
            type="text"
            value={draft as string}
          />
          <span className="field-help">
            {numberBounds(question.numberMin, question.numberMax)}
          </span>
        </>
      )
  }
}

function handleSubmissionError(
  error: unknown,
  questionIdsByAnswerIndex: number[],
  survey: PublicSurvey,
  answers: PublicAnswerState,
  setQuestionFeedback: React.Dispatch<React.SetStateAction<PublicQuestionFeedback | null>>,
  setStepIndex: React.Dispatch<React.SetStateAction<number>>,
  setFocusRequest: React.Dispatch<React.SetStateAction<number>>,
  setSubmissionMessage: React.Dispatch<React.SetStateAction<string | null>>,
  setRetryableSubmission: React.Dispatch<React.SetStateAction<boolean>>,
  setTerminal: React.Dispatch<React.SetStateAction<TerminalState | null>>,
) {
  if (!(error instanceof PublicApiError)) {
    setSubmissionMessage('응답을 제출하지 못했습니다. 다시 시도해 주세요.')
    return
  }

  switch (error.code) {
    case 'RESPONSE_INVALID':
    case 'VALIDATION_FAILED': {
      const feedback = publicResponseFieldFeedback(
        error.fieldErrors,
        questionIdsByAnswerIndex,
        survey.questions,
        answers,
      ) ?? { questionId: null, message: '응답 내용을 확인해 주세요.' }
      setQuestionFeedback(feedback)
      if (feedback.questionId !== null) {
        const targetIndex = survey.questions.findIndex(
          (question) => question.id === feedback.questionId,
        )
        if (targetIndex >= 0) {
          setStepIndex(targetIndex)
        }
        setFocusRequest((request) => request + 1)
      }
      return
    }
    case 'SURVEY_NOT_FOUND':
      setTerminal('unavailable')
      return
    case 'SURVEY_NOT_OPEN':
      setTerminal('not-open')
      return
    case 'RESPONSE_DUPLICATE_CONFLICT':
      setTerminal('conflict')
      return
    case 'RESPONSE_PAYLOAD_TOO_LARGE':
      setSubmissionMessage('응답 전체 크기가 너무 큽니다. 내용을 줄인 뒤 다시 제출해 주세요.')
      return
    case 'RATE_LIMITED':
      setSubmissionMessage('요청이 많습니다. 잠시 후 같은 응답으로 안전하게 다시 시도할 수 있습니다.')
      setRetryableSubmission(true)
      return
    case 'TEMPORARILY_UNAVAILABLE':
      setSubmissionMessage('일시적인 문제로 제출하지 못했습니다. 같은 응답으로 안전하게 다시 시도할 수 있습니다.')
      setRetryableSubmission(true)
      return
    case 'UNEXPECTED_RESPONSE':
      setSubmissionMessage('응답을 제출하지 못했습니다. 다시 시도해 주세요.')
  }
}

function PublicShell({ children }: { children: React.ReactNode }) {
  return (
    <main className="public-shell">
      <div className="public-card">{children}</div>
    </main>
  )
}

function PublicState({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children?: React.ReactNode
}) {
  return (
    <PublicShell>
      <p className="product-name">FormDock</p>
      <h1>{title}</h1>
      <p className="status-message">{description}</p>
      {children}
    </PublicShell>
  )
}

function scaleValues(question: PublicSurveyQuestion): number[] {
  if (question.scaleMin === null || question.scaleMax === null) {
    return []
  }
  return Array.from(
    { length: question.scaleMax - question.scaleMin + 1 },
    (_, index) => question.scaleMin! + index,
  )
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

function createSubmissionId(): string {
  return crypto.randomUUID()
}

export default PublicSurveyPage
