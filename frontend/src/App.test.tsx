import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import App from './App.tsx'
import {
  AuthApiError,
  type AuthClient,
  type Creator,
} from './auth/authClient.ts'
import type {
  PublicSurveyClient,
  PublicSurvey,
} from './public/publicSurveyClient.ts'
import type {
  SurveyClient,
  SurveyDetail,
} from './surveys/surveyClient.ts'

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

describe('Creator routes', () => {
  it('should_renderAccessibleLogin_when_routeIsLogin', () => {
    renderRoute('/login', createClient())

    expect(
      screen.getByRole('heading', { name: '관리자 로그인' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('이메일')).toHaveAttribute(
      'autocomplete',
      'email',
    )
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute(
      'autocomplete',
      'current-password',
    )
    expect(screen.queryByText(/sign up|reset password|oauth/i)).not.toBeInTheDocument()
  })

  it('should_redirectAnonymousNestedAdminRouteToLogin_withoutProtectedContentFlash', async () => {
    const client = createClient({
      me: vi.fn(async () => {
        throw new AuthApiError('AUTH_REQUIRED', 401)
      }),
    })

    renderRoute('/admin/surveys/7/preview', client)

    expect(
      screen.queryByRole('heading', { name: 'Research survey' }),
    ).not.toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { name: '관리자 로그인' }),
    ).toBeInTheDocument()
  })

  it('should_redirectAdminToAuthenticatedSurveyList_withOneSessionCheck', async () => {
    const me = vi.fn(async () => creator)
    renderRoute('/admin', createClient({ me }))

    expect(
      await screen.findByRole('heading', { name: '관리자' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { name: '설문' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Local Creator')).toBeInTheDocument()
    expect(me).toHaveBeenCalledOnce()
  })

  it('should_supportDirectBuilderAndPreviewRoutes_when_authenticated', async () => {
    const surveys = createSurveyClient()
    const first = renderRoute('/admin/surveys/7', createClient(), surveys)

    expect(
      await screen.findByRole('heading', { name: 'Research survey' }),
    ).toBeInTheDocument()
    first.unmount()

    renderRoute('/admin/surveys/7/preview', createClient(), surveys)
    expect(
      await screen.findByText(/읽기 전용 관리자 미리보기/),
    ).toBeInTheDocument()
  })

  it('should_navigateToAdmin_when_loginSucceeds', async () => {
    const login = vi.fn(async () => creator)
    const me = vi.fn(async () => creator)
    const storageWrite = vi.spyOn(Storage.prototype, 'setItem')
    const password = 'local-password-value'
    renderRoute('/login', createClient({ login, me }))

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'Creator@Example.test' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: password },
    })
    fireEvent.submit(screen.getByRole('form', { name: '관리자 로그인' }))

    expect(
      await screen.findByRole('heading', { name: '관리자' }),
    ).toBeInTheDocument()
    expect(login).toHaveBeenCalledWith('Creator@Example.test', password)
    expect(me).toHaveBeenCalledOnce()
    expect(screen.queryByDisplayValue(password)).not.toBeInTheDocument()
    expect(storageWrite).not.toHaveBeenCalled()
  })

  it('should_showGenericCredentialError_when_loginIsRejected', async () => {
    const password = 'not-a-real-secret'
    const login = vi.fn(async () => {
      throw new AuthApiError('AUTH_INVALID_CREDENTIALS', 401)
    })
    renderRoute('/login', createClient({ login }))

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'unknown@example.test' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: password },
    })
    fireEvent.submit(screen.getByRole('form', { name: '관리자 로그인' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다.')
    expect(alert).not.toHaveTextContent(password)
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled()
  })

  it('should_showSafeUnavailableError_when_authServiceIsTransientlyUnavailable', async () => {
    const login = vi.fn(async () => {
      throw new AuthApiError('TEMPORARILY_UNAVAILABLE', 503)
    })
    renderRoute('/login', createClient({ login }))

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'creator@example.test' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'unavailable-password' },
    })
    fireEvent.submit(screen.getByRole('form', { name: '관리자 로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'FormDock을 일시적으로 사용할 수 없습니다. 다시 시도해 주세요.',
    )
  })

  it('should_showSafeCsrfError_when_refreshedTokenIsRejected', async () => {
    const login = vi.fn(async () => {
      throw new AuthApiError('CSRF_INVALID', 403)
    })
    renderRoute('/login', createClient({ login }))

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'creator@example.test' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'csrf-password' },
    })
    fireEvent.submit(screen.getByRole('form', { name: '관리자 로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '보안 세션을 갱신하지 못했습니다. 다시 시도해 주세요.',
    )
  })

  it('should_preventDuplicateSubmission_when_loginIsPending', async () => {
    let resolveLogin: ((value: Creator) => void) | undefined
    const login = vi.fn(
      () =>
        new Promise<Creator>((resolve) => {
          resolveLogin = resolve
        }),
    )
    renderRoute('/login', createClient({ login, me: vi.fn(async () => creator) }))

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'creator@example.test' },
    })
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'pending-password' },
    })
    const form = screen.getByRole('form', { name: '관리자 로그인' })
    fireEvent.submit(form)
    fireEvent.submit(form)

    expect(login).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: '로그인 중…' })).toBeDisabled()

    await act(async () => {
      resolveLogin?.(creator)
    })
  })

  it('should_returnToLogin_withoutDuplicateRequest_when_logoutSucceeds', async () => {
    let resolveLogout: (() => void) | undefined
    const logout = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveLogout = resolve
        }),
    )
    renderRoute('/admin', createClient({ logout }))

    await screen.findByRole('heading', { name: '관리자' })
    const button = screen.getByRole('button', { name: '로그아웃' })
    fireEvent.click(button)
    fireEvent.click(button)

    expect(logout).toHaveBeenCalledOnce()
    expect(
      screen.getByRole('button', { name: '로그아웃 중…' }),
    ).toBeDisabled()

    await act(async () => {
      resolveLogout?.()
    })

    expect(
      await screen.findByRole('heading', { name: '관리자 로그인' }),
    ).toBeInTheDocument()
  })

  it('should_hideProtectedContent_when_sessionCheckIsUnavailable', async () => {
    const me = vi.fn(async () => {
      throw new AuthApiError('TEMPORARILY_UNAVAILABLE', 503)
    })
    renderRoute('/admin', createClient({ me }))

    expect(
      await screen.findByRole('heading', { name: '세션을 확인할 수 없습니다' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: '관리자' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('creator@example.test')).not.toBeInTheDocument()
  })

  it('should_renderMinimalNotFound_when_routeIsUnknown', () => {
    renderRoute('/not-a-formdock-route', createClient())

    expect(
      screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/survey/i)).not.toBeInTheDocument()
  })

  it('should_renderPublicSurveyOutsideCreatorSessionGuard', async () => {
    const me = vi.fn(async () => {
      throw new AuthApiError('AUTH_REQUIRED', 401)
    })
    renderRoute(
      '/s/research-survey',
      createClient({ me }),
      createSurveyClient(),
      createPublicSurveyClient(),
    )

    expect(
      await screen.findByRole('heading', { name: '공개 설문' }),
    ).toBeInTheDocument()
    expect(me).not.toHaveBeenCalled()
  })
})

