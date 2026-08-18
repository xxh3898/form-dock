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

# 4. Structure Lock UX

첫 Response 이후 destructive 구조 변경 버튼을 비활성화하거나 명확하게 거절한다.

왜 변경할 수 없는지 설명하고 `Duplicate Survey`를 대안으로 제공한다.

# 5. Autosave

V1 필수 아님.

명시적 Save 방식 허용.

# 6. Drag & Drop

V1 필수 아님.

Up/Down 이동으로 충분하다.

# 7. Data Integrity

Builder는 invalid Survey를 OPEN 상태로 전환할 수 없어야 한다.
