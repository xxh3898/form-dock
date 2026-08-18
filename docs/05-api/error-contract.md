---
title: API Error Contract
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Goal

Frontend가 HTTP status 문자열 parsing 없이 안정적으로 오류를 처리할 수 있게 한다.

# 2. Shape

후보:

```json
{
  "code": "SURVEY_STRUCTURE_LOCKED",
  "message": "응답이 존재하는 설문 구조는 변경할 수 없습니다.",
  "fieldErrors": []
}
```

# 3. Candidate Codes

```text
AUTH_INVALID_CREDENTIALS
AUTH_REQUIRED
FORBIDDEN

SURVEY_NOT_FOUND
SURVEY_NOT_OPEN
SURVEY_STRUCTURE_LOCKED
SURVEY_SLUG_CONFLICT
SURVEY_INVALID_STRUCTURE

QUESTION_NOT_FOUND
QUESTION_INVALID_CONFIGURATION

RESPONSE_INVALID
RESPONSE_DUPLICATE_CONFLICT

RATE_LIMITED
```

# 4. Validation Errors

Field error에는 machine-readable field/path를 제공한다.

# 5. Internal Error

stack trace/internal DB message를 Public/Admin response에 노출하지 않는다.
