---
title: Test Strategy
status: draft
version: 1.4
last_updated: 2026-08-26
---

# 1. Backend

Java 25에서 `./gradlew clean check`를 canonical command로 사용한다. Context, Actuator health, PostgreSQL 18, Flyway V1..V6 migration, deny-by-default security를 Testcontainers PostgreSQL로 검증한다.

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

## Phase 3-A Public Survey Read Backend

- OPEN + not-deleted slug 200와 DRAFT/CLOSED/deleted/unknown identical 404 concealment
- internal Survey ID/owner/Admin metadata/count/lock/auth exposure 0
- all six type public DTO, ordered Question/Option와 type-specific configuration
- anonymous exact GET와 broad public security matcher 부재
- Spring REST Docs와 PostgreSQL projection integration

## Phase 3-B Response Data and Canonicalization

- V1~V5 checksum 불변과 clean V1→V6 migration, V6 ownership table 2개만
- Answer/AnswerOption FK/unique/check/delete semantics와 Product delete path 0
- exact text preservation, unanswered omission, Question/Option sort, SCALE/NUMBER canonical string
- fixed compact JSON UTF-8 bytes와 SHA-256 lowercase hex deterministic vectors
- `clientSubmissionId`/transport field hash exclusion
- existing V5 SurveyResponse persistence/idempotency primitive와 unique-race convergence

## Phase 3-C Atomic Public Submission Backend

- first create 201, same canonical replay 200, conflicting replay 409와 canonical timestamp/id reuse
- deleted/DRAFT/unknown 404, CLOSED existing 200/409와 new identity 409
- full required/type/value/Question/Option ownership validation와 partial aggregate 0
- exact Public POST CSRF exemption, same-origin/no CORS와 anonymous contract
- raw body 1 MiB(1,048,576 bytes) boundary 413, non-JSON 415, bounded ephemeral rate limit 429와 persisted tracking 0
- forwarded identity header를 Production trust gate 전 무시
- mutation-first: submit wait/latest structure validation/no stale Response commit
- submit-first: mutation wait/real V5 EXISTS/`SURVEY_STRUCTURE_LOCKED`/structure write 0
- bounded lock/dependency 503와 same `clientSubmissionId` retry

## Phase 4-A Creator Response Read Backend

- owned zero/multiple Response list, page/size default·bound와 fixed `submittedAt DESC, responseId DESC`
- invalid page/size 400, out-of-range page 200 empty와 Survey owner/deleted concealment
- complete Question-order detail, optional unanswered `answer=null`과 six Answer representation
- unknown/foreign Response `RESPONSE_NOT_FOUND`, transport identity/hash output 0
- `CreatorResponseReadApiIntegrationTest`에서 PostgreSQL 18.6을 사용해 zero/multiple/tie/out-of-range와 DRAFT/OPEN/CLOSED read를 검증
- owner-first malformed pagination, unknown/unowned/deleted Survey concealment와 anonymous 401 검증
- exact text, canonical decimal/zero, selected Option position, optional unanswered null과 GET write 0 검증
- list success/validation/Survey concealment와 detail success/`RESPONSE_NOT_FOUND` REST Docs 생성

## Phase 4-B Result Summary Backend

- zero-response total 0/last null과 Question/Option deterministic position order
- Choice exact count와 answered-count denominator percentage, scale 2 `HALF_UP`
- MULTIPLE percentage sum 100 초과 case
- Scale average와 configured 전체 bucket distribution, zero-count/no-answer case
- Text/Number answeredCount only와 unbounded raw array 0
- grouped database aggregation과 obvious N+1 0
- `CreatorResponseSummaryApiIntegrationTest`에서 owner-first concealment, DRAFT/OPEN/CLOSED, anonymous 401와 GET write 0 검증
- Option ID insertion order 독립성, optional unanswered denominator, `1/3 → 33.33`과 MULTIPLE 200% fixture 검증
- Scale `2.50`, `3.00`, exact `2.375 → 2.38`과 configured zero bucket/no-answer null 검증
- representative/zero-response/Survey concealment REST Docs와 transport/internal/raw Text/Number output 0 검증

## Phase 4-C CSV Export Backend

