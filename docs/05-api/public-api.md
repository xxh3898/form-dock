---
title: Public Survey API
status: draft
version: 0.3
last_updated: 2026-08-21
---

# 1. Get Survey

```text
GET /api/public/surveys/{slug}
```

OPEN + not-deleted Survey만 respondent-safe definition과 `200 OK`를 반환한다.

| Survey state | Status |
|---|---|
| OPEN + not deleted | `200 OK` |
| DRAFT | `404 Not Found` |
| CLOSED | `404 Not Found` |
| deleted | `404 Not Found` |
| unknown slug | `404 Not Found` |

모든 unavailable 상태는 같은 safe public error shape를 사용해 Survey 존재와 lifecycle을 추가로 노출하지 않는다.

Canonical response DTO:

```json
{
  "slug": "project-research",
  "title": "Project Research",
  "description": "Optional introduction",
  "privacyNotice": null,
  "questions": [
    {
      "id": 10,
      "type": "SINGLE_CHOICE",
      "title": "Choose one",
      "description": null,
      "required": true,
      "position": 0,
      "scaleMin": null,
      "scaleMax": null,
      "scaleMinLabel": null,
      "scaleMaxLabel": null,
      "numberMin": null,
      "numberMax": null,
      "options": [
        { "id": 100, "label": "First", "position": 0 },
        { "id": 101, "label": "Second", "position": 1 }
      ]
    }
  ]
}
```

- `questions`와 Choice `options`는 `position` 오름차순이다.
- Question/Option IDs는 submission handle이므로 의도적으로 public이다.
- SCALE/NUMBER config는 해당 type에서만 값을 가지며 unused scalar는 `null`, non-Choice `options`는 `[]`다.
- NUMBER bound는 exponent 없는 decimal string 또는 `null`이다.
- internal Survey ID, owner/User identity, Admin timestamp/metadata, `responseCount`, `structureLocked`, password/session/auth data는 포함하지 않는다.
- V1 Public Survey에 cache, ETag 또는 version precondition contract를 추가하지 않는다.

# 2. Submit Response

```text
POST /api/public/surveys/{slug}/responses
Content-Type: application/json
```

Raw request body는 최대 1 MiB(1,048,576 bytes)다.

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
- unknown/foreign/duplicate Question ID를 거절한다.
- required Question은 valid Answer 하나를 요구하고 optional unanswered Question은 answers array에서 생략한다.
- Text는 blank-only일 수 없고 SHORT_TEXT 500, LONG_TEXT 5000 Unicode code point 제한을 따른다. Accepted text를 trim/Unicode normalize하지 않는다.
- SINGLE_CHOICE `optionIds`는 정확히 1개, MULTIPLE_CHOICE는 1개 이상의 distinct owned ID다.
- SCALE `numericValue`는 configured inclusive range 안의 base-10 integer string이다.
- NUMBER `numericValue`는 exponent 없는 plain decimal string이며 `NUMERIC(19,4)` precision/scale과 configured bounds를 만족해야 한다.
- duplicate/foreign Option ID, empty `optionIds`, unused value field는 invalid payload다.
- client는 `payloadHash`를 제공하지 않으며 server validation이 final authority다.

# 3. Canonicalization and Transaction

Server는 validated semantic answers를 `questionId` ascending으로 정렬하고 MULTIPLE_CHOICE Option ID를 distinct 검증 후 ascending으로 정렬한다. SCALE은 base-10 integer string, NUMBER는 `BigDecimal.stripTrailingZeros().toPlainString()`과 zero `0`, text는 decoded exact string을 사용한다. Fixed field/order compact JSON을 UTF-8로 직렬화한 뒤 SHA-256 lowercase hex를 계산한다. `clientSubmissionId`는 canonical JSON과 hash에서 제외한다.

Admitted request는 ADR-0004와 같은 Survey lock을 사용한다.

