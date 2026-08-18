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

DRAFT/CLOSED는 soft delete 가능.

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

- submit 순간 server Survey status가 authority
- submit 순간 deletedAt 확인
- 첫 Response commit과 structure mutation concurrency는 architecture에서 보호

# 10. Invariants

- DRAFT/CLOSED 신규 Response 없음
- client state보다 server state 우선
- first Response 이후 structure immutable
- partial persisted Response 없음
