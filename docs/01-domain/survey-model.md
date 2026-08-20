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

Slug contract:

```text
length  3..64
regex   ^[a-z0-9]+(?:-[a-z0-9]+)*$
case    lowercase
```

- 생성 시 title을 lowercase kebab-case로 정규화하고 충돌하면 server가 suffix를 붙인다. 정규화 결과가 3자 미만이면 `survey-{server-generated-suffix}`를 사용하며 최종 길이는 64자를 넘지 않는다.
- Creator는 `DRAFT`이고 아직 한 번도 OPEN하지 않은 Survey만 slug를 수동 변경할 수 있다.
- 첫 OPEN 이후에는 CLOSED 또는 response 0건이어도 slug를 변경하지 않는다.
- Slug는 globally unique이며 soft delete 후에도 영구 예약한다.

`openedAt`은 첫 DRAFT → OPEN에서 한 번만 설정하고 reopen 시 지우거나 덮어쓰지 않는다. Slug mutation은 이 값으로 first-OPEN 여부를 판단한다.

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

- `DRAFT`와 `CLOSED`만 soft delete할 수 있다.
- `OPEN` 직접 delete는 거절하며 `OPEN → CLOSED → DELETE` 순서를 강제한다.
- V1 restore 기능은 제공하지 않는다.
- 삭제 후 Public API와 일반 Admin list/detail/result API는 Survey를 찾을 수 없는 것으로 처리한다.
- Soft delete는 기존 SurveyResponse를 purge하지 않는다.

# 8. Ownership

Survey는 정확히 한 Creator가 소유한다.

V1에는 공동 owner/workspace/ownership transfer가 없다.

# 9. Invariants

- slug unique
- deleted slug reserved
- first OPEN 이후 slug immutable
- DRAFT/CLOSED 신규 Response 불가
- first Response 이후 structure immutable
- SurveyResponse separate aggregate