```text
resolve Survey identity without unavailable-state disclosure
→ BEGIN TX
→ Survey PESSIMISTIC_WRITE
→ re-read deleted/lifecycle/ordered Question+Option state
→ unknown/deleted/DRAFT: 404
→ existing (survey_id, clientSubmissionId) lookup
   → request를 locked structure 기준으로 canonicalize
   → same hash: 200 replayed=true
   → different hash: 409 RESPONSE_DUPLICATE_CONFLICT
→ no existing Response: require OPEN, otherwise 409 SURVEY_NOT_OPEN
→ full required/type/ownership validation
→ SurveyResponse + Answer + AnswerOption atomic insert
→ COMMIT
→ 201 replayed=false
```

Deleted Survey는 known `clientSubmissionId` replay도 404다. CLOSED Survey는 existing same replay 200, existing conflict 409, new identity 409다. Unique constraint race는 canonical row를 다시 읽어 200/409로 수렴하고 500으로 노출하지 않는다. Validation/persistence failure와 bounded lock timeout/deadlock은 aggregate 전체를 rollback하며 timeout/deadlock은 `503 TEMPORARILY_UNAVAILABLE`다.

# 4. Success

최초 canonical Response 생성:

```text
201 Created
```

동일 `clientSubmissionId` + 동일 canonical payload replay:

```text
200 OK
```

First create body:

```json
{
  "responseId": 9001,
  "submittedAt": "2026-08-21T00:00:00Z",
  "replayed": false
}
```

Replay는 같은 `responseId`와 `submittedAt`, `replayed: true`를 반환한다. 동일 ID + 다른 payload는 `409 RESPONSE_DUPLICATE_CONFLICT`다.

Respondent Response GET/edit/delete endpoint는 제공하지 않는다.

# 5. Errors

| Status / code | Contract |
|---|---|
| `400 RESPONSE_INVALID` or `VALIDATION_FAILED` | malformed 또는 semantic Answer violation |
| `404 SURVEY_NOT_FOUND` | unknown, DRAFT, deleted submit 또는 모든 unavailable GET state |
| `409 SURVEY_NOT_OPEN` | CLOSED Survey의 new `clientSubmissionId` |
| `409 RESPONSE_DUPLICATE_CONFLICT` | same identity, different canonical payload |
| `413 RESPONSE_PAYLOAD_TOO_LARGE` | raw request body 1 MiB(1,048,576 bytes) 초과 |
| `415 Unsupported Media Type` | submit Content-Type이 `application/json` 아님 |
| `429 RATE_LIMITED` | ephemeral application guard가 request 거절 |
| `503 TEMPORARILY_UNAVAILABLE` | bounded lock/dependency failure; same identity retry 가능 |

`CLOSED`는 reopen 가능하므로 `410 Gone`을 사용하지 않는다.

# 6. Security and Rate Limit

- Creator auth가 필요하지 않다.
- exact `POST /api/public/surveys/{slug}/responses`만 CSRF 검증에서 제외하고 `/api/public/**` broad exemption은 사용하지 않는다.
- Creator session을 authorization 또는 data authority로 사용하지 않는다.
- same-origin Web `/api` deployment만 지원하며 credentialed CORS를 허용하지 않는다.
- request body limit/rate guard를 통과하기 전 Response write를 시작하지 않는다.
- V1 rate limit은 bounded ephemeral in-memory state이며 DB-backed IP/token, cookie와 Web Storage respondent identity를 만들지 않는다.
- threshold/window는 configuration-driven setting이고 Product data로 persist하지 않는다.
- Production proxy-trust gate 전에는 `X-Forwarded-For`, `CF-Connecting-IP` 또는 다른 proxy header를 신뢰하지 않는다.
- `429`는 idempotency/replay evaluation보다 먼저 반환될 수 있다. Request가 guard에 admitted된 뒤에는 Section 3 replay semantics를 적용한다.
- arbitrary Response read와 persistent respondent token은 없다.

# 7. Phase Boundary

Phase 3-A exact anonymous GET과 Phase 3-B V6/data/canonicalization foundation은 `dev`에 통합됐다. 현재 Phase 3-C tree는 이 문서의 exact Public POST, security/transport guard, lifecycle/replay mapping과 atomic persistence를 구현하며 user merge와 latest `dev` validation 전까지 통합 완료가 아니다. `/s/:slug` frontend는 3-D이며 Results/CSV와 Production은 Phase 3 범위가 아니다.
