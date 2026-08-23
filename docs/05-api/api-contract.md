---
title: Admin API Contract
status: draft
version: 1.2
last_updated: 2026-08-23
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

Phase 1 PR B는 위 네 endpoint와 stable auth/CSRF error body를 Spring REST Docs integration test로 고정한다. Session/User repository의 request-time data access failure는 내부 DB message를 노출하지 않는 `503 TEMPORARILY_UNAVAILABLE`로 처리한다. Malformed email과 unknown email도 credential failure에서 동일한 dummy password verification 경로를 사용한다.

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

Phase 2-C runtime은 위 Survey endpoint와 Section 4의 Question mutation endpoint를 구현한다. 기존 Survey wire shape를 유지하면서 Question/Option은 V4 persistence, Response count/lock field는 V5 real COUNT/EXISTS를 authority로 사용한다. Phase 2-C 변경은 `dev` merge/validation 전까지 release 또는 Phase 2-D authorization evidence가 아니다.

## 3.1 Ownership and Visibility

- list/detail과 모든 mutation은 authenticated Creator가 소유한 non-deleted Survey만 대상으로 한다.
- 다른 Creator의 Survey, soft-deleted Survey와 그 Question은 존재 여부를 숨기기 위해 `404 SURVEY_NOT_FOUND` 또는 `QUESTION_NOT_FOUND`로 처리한다.
- 모든 unsafe Admin endpoint는 existing session/CSRF contract를 따른다.
- Phase 2는 reserved `slug` identity를 Admin DTO에 제공하지만 functional/clickable `/s/{slug}` route를 제공하지 않는다.

## 3.2 Survey List

`GET /api/surveys`는 pagination 없는 V1 direct JSON array와 `200 OK`를 반환한다. 각 item은 다음 필드만 가진다.

```json
[
  {
    "id": 1,
    "title": "Project Research",
    "status": "DRAFT",
    "slug": "project-research",
    "responseCount": 0,
    "updatedAt": "2026-08-20T00:00:00Z"
  }
]
```

`responseCount`는 저장 column이 아니다. Phase 2-A에서는 Response capability 부재로 논리적으로 `0`이며, Phase 2-B에서 V5가 추가된 뒤에는 canonical `survey_responses` rows의 derived `COUNT`다. Phase 2 Product runtime에는 SurveyResponse writer가 없으므로 두 단계 모두 정상 Product flow에서는 `0`이다.

## 3.3 Canonical Builder Detail

`GET /api/surveys/{surveyId}`는 `200 OK`와 owner-visible Builder 전체 상태를 반환하는 유일한 canonical read endpoint다. 별도 Question GET endpoint를 만들지 않는다.

