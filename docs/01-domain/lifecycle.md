---
title: FormDock Lifecycle
status: draft
version: 0.1
last_updated: 2026-08-18
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

# 3. OPEN → CLOSED

신규 Response 중단.

기존 결과는 유지한다.

# 4. CLOSED → OPEN

reopen 허용.

기존 Response가 있다면 structure lock 유지.

# 5. Structure Lock

```text
OPEN + 0 response = mutable
OPEN + >=1 response = locked
```

Authority는 canonical Response 존재 여부.

# 6. Delete

```text
OPEN → CLOSED → DELETE
```

- DRAFT/CLOSED는 soft delete 가능.
- OPEN direct delete는 domain error이며 먼저 CLOSED로 전환해야 한다.
- delete 후 public/admin 일반 조회와 result access는 V1에서 제공하지 않는다.
- restore와 automatic Response purge는 V1에 없다.

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
