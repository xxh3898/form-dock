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

| Survey state | Status |
|---|---|
| OPEN + not deleted | `200 OK` |
| DRAFT | `404 Not Found` |
| CLOSED | `404 Not Found` |
| deleted | `404 Not Found` |
| unknown slug | `404 Not Found` |

Unavailable 상태를 구분하지 않아 Survey 존재와 lifecycle을 추가로 노출하지 않는다.

# 2. Submit Response

```text
POST /api/public/surveys/{slug}/responses
```

`Content-Type: application/json`만 허용한다.

```json
{
  "clientSubmissionId": "550e8400-e29b-41d4-a716-446655440000",
  "answers": [
    {
      "questionId": 1,
      "textValue": "텍스트 응답"
    },
    {
      "questionId": 2,
      "optionIds": [10, 11]
    },
    {
      "questionId": 3,
      "numericValue": "7.5000"
    }
  ]
}
```

- SHORT_TEXT/LONG_TEXT: `textValue`
- SINGLE_CHOICE/MULTIPLE_CHOICE: `optionIds`
- SCALE/NUMBER: precision loss를 피하기 위한 decimal string `numericValue`
- Answer 하나에는 type에 맞는 value field 하나만 존재해야 한다.
- Optional unanswered Question은 answers array에서 생략한다.
- Text는 blank-only일 수 없고 SHORT_TEXT 500, LONG_TEXT 5000 Unicode code point 제한을 따른다.
- SINGLE_CHOICE `optionIds`는 정확히 1개, MULTIPLE_CHOICE는 1개 이상의 distinct ID다.
- SCALE `numericValue`는 범위 안의 base-10 integer string이다.
- NUMBER `numericValue`는 exponent 없는 plain decimal string이며 `NUMERIC(19,4)` precision/scale과 설정 범위를 만족해야 한다.
- 같은 Question이나 Option ID의 duplicate, empty `optionIds`, unused value field는 invalid payload다.

# 3. Success

최초 canonical Response 생성:

```text
201 Created
```

동일 `clientSubmissionId` + 동일 canonical payload replay:

```text
200 OK
```

두 경우 모두 같은 `responseId`, `submittedAt`을 반환한다. replay 응답은 `replayed: true`, 최초 응답은 `replayed: false`다.

동일 ID + 다른 payload는 `409 Conflict` / `RESPONSE_DUPLICATE_CONFLICT`다.

기존 ID replay를 신규 submission의 OPEN 상태 검사보다 먼저 판정한다. Survey가 CLOSED된 뒤에도 기존 ID + 동일 payload는 200, 기존 ID + 다른 payload는 409 conflict다. 새로운 ID의 CLOSED submit만 `SURVEY_NOT_OPEN`이다. Deleted Survey는 replay를 포함해 404다.

# 4. Errors

- `400 Bad Request`: malformed/invalid answer payload
- `404 Not Found`: unknown, DRAFT, deleted Survey
- `409 Conflict` / `SURVEY_NOT_OPEN`: submit 시점에 CLOSED인 새로운 ID
- `409 Conflict` / `RESPONSE_DUPLICATE_CONFLICT`: same ID, different payload
- `415 Unsupported Media Type`: non-JSON submit
- `429 Too Many Requests`: abuse/rate limit

`CLOSED`는 reopen 가능하므로 `410 Gone`을 사용하지 않는다.

# 5. Security

- no creator auth required
- exact Public Response POST만 CSRF 검증 제외
- Creator session을 authorization/data authority로 사용하지 않음
- same-origin Web `/api` deployment, cross-origin CORS 허용 없음
- strict body size
- rate limiting
- server validation
- no arbitrary Response reads
