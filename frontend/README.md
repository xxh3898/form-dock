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

`npm run dev`는 `/login`, 보호된 `/admin` Creator shell과 공개 `/s/:slug` respondent flow를 제공하고 `/api`를 기본 `http://127.0.0.1:18081`로 proxy한다. `/`는 `/admin`으로 이동하고 session guard는 anonymous Creator route 요청만 `/login`으로 보낸다. 보호된 Results route는 `/admin/surveys/:surveyId/responses`와 `/admin/surveys/:surveyId/responses/:responseId`이며 요약, 최신순 목록, 개별 응답과 CSV 다운로드를 제공한다. 필요하면 `FORMDOCK_API_PROXY_TARGET`으로 proxy target을 변경한다.

Auth client는 browser-managed HttpOnly session과 memory-only CSRF state를 사용한다. 별도 Public Survey client는 Creator CSRF flow를 호출하지 않고 form instance마다 하나의 memory-only `clientSubmissionId`를 유지한다. Results client도 same-origin Creator session을 유지하며 JSON DTO를 엄격히 검증하고 CSV 오류 응답을 파일로 저장하지 않는다. 별도 server-state, form, styling framework dependency는 의도적으로 추가하지 않았다.
