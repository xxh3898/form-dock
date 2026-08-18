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

후보:

- React Router
- 동일 역할의 경량 router

구체 라이브러리는 구현 시작 시 최신 호환성 확인 후 확정.

# 6. UX

Respondent는 mobile-first.

Creator는 desktop-first지만 tablet 대응 가능하도록 구성.

# 7. Accessibility

Semantic controls, focus, error association, keyboard navigation 기본 적용.
