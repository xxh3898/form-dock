---
title: ADR-0001 Creator Authentication
status: draft
version: 0.1
last_updated: 2026-08-18
---

# Status

`proposed`

# Context

V1 Creator는 소수의 승인 사용자이며 공개 signup이나 mobile API consumer가 핵심이 아니다.

# Options

1. JWT access/refresh token
2. Server-side session cookie
3. OAuth-only

# Decision

추천:

```text
Server-side session cookie
```

# Rationale

- Admin Web 중심
- JWT lifecycle 불필요
- Spring Security 기본 모델과 잘 맞음
- HttpOnly/Secure cookie 사용 가능
- logout/revocation 단순

# Consequences

- CSRF 보호 필요
- session persistence 전략 결정 필요
- horizontal scaling 시 session store 재검토

# Non-goals

- public signup
- social login
- mobile auth
