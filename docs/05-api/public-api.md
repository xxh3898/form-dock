---
title: Public Survey API
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Get Survey

```text
GET /api/public/surveys/{slug}
```

OPEN + not deleted Survey만 respondent definition 반환.

응답에는 내부 owner 정보, password, Admin metadata를 노출하지 않는다.

# 2. Submit Response

```text
POST /api/public/surveys/{slug}/responses
```

Request 후보:

```json
{
  "clientSubmissionId": "uuid",
  "answers": [
    {
      "questionId": 1,
      "textValue": "..."
    }
  ]
}
```

실제 polymorphic answer DTO shape는 구현 전에 확정한다.

# 3. Success

최초 생성은 `201 Created` 후보.

Idempotent replay 응답 status는 API contract 테스트와 함께 확정한다.

# 4. Errors

- 400 invalid payload
- 404 unavailable slug/not found policy
- 409 conflicting replay/structure conflict
- 410 closed survey 후보
- 429 abuse/rate limit

정확한 status 의미는 error contract에서 최종 고정한다.

# 5. Security

- no creator auth required
- strict body size
- rate limiting
- server validation
- no arbitrary Response reads
