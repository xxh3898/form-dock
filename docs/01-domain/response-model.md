---
title: FormDock Response Domain Model
status: draft
version: 0.3
last_updated: 2026-08-21
---

# 1. Aggregate

```text
SurveyResponse
└─ Answer
   └─ AnswerOption
```

V1 Response Draft는 없다.

# 2. SurveyResponse

```text
id
surveyId
clientSubmissionId
payloadHash
submittedAt
```

## 2.1 Cross-Phase Schema Sequencing

[ADR-0006](../08-decisions/adr-0006-response-schema-sequencing-for-structure-lock.md)에 따라 Phase 2는 first-Response structure lock의 canonical existence authority로 final `survey_responses` table을 schema-only로 먼저 생성할 수 있다. Phase 2가 허용받는 동작은 Survey pessimistic write lock 안에서 canonical row existence를 조회하는 것뿐이다.

SurveyResponse row creation, canonical payload hash/idempotency runtime과 Public Response submission은 Phase 3가 처음 소유한다. Phase 3 Entry는 이 contract만 승인하며 runtime은 별도 3-A→3-D Issue/PR에서 직렬 구현한다. 기존 V1~V5 migration은 immutable하고, Phase 3-B current tree는 `V6__create_answers_and_answer_options.sql`로 `answers`와 `answer_options`만 추가한다. Phase 2/3 모두 temporary row, `structure_locked` flag 또는 denormalized response count를 별도 authority로 만들지 않는다.

Phase 3-B persistence primitive는 caller가 시작한 transaction을 필수로 요구하고 자체 transaction이나 partial commit을 만들지 않는다. Exact `(survey_id, client_submission_id)` unique conflict만 canonical row 재조회와 hash 비교로 수렴하며 unrelated FK/CHECK violation은 duplicate로 오인하지 않고 그대로 실패시킨다. Survey lock, lifecycle/Answer semantic validation, Public HTTP 201/200/409 mapping은 Phase 3-C가 같은 aggregate transaction에서 결합한다.

# 3. Idempotency

```text
UNIQUE(survey_id, client_submission_id)
```

- 첫 request: create
- same id + same payload: canonical Response로 수렴
- same id + different payload: reject

`payloadHash`는 client가 제공하지 않으며 서버가 semantic payload를 canonical JSON으로 만든 뒤 SHA-256 lowercase hex로 계산한다.

Canonicalization contract:

- Answer는 `questionId` 오름차순으로 정렬한다.
- optional unanswered Question은 제외한다.
- 각 Answer는 Question type에 맞는 하나의 value representation만 포함한다.
- duplicate Question/Option ID는 validation에서 거절한 뒤 MULTIPLE_CHOICE Option ID를 오름차순으로 정렬한다.
- SCALE은 검증된 integer의 base-10 문자열, NUMBER는 `BigDecimal.stripTrailingZeros().toPlainString()` 결과로 정규화하고 zero는 `0`으로 고정한다.
- Text는 JSON decoding 후의 UTF-8 문자열을 Unicode normalization이나 trim 없이 그대로 보존한다.
- top-level canonical object는 `answers` field 하나만 가지며 Answer object는 `questionId` 뒤에 정확히 하나의 `textValue`, `numericValue`, `optionIds` field를 둔다.
- compact JSON은 위 field order와 array order를 고정하고 insignificant whitespace/BOM 없이 UTF-8 bytes로 직렬화한다. JSON string escaping은 decoded text value를 바꾸지 않는다.
- `clientSubmissionId`, `payloadHash`와 다른 transport-only field는 canonical JSON과 hash에서 제외한다.
- 서버가 위 bytes의 SHA-256 lowercase hex를 계산하며 client는 `payloadHash`를 제공하지 않는다.

Representation별 canonical shape:

```json
{"answers":[{"questionId":1,"textValue":"exact decoded text"},{"questionId":2,"optionIds":[10,11]},{"questionId":3,"numericValue":"7.5"}]}
```

HTTP behavior:

- 첫 canonical Response 생성: `201 Created`
- same id + same hash: `200 OK`와 기존 canonical Response
- same id + different hash: `409 Conflict` / `RESPONSE_DUPLICATE_CONFLICT`

동시 duplicate는 unique constraint가 하나의 row만 허용한다. unique race에서 진 request는 canonical row를 다시 조회해 같은 hash 비교 규칙으로 200 또는 409를 반환하며 500으로 노출하지 않는다.

이미 존재하는 ID의 replay 판단은 신규 submission의 OPEN 상태 검사보다 먼저 수행한다. 따라서 Survey가 이후 CLOSED되어도 동일 replay는 200으로 복구되며, 새 ID는 `SURVEY_NOT_OPEN`으로 거절된다. Deleted Survey는 Public API에서 항상 unavailable이므로 replay도 404다.

# 4. No Persistent Respondent Token

V1은 동일 브라우저 장기 추적용 token을 저장하지 않는다.

악의적 다중응답 방지는 Non-goal이다.

# 5. Atomic Submission

```text
transport/security admission
→ resolve Survey identity without unavailable-state disclosure
→ BEGIN TX
→ Survey PESSIMISTIC_WRITE
→ re-read deleted/lifecycle/Question/Option state
→ deleted/unknown/DRAFT: 404
→ canonicalize valid existing-id request against locked structure
→ existing same hash: 200 replayed=true
→ existing different hash: 409 RESPONSE_DUPLICATE_CONFLICT
→ no existing Response: require OPEN, otherwise 409 SURVEY_NOT_OPEN
→ validate required/type/ownership semantics
→ SurveyResponse
→ Answer
→ AnswerOption
→ COMMIT
→ 201 replayed=false
```

전체 canonical aggregate는 한 transaction이다. validation/persistence 실패 또는 bounded lock timeout/deadlock은 모든 write를 rollback하며 timeout/deadlock은 `503 TEMPORARILY_UNAVAILABLE`로 반환한다. unique constraint race는 `(survey_id, client_submission_id)` canonical row를 다시 읽어 같은 hash면 200, 다른 hash면 409로 수렴하고 500이나 partial aggregate를 남기지 않는다.

ADR-0004 lock order는 `Survey → Question/Option → SurveyResponse/Answer/AnswerOption`이다. Mutation-first에서는 submit이 기다린 뒤 최신 structure로 재검증하고, submit-first에서는 structure mutation이 기다린 뒤 real V5 Response existence를 보고 `SURVEY_STRUCTURE_LOCKED`를 반환한다. 두 ordering 모두 stale structure Response와 structural mutation이 함께 commit될 수 없다.

# 6. Answer

```text
id
responseId
questionId
textValue
numericValue
createdAt
```

```text
UNIQUE(response_id, question_id)
```

Optional question unanswered는 Answer row 자체를 만들지 않는다.

# 7. Value Mapping

| Type | text | numeric | option |
|---|---|---|---|
| SHORT_TEXT | YES | NO | NO |
| LONG_TEXT | YES | NO | NO |
| SINGLE_CHOICE | NO | NO | YES |
| MULTIPLE_CHOICE | NO | NO | YES |
| SCALE | NO | YES | NO |
| NUMBER | NO | YES | NO |

# 8. Mutation

Respondent는 제출 후 수정/삭제 불가.

Creator도 V1에서 원문 수정 불가.

# 9. Invariants

- OPEN Survey만 신규 Response
- transaction atomic
- Question당 최대 1 Answer
- Option ownership 검증
- idempotent retry
- persistent tracking identifier 없음
- Result/Response read, edit/delete와 CSV 없음
