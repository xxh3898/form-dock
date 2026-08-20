import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import App from '../App.tsx'
import type { AuthClient, Creator } from '../auth/authClient.ts'
import { ApiError } from '../api/apiClient.ts'
import type {
  SurveyClient,
  SurveyDetail,
  SurveyQuestion,
} from '../surveys/surveyClient.ts'

const creator: Creator = {
  id: 1,
  email: 'creator@example.test',
  displayName: 'Local Creator',
  role: 'ADMIN',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('Survey Creator workflow', () => {
  it('should_renderCanonicalOwnerList_andKeepReservedSlugNonClickable', async () => {
    renderAt('/admin/surveys')

    expect(await screen.findByRole('heading', { name: 'Surveys' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Research survey' })).toHaveAttribute(
      'href',
      '/admin/surveys/7',
    )
    expect(screen.getByText('0')).toBeInTheDocument()
    const slug = screen.getByText('research-survey')
    expect(slug.tagName).toBe('CODE')
    expect(slug.closest('a')).toBeNull()
    expect(screen.queryByText(/pagination/i)).not.toBeInTheDocument()
  })

  it('should_retrySurveyList_when_transientLoadFails', async () => {
    const listSurveys = vi
      .fn()
      .mockRejectedValueOnce(new ApiError('TEMPORARILY_UNAVAILABLE', 503))
      .mockResolvedValueOnce([listItem(baseSurvey)])
    renderAt('/admin/surveys', createSurveyClient({ listSurveys }))

    expect(
      await screen.findByRole('heading', { name: 'Surveys unavailable' }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: 'Research survey' })).toBeInTheDocument()
    expect(listSurveys).toHaveBeenCalledTimes(2)
  })

  it('should_replaceWithLogin_when_protectedApiSessionExpires', async () => {
    const listSurveys = vi.fn(async () => {
      throw new ApiError('AUTH_REQUIRED', 401)
    })
    renderAt('/admin/surveys', createSurveyClient({ listSurveys }))

    expect(
      await screen.findByRole('heading', { name: 'Creator sign in' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Surveys' }),
    ).not.toBeInTheDocument()
  })

  it('should_createSurvey_withNullGeneratedSlugIntent_andNavigateToBuilder', async () => {
    const createSurvey = vi.fn(async () => baseSurvey)
    const getSurvey = vi.fn(async () => baseSurvey)
    renderAt(
      '/admin/surveys/new',
      createSurveyClient({ createSurvey, getSurvey }),
    )

    await screen.findByRole('heading', { name: 'Create Survey' })
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: ' Research survey ' },
    })
    fireEvent.change(screen.getByLabelText('Description (optional)'), {
      target: { value: ' Discovery ' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create Survey' }))

    await waitFor(() => {
      expect(createSurvey).toHaveBeenCalledWith({
        title: 'Research survey',
        description: ' Discovery ',
        privacyNotice: null,
        slug: null,
      })
    })
    expect(
      await screen.findByRole('heading', { name: 'Research survey' }),
    ).toBeInTheDocument()
    expect(getSurvey).toHaveBeenCalledWith(7)
  })

  it('should_associateCreateFieldError_when_slugConflicts', async () => {
    const createSurvey = vi.fn(async () => {
      throw new ApiError('SURVEY_SLUG_CONFLICT', 409, [
        { path: 'slug', code: 'NOT_UNIQUE', message: 'Slug is already reserved.' },
      ])
    })
    renderAt('/admin/surveys/new', createSurveyClient({ createSurvey }))

    await screen.findByRole('heading', { name: 'Create Survey' })
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Research survey' },
    })
    fireEvent.change(screen.getByLabelText('Reserved slug (optional)'), {
      target: { value: 'reserved' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create Survey' }))

    expect(await screen.findByText('Slug is already reserved.')).toBeInTheDocument()
    expect(screen.getByLabelText('Reserved slug (optional)')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('should_patchOnlyChangedMetadata_andRespectSlugLifecycle', async () => {
    const updated = { ...baseSurvey, title: 'Updated survey' }
    const updateSurvey = vi.fn(async () => updated)
    renderAt('/admin/surveys/7', createSurveyClient({ updateSurvey }))

    await screen.findByRole('heading', { name: 'Research survey' })
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Updated survey' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save metadata' }))

    await waitFor(() => {
      expect(updateSurvey).toHaveBeenCalledWith(7, { title: 'Updated survey' })
    })
    expect(
      await screen.findByRole('heading', { name: 'Updated survey' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Reserved slug')).toBeEnabled()
  })

  it('should_followOpenCloseReopenCanonicalLifecycle', async () => {
    const opened = {
      ...baseSurvey,
      status: 'OPEN' as const,
      openedAt: '2026-08-20T01:00:00Z',
    }
    const closed = {
      ...opened,
      status: 'CLOSED' as const,
      closedAt: '2026-08-20T02:00:00Z',
    }
    const reopened = { ...opened, closedAt: null }
    const openSurvey = vi
      .fn()
      .mockResolvedValueOnce(opened)
      .mockResolvedValueOnce(reopened)
    const closeSurvey = vi.fn(async () => closed)
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ openSurvey, closeSurvey }),
    )

    await screen.findByRole('button', { name: 'Open' })
    fireEvent.click(screen.getByRole('button', { name: 'Open' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Close' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Open' }))

    await waitFor(() => {
      expect(openSurvey).toHaveBeenCalledTimes(2)
      expect(closeSurvey).toHaveBeenCalledOnce()
    })
    expect(screen.getByText('OPEN')).toBeInTheDocument()
    expect(screen.getByLabelText('Reserved slug')).toBeDisabled()
  })

  it('should_explainInvalidStructure_when_openIsRejected', async () => {
    const openSurvey = vi.fn(async () => {
      throw new ApiError('SURVEY_INVALID_STRUCTURE', 409)
    })
    renderAt('/admin/surveys/7', createSurveyClient({ openSurvey }))

    fireEvent.click(await screen.findByRole('button', { name: 'Open' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Add at least one valid Question before opening this Survey.',
    )
  })

  it('should_duplicateToFreshDraft_andNavigateToSeparateBuilder', async () => {
    const duplicate = { ...baseSurvey, id: 8, title: 'Research survey copy' }
    const duplicateSurvey = vi.fn(async () => duplicate)
    const getSurvey = vi.fn(async (surveyId: number) =>
      surveyId === 8 ? duplicate : baseSurvey,
    )
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ duplicateSurvey, getSurvey }),
    )

    await screen.findByRole('heading', { name: 'Research survey' })
    fireEvent.click(screen.getByRole('button', { name: 'Duplicate' }))

    expect(
      await screen.findByRole('heading', { name: 'Research survey copy' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Editable DRAFT copy created without Responses.'),
    ).toBeInTheDocument()
    expect(duplicateSurvey).toHaveBeenCalledWith(7)
    await waitFor(() => {
      expect(getSurvey).toHaveBeenLastCalledWith(8)
    })
  })

  it('should_confirmSoftDelete_andReturnToSurveyList', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const deleteSurvey = vi.fn(async () => undefined)
    const listSurveys = vi.fn(async () => [])
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ deleteSurvey, listSurveys }),
    )

    await screen.findByRole('button', { name: 'Delete' })
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))

    expect(
      await screen.findByRole('heading', { name: 'No Surveys yet' }),
    ).toBeInTheDocument()
    expect(deleteSurvey).toHaveBeenCalledWith(7)
  })
})

