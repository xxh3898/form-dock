---
title: Frontend Architecture
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Stack

```text
React
TypeScript
Vite
```

# 2. Main Areas

```text
/admin/*
/s/{slug}
```

Creator Admin과 Public Respondent UI를 기능적으로 분리한다.

# 3. State

Server state는 API client/query layer에서 관리.

불필요한 global state library는 초기 도입하지 않는다.

# 4. Forms

Question Builder와 Respondent Answer state는 local/form state 중심.

# 5. Routing

Router dependency는 application scaffold에 포함하지 않는다.

첫 Admin/Public navigation feature를 구현할 때 React Router를 우선 평가하고 당시 React/Vite 호환 stable version을 확정한다. 이 deferred 선택은 project scaffold를 막지 않는다.

# 6. UX

Respondent는 mobile-first.

Creator는 desktop-first지만 tablet 대응 가능하도록 구성.

# 7. Accessibility

Semantic controls, focus, error association, keyboard navigation 기본 적용.
