---
title: Frontend Architecture
status: active
version: 0.9
last_updated: 2026-08-23
---

# 1. Stack

```text
React
TypeScript
Vite
React Router 8.3.0
```

# 2. Main Areas

```text
/admin/*
/s/{slug}
```

Creator Admin과 Public Respondent UI를 기능적으로 분리한다.

# 3. Language and Locale

V1 frontend는 한국어 단일 언어를 사용한다. HTML document language는 `ko`, locale-sensitive 날짜 표시는 명시적 `ko-KR`을 사용한다. 사용자에게 보이는 Survey status는 `DRAFT → 초안`, `OPEN → 공개`, `CLOSED → 마감`으로 하나의 UI mapping에서 변환한다.

API field, error code, route, database identifier와 enum wire value는 영어 contract를 유지한다. V1은 runtime locale 전환, 영어 locale과 i18n dependency를 도입하지 않는다.

# 4. State

Server state는 API client/query layer에서 관리.

불필요한 global state library는 초기 도입하지 않는다.

Creator auth는 route-local React state와 typed same-origin auth client를 사용한다. Server-side session이 identity authority이며 frontend는 auth token이나 session ID를 저장하지 않는다. CSRF token은 client instance memory에만 유지한다.

Phase 2-D Survey client도 같은 focused same-origin transport contract를 사용한다. Relative `/api/*`, `credentials: same-origin`, memory-only CSRF와 첫 `CSRF_INVALID` 뒤 정확히 한 번의 refresh/retry를 유지하고 stable status/code/fieldErrors와 malformed canonical DTO rejection을 UI boundary에 제공한다. 별도 query cache/global store는 추가하지 않으며 mutation response 또는 explicit refetch의 canonical `SurveyDetail`을 page-local state authority로 사용한다.

# 5. Forms

Question Builder와 Respondent Answer state는 local/form state 중심.

# 6. Routing

첫 navigation feature인 Phase 1 PR C에서 `react-router` 8.3.0 Declarative Mode를 확정했다. `BrowserRouter`, `Routes`, `Route`를 사용하며 Framework Mode, loaders/actions architecture와 SSR은 도입하지 않는다.

```text
/        → replace /admin
/login   → Creator Login
/admin   → GET /api/auth/me guard 뒤 Creator Admin shell
*        → minimal not found
```

`/`는 항상 `/admin`으로 이동하고 Admin guard가 server session을 조회해 anonymous/expired session만 `/login`으로 replace한다. Session check 중 protected Creator content는 렌더링하지 않는다. Nginx는 `/login`, `/admin` direct load를 `index.html`로 fallback하고 `/api`는 same-origin API로 proxy한다.

Public Respondent `/s/:slug`는 Admin guard 밖의 유일한 공개 frontend route다. Phase 3-D는 3-A→3-C의 merged API를 사용하며 [Phase 3 Completion Evidence](../06-quality/phase-3-completion-evidence.md)의 exact `dev` regression을 통과했다.

Phase 2-D가 구현하는 canonical route는 다음과 같다.

```text
/admin                              → replace /admin/surveys
/admin/surveys                     → owner Survey list
/admin/surveys/new                 → Survey create
/admin/surveys/{surveyId}          → canonical Builder edit
/admin/surveys/{surveyId}/preview  → Admin-only preview
```

Reserved slug는 Admin UI의 identity text이고 respondent는 직접 전달받은 `/s/:slug`에서 참여한다. Phase 3-D는 Admin Builder에 public-link 관리 기능을 추가하지 않으며 broad design system, SSR/framework migration 또는 unrelated state-management library도 도입하지 않는다.

모든 `/admin/*` child route는 하나의 shared Admin layout이 `/api/auth/me`를 확인한 뒤에만 렌더링한다. `/admin`은 `/admin/surveys`로 replace하고 direct Builder/Preview load도 같은 guard를 통과한다. Preview는 authenticated canonical detail을 read-only로 렌더링하며 submit handler, Public request 또는 Response persistence를 갖지 않는다.

Question form은 six-type complete semantic payload를 local form state에서 구성한다. Type 전환 시 unused scalar는 `null`, non-Choice options는 `[]`로 normalize하고 NUMBER bound는 decimal string을 유지한다. Existing Choice Option ID는 보존하고 새 Option은 ID를 생략한다. `structureLocked`는 structural controls만 잠그며 metadata는 별도 lifecycle contract를 따른다.

## 6.1 Phase 3 Respondent Route

Phase 3-D가 추가한 유일한 public route는 `/s/:slug`다.

```text
Intro
→ step-by-step Questions
→ Submit
→ Completion
```

- 360px 폭에서 usable한 mobile-first layout, visible progress, keyboard/focus/label/error/touch-target accessibility를 제공한다.
- public GET 404는 unavailable, 신규 CLOSED submit 409는 cannot-submit, 413/429/503은 safe retry state로 처리한다.
- server validation이 final authority이며 client validation은 immediate feedback만 제공한다.
- current in-memory form/submission attempt마다 UUID `clientSubmissionId` 하나를 만들고 transient/uncertain retry에서 그대로 재사용한다.
- page reload/new form instance는 새 UUID를 만들 수 있지만 localStorage, sessionStorage, cookie에는 submission identity를 저장하지 않는다.
- Phase 3 Respondent route는 Result/Response read, summary와 CSV UI를 포함하지 않는다. Phase 4 Results UI는 Public state와 분리된 Admin route가 소유한다.

## 6.2 Phase 4 Admin Results Routes

Phase 4-D 구현의 canonical route는 shared Admin guard 안에 둔다.

```text
/admin/surveys/:surveyId/responses
/admin/surveys/:surveyId/responses/:responseId
```

- overview, Question summary, newest-first paginated list, detail navigation과 CSV download를 제공한다.
- loading/empty/out-of-range pagination/404/transient failure를 stable status와 error code로 처리하고 raw backend `message`를 분기 authority로 사용하지 않는다.
- same-origin authenticated client를 유지하며 GET Result/CSV 때문에 CSRF나 CORS contract를 약화하지 않는다.
- broad global state/query library, chart dependency와 design-system rewrite를 추가하지 않는다.
- semantic table/CSS, keyboard/focus/label과 narrow-layout baseline을 유지한다.
- Response edit/delete/exclude control과 Public Respondent result state를 추가하지 않는다.

Phase 4-D frontend는 dedicated strict Results client와 위 두 route를 구현했으며 `dev` 통합 대기 상태다. List/summary/detail JSON과 CSV transport는 same-origin Creator session을 사용하고 stable error code만 UI 분기 authority로 삼는다.

# 7. UX

Respondent는 mobile-first.

Creator는 desktop-first지만 tablet 대응 가능하도록 구성.

# 8. Accessibility

Semantic controls, focus, error association, keyboard navigation 기본 적용.

# 9. References

- [React Router Declarative Mode installation](https://reactrouter.com/start/declarative/installation)
- [React Router routing](https://reactrouter.com/start/declarative/routing)