describe('Question Builder workflow', () => {
  it('should_createChoiceQuestion_withCompleteNormalizedPayload', async () => {
    const createdQuestion = choiceQuestion(10)
    const createQuestion = vi.fn(async () => ({
      ...baseSurvey,
      questions: [createdQuestion],
    }))
    renderAt('/admin/surveys/7', createSurveyClient({ createQuestion }))

    await screen.findByRole('button', { name: 'Add Question' })
    fireEvent.click(screen.getByRole('button', { name: 'Add Question' }))
    fireEvent.change(screen.getByLabelText('Question type'), {
      target: { value: 'SINGLE_CHOICE' },
    })
    expect(
      screen.getAllByRole('option').map((option) => option.getAttribute('value')),
    ).toEqual([
      'SHORT_TEXT',
      'LONG_TEXT',
      'SINGLE_CHOICE',
      'MULTIPLE_CHOICE',
      'SCALE',
      'NUMBER',
    ])
    fireEvent.change(screen.getByLabelText('Question title'), {
      target: { value: 'Choose one' },
    })
    fireEvent.change(screen.getByLabelText('Option 1'), {
      target: { value: 'Alpha' },
    })
    fireEvent.change(screen.getByLabelText('Option 2'), {
      target: { value: 'Beta' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save question' }))

    await waitFor(() => {
      expect(createQuestion).toHaveBeenCalledWith(7, {
        type: 'SINGLE_CHOICE',
        title: 'Choose one',
        description: null,
        required: false,
        scaleMin: null,
        scaleMax: null,
        scaleMinLabel: null,
        scaleMaxLabel: null,
        numberMin: null,
        numberMax: null,
        options: [{ label: 'Alpha' }, { label: 'Beta' }],
      })
    })
    expect(await screen.findByText('Question added.')).toBeInTheDocument()
  })

  it('should_preserveExistingChoiceOptionIds_andOmitNewOptionId', async () => {
    const question = choiceQuestion(10)
    const detail = { ...baseSurvey, questions: [question] }
    const updateQuestion = vi.fn(async () => detail)
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ getSurvey: vi.fn(async () => detail), updateQuestion }),
    )

    await screen.findByRole('heading', { name: 'Choose one' })
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Option 1'), {
      target: { value: 'Updated Alpha' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add option' }))
    fireEvent.change(screen.getByLabelText('Option 3'), {
      target: { value: 'Gamma' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save question' }))

    await waitFor(() => {
      expect(updateQuestion).toHaveBeenCalledWith(
        7,
        10,
        expect.objectContaining({
          options: [
            { id: 21, label: 'Updated Alpha' },
            { id: 22, label: 'Beta' },
            { label: 'Gamma' },
          ],
        }),
      )
    })
  })

  it('should_reorderWithCompleteQuestionIdSet_andRefetchAfterDelete', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const first = choiceQuestion(10)
    const second = numberQuestion(11, 1)
    const initial = { ...baseSurvey, questions: [first, second] }
    const reordered = {
      ...baseSurvey,
      questions: [
        { ...second, position: 0 },
        { ...first, position: 1 },
      ],
    }
    const reorderQuestions = vi.fn(async () => reordered)
    const deleteQuestion = vi.fn(async () => undefined)
    const getSurvey = vi
      .fn()
      .mockResolvedValueOnce(initial)
      .mockResolvedValueOnce({
        ...baseSurvey,
        questions: [{ ...first, position: 0 }],
      })
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ getSurvey, reorderQuestions, deleteQuestion }),
    )

    await screen.findByRole('heading', { name: 'Choose one' })
    fireEvent.click(screen.getByRole('button', { name: 'Move Choose one down' }))
    await waitFor(() => {
      expect(reorderQuestions).toHaveBeenCalledWith(7, [11, 10])
    })
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Move Number answer up' }),
      ).toBeDisabled()
    })
    fireEvent.click(
      screen.getByRole('button', { name: 'Delete Number answer' }),
    )

    await waitFor(() => {
      expect(deleteQuestion).toHaveBeenCalledWith(7, 11)
      expect(getSurvey).toHaveBeenCalledTimes(2)
    })
  })

  it('should_disableStructuralControls_andOfferDuplicate_when_canonicalStructureIsLocked', async () => {
    const locked = {
      ...baseSurvey,
      responseCount: 3,
      structureLocked: true,
      questions: [choiceQuestion(10)],
    }
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ getSurvey: vi.fn(async () => locked) }),
    )

    expect(
      await screen.findByRole('heading', { name: 'Question structure is locked' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add Question' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Duplicate Survey' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save metadata' })).toBeEnabled()
  })

  it('should_refetchCanonicalDetail_andEnterLockedState_when_staleMutationIsRejected', async () => {
    const locked = { ...baseSurvey, responseCount: 1, structureLocked: true }
    const getSurvey = vi
      .fn()
      .mockResolvedValueOnce(baseSurvey)
      .mockResolvedValueOnce(locked)
    const createQuestion = vi.fn(async () => {
      throw new ApiError('SURVEY_STRUCTURE_LOCKED', 409)
    })
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ getSurvey, createQuestion }),
    )

    await screen.findByRole('button', { name: 'Add Question' })
    fireEvent.click(screen.getByRole('button', { name: 'Add Question' }))
    fireEvent.change(screen.getByLabelText('Question title'), {
      target: { value: 'Stale question' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save question' }))

    expect(
      await screen.findByRole('heading', { name: 'Question structure is locked' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Existing Responses lock Question structure',
    )
    expect(getSurvey).toHaveBeenCalledTimes(2)
  })

  it('should_clearStaleEditor_after_questionNotFoundCanonicalRefetch', async () => {
    const removed = choiceQuestion(10)
    const remaining = numberQuestion(11, 1)
    const initial = { ...baseSurvey, questions: [removed, remaining] }
    const refreshed = {
      ...baseSurvey,
      questions: [{ ...remaining, position: 0 }],
    }
    const getSurvey = vi
      .fn()
      .mockResolvedValueOnce(initial)
      .mockResolvedValueOnce(refreshed)
    const updateQuestion = vi.fn(async () => {
      throw new ApiError('QUESTION_NOT_FOUND', 404)
    })
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({ getSurvey, updateQuestion }),
    )

    await screen.findByRole('heading', { name: 'Choose one' })
    fireEvent.click(screen.getAllByRole('button', { name: 'Edit' })[0]!)
    fireEvent.click(screen.getByRole('button', { name: 'Save question' }))

    expect(
      await screen.findByText(
        'That Question is no longer available. Refresh the Builder.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Choose one' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Number answer' }),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText('Question title')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add Question' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeEnabled()
    expect(getSurvey).toHaveBeenCalledTimes(2)
  })

  it('should_keepQuestionTitleError_outOfSurveyMetadata', async () => {
    const question = choiceQuestion(10)
    const detail = { ...baseSurvey, questions: [question] }
    const updateQuestion = vi.fn(async () => {
      throw new ApiError('QUESTION_INVALID_CONFIGURATION', 400, [
        {
          path: 'title',
          code: 'REQUIRED',
          message: 'Question title is invalid.',
        },
      ])
    })
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({
        getSurvey: vi.fn(async () => detail),
        updateQuestion,
      }),
    )

    await screen.findByRole('heading', { name: 'Choose one' })
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save question' }))

    expect(
      await screen.findByText('Question title is invalid.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Question title')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(screen.getByLabelText('Title')).not.toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('should_keepSurveyTitleError_outOfQuestionEditor', async () => {
    const question = choiceQuestion(10)
    const detail = { ...baseSurvey, questions: [question] }
    const updateSurvey = vi.fn(async () => {
      throw new ApiError('VALIDATION_FAILED', 400, [
        {
          path: 'title',
          code: 'REQUIRED',
          message: 'Survey title is invalid.',
        },
      ])
    })
    renderAt(
      '/admin/surveys/7',
      createSurveyClient({
        getSurvey: vi.fn(async () => detail),
        updateSurvey,
      }),
    )

    await screen.findByRole('heading', { name: 'Choose one' })
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Changed Survey title' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save metadata' }))

    expect(
      await screen.findByText('Survey title is invalid.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Title')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(screen.getByLabelText('Question title')).not.toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })
})

describe('Admin Preview', () => {
  it('should_renderAllSixQuestionTypesReadOnly_withoutPublicOrSubmitAction', async () => {
    const previewSurvey = { ...baseSurvey, questions: allSixQuestions }
    renderAt(
      '/admin/surveys/7/preview',
      createSurveyClient({ getSurvey: vi.fn(async () => previewSurvey) }),
    )

    expect(
      await screen.findByText('Read-only Admin Preview · Responses are not submitted.'),
    ).toBeInTheDocument()
    for (const title of [
      'Short answer',
      'Long answer',
      'Single choice',
      'Multiple choice',
      'Scale answer',
      'Number answer',
    ]) {
      expect(screen.getByRole('heading', { name: new RegExp(title) })).toBeInTheDocument()
    }
    expect(screen.queryByRole('button', { name: /submit/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /research-survey/i })).not.toBeInTheDocument()
    expect(screen.getByText('research-survey').tagName).toBe('CODE')
  })
})

function renderAt(path: string, surveys: SurveyClient = createSurveyClient()) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App client={createAuthClient()} surveys={surveys} />
    </MemoryRouter>,
  )
}

