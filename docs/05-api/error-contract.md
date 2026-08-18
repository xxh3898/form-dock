---
title: API Error Contract
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Goal

Frontend가 HTTP status 문자열 parsing 없이 안정적으로 오류를 처리할 수 있게 한다.

# 2. Shape

```json
{
  "code": "SURVEY_STRUCTURE_LOCKED",
  "message": "응답이 존재하는 설문 구조는 변경할 수 없습니다.",
  "fieldErrors": [
    {
      "path": "questions[0].title",
      "code": "REQUIRED",
      "message": "질문 제목은 필수입니다."
    }
  ],
  "traceId": "optional-correlation-id"
}
```

- `code`는 stable machine-readable identifier다.
- `message`는 사용자 표시 가능한 안전한 요약이며 client 분기 기준이 아니다.
- `fieldErrors`는 없을 때도 빈 array다.
- `traceId`는 server correlation ID가 있을 때만 포함한다.

# 3. Codes

```text
AUTH_INVALID_CREDENTIALS
AUTH_REQUIRED
CSRF_INVALID
FORBIDDEN

SURVEY_NOT_FOUND
SURVEY_NOT_OPEN
SURVEY_STRUCTURE_LOCKED
SURVEY_SLUG_CONFLICT
SURVEY_SLUG_IMMUTABLE
SURVEY_DELETE_REQUIRES_CLOSED
SURVEY_INVALID_STRUCTURE

QUESTION_NOT_FOUND
QUESTION_INVALID_CONFIGURATION

RESPONSE_INVALID
RESPONSE_DUPLICATE_CONFLICT

RATE_LIMITED
TEMPORARILY_UNAVAILABLE
```

# 4. HTTP Mapping

| Status | Meaning |
|---|---|
| 400 | malformed or semantically invalid request |
| 401 | Creator authentication required or credentials invalid |
| 403 | authenticated but forbidden, or CSRF invalid |
| 404 | unavailable/unknown/deleted resource or ownership concealed |
| 409 | current Survey state or idempotency identity conflicts with request |
| 415 | Public Response Content-Type is not `application/json` |
| 429 | rate limit exceeded |
| 503 | bounded lock timeout/deadlock or transient dependency failure; retry allowed |

`CLOSED` Survey는 reopen될 수 있으므로 `410 Gone`을 사용하지 않는다.

# 5. Validation Errors

Field error에는 machine-readable field/path를 제공한다.

# 6. Internal Error

stack trace/internal DB message를 Public/Admin response에 노출하지 않는다.
