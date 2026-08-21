---
title: API Error Contract
status: draft
version: 0.3
last_updated: 2026-08-21
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
VALIDATION_FAILED

SURVEY_NOT_FOUND
SURVEY_NOT_OPEN
SURVEY_STATE_CONFLICT
SURVEY_STRUCTURE_LOCKED
SURVEY_SLUG_CONFLICT
SURVEY_SLUG_IMMUTABLE
SURVEY_DELETE_REQUIRES_CLOSED
SURVEY_INVALID_STRUCTURE

QUESTION_NOT_FOUND
QUESTION_INVALID_CONFIGURATION

RESPONSE_INVALID
RESPONSE_DUPLICATE_CONFLICT
RESPONSE_PAYLOAD_TOO_LARGE

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
| 413 | Public Response raw request body exceeds 1 MiB (1,048,576 bytes) |
| 415 | Public Response Content-Type is not `application/json` |
| 429 | rate limit exceeded |
| 503 | bounded lock timeout/deadlock or transient dependency failure; retry allowed |

`CLOSED` Survey는 reopen될 수 있으므로 `410 Gone`을 사용하지 않는다.

## 4.1 Phase 2 Admin Mapping

| Code | Status | Contract |
|---|---|---|
| `VALIDATION_FAILED` | 400 | malformed field, unknown PATCH field, invalid reorder set와 general DTO validation |
| `QUESTION_INVALID_CONFIGURATION` | 400 | type-specific field/Option combination violation |
| `SURVEY_NOT_FOUND` | 404 | unknown, unowned 또는 soft-deleted Survey concealment |
| `QUESTION_NOT_FOUND` | 404 | unknown, unowned, deleted-parent 또는 Survey에 속하지 않는 Question concealment |
| `SURVEY_STATE_CONFLICT` | 409 | open/close endpoint의 invalid current lifecycle state |
| `SURVEY_SLUG_CONFLICT` | 409 | globally reserved slug collision |
| `SURVEY_SLUG_IMMUTABLE` | 409 | first OPEN 이후 slug mutation |
| `SURVEY_DELETE_REQUIRES_CLOSED` | 409 | OPEN Survey direct delete |
| `SURVEY_INVALID_STRUCTURE` | 409 | OPEN precondition을 만족하지 못한 Survey structure |
| `SURVEY_STRUCTURE_LOCKED` | 409 | canonical Response가 존재하는 structure mutation |
| `TEMPORARILY_UNAVAILABLE` | 503 | bounded Survey lock timeout/deadlock 또는 transient dependency failure |

Invalid lifecycle transition은 silent success가 아니며 unrelated `SURVEY_NOT_OPEN`을 재사용하지 않는다. `SURVEY_NOT_OPEN`은 Phase 3 신규 Public Response submission contract에 남는다.

## 4.2 Phase 3 Public Mapping

| Code | Status | Contract |
|---|---|---|
| `SURVEY_NOT_FOUND` | 404 | unknown/DRAFT/deleted submit 또는 DRAFT/CLOSED/deleted/unknown Public GET의 identical concealment |
| `SURVEY_NOT_OPEN` | 409 | CLOSED Survey의 new `clientSubmissionId` submit |
| `RESPONSE_INVALID` | 400 | Question/Option/type/required/value semantic violation |
| `RESPONSE_DUPLICATE_CONFLICT` | 409 | same identity와 different canonical payload |
| `RESPONSE_PAYLOAD_TOO_LARGE` | 413 | raw Public Response request body가 1 MiB(1,048,576 bytes) 초과 |
| `RATE_LIMITED` | 429 | bounded ephemeral application guard rejection; Response write 0 |
| `TEMPORARILY_UNAVAILABLE` | 503 | bounded Survey lock/dependency failure; same identity retry 가능 |

Public GET의 unavailable state는 lifecycle별 code/message로 구분하지 않는다. Internal parser, DB, rate-limit key와 proxy-header detail은 error body에 노출하지 않는다.

# 5. Validation Errors

Field error에는 machine-readable field/path를 제공한다.

# 6. Internal Error

stack trace/internal DB message를 Public/Admin response에 노출하지 않는다.
