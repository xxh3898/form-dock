---
title: Frontend Architecture
status: active
version: 0.2
last_updated: 2026-08-19
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

# 3. State

Server state는 API client/query layer에서 관리.

불필요한 global state library는 초기 도입하지 않는다.

Creator auth는 route-local React state와 typed same-origin auth client를 사용한다. Server-side session이 identity authority이며 frontend는 auth token이나 session ID를 저장하지 않는다. CSRF token은 client instance memory에만 유지한다.

# 4. Forms

Question Builder와 Respondent Answer state는 local/form state 중심.

# 5. Routing

첫 navigation feature인 Phase 1 PR C에서 `react-router` 8.3.0 Declarative Mode를 확정했다. `BrowserRouter`, `Routes`, `Route`를 사용하며 Framework Mode, loaders/actions architecture와 SSR은 도입하지 않는다.

```text
/        → replace /admin
/login   → Creator Login
/admin   → GET /api/auth/me guard 뒤 Creator Admin shell
*        → minimal not found
```

`/`는 항상 `/admin`으로 이동하고 Admin guard가 server session을 조회해 anonymous/expired session만 `/login`으로 replace한다. Session check 중 protected Creator content는 렌더링하지 않는다. Nginx는 `/login`, `/admin` direct load를 `index.html`로 fallback하고 `/api`는 same-origin API로 proxy한다.

Public Respondent `/s/{slug}` route는 Survey Domain authorization 이후 별도 구현한다.

# 6. UX

Respondent는 mobile-first.

Creator는 desktop-first지만 tablet 대응 가능하도록 구성.

# 7. Accessibility

Semantic controls, focus, error association, keyboard navigation 기본 적용.

# 8. References

- [React Router Declarative Mode installation](https://reactrouter.com/start/declarative/installation)
- [React Router routing](https://reactrouter.com/start/declarative/routing)
