---
title: FormDock Response Domain Model
status: draft
version: 0.2
last_updated: 2026-08-20
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

SurveyResponse row creation, canonical payload hash/idempotency runtime과 Public Response submission은 Phase 3가 처음 소유한다. `answers`와 `answer_options` schema/runtime도 Phase 3에 남는다. Phase 2는 temporary row, `structure_locked` flag 또는 denormalized response count를 별도 authority로 만들지 않는다.

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
- MULTIPLE_CHOICE Option ID는 오름차순으로 정렬하고 duplicate ID는 거절한다.
- SCALE은 검증된 integer의 base-10 문자열, NUMBER는 `BigDecimal.stripTrailingZeros().toPlainString()` 결과로 정규화하고 zero는 `0`으로 고정한다.
- Text는 JSON decoding 후의 UTF-8 문자열을 Unicode normalization이나 trim 없이 그대로 보존한다.
- fixed field name/order로 UTF-8 JSON을 만들며 `clientSubmissionId`와 transport-only field는 hash에서 제외한다.

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
Survey validation
→ Question/Answer validation
→ idempotency
→ SurveyResponse
→ Answer
→ AnswerOption
→ COMMIT
```

실패 시 rollback.

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
