---
title: Admin API Contract
status: draft
version: 0.2
last_updated: 2026-08-19
---

# 1. Prefix

```text
/api
```

# 2. Auth

```text
GET  /api/auth/csrf
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

Login, logout와 모든 Admin mutation은 CSRF token을 요구한다. `/api/auth/csrf`는 로그인 전에도 token을 발급할 수 있다.

| Endpoint | Success | Failure |
|---|---|---|
| `GET /api/auth/csrf` | `200 OK`, CSRF token DTO | transient session dependency failure `503 TEMPORARILY_UNAVAILABLE` |
| `POST /api/auth/login` | `200 OK`, Creator DTO와 server-side session | invalid CSRF `403`; unknown email/wrong password 모두 `401 AUTH_INVALID_CREDENTIALS` |
| `POST /api/auth/logout` | `204 No Content`, session/context/cookie invalidated | invalid CSRF `403`; unauthenticated `401 AUTH_REQUIRED` |
| `GET /api/auth/me` | `200 OK`, Creator DTO | unauthenticated `401 AUTH_REQUIRED` |

Login request:

```json
{
  "email": "creator@example.com",
  "password": "operator-provided-secret"
}
```

Login과 Current Creator response는 같은 shape를 사용한다.

```json
{
  "id": 1,
  "email": "creator@example.com",
  "displayName": "Creator",
  "role": "ADMIN"
}
```

CSRF response는 token과 client가 사용할 고정 header name을 제공한다.

```json
{
  "token": "request-specific-token",
  "headerName": "X-CSRF-TOKEN"
}
```

Password hash, plaintext password와 session ID는 어떤 auth response에도 포함하지 않는다. Login/logout 성공 뒤 client는 `/api/auth/csrf`를 다시 호출한다.

# 3. Surveys

```text
GET    /api/surveys
POST   /api/surveys
GET    /api/surveys/{surveyId}
PATCH  /api/surveys/{surveyId}
DELETE /api/surveys/{surveyId}

POST   /api/surveys/{surveyId}/duplicate
POST   /api/surveys/{surveyId}/open
POST   /api/surveys/{surveyId}/close
```

- list/detail/result는 authenticated owner의 non-deleted Survey만 반환한다.
- slug PATCH는 DRAFT + `openedAt == null`일 때만 허용한다.
- OPEN DELETE는 `409 Conflict`이며 close 후 다시 요청해야 한다.
- soft delete 후 일반 Admin API와 result API는 `404 Not Found`다.

# 4. Questions

```text
POST   /api/surveys/{surveyId}/questions
PATCH  /api/surveys/{surveyId}/questions/{questionId}
DELETE /api/surveys/{surveyId}/questions/{questionId}
POST   /api/surveys/{surveyId}/questions/reorder
```

QuestionOption은 Question aggregate의 create/update payload 안에 ordered list로 포함한다. V1은 별도 Option mutation endpoint를 제공하지 않는다. update는 전달된 전체 Option list를 transaction에서 검증하고 교체한다.

# 5. Responses

```text
GET /api/surveys/{surveyId}/responses
GET /api/surveys/{surveyId}/responses/{responseId}
GET /api/surveys/{surveyId}/responses/summary
GET /api/surveys/{surveyId}/responses/export.csv
```

# 6. Authorization

모든 Admin Survey API는 owner check 필수.

다른 Creator 소유 resource도 caller에게는 `404 Not Found`로 처리해 존재 여부를 노출하지 않는다.

# 7. Documentation

Spring REST Docs로 구현된 contract를 검증한다.