function createAuthClient(): AuthClient {
  return {
    login: vi.fn(async () => creator),
    logout: vi.fn(async () => undefined),
    me: vi.fn(async () => creator),
  }
}

function createSurveyClient(
  overrides: Partial<SurveyClient> = {},
): SurveyClient {
  return {
    closeSurvey: vi.fn(async () => ({
      ...baseSurvey,
      status: 'CLOSED' as const,
    })),
    createQuestion: vi.fn(async () => baseSurvey),
    createSurvey: vi.fn(async () => baseSurvey),
    deleteQuestion: vi.fn(async () => undefined),
    deleteSurvey: vi.fn(async () => undefined),
    duplicateSurvey: vi.fn(async () => ({ ...baseSurvey, id: 8 })),
    getSurvey: vi.fn(async () => baseSurvey),
    listSurveys: vi.fn(async () => [listItem(baseSurvey)]),
    openSurvey: vi.fn(async () => ({ ...baseSurvey, status: 'OPEN' as const })),
    reorderQuestions: vi.fn(async () => baseSurvey),
    updateQuestion: vi.fn(async () => baseSurvey),
    updateSurvey: vi.fn(async () => baseSurvey),
    ...overrides,
  }
}

function listItem(survey: SurveyDetail) {
  return {
    id: survey.id,
    title: survey.title,
    status: survey.status,
    slug: survey.slug,
    responseCount: survey.responseCount,
    updatedAt: survey.updatedAt,
  }
}

