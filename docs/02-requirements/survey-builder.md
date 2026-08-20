---
title: Survey Builder Requirements
status: draft
version: 0.2
last_updated: 2026-08-20
---

# 1. Builder Goal

Creator가 코드 없이 Survey 구조를 만들 수 있어야 한다.

# 2. Supported Actions

- add question
- edit title/description
- set type
- required toggle
- add/edit/delete options
- move question up/down
- preview

# 3. Type Configuration

- SCALE min/max/labels
- NUMBER min/max
- Choice options
- 현재 Question type에서 사용하지 않는 configuration field는 저장하지 않으며 invalid 조합은 거절

Slug는 처음 OPEN하기 전 DRAFT에서만 수정할 수 있다. 첫 OPEN 이후에는 public URL 안정성을 위해 변경 UI를 제공하지 않는다.

# 4. Structure Lock UX

첫 Response 이후 destructive 구조 변경 버튼을 비활성화하거나 명확하게 거절한다.

왜 변경할 수 없는지 설명하고 `Duplicate Survey`를 대안으로 제공한다.

화면 상태가 stale해도 Backend가 Survey row lock 이후 canonical Response 존재 여부를 다시 확인한다.

모든 Question/Option create/update/delete/reorder는 같은 Survey row lock을 먼저 획득하고 real `survey_responses` table의 canonical existence를 조회한다. Phase 2 Product code는 이 table을 COUNT/EXISTS로만 읽고 row를 생성하지 않는다.

# 5. Duplicate Survey

Duplicate는 current Creator가 소유한 source를 같은 Creator의 새 aggregate로 deep-copy한다.

```text
status/openedAt/closedAt/deletedAt  DRAFT/null/null/null
metadata                            title/description/privacyNotice copy
slug                                fresh globally unique allocation
questions/options                   ordered structure/config deep copy
responses                           not copied
responseCount/structureLocked       0/false
```

Canonical Responses가 있는 source도 duplicate할 수 있지만 Response/Answer row는 복사하지 않는다. Deleted 또는 다른 Creator의 source는 owner-scoped 404다.

# 6. Question and Option Mutation

- Question create/update는 complete semantic configuration을 제출한다.
- Choice options는 Question aggregate가 소유한 하나의 ordered list이며 update 시 list 전체를 검증하고 교체한다.
- Non-Choice options는 empty, unused type-specific scalar field는 `null`이어야 한다.
- Reorder는 current Question ID complete set을 정확히 한 번씩 제출하고 server가 zero-based gapless position으로 정규화한다.
- V1 standalone Option endpoint는 없다.

# 7. Autosave

V1 필수 아님.

명시적 Save 방식 허용.

# 8. Drag & Drop

V1 필수 아님.

Up/Down 이동으로 충분하다.

# 9. Data Integrity

Builder는 invalid Survey를 OPEN 상태로 전환할 수 없어야 한다.
