# Frontend

React 19 / TypeScript / Vite 8 frontend with React Router 8 Declarative Mode.

## Commands

```bash
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

`npm run dev`는 `/login`, 보호된 `/admin` Creator shell과 공개 `/s/:slug` respondent flow를 제공하고 `/api`를 기본 `http://127.0.0.1:18081`로 proxy한다. `/`는 `/admin`으로 이동하고 session guard는 anonymous Creator route 요청만 `/login`으로 보낸다. 필요하면 `FORMDOCK_API_PROXY_TARGET`으로 proxy target을 변경한다.

Auth client는 browser-managed HttpOnly session과 memory-only CSRF state를 사용한다. 별도 Public Survey client는 Creator CSRF flow를 호출하지 않고 form instance마다 하나의 memory-only `clientSubmissionId`를 유지한다. 별도 server-state, form, styling framework dependency는 의도적으로 추가하지 않았다.