const baseSurvey: SurveyDetail = {
  id: 7,
  title: 'Research survey',
  description: null,
  slug: 'research-survey',
  privacyNotice: null,
  status: 'DRAFT',
  openedAt: null,
  closedAt: null,
  createdAt: '2026-08-20T00:00:00Z',
  updatedAt: '2026-08-20T00:00:00Z',
  responseCount: 0,
  structureLocked: false,
  questions: [],
}

function choiceQuestion(id: number, position = 0): SurveyQuestion {
  return {
    id,
    type: 'SINGLE_CHOICE',
    title: 'Choose one',
    description: null,
    required: true,
    position,
    scaleMin: null,
    scaleMax: null,
    scaleMinLabel: null,
    scaleMaxLabel: null,
    numberMin: null,
    numberMax: null,
    options: [
      { id: 21, label: 'Alpha', position: 0 },
      { id: 22, label: 'Beta', position: 1 },
    ],
  }
}

function numberQuestion(id: number, position: number): SurveyQuestion {
  return {
    id,
    type: 'NUMBER',
    title: 'Number answer',
    description: null,
    required: false,
    position,
    scaleMin: null,
    scaleMax: null,
    scaleMinLabel: null,
    scaleMaxLabel: null,
    numberMin: '0.0001',
    numberMax: '100.0000',
    options: [],
  }
}

const allSixQuestions: SurveyQuestion[] = [
  {
    ...numberQuestion(1, 0),
    type: 'SHORT_TEXT',
    title: 'Short answer',
    numberMin: null,
    numberMax: null,
  },
  {
    ...numberQuestion(2, 1),
    type: 'LONG_TEXT',
    title: 'Long answer',
    numberMin: null,
    numberMax: null,
  },
  { ...choiceQuestion(3, 2), title: 'Single choice' },
  {
    ...choiceQuestion(4, 3),
    type: 'MULTIPLE_CHOICE',
    title: 'Multiple choice',
  },
  {
    ...numberQuestion(5, 4),
    type: 'SCALE',
    title: 'Scale answer',
    scaleMin: 1,
    scaleMax: 5,
    scaleMinLabel: 'Low',
    scaleMaxLabel: 'High',
    numberMin: null,
    numberMax: null,
  },
  numberQuestion(6, 5),
]
