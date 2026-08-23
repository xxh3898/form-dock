---
title: Creator Requirements
status: draft
version: 0.4
last_updated: 2026-08-23
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

# 3. Phase Boundary

Phase 1은 User persistence, bootstrap, session schema, Login/Logout/Current Creator, Creator-only Admin protection과 최소 Login/Admin shell을 완료했다.

Phase 2 owner-scoped Survey/Question Builder와 Admin preview, Phase 3 Public Survey/Response는 완료되어 release됐다. Phase 4는 owner-scoped Result/CSV read를 승인하며 Production은 별도 Gate 전까지 승인되지 않는다.

# 4. Survey List

Creator는 자신의 non-deleted Survey 목록을 볼 수 있다.

V1 기본 표시:

- title
- status
- response count
- updatedAt
- reserved slug identity

Phase 2 Admin API/UI는 reserved slug를 표시할 수 있지만 functional/clickable public `/s/{slug}` URL이 available한 것처럼 표시하지 않는다. Public route는 Phase 3가 구현한다.

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

`stale request`는 Backend가 authoritative transaction 안에서 owner/state/`deletedAt`/`openedAt`/canonical Response existence를 다시 확인하고 `404`/`409`/`503`으로 결과를 알린다는 의미다. V1의 general field edit에 `@Version`, ETag, `If-Match` 또는 request revision을 암묵적으로 요구하지 않는다.

# 8. V1 Non-goals

- team
- workspace
- invite
- ownership transfer
- public signup

# 9. Results Operations

Creator는 자신의 non-deleted Survey에 한해 다음 read-only 기능을 사용할 수 있다.

- newest-first paginated Response list
- complete Question-order individual Response detail
- Survey overview와 bounded Question summary
- CSV export

Unknown/unowned/deleted Survey는 동일하게 숨기고 다른 Survey의 Response 존재 여부도 노출하지 않는다. V1은 Response edit/delete/exclude, arbitrary search/filter/sort와 cross-Survey analytics를 제공하지 않는다.