- UTF-8 BOM exactly once, RFC 4180 quoting와 CRLF record
- Hangul/comma/quote/newline round-trip와 deterministic metadata/Question/Option columns
- row `submitted_at ASC,response_id ASC`, unanswered empty와 canonical Choice/Scale/Number representation
- MULTIPLE_CHOICE Option boolean columns와 `=`, `+`, `-`, `@` formula-like string neutralization
- zero-response header-only, owner concealment와 memory-bounded read-only generation
- `CreatorResponseCsvExportApiIntegrationTest`에서 257 Response fetch-size 경계, six-type byte round-trip, lifecycle/auth/concealment와 GET write 0 검증

각 Phase 4 backend slice는 Phase 1 Creator auth/session/CSRF, Phase 2 Builder/lifecycle/structure lock, Phase 3 Public GET/POST/idempotency/concurrency의 전체 backend regression을 함께 통과해야 한다.

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

## Phase 2-D Survey Builder Frontend + Preview

- canonical Survey list/detail와 ordered six-type Question/Option runtime parser, NUMBER decimal string와 malformed response rejection
- relative same-origin credential mode, memory CSRF, one bounded retry와 stable status/code/fieldErrors
- `/`, `/admin`, nested list/create/Builder/Preview, anonymous redirect, wildcard와 `/s/{slug}` absence
- list loading/empty/retry, create navigation, metadata nullable/slug semantics와 canonical response state replacement
- Question create/update/delete/reorder, Choice existing/new Option identity, type-specific unused-field normalization
- lifecycle open/close/reopen, duplicate-to-new-DRAFT navigation와 confirmed soft delete
- canonical structure lock controls와 stale 409 refetch, validation/lifecycle/404/503 safe UX
- all-six-type read-only Admin Preview와 submit/Public request 0
- existing Login/Logout/session behavior regression

Phase 2-D는 Vitest + React Testing Library/jsdom과 production build를 canonical frontend evidence로 사용한다. 별도 browser framework나 new dependency를 추가하지 않으며 backend PostgreSQL integration은 Phase 2-C full regression을 그대로 통과해야 한다.

## Phase 3-D Respondent Frontend

- `/s/:slug` only public route, GET 404 unavailable와 409 closed-submit state
- Intro/ordered step/progress/submit/completion과 all-six-type input
- 360px layout, keyboard/focus/label/error/touch-target accessibility
- server-error 400/404/409/413/429/503 safe UX와 client validation non-authority
- form instance당 UUID 하나, transient/uncertain retry reuse와 failure-only regeneration 0
- localStorage/sessionStorage/cookie submission identity write 0
- Result/CSV/Public Response read UI 0

## Phase 4-D Results Frontend

- shared Admin guard 안의 Results list/detail route와 same-origin authenticated client
- overview/summary/newest-first pagination/detail/CSV action
- loading/empty/out-of-range/404/transient failure의 stable code 기반 한국어 UX
- raw backend `message` 분기 0, Response mutation control 0와 Public state 격리
- semantic table, keyboard/focus/label과 narrow-layout regression
- strict list/summary/detail parser, invalid route ID 선행 차단과 `RESPONSE_NOT_FOUND` mapping
- CSV success filename/Blob/revoke와 JSON error 비다운로드, pending single-flight regression

Phase 4-D frontend는 위 regression을 포함해 `dev`에 통합됐고 [Phase 4 Completion Evidence](phase-4-completion-evidence.md)의 exact frontend regression과 360×800 actual-browser smoke를 통과했다. Backend/Flyway/schema/API/CI와 dependency는 변경하지 않았다.

E2E 범위는 V1 핵심 flow 중심.

# 3. Infrastructure

- Docker build
- Compose config
- health checks
- ARM64

Scaffold PR은 Apple Silicon local Compose build로 ARM64를 검증한다. Gate 3 Release Candidate는 native ARM64 GitHub runner에서 exact PR head의 API/Web `linux/arm64` image를 build하며 QEMU evidence로 대체하지 않는다.

## Phase 5 Entry and Readiness

Entry PR:

- Markdown/frontmatter/local link와 current-state contradiction 검사
- exact `main`/`dev` release ancestry, annotated `v0.4.0` object/target와 GitHub Release 0 검증
- Product/runtime/test/Flyway/dependency/workflow/Docker/Compose diff 0
- ordinary docs → `dev` Hosted Backend/Frontend/Infrastructure success와 ARM64 expected skip

Phase 5-A:

