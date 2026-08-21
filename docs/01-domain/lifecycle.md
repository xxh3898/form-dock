---
title: FormDock Lifecycle
status: draft
version: 0.3
last_updated: 2026-08-20
---

# 1. Survey Lifecycle

```text
DRAFT → OPEN ↔ CLOSED
```

Soft delete는 status가 아니라 `deletedAt`이다.

# 2. DRAFT → OPEN

Preconditions:

- not deleted
- title/slug valid
- at least one Question
- all Questions valid
- Choice >= 2 options
- type configuration valid

첫 DRAFT→OPEN에서 `openedAt = now`를 한 번만 설정하고 `closedAt`은 `null`로 유지한다. 이미 OPEN인 Survey에 open transition을 다시 요청하면 `409 SURVEY_STATE_CONFLICT`다.

# 3. OPEN → CLOSED

신규 Response 중단.

기존 결과는 유지한다.

Transition은 `closedAt = now`를 설정한다. DRAFT/CLOSED에 close transition을 요청하면 `409 SURVEY_STATE_CONFLICT`다.

# 4. CLOSED → OPEN

reopen 허용.

기존 Response가 있다면 structure lock 유지.

Reopen은 original `openedAt`을 보존하고 current OPEN state가 명확하도록 `closedAt = null`로 clear한다. 이미 OPEN인 Survey에 reopen을 요청하면 `409 SURVEY_STATE_CONFLICT`다.

# 5. Structure Lock

```text
OPEN + 0 response = mutable
OPEN + >=1 response = locked
```

Authority는 canonical Response 존재 여부.

[ADR-0006](../08-decisions/adr-0006-response-schema-sequencing-for-structure-lock.md)에 따라 Phase 2 structure mutation은 Survey row lock 안에서 final `survey_responses` table의 canonical row existence를 실제로 조회한다. Phase 2는 이 table에 row를 생성하지 않으며 Public Response runtime과 최초 canonical insert는 Phase 3가 소유한다. 따라서 Phase가 바뀌어도 structure-lock authority와 mutation transaction semantics는 바뀌지 않는다.

# 6. Delete

```text
OPEN → CLOSED → DELETE
```

- DRAFT/CLOSED는 soft delete 가능.
- OPEN direct delete는 domain error이며 먼저 CLOSED로 전환해야 한다.
- delete 후 public/admin 일반 조회와 result access는 V1에서 제공하지 않는다.
- restore와 automatic Response purge는 V1에 없다.

Lifecycle transition은 silent no-op으로 처리하지 않는다. Unknown, unowned 또는 deleted Survey는 owner-scoped `404 SURVEY_NOT_FOUND`, invalid state는 stable `409` code를 사용한다.

# 7. Response Lifecycle

V1은 단일 canonical `SUBMITTED` 상태만 가진다.

`DRAFT`, `IN_PROGRESS`, `REVISED` 없음.

# 8. Retry

```text
POST succeeds, response lost
→ same clientSubmissionId + same payload
→ canonical Response 반환
```

# 9. Race Conditions

- 신규 submit 순간 server Survey status가 authority
- submit 순간 deletedAt 확인
- Response submit과 structure mutation은 동일 Survey row의 pessimistic write lock을 transaction 시작부에서 획득한다.
- 먼저 lock을 얻은 transaction이 commit된 뒤 후속 transaction은 status, deletedAt, canonical Response 존재 여부를 다시 검증한다.

# 10. Invariants

- DRAFT/CLOSED 신규 Response 없음
- client state보다 server state 우선
- first Response 이후 structure immutable
- partial persisted Response 없음
