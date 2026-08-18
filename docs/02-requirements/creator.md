---
title: Creator Requirements
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Authentication

Creator는 로그인해야 Admin 영역에 접근할 수 있다.

V1 공개 signup 없음.

# 2. Survey List

Creator는 자신의 non-deleted Survey 목록을 볼 수 있다.

표시 후보:

- title
- status
- response count
- updatedAt
- public URL if OPEN

# 3. Survey Operations

- create
- edit
- duplicate
- preview
- open
- close
- soft delete

# 4. Ownership

다른 Creator의 Survey ID를 직접 입력해도 접근할 수 없어야 한다.

# 5. Error UX

- validation error
- unauthorized
- structure locked
- slug conflict
- stale request

에 대해 사용자에게 재시도 가능한 메시지를 제공한다.

# 6. V1 Non-goals

- team
- workspace
- invite
- ownership transfer
- public signup
