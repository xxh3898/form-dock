---
title: Authentication & Session Architecture
status: active
version: 1.5
last_updated: 2026-08-21
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

Application 기본 session cookie는 `SESSION`, `HttpOnly=true`, `Secure=true`, `SameSite=Lax`로 설정하고 host 범위를 불필요하게 넓히지 않는다. V1은 `forms.chochiho.cloud`에서 Web과 `/api`를 함께 제공하는 same-origin deployment다. `Secure=false` override는 loopback-only `local` profile에만 둔다.

# 3. Password

Spring Security의 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`를 사용한다.

- 새 password의 encode 대상은 BCrypt이며 저장 값은 `{bcrypt}` identifier를 포함한다.
- Phase 1은 factory가 제공하는 BCrypt 기본 strength 10을 사용하며 custom tuning을 추가하지 않는다. Production Readiness에서 target host verification time을 측정해 상향 필요성을 다시 판단한다.
- `DelegatingPasswordEncoder` format을 유지해 향후 encoder upgrade와 기존 hash 검증을 가능하게 한다.

Bootstrap password는 15자 이상이고 UTF-8 encoding 기준 72 byte 이하여야 한다. Unicode와 whitespace를 허용하고 대문자/소문자/숫자/특수문자 조합 규칙은 강제하지 않는다. Password 원문을 정규화하거나 truncate하지 않고 그대로 encoder에 전달한다.

Plaintext password는 저장하거나 log하지 않는다.

# 4. CSRF

- 로그인, 로그아웃과 모든 Admin unsafe method(`POST`, `PUT`, `PATCH`, `DELETE`)는 CSRF token이 필수다.
- React client는 same-origin `GET /api/auth/csrf`에서 token을 받고 `X-CSRF-TOKEN` header로 전송한다. 로그인/로그아웃 뒤 token rotation을 반영해 다시 조회한다.
- Public Survey GET은 safe method이므로 별도 CSRF token이 필요 없다.
- Anonymous `POST /api/public/surveys/{slug}/responses`만 exact matcher로 CSRF 검증에서 제외한다. 이 endpoint는 Creator session을 authorization 또는 data authority로 사용하지 않는다.
- Public submit은 `Content-Type: application/json`, 1 MiB raw request body limit, server validation과 bounded ephemeral rate limiting을 별도로 강제한다.

CSRF 제외 범위를 `/api/public/**` 전체로 넓히지 않는다.

# 5. CORS

V1 browser client는 same-origin만 지원한다. Cross-origin origin allowlist와 credentialed CORS를 구성하지 않으며 API는 임의의 `Access-Control-Allow-Origin`을 반환하지 않는다.

Local Vite dev server를 사용할 때도 browser는 Vite의 same-origin `/api` proxy를 호출한다. Local convenience를 이유로 API CORS allowlist를 추가하지 않는다.

## 5.1 Public Request Guard

- Public Response POST의 raw request body는 최대 1 MiB(1,048,576 bytes)이며 초과 시 `413 RESPONSE_PAYLOAD_TOO_LARGE`를 반환하고 Response write를 시작하지 않는다.
- V1 application rate limit은 bounded in-memory state만 사용한다. DB-backed IP/token record, cookie 또는 Web Storage respondent identity를 만들지 않는다.
- threshold/window는 configuration-driven runtime setting이며 persisted Product data가 아니다.
- 현재 application 기본값은 direct peer당 1분에 60회, 최대 10,000 identity이며 `formdock.public-response.rate-limit.*` configuration으로 override한다.
- Production proxy-trust gate 전에는 `X-Forwarded-For`, `CF-Connecting-IP` 또는 다른 forwarded identity header를 client authority로 신뢰하지 않는다.
- limit을 초과한 request는 `429 RATE_LIMITED`이며 idempotency lookup 전에 거절될 수 있다. Guard를 통과한 request에는 canonical replay contract를 적용한다.
- Cloudflare edge limit과 trusted client-IP extraction은 Production Readiness scope다.

# 6. Session Store

Spring Session JDBC를 사용하고 기존 PostgreSQL 18에 HttpSession을 저장한다.

- API container restart 뒤에도 유효한 Creator session을 유지한다.
- 별도 Redis는 V1에 추가하지 않는다.
- Spring Session table은 production auto-initialization이 아니라 Flyway migration이 소유한다.
- session expiry와 logout은 DB-backed session을 invalidation한다.

Phase 1 PR A는 `V1__create_users.sql`과 별도 `V2__create_spring_session.sql`로 business/infrastructure schema 책임을 분리한다. Session migration은 resolved `spring-session-jdbc-4.1.0.jar`의 `schema-postgresql.sql` table, index와 foreign key contract를 사용한다. `spring.session.jdbc.initialize-schema=never`는 유지하고 cleanup scheduler는 Spring Boot 4.1 기본 cron인 `0 * * * * *`로 활성화한다.

PostgreSQL 장애 시 인증 session도 사용할 수 없다는 의존성을 수용한다. 향후 수평 확장이나 DB 부하가 실제 문제가 될 때 store를 재검토한다.

Exact production inactivity timeout은 Production Readiness에서 고정한다. Phase 1은 Spring Boot property를 사용하고 test profile에서 짧은 timeout으로 expiry behavior를 검증한다.

# 7. Initial Creator Provisioning

공개 signup 없음.

명시적 enable flag와 environment secret을 사용하는 one-time bootstrap을 제공한다.

```text
FORMDOCK_BOOTSTRAP_ENABLED
FORMDOCK_BOOTSTRAP_EMAIL
FORMDOCK_BOOTSTRAP_PASSWORD
FORMDOCK_BOOTSTRAP_DISPLAY_NAME
```

- enable flag 기본값은 `false`이며 disabled 상태에서는 user write를 수행하지 않는다.
- bootstrap이 enabled이면 email, plaintext password, display name input이 모두 있어야 하며 일부만 있으면 fail closed한다.
- email은 trim/lowercase, display name은 trim과 1~100자, password는 위 password policy로 hash 전에 검증한다.
- normalized bootstrap email의 user가 이미 있으면 credential이나 profile을 변경하지 않는 idempotent no-op이다.
- 같은 email은 없고 user가 0명일 때만 transaction에서 한 명의 `ADMIN`을 만든다.
- 같은 email은 없지만 다른 user가 있으면 account를 만들지 않고 fail closed한다.
- 생성 직후 operator는 bootstrap flag와 plaintext password environment를 제거해야 한다.
- secret 값은 log, error response, repository, image, migration에 남기지 않는다.

Application은 `formdock.bootstrap` properties를 위 environment variable에 연결한다. Startup runner는 disabled/create/already-provisioned 결과만 log하고 email, password와 hash를 포함하지 않는다. Concurrent first provisioning에서 DB unique constraint가 duplicate를 막으며 losing startup은 fail closed한다.

Manual DB insert와 Flyway secret seed는 bootstrap 방법으로 사용하지 않는다. 별도 CLI는 V1 운영 복잡도를 늘리므로 도입하지 않는다.

# 8. Authentication Semantics

- `GET /api/auth/csrf`는 anonymous request에도 200으로 token을 발급한다.
- `POST /api/auth/login`은 valid CSRF와 email/password JSON을 받고 성공 시 session fixation protection 뒤 200 Creator DTO를 반환한다.
- REST login은 `SessionAuthenticationStrategy`를 적용해 session ID를 변경한 뒤 authenticated `SecurityContext`를 `SecurityContextRepository`에 명시적으로 저장한다.
- Unknown email과 wrong password는 동일한 401 `AUTH_INVALID_CREDENTIALS` response를 사용하고 comparable password verification path를 거친다.
- `GET /api/auth/me`는 authenticated Creator DTO 200, anonymous request는 401 `AUTH_REQUIRED`다.
- `POST /api/auth/logout`은 valid authenticated request와 CSRF에서 204를 반환하고 server-side session, security context와 session cookie를 무효화한다.
- Login/logout 성공 뒤 SPA는 CSRF token을 다시 조회한다.

Phase 1 PR C browser client는 relative `/api/auth/*`와 `credentials=same-origin`만 사용한다. `SESSION` cookie는 HttpOnly/browser-managed 상태로 두고 password, cookie, session ID를 Web Storage, IndexedDB나 log에 저장하지 않는다. CSRF token은 process memory에만 보관하며 login/logout 전 확보한다. `CSRF_INVALID`이면 token을 한 번 강제 재조회하고 unsafe request를 최대 한 번만 재시도한다. 성공한 login/logout 뒤 token을 다시 조회하며, 이 후속 조회가 실패하면 stale token을 버리고 다음 unsafe request 전에 다시 확보한다.

Frontend는 `AUTH_INVALID_CREDENTIALS`, `AUTH_REQUIRED`, `CSRF_INVALID`, `TEMPORARILY_UNAVAILABLE` 같은 stable `code`만 분기한다. Backend `message`는 client 제어 흐름이나 credential 구분에 사용하지 않는다.

Spring Security의 Servlet 기본 `changeSessionId` fixation protection을 유지하며 이를 비활성화하지 않는다. Password hash, plaintext password와 session ID는 response나 application log에 포함하지 않는다.

Phase 1 PR B implementation은 password hash가 없는 serializable `CreatorPrincipal`, canonical email을 조회하는 custom `AuthenticationProvider`, `ChangeSessionIdAuthenticationStrategy`와 CSRF token clear를 묶은 composite strategy를 사용한다. Login service는 strategy 호출 뒤 `HttpSessionSecurityContextRepository`에 authenticated context를 explicit save한다. Unknown/malformed email은 startup에 생성한 dummy BCrypt hash를 검증하고 known wrong password도 stored hash를 한 번 검증해 credential failure 경로를 맞춘다.

Logout은 `SecurityContextLogoutHandler`와 `SESSION` cookie clear를 함께 적용한다. Request 처리 중 JDBC access failure는 Spring Session filter보다 앞선 narrow error filter가 safe `503 TEMPORARILY_UNAVAILABLE` body로 변환하며 이미 commit된 response는 재작성하지 않는다.

현재 runtime security matcher는 health, CSRF 발급, login과 exact `GET /api/public/surveys/{slug}`만 anonymous로 허용한다. 나머지 `/api/**`는 authenticated boundary이고 API 밖의 route는 deny-by-default다. Phase 3-A는 Public Survey GET matcher만 추가하며 CSRF ignore/exemption이나 CORS를 추가하지 않는다. Phase 3-C가 exact Public Response POST anonymous/CSRF-exempt matcher와 request guard를 별도 구현·검증한다. `/api/public/**` broad exemption은 계속 금지한다.

# 9. Non-goals

- JWT
- Redis session
- public signup
- password reset automation
- OAuth/social login
- account disable/delete와 추가 RBAC

# 10. References

- [Spring Security Password Storage](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html)
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
