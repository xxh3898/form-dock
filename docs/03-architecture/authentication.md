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

Production session cookie는 `HttpOnly`, `Secure`, `SameSite=Lax`로 설정하고 host 범위를 불필요하게 넓히지 않는다. V1은 `forms.chochiho.cloud`에서 Web과 `/api`를 함께 제공하는 same-origin deployment다. `Secure=false`는 loopback-only local profile에서만 허용한다.

# 3. Password

Spring Security의 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`를 사용한다.

- 새 password의 encode 대상은 BCrypt이며 저장 값은 `{bcrypt}` identifier를 포함한다.
- exact work factor는 contract에 고정하지 않고 auth 구현 시 target host에서 검증해 Spring Security 기본값을 유지하거나 상향한다.
- `DelegatingPasswordEncoder` format을 유지해 향후 encoder upgrade와 기존 hash 검증을 가능하게 한다.

Plaintext password는 저장하거나 log하지 않는다.

# 4. CSRF

- 로그인, 로그아웃과 모든 Admin unsafe method(`POST`, `PUT`, `PATCH`, `DELETE`)는 CSRF token이 필수다.
- React client는 same-origin `GET /api/auth/csrf`에서 token을 받고 `X-CSRF-TOKEN` header로 전송한다. 로그인/로그아웃 뒤 token rotation을 반영해 다시 조회한다.
- Public Survey GET은 safe method이므로 별도 CSRF token이 필요 없다.
- Anonymous `POST /api/public/surveys/{slug}/responses`만 exact matcher로 CSRF 검증에서 제외한다. 이 endpoint는 Creator session을 authorization 또는 data authority로 사용하지 않는다.
- Public submit은 `Content-Type: application/json`, strict body limit, server validation, rate limiting을 별도로 강제한다.

CSRF 제외 범위를 `/api/public/**` 전체로 넓히지 않는다.

# 5. CORS

V1 browser client는 same-origin만 지원한다. Cross-origin origin allowlist와 credentialed CORS를 구성하지 않으며 API는 임의의 `Access-Control-Allow-Origin`을 반환하지 않는다.

Local Vite dev server를 사용할 때도 browser는 Vite의 same-origin `/api` proxy를 호출한다. Local convenience를 이유로 API CORS allowlist를 추가하지 않는다.

# 6. Session Store

Spring Session JDBC를 사용하고 기존 PostgreSQL 18에 HttpSession을 저장한다.

- API container restart 뒤에도 유효한 Creator session을 유지한다.
- 별도 Redis는 V1에 추가하지 않는다.
- Spring Session table은 production auto-initialization이 아니라 Flyway migration이 소유한다.
- session expiry와 logout은 DB-backed session을 invalidation한다.

PostgreSQL 장애 시 인증 session도 사용할 수 없다는 의존성을 수용한다. 향후 수평 확장이나 DB 부하가 실제 문제가 될 때 store를 재검토한다.

# 7. Initial Creator Provisioning

공개 signup 없음.

명시적 enable flag와 environment secret을 사용하는 one-time bootstrap을 제공한다.

- bootstrap이 enabled이면 email, plaintext password, display name input이 모두 있어야 하며 일부만 있으면 fail closed한다.
- normalized bootstrap email의 user가 이미 있으면 credential이나 profile을 변경하지 않는 idempotent no-op이다.
- 같은 email은 없고 user가 0명일 때만 transaction에서 한 명의 `ADMIN`을 만든다.
- 같은 email은 없지만 다른 user가 있으면 account를 만들지 않고 fail closed한다.
- 생성 직후 operator는 bootstrap flag와 plaintext password environment를 제거해야 한다.
- secret 값은 log, error response, repository, image, migration에 남기지 않는다.

Manual DB insert와 Flyway secret seed는 bootstrap 방법으로 사용하지 않는다. 별도 CLI는 V1 운영 복잡도를 늘리므로 도입하지 않는다.

# 8. Non-goals

- JWT
- Redis session
- public signup
- password reset automation

# 9. References

- [Spring Security Password Storage](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html)
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