function renderRoute(
  path: string,
  client: AuthClient,
  surveys: SurveyClient = createSurveyClient(),
  publicSurveys: PublicSurveyClient = createPublicSurveyClient(),
) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App
        client={client}
        publicSurveys={publicSurveys}
        surveys={surveys}
      />
    </MemoryRouter>,
  )
}

function createPublicSurveyClient(
  overrides: Partial<PublicSurveyClient> = {},
): PublicSurveyClient {
  return {
    getSurvey: vi.fn(async () => publicSurvey),
    submitResponse: vi.fn(async () => ({
      responseId: 9001,
      submittedAt: '2026-08-21T00:00:00Z',
      replayed: false,
    })),
    ...overrides,
  }
}

function createClient(overrides: Partial<AuthClient> = {}): AuthClient {
  return {
    login: vi.fn(async () => creator),
    logout: vi.fn(async () => undefined),
    me: vi.fn(async () => creator),
    ...overrides,
  }
}

function createSurveyClient(
  overrides: Partial<SurveyClient> = {},
): SurveyClient {
  return {
    closeSurvey: vi.fn(async () => ({
      ...surveyDetail,
      status: 'CLOSED' as const,
    })),
    createQuestion: vi.fn(async () => surveyDetail),
    createSurvey: vi.fn(async () => surveyDetail),
    deleteQuestion: vi.fn(async () => undefined),
    deleteSurvey: vi.fn(async () => undefined),
    duplicateSurvey: vi.fn(async () => ({ ...surveyDetail, id: 8 })),
    getSurvey: vi.fn(async () => surveyDetail),
    listSurveys: vi.fn(async () => [
      {
        id: surveyDetail.id,
        title: surveyDetail.title,
        status: surveyDetail.status,
        slug: surveyDetail.slug,
        responseCount: surveyDetail.responseCount,
        updatedAt: surveyDetail.updatedAt,
      },
    ]),
    openSurvey: vi.fn(async () => ({ ...surveyDetail, status: 'OPEN' as const })),
    reorderQuestions: vi.fn(async () => surveyDetail),
    updateQuestion: vi.fn(async () => surveyDetail),
    updateSurvey: vi.fn(async () => surveyDetail),
    ...overrides,
  }
}

const surveyDetail: SurveyDetail = {
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

const publicSurvey: PublicSurvey = {
  slug: 'research-survey',
  title: '공개 설문',
  description: null,
  privacyNotice: null,
  questions: [
    {
      id: 10,
      type: 'SHORT_TEXT',
      title: '질문',
      description: null,
      required: false,
      position: 0,
      scaleMin: null,
      scaleMax: null,
      scaleMinLabel: null,
      scaleMaxLabel: null,
      numberMin: null,
      numberMax: null,
      options: [],
    },
  ],
}
