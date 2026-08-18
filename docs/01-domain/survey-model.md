---
title: FormDock Survey Domain Model
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Aggregate

```text
Survey
├─ Question
│  └─ QuestionOption

SurveyResponse = separate aggregate
```

Survey definition과 Response lifecycle은 분리한다.

# 2. Survey Fields

```text
id
ownerId
title
description
slug
privacyNotice
status
createdAt
updatedAt
openedAt
closedAt
deletedAt
```

# 3. Identity

- Internal: `BIGINT`
- Public: `slug`

Slug는 globally unique이며 삭제 후에도 재사용하지 않는다.

# 4. Status

```text
DRAFT
OPEN
CLOSED
```

# 5. Structure Lock

Derived state:

```text
structureLocked = responseCount > 0
```

별도 column으로 저장하지 않는다.

첫 Response 이전: 구조 편집 가능.

첫 Response 이후: Question semantics immutable.

# 6. Structural Change

기존 응답이 있는 Survey 구조를 바꿔야 하면:

```text
Duplicate
→ New DRAFT
→ Modify
→ OPEN
```

V1 Survey Version aggregate는 만들지 않는다.

# 7. Soft Delete

Survey delete:

```text
deletedAt = now
```

OPEN Survey는 `OPEN → CLOSED → DELETE` 순서를 권장한다.

# 8. Ownership

Survey는 정확히 한 Creator가 소유한다.

V1에는 공동 owner/workspace/ownership transfer가 없다.

# 9. Invariants

- slug unique
- deleted slug reserved
- DRAFT/CLOSED 신규 Response 불가
- first Response 이후 structure immutable
- SurveyResponse separate aggregate
