---
title: Authentication & Session Architecture
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Scope

Creator만 인증한다.

Respondent는 비로그인.

# 2. Mechanism

```text
Spring Security
Server-side Session
HttpOnly Cookie
Secure
SameSite
```

# 3. Password

Password는 강한 adaptive hash를 사용한다.

후보: BCrypt/Argon2.

Spring Security 지원과 운영 단순성을 기준으로 구현 시 확정.

# 4. CSRF

Session cookie 인증이므로 Admin mutation API는 CSRF 보호가 필요하다.

Public Response submit의 CSRF/abuse 경계는 별도 검토한다.

# 5. Session Store

V1 후보:

- in-memory session: 단일 인스턴스 단순
- DB/Redis session: 재시작/확장 고려

현재 single API instance 기준으로 최소 구성을 우선하되 production restart UX를 고려해 구현 전에 확정한다.

# 6. Initial Creator Provisioning

공개 signup 없음.

초기 계정 생성 방식 `TBD`.

후보:

- seed migration에 hash 포함 금지 권장
- startup admin provisioning command
- environment 기반 one-time bootstrap

secret은 repository에 저장하지 않는다.
