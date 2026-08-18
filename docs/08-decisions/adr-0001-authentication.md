---
title: ADR-0001 Creator Authentication
status: accepted
version: 1.0
last_updated: 2026-08-18
---

# Status

`accepted`

# Context

V1 Creator는 소수의 승인 사용자이며 공개 signup이나 mobile API consumer가 핵심이 아니다.

# Options Considered

Authentication:

1. JWT access/refresh token
2. Server-side session cookie
3. OAuth-only

Session persistence:

1. in-memory HttpSession
2. Spring Session JDBC
3. Redis

Initial Creator:

1. environment-based one-time bootstrap
2. CLI/admin command
3. manual DB insert
4. Flyway secret seed

# Decision

```text
Creator auth        Server-side session cookie
Session store       Spring Session JDBC on PostgreSQL
Password            DelegatingPasswordEncoder, BCrypt encode default
Initial Creator     Environment-based one-time bootstrap
Browser deployment  Same-origin Web + /api
```

Login, logout와 Admin mutation은 CSRF로 보호한다. Anonymous Public Response POST만 exact matcher로 제외하며 Creator session authority를 사용하지 않는다. 상세 contract는 [Authentication & Session Architecture](../03-architecture/authentication.md)를 따른다.

# Rationale

- Admin Web 중심
- JWT lifecycle 불필요
- Spring Security 기본 모델과 잘 맞음
- HttpOnly/Secure cookie 사용 가능
- logout/revocation 단순
- API container restart 뒤 session 유지
- 이미 필요한 PostgreSQL을 재사용해 Redis 운영 요소를 추가하지 않음
- repository/migration에 bootstrap secret을 저장하지 않음

# Consequences

- CSRF 보호 필요
- session table도 Flyway schema 관리 필요
- PostgreSQL 장애 시 session 사용 불가
- horizontal scaling 시 session store 재검토
- bootstrap 완료 후 enable flag와 plaintext password environment 제거 필요

# Non-goals

- public signup
- social login
- mobile auth
- Redis session
- password reset automation
