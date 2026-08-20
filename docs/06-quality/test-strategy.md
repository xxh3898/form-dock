---
title: Test Strategy
status: draft
version: 0.6
last_updated: 2026-08-20
---

# 1. Backend

Java 25에서 `./gradlew clean check`를 canonical command로 사용한다. Context, Actuator health, PostgreSQL 18, Flyway V1..V5 migration, deny-by-default security를 Testcontainers PostgreSQL로 검증한다.

## Unit

- domain policy
- validation
- lifecycle
- payload canonicalization

## Integration

Testcontainers PostgreSQL 사용.

- JPA constraints
- Flyway
- ownership
- Response transaction
- idempotency: 최초 201, 동일 replay 200, conflict 409, concurrent duplicate
- concurrency: first Response와 structure mutation의 두 lock 순서 모두
- public status: unavailable GET 404, CLOSED 신규 submit 409, CLOSED 기존 replay 200/409
- JDBC session restart/expiry/invalidation
- CSRF: login/logout/Admin mutation 보호와 exact Public submit 제외
- Creator bootstrap zero/existing/partial/conflicting input

## Phase 1 Creator Foundation

PR A:

- Flyway clean PostgreSQL: `users`, `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`와 required index/FK 생성
- normalized email unique, `ADMIN` role, `{bcrypt}` hash persistence, plaintext persistence/log 0
- bootstrap disabled write 0, enabled complete create 1, same normalized email no-op, partial/conflicting state startup failure
- password 15자/UTF-8 72 byte boundary와 non-truncation
- Session auto-init `never`, cleanup cron enabled와 expired-session cleanup query 성공

PR B:

- valid login 200, unknown email/wrong password 동일 401/body, authentication failure log에 credential 0
- login 전후 session ID 변경, authenticated `/me` 200, anonymous `/me` 401
- valid logout 204, session/context invalidation, logout 뒤 `/me` 401
- login/logout/Admin unsafe request CSRF 거절/성공과 login/logout 뒤 token refresh
- API restart 뒤 JDBC-backed session 유지, test timeout 뒤 expiry
- anonymous Admin API 401, authenticated Creator 허용, arbitrary CORS response 0
- REST Docs auth request/response/error contract 동기화
- Spring Session/UserRepository data access failure의 safe 503 mapping
- Hosted Backend log에 pinned PostgreSQL Testcontainer 실행 test와 total/passed/failed/skipped summary 출력

PR C:

- `/`, `/login`, `/admin`, unknown route와 anonymous protected-content flash 0
- valid session `/me` restore, expired/anonymous redirect와 transient session failure
- valid login navigation, generic invalid credential, transient/CSRF error mapping
- login/logout pending duplicate submit 방지와 logout 뒤 `/login`
- CSRF login/logout acquisition, auth transition 뒤 refresh, stale token 1회 retry와 retry bound
- same-origin credential mode와 backend `message` 비분기
- frontend credential/error handling에서 Web Storage write와 password/session identifier 노출 0
- Nginx `/login`, `/admin` SPA fallback과 `/api` same-origin proxy

## Phase 2-A Survey DRAFT Core

- V3 clean migration, owner FK, lifecycle CHECK, global slug reservation과 owner-active list index
- title/optional text Unicode code-point validation과 presence-aware PATCH null semantics
- ASCII/non-ASCII slug generation, bounded collision retry, explicit conflict와 safe exhaustion
- owner-scoped list/detail/mutation concealment, deterministic ordering와 soft-delete reservation
- Phase 2-A logical DTO values `questions=[]`, `responseCount=0`, `structureLocked=false`
- anonymous 401, unsafe mutation CSRF 403와 stable Survey 400/404/409/503 errors
- REST Docs list/create/detail/PATCH/delete와 representative error contract

## Phase 2-B Question/Lock Data Foundation

- V1/V2/V3 checksum 불변과 clean V1→V5 migration
- V4 six-type Question/Option constraints, persistence와 deterministic ordering
- V5 final SurveyResponse identity/unique/hash constraints와 Answer-related table 0
- Product SurveyResponse writer 0, real COUNT/EXISTS/grouped COUNT read
- Survey list의 grouped count 1회와 detail/create/PATCH의 real ordered structure/count/lock authority
- owner/deleted concealment, current status revalidation과 caller-owned lock transaction
- PostgreSQL `pg_blocking_pids` evidence, bounded lock timeout, safe 503와 partial caller write 0
- Phase 2-A owner/slug/PATCH-delete concurrency/API regression

## Phase 2-C Survey Builder Backend Completion

- all six complete-state Question payload와 unknown/unused/NUMBER decimal validation
- Option identity preserve/new/delete, foreign/duplicate ID와 Question concealment
- Question delete/reorder의 immediate UNIQUE-safe two-phase position normalization
- 모든 mutation의 real V5 structure guard, seeded Response 409와 Product mutation bounded 503/partial write 0
- DRAFT→OPEN, OPEN→CLOSED, CLOSED→OPEN timestamp/state와 lock 이후 persisted structure validation
- DRAFT/OPEN/CLOSED/Response-present source deep duplicate, fresh identities/slug와 Response copy 0
- duplicate slug retry/copy failure의 whole-attempt rollback
- new unsafe endpoint anonymous/CSRF와 Spring REST Docs success/error contract

## REST Docs

API contract와 controller behavior 동기화.

# 2. Frontend

Scaffold baseline command:

```text
npm run lint
npm run typecheck
npm test
npm run build
```

- component/unit
- form validation
- Builder behavior
- respondent flow
- CSRF token refresh와 error-code mapping

Phase 1 PR C는 Vitest + React Testing Library/jsdom에서 auth client와 route component boundary를 검증한다. JDBC session/cookie server behavior는 PR B PostgreSQL integration regression이 authority이며, 별도 browser framework는 PR C에서 추가하지 않는다.

E2E 범위는 V1 핵심 flow 중심.

# 3. Infrastructure

- Docker build
- Compose config
- health checks
- ARM64

Scaffold PR은 Apple Silicon local Compose build로 ARM64를 검증하고 baseline CI에서는 GitHub runner native image build를 수행한다. QEMU multi-platform build는 main Release Candidate Gate에서 추가한다.

# 4. Manual Smoke

- Creator login
- Survey create
- OPEN
- mobile response
- result view
- CSV
- UTF-8 BOM, RFC 4180, formula-like text, MULTIPLE_CHOICE boolean columns
- close

# 5. Production

public read/smoke는 mutation 최소화.

실제 dogfooding survey로 최종 end-to-end 확인.