```json
{
  "id": 1,
  "title": "Project Research",
  "description": "Optional introduction",
  "slug": "project-research",
  "privacyNotice": null,
  "status": "DRAFT",
  "openedAt": null,
  "closedAt": null,
  "createdAt": "2026-08-20T00:00:00Z",
  "updatedAt": "2026-08-20T00:00:00Z",
  "responseCount": 0,
  "structureLocked": false,
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

- `questions`와 `options`는 `position` 오름차순이다.
- position은 server-owned zero-based gapless integer다.
- Phase 2-B 이후 `structureLocked`는 canonical Response existence에서, `responseCount`는 canonical Response count에서 파생하며 둘 다 별도 persistence authority가 아니다.
- `numberMin`/`numberMax`는 precision loss를 피하기 위한 exponent 없는 decimal string 또는 `null`이다.
- Question type에서 사용하지 않는 scalar configuration은 response에서 `null`, non-Choice `options`는 빈 array다.
- timestamp는 UTC ISO-8601 `Instant` string이다.

### Phase 2-A → Phase 2-B Authority Transition

Survey list/detail의 wire field와 JSON shape는 Phase 2-A부터 최종 contract를 유지한다. 위 populated `questions` example은 V4가 존재하는 Phase 2-B 이후 shape를 보여준다.

| Phase | `questions` | `responseCount` | `structureLocked` |
|---|---|---|---|
| Phase 2-A — V3 only | `[]` | `0` | `false` |
| Phase 2-B 이후 — V4/V5 | real ordered `questions`/`question_options` persistence | real `survey_responses` `COUNT` | real `survey_responses` `EXISTS` |

Phase 2-A의 값은 temporary database field, repository authority, query result 또는 mock/stub이 아니다. Question/Response capability가 아직 없다는 Phase boundary가 값을 논리적으로 보장하며, Phase 2-A는 Question/Response repository, query, constant-false adapter 또는 stub을 만들지 않는다.

Phase 2-B에서 V4/V5가 추가되면 wire DTO를 바꾸지 않고 내부 authority만 real persistence로 전환한다. Question structure mutation API는 Phase 2-C에서 처음 구현되며 그 첫 path부터 ADR-0004/0006 transaction 안의 real V5 `survey_responses EXISTS`를 사용한다. Question mutation에서 constant false, mock adapter 또는 stub은 허용하지 않는다.

## 3.4 Create and Update

`POST /api/surveys` request:

```json
{
  "title": "Project Research",
  "description": null,
  "privacyNotice": null,
  "slug": null
}
```

- `title`은 trim 후 1..200 Unicode code points이며 canonical stored value도 trimmed title이다.
- `description`과 `privacyNotice`는 optional string 또는 `null`이며 각각 최대 5000 Unicode code points다. Omitted field는 `null`과 같다.
- `slug`가 omitted/`null`이면 title에서 canonical allocator로 생성한다. 명시된 slug도 canonical regex/length/global uniqueness를 만족해야 한다.
- 성공하면 `201 Created`와 canonical Survey detail을 반환한다.

`PATCH /api/surveys/{surveyId}`는 다음 metadata field의 partial update만 허용한다.

```json
{
  "title": "Updated title",
  "description": null,
  "privacyNotice": "Updated notice",
  "slug": "updated-slug"
}
```

- 적어도 한 field가 있어야 하며 unknown field와 `status`는 `400 VALIDATION_FAILED`다.
- `title`과 `slug`는 present일 때 `null`일 수 없다. `description`/`privacyNotice: null`은 값을 clear한다.
- slug PATCH는 `status == DRAFT && openedAt == null`일 때만 허용한다. 첫 OPEN 이후 CLOSED/reopen 또는 Response 0건이어도 `409 SURVEY_SLUG_IMMUTABLE`이다.
- status 변경은 `/open`과 `/close`만 사용한다.
- 성공하면 `200 OK`와 updated Survey detail을 반환한다.

## 3.5 Lifecycle, Delete, and Duplicate

| Endpoint | Valid state | Success | State failure |
|---|---|---|---|
| `POST .../open` | DRAFT or CLOSED | `200`, Survey detail | already OPEN: `409 SURVEY_STATE_CONFLICT`; invalid structure: `409 SURVEY_INVALID_STRUCTURE` |
| `POST .../close` | OPEN | `200`, Survey detail | DRAFT/CLOSED: `409 SURVEY_STATE_CONFLICT` |
| `DELETE /api/surveys/{surveyId}` | DRAFT or CLOSED | `204 No Content` | OPEN: `409 SURVEY_DELETE_REQUIRES_CLOSED` |

Open/close request body는 없다.

- first DRAFT→OPEN은 `openedAt`을 한 번 설정하고 `closedAt`을 `null`로 유지한다.
- OPEN→CLOSED는 `closedAt = now`로 설정한다.
- CLOSED→OPEN은 original `openedAt`을 보존하고 `closedAt = null`로 clear한다.
- invalid transition은 silent no-op이 아니다.
- soft delete 후 restore endpoint는 V1에 없다.

`POST /api/surveys/{surveyId}/duplicate`는 request body 없이 `201 Created`와 새 Survey detail을 반환한다.

```text
source/new owner       current authenticated Creator
new status             DRAFT
new openedAt           null
new closedAt           null
new deletedAt          null
metadata               title/description/privacyNotice deep copy
slug                   canonical allocator의 fresh globally unique slug
questions/options      position과 type config를 보존한 deep copy
responses              not copied
responseCount          0
structureLocked        false
```

Canonical Responses가 있는 source도 duplicate할 수 있다. Deleted/unowned source는 `404 SURVEY_NOT_FOUND`다.

# 4. Questions

```text
POST   /api/surveys/{surveyId}/questions
PATCH  /api/surveys/{surveyId}/questions/{questionId}
DELETE /api/surveys/{surveyId}/questions/{questionId}
POST   /api/surveys/{surveyId}/questions/reorder
```

## 4.1 Create and Update Payload

Question create/update는 server-owned Question `id`와 `position`을 제외한 complete semantic state를 전달한다. Create의 Option에는 `label`만, update의 Option에는 existing `id` 또는 새 Option을 뜻하는 omitted `id`와 `label`을 전달한다.

```json
{
  "type": "SINGLE_CHOICE",
  "title": "Choose one",
  "description": null,
  "required": true,
  "scaleMin": null,
  "scaleMax": null,
  "scaleMinLabel": null,
  "scaleMaxLabel": null,
  "numberMin": null,
  "numberMax": null,
  "options": [
    { "label": "First" },
    { "label": "Second" }
  ]
}
```

PATCH에서 existing Option identity를 보존하면서 새 Option을 추가하는 `options` fragment는 다음과 같다.

```json
{
  "options": [
    { "id": 100, "label": "Renamed first" },
    { "label": "New second" }
  ]
}
```

- `title`은 trim 후 1..500 Unicode code points, Option `label`은 trim 후 1..500 code points다.
- `description`은 optional/`null`, 최대 2000 code points다.
- `type`, `title`, `required`, 모든 type-specific field와 `options` array를 항상 포함한다.
- Choice는 ordered `options` 2개 이상을 요구한다. Non-Choice는 빈 `options` array를 요구한다.
- SCALE은 `1 <= scaleMin < scaleMax <= 10`인 필수 integer와 최대 100 code points의 optional labels만 사용한다.
- NUMBER는 PostgreSQL `NUMERIC(19,4)` 범위의 `numberMin`/`numberMax`를 exponent 없는 decimal string 또는 `null`로 사용하며 둘 다 있으면 `numberMin <= numberMax`여야 한다.
- 현재 type에서 사용하지 않는 scalar configuration은 반드시 `null`이며 invalid combination은 `400 QUESTION_INVALID_CONFIGURATION`이다.
- `POST .../questions`는 마지막 position에 append하고 `201 Created`와 updated canonical Survey detail을 반환한다.
- `PATCH .../questions/{questionId}`는 Question semantic state와 ordered Option list 전체를 transaction에서 교체하고 `200 OK`와 updated Survey detail을 반환한다. Current Question의 existing Option은 `id`로 identity를 보존하고, `id`가 없으면 새 identity를 할당하며, list에서 빠진 existing Option은 삭제한다. Duplicate/foreign Option ID는 `400 VALIDATION_FAILED`다.
- `DELETE .../questions/{questionId}`는 성공 시 `204 No Content`이고 남은 position을 gapless하게 정규화한다.
- V1은 standalone Option mutation/read endpoint를 제공하지 않는다.

## 4.2 Reorder

```json
{
  "questionIds": [12, 10, 11]
}
```

- list는 current non-deleted Survey의 Question ID complete set을 각각 정확히 한 번 포함해야 한다.
- missing, duplicate 또는 foreign Question ID는 `400 VALIDATION_FAILED`다.
- server는 list order대로 position을 `0..n-1`로 정규화한다.
- 성공하면 `200 OK`와 updated canonical Survey detail을 반환한다.

## 4.3 Mutation Authority

모든 Question/Option create/update/delete/reorder는 ADR-0004/0006 transaction을 따른다.

```text
BEGIN TX
→ Survey row PESSIMISTIC_WRITE
→ owner, current status, deletedAt 재검증
→ real survey_responses canonical existence 조회
→ 존재하면 409 SURVEY_STRUCTURE_LOCKED
→ structure mutation
→ COMMIT
```

Phase 2 Product runtime은 `survey_responses` COUNT/EXISTS만 읽고 row를 insert하지 않는다. Lock timeout/deadlock은 `503 TEMPORARILY_UNAVAILABLE`다.

DRAFT/OPEN/CLOSED status 자체는 structure mutation을 막지 않는다. Canonical Response가 아직 없고 owner/deleted-state validation을 통과하면 모든 status에서 Question structure를 변경할 수 있으며, first canonical Response부터만 immutable하다.

## 4.4 Stale Request Boundary

V1 mutation authority는 owner, current state, `deletedAt`, `openedAt`과 canonical Response existence를 authoritative transaction 안에서 다시 확인하는 것이다. `updatedAt`은 response metadata일 뿐 precondition token이 아니다.

`stale request`라는 UX 표현은 general field-level lost-update detection을 뜻하지 않는다. Phase 2는 `@Version`, ETag, `If-Match` 또는 request revision을 암묵적으로 요구하지 않으며 current-state conflict를 documented `404`/`409`/`503`으로 반환한다.

따라서 서로 유효한 concurrent metadata PATCH는 V1에서 last-commit-wins일 수 있다. 이는 structure-lock invariant와 별개이며 향후 실제 충돌 문제가 확인되면 dedicated optimistic-concurrency decision으로 재검토한다.

## 4.5 Success Status Summary

| Endpoint | Success |
|---|---|
| `GET /api/surveys` | `200 OK` |
| `POST /api/surveys` | `201 Created` |
| `GET /api/surveys/{surveyId}` | `200 OK` |
| `PATCH /api/surveys/{surveyId}` | `200 OK` |
| `DELETE /api/surveys/{surveyId}` | `204 No Content` |
| `POST /api/surveys/{surveyId}/duplicate` | `201 Created` |
| `POST /api/surveys/{surveyId}/open` | `200 OK` |
| `POST /api/surveys/{surveyId}/close` | `200 OK` |
| `POST .../questions` | `201 Created` |
| `PATCH .../questions/{questionId}` | `200 OK` |
| `DELETE .../questions/{questionId}` | `204 No Content` |
| `POST .../questions/reorder` | `200 OK` |

# 5. Responses

```text
GET /api/surveys/{surveyId}/responses
GET /api/surveys/{surveyId}/responses/{responseId}
GET /api/surveys/{surveyId}/responses/summary
GET /api/surveys/{surveyId}/responses/export.csv
```

Phase 4의 네 endpoint는 Creator session이 필요한 owner-scoped Admin GET이다. Caller가 소유한 non-deleted Survey를 먼저 resolve하며 unknown/unowned/soft-deleted Survey는 모두 `404 SURVEY_NOT_FOUND`다. DRAFT/OPEN/CLOSED lifecycle 자체는 Results read를 막지 않는다.

새 CSRF exemption, credentialed cross-origin CORS, Public Response read endpoint를 만들지 않는다. Result DTO와 CSV는 `clientSubmissionId`, `payloadHash`, owner/session metadata를 포함하지 않는다.

## 5.1 Paginated Response List

```text
GET /api/surveys/{surveyId}/responses?page={page}&size={size}
```

```text
page default/minimum  0 / 0
size default/range    50 / 1..100
sort                  submittedAt DESC, responseId DESC
```

Invalid page/size는 `400 VALIDATION_FAILED`다. Existing Survey의 범위를 넘는 page는 `200 OK`와 empty `items`를 반환한다. User-selected sort/filter/search는 V1 범위 밖이다.

```json
{
  "items": [
    {
      "responseId": 9001,
      "submittedAt": "2026-08-23T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

`totalPages = ceil(totalElements / size)`이며 total이 0이면 `0`이다.

Phase 4-A list 구현은 owner/non-deleted Survey를 먼저 resolve하고 raw `page`/`size`를 검증한 뒤 count와 bounded SQL page를 읽는다. Offset arithmetic이 안전한 범위를 넘으면 `400 VALIDATION_FAILED`다.

## 5.2 Individual Response Detail

```text
GET /api/surveys/{surveyId}/responses/{responseId}
```

Survey를 먼저 owner scope로 resolve한다. Response가 없거나 exact owned Survey에 속하지 않으면 존재 위치를 숨기고 `404 RESPONSE_NOT_FOUND`를 반환한다.

```json
{
  "responseId": 9001,
  "submittedAt": "2026-08-23T00:00:00Z",
  "questions": [
    {
      "questionId": 10,
      "type": "SHORT_TEXT",
      "title": "의견을 적어 주세요",
      "description": null,
      "required": false,
      "position": 0,
      "answer": {
        "textValue": "원문 답변",
        "numericValue": null,
        "options": []
      }
    },
    {
      "questionId": 11,
      "type": "NUMBER",
      "title": "값",
      "description": null,
      "required": false,
      "position": 1,
      "answer": null
    }
  ]
}
```

- current Survey Question 전체를 `position ASC`로 제공한다.
- optional unanswered Question도 포함하고 `answer=null`로 표현한다.
- text는 stored exact decoded text를 반환한다.
- SCALE/NUMBER `numericValue`는 exponent 없는 canonical plain decimal string이며 zero는 `"0"`이다.
- Choice는 selected Option만 `{ id, label, position }`으로 `position ASC`에 제공한다.
- non-Choice `options`는 빈 array다.
- JPA Entity를 직접 직렬화하지 않는다.

Section 5.1과 5.2 Phase 4-A list/detail 및 Section 5.3 summary는 `dev`에 통합됐다. Section 5.4 CSV는 Phase 4-C runtime으로 구현됐지만 아직 `dev` 통합 전이다.

## 5.3 Result Summary

```text
GET /api/surveys/{surveyId}/responses/summary
```

Owned non-deleted Survey는 Response 0건이어도 `200 OK`다.

```json
{
  "surveyId": 123,
  "status": "OPEN",
  "totalResponses": 10,
  "lastSubmittedAt": "2026-08-23T00:00:00Z",
  "questionCount": 4,
  "questions": []
}
```

Response 0건이면 `totalResponses=0`, `lastSubmittedAt=null`이다. Question summary는 `questionId`, `type`, `title`, `position`, `answeredCount`를 공통으로 가지며 `position ASC`다.

### 5.3.1 Choice

SINGLE_CHOICE/MULTIPLE_CHOICE는 Option을 `position ASC`로 제공한다.

```text
optionId
label
position
count
percentage
```

Percentage denominator는 해당 Question의 `answeredCount`다. 값은 scale 2 `HALF_UP` decimal string이며 no answers이면 `"0.00"`이다. MULTIPLE_CHOICE는 한 Response가 여러 Option을 선택할 수 있어 합계가 100%를 초과할 수 있다.

### 5.3.2 Scale

SCALE summary는 `average`와 configured `scaleMin..scaleMax`의 모든 integer bucket을 가진 `distribution`을 제공한다. 각 bucket은 `value`, `count`, `percentage`를 가진다.

- zero-count bucket도 포함한다.
- percentage denominator는 `answeredCount`, scale은 2, rounding은 `HALF_UP`이다.
- average도 scale 2 `HALF_UP` plain decimal string이다.
- no answers이면 `average=null`, 모든 bucket count는 0, percentage는 `"0.00"`이다.

### 5.3.3 Text and Number

SHORT_TEXT/LONG_TEXT/NUMBER summary는 `answeredCount`만 제공한다. Unbounded raw value array와 NUMBER average를 포함하지 않는다. Raw Text/Number는 paginated list와 individual detail로 조회한다.

Phase 4-B runtime은 owner/non-deleted Survey를 aggregate 전에 resolve한다. Overview, Question Answer count, Choice count, Scale average/distribution은 exact Survey scope의 고정 grouped SQL로 읽으며 zero-count Option/bucket만 current structure와 결합해 채운다. Response/Question/Option별 query loop와 Product write는 없다.

## 5.4 CSV Export

```text
GET /api/surveys/{surveyId}/responses/export.csv
```

```text
200 OK
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="formdock-survey-{surveyId}-responses.csv"
```

Response 0건도 canonical header-only CSV로 `200`을 반환한다.

- UTF-8 BOM bytes `EF BB BF`를 정확히 한 번 출력한다.
- RFC 4180 quoting/escaping과 CRLF(`\r\n`) record separator를 사용한다.
- row order는 `submitted_at ASC, response_id ASC`다.
- metadata column은 `response_id`, `submitted_at` 순서이며 timestamp는 UTC ISO-8601이다.
- 이후 current Question을 `position ASC`, MULTIPLE_CHOICE Option을 `position ASC`로 출력한다.
- single-value header는 `q_{questionId}: {questionTitle}`이다.
- MULTIPLE_CHOICE header는 `q_{questionId}_option_{optionId}: {questionTitle} / {optionLabel}`이고 cell은 `true` 또는 `false`다.
- optional unanswered는 empty field다.
- SHORT_TEXT/LONG_TEXT는 stored exact text, SINGLE_CHOICE는 `{optionId}: {optionLabel}`, SCALE/NUMBER는 canonical plain decimal이며 zero는 `0`이다.
- Dynamic string cell의 첫 non-whitespace 문자가 `=`, `+`, `-`, `@`이면 content 앞에 ASCII apostrophe(`'`)를 붙인 뒤 RFC 4180 escaping을 적용한다.
- 전체 CSV 문자열이나 전체 Answer graph를 무제한 materialize하지 않고 read-only transaction의 memory-bounded row/streaming generation을 사용한다.

Phase 4-C runtime은 owner/non-deleted Survey와 current Question/Option schema를 첫 CSV byte 전에 확인한다. PostgreSQL `REPEATABLE READ` read-only snapshot에서 exact `survey_id`의 forward-only JDBC cursor를 fetch size `256`으로 읽어 현재 Response 한 행만 구성하고, UTF-8 `OutputStream`에 BOM/header/row를 순차 기록한다. Per-Response/per-Question/per-Option query와 Product write는 없다. 구현은 완료됐지만 아직 `dev` 통합 전이다.

# 6. Authorization

모든 Admin Survey API는 owner check 필수.

다른 Creator 소유 resource도 caller에게는 `404 Not Found`로 처리해 존재 여부를 노출하지 않는다.

Phase 2 Admin Survey/Question runtime과 [Public Survey API](public-api.md)의 Phase 3 Public Survey/Response runtime은 complete + released다. Section 5 Creator Result/Response endpoint contract는 Phase 4-A→D serial implementation을 승인한다. Phase 4 Entry 자체는 runtime endpoint를 만들지 않으며 Response mutation, Public Response read, V7/schema 변경과 Production은 승인하지 않는다.

# 7. Documentation

Spring REST Docs로 구현된 contract를 검증한다.
