---
title: Survey Builder Requirements
status: draft
version: 0.1
last_updated: 2026-08-18
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

# 5. Autosave

V1 필수 아님.

명시적 Save 방식 허용.

# 6. Drag & Drop

V1 필수 아님.

Up/Down 이동으로 충분하다.

# 7. Data Integrity

Builder는 invalid Survey를 OPEN 상태로 전환할 수 없어야 한다.