- Production Compose/config의 deterministic render와 isolated startup
- Web/API/Postgres internal network, public DB port 0, health/startup dependency와 persistent path ownership
- exact image reference input 및 missing/invalid configuration fail-closed
- local development Compose와 Production canonical Compose의 분리
- static render에서 API/Web `build:` 0, API/Postgres published port 0, Web loopback-only와 network membership 검증
- exact disposable project `dev-form-dock-phase5a`에서 local-only image tag로 Postgres/API/Web health와 Web→API 확인
- PostgreSQL container recreation 전후 Flyway V1→V6 history를 확인하고 disposable container/network/volume residue 0으로 종료
- Production `local` profile, real Secret, live/shared DB, Cloudflare/public URL와 remote image publish 사용 0

Phase 5-B:

- disposable PostgreSQL에서 `pg_dump -Fc` private staging, custom-format readability, SHA-256/allowlist metadata와 metadata-last finalization
- existing completed set overwrite, missing/unsafe input, checksum mismatch와 partial success exposure의 fail-closed evidence
- verified complete set만 대상으로 configured retention dry-run/apply, partial/unrelated preservation와 exact bounded deletion
- distinct private directory off-host simulation의 partial copy/checksum/finalize와 overwrite 거절
- new labeled `dev-form-dock-scratch-*` resource only, host port 0와 existing resource reuse 거절
- `pg_restore --exit-on-error --no-owner --no-acl`, Flyway history V1→V6와 representative Creator/Survey/Question/Response/Answer integrity
- restored API health와 source/scratch/container/network/volume/temp artifact residue 0
- macOS Bash 3.2/Linux Bash, `shasum -a 256`/`sha256sum` fallback와 path quoting
- live/shared/Production database 접근 0

Phase 5-C1:

- fixed allowlist deployment state의 partial/unknown/duplicate/`latest` fail-closed와 candidate/previous SHA linkage
- canonical Production Compose, local exact image ID와 unique `dev-form-dock-delivery-*` project의 staging/health
- API/PostgreSQL host port 0, Web loopback-only, canonical network와 same-origin Web→API
- distinct previous application image rollback과 동일 PostgreSQL volume/Flyway V1→V6 보존
- Web/API/PostgreSQL bounded Docker logging static contract
- six provider-neutral monitoring signals의 OK/ALERT, invalid input exit와 Secret/raw payload output 0
- disposable container/network/volume/temp state residue 0과 GHCR/Production mutation 0

Phase 5-C2:

- exact Issue/branch job에만 job-scoped package write permission을 부여하고 ephemeral job token 외 credential 사용 0
- annotated release tag target/SHA/tree와 clean build context를 native `linux/arm64` build 전에 fail-closed 검증
- approved API/Web full-SHA tag collision 0일 때만 최초 publish하고 subsequent run은 OCI source identity와 recorded digest를 read-only 검증
- remote tag digest/platform/visibility 관찰 뒤 tag가 아닌 digest refs를 pull
- current canonical Compose/delivery tooling의 unique disposable staging에서 health, same-origin, exposure/network와 Flyway V1→V6 검증
- disposable container/network/volume/temp residue 0, moving alias/overwrite/delete/visibility/Production mutation 0
- 5-C1 local image ID를 remote publication evidence로 오인하지 않음

Phase 5-D1:

- canonical project/port/release/digest input mismatch fail-closed
- Mac/Docker arm64, disk, port와 exact FormDock resource absence의 sanitized read-only evidence
- immutable GHCR digest와 `linux/arm64` remote manifest identity
- existing external `edge`, running cloudflared attachment와 `ROUTE_ABSENT / DNS_NXDOMAIN` 분류
- healthy HomeOps authority와 outbound notification operator choice 분류
- fixture의 ambiguous target, duplicate/unknown field 거절과 mutation command audit
- Secret/config content read 0, Production/Cloudflare/HomeOps mutation 0

Phase 5-D2는 별도 live-operation authorization 뒤 exact environment에서만 검증한다. D1의 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`, accepted off-host risk와 operation/security contract를 input으로 required config/lock, clean Flyway startup, deploy, Cloudflare/public smoke, HomeOps registration과 rollback/recovery acceptance를 기록한다. 5-A~5-D1 evidence를 live activation PASS로 재사용하지 않는다.

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

Production activation 전 current health, exact artifact, disk/Docker, database state, required backup과 rollback을 확인한다. Public smoke는 mutation을 최소화하고 anonymous Public Survey/Response와 Creator login/Admin/Results의 대표 flow를 분리해 기록한다.

실제 dogfooding survey로 최종 end-to-end 확인.
