---
title: Creator Requirements
status: draft
version: 0.2
last_updated: 2026-08-19
---

# 1. Authentication

Creator는 로그인해야 Admin 영역에 접근할 수 있다.

V1 공개 signup 없음.

# 2. Creator Model

Persistent aggregate 이름은 `User`이고, 인증된 product actor를 Creator라고 부른다.

```text
id
email
passwordHash
displayName
role
createdAt
updatedAt
```

- email은 trim 후 lowercase로 정규화하고 normalized value를 unique identity로 사용한다.
- `passwordHash`만 persistence가 소유하며 plaintext password는 저장하거나 log하지 않는다.
- `displayName`은 trim 후 1~100자다.
- V1 role은 `ADMIN` 하나이며 추가 RBAC semantics는 없다.
- disabled/deleted account와 self-service profile/password lifecycle은 Phase 1 범위가 아니다.

# 3. Phase 1 Authorization Boundary

Phase 1은 User persistence, bootstrap, session schema, Login/Logout/Current Creator, Creator-only Admin protection과 최소 Login/Admin shell만 구현한다.

Survey aggregate와 ownership enforcement는 Phase 1에 포함하지 않는다. 다른 Creator의 Survey 접근 차단 요구사항은 Survey CRUD가 승인되는 다음 Phase에서 구현한다.

# 4. Survey List

Creator는 자신의 non-deleted Survey 목록을 볼 수 있다.

V1 기본 표시:

- title
- status
- response count
- updatedAt
- public URL if OPEN

# 5. Survey Operations

- create
- edit
- duplicate
- preview
- open
- close
- soft delete

# 6. Ownership

다른 Creator의 Survey ID를 직접 입력해도 접근할 수 없어야 한다.

# 7. Error UX

- validation error
- unauthorized
- structure locked
- slug conflict
- stale request

에 대해 사용자에게 재시도 가능한 메시지를 제공한다.

# 8. V1 Non-goals

- team
- workspace
- invite
- ownership transfer
- public signup
