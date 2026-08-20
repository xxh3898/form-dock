---
title: V1 Acceptance Criteria
status: draft
version: 0.4
last_updated: 2026-08-20
---

# Creator

- [x] 승인된 계정 로그인 가능
- [x] API container restart 후 JDBC-backed Creator session 유지
- [x] login/logout/Admin mutation CSRF 보호
- [x] one-time bootstrap이 secret을 저장/log하지 않고 duplicate user를 만들지 않음
- [x] 타 Creator Survey 접근 차단
- [ ] Survey create/edit/duplicate
- [ ] DRAFT/OPEN/CLOSED 동작
- [ ] 6개 Question type
- [ ] structure lock
- [ ] 첫 Response와 structure mutation concurrency에서 둘 다 commit되지 않음
- [ ] first OPEN 이후 slug immutable
- [ ] OPEN direct delete 거절

## Phase 1 PR A Evidence

- [x] PostgreSQL 18 clean database에 Flyway V1 `users` schema 적용
- [x] Flyway V2 Spring Session JDBC table/index/FK 적용
- [x] email canonicalization, `ADMIN` role, BCrypt strength 10 hash와 timestamp persistence
- [x] bootstrap disabled write 0, 최초 provisioning 1건, 동일 email replay no-op
- [x] bootstrap missing/invalid input fail-closed와 password 15자/UTF-8 72 byte 경계
- [x] Session schema auto-init `never`, cleanup scheduler와 expired-session 삭제 동작

Frontend Login은 PR C 범위이므로 PR A evidence에는 포함하지 않는다.

## Phase 1 PR B Evidence

- [x] anonymous CSRF token 발급과 `X-CSRF-TOKEN` contract
- [x] canonical Creator login 200, unknown email/wrong password 동일 401/body
- [x] login 전후 session ID rotation과 JDBC-backed authenticated context 저장
- [x] authenticated `/me` 200, anonymous `/me` 401
- [x] logout 204 뒤 server session/context/cookie invalidation과 `/me` 401
- [x] login/logout/future Admin unsafe method CSRF 거절, same-origin only/CORS header 0
- [x] 두 번째 application context에서 unexpired JDBC session 복원, test timeout expiry
- [x] safe 503 dependency error, password/hash/session identifier response·log 노출 0
- [x] Spring REST Docs auth success/error snippets와 PostgreSQL/Testcontainers integration evidence

Frontend Login/Admin shell과 browser integration evidence는 PR C 범위이므로 PR B evidence에는 포함하지 않는다.

## Phase 1 PR C Evidence

- [x] React Router Declarative Mode의 `/`, `/login`, `/admin`, unknown route 동작
- [x] anonymous `/admin`에서 protected Creator content flash 없이 `/login` 이동
- [x] valid `/me` session restore와 safe Creator identity render
- [x] CSRF-backed login/logout, auth transition 뒤 token refresh와 stale token 1회 retry
- [x] invalid credential, expired session, CSRF와 transient failure의 stable code handling
- [x] accessible labels/autocomplete/alert/keyboard submit과 pending duplicate submit 방지
- [x] password/session identifier storage·log·rendered error 노출 0
- [x] Nginx `/login`·`/admin` SPA fallback과 same-origin `/api` proxy 유지

PR C merge와 post-merge `dev` validation을 포함한 완료 근거는 [Phase 1 Completion Evidence](phase-1-completion-evidence.md)에 기록한다. Phase 1은 `COMPLETE + RELEASED`이며 Phase 2 Survey Builder는 authorized/in progress다. Phase 2-A의 owner-scoped Survey DRAFT backend core만 구현됐고 Question/lifecycle completion/Builder frontend는 후속 slice다.

## Phase 2 Entry Contract Evidence

- [x] owner-scoped Survey/Question Admin endpoint, DTO와 success/error status 확정
- [x] duplicate, soft delete, lifecycle timestamp와 invalid-transition semantics 확정
- [x] reserved slug와 Phase 3 전 clickable Public Survey URL 금지 확정
- [x] stale request가 implicit `@Version`/ETag 요구가 아닌 authoritative transaction revalidation임을 확정
- [x] Question full semantic payload, Option identity와 reorder complete-set contract 확정
- [x] V3 `surveys`, V4 `questions`/`question_options`, V5 schema-only `survey_responses` migration ownership 확정
- [x] Phase 2-A→B→C→D serial implementation boundary와 Phase 3/Production exclusion 확정

이 checklist는 documentation/authorization evidence이며 Survey Builder Product acceptance 완료를 의미하지 않는다.

## Phase 2-A Survey DRAFT Core Evidence

- [x] V1/V2 변경 없이 V3 `surveys` clean migration, owner FK, lifecycle CHECK와 globally reserved slug 적용
- [x] DRAFT create, owner-only active list/detail, presence-aware metadata PATCH와 soft delete 구현
- [x] canonical slug normalization, non-ASCII fallback, bounded generated collision retry와 explicit conflict 처리
- [x] title/optional text Unicode code-point 경계와 unknown/null field validation
- [x] cross-owner/unknown/deleted Survey 동일 404 concealment, anonymous 401와 mutation CSRF 보호
- [x] Phase 2-A DTO `questions=[]`, `responseCount=0`, `structureLocked=false`를 Question/Response authority 없이 제공
- [x] REST Docs success/error contract와 PostgreSQL integration/concurrency test 추가

Phase 2-A merge와 post-merge `dev` validation 전에는 Phase 2-B를 시작하지 않는다. Phase 2 전체 완료는 A→D 전부 통합된 뒤 별도로 판정한다.

# Respondent

- [ ] 비로그인 OPEN Survey 접근
- [ ] 모바일 단계별 응답
- [ ] progress
- [ ] required/type validation
- [ ] atomic submit
- [ ] retry duplicate 방지
- [ ] same payload replay 200 / conflicting replay 409
- [ ] CLOSED 신규 submit 409 / 기존 동일 replay 200, unavailable public GET 404
- [ ] completion

# Results

- [ ] total count
- [ ] individual response
- [ ] choice summary
- [ ] scale summary
- [ ] text/number display
- [ ] CSV
- [ ] MULTIPLE_CHOICE option별 boolean column
- [ ] CSV formula injection 방어

# Data

- [ ] Flyway clean install
- [ ] PostgreSQL constraints
- [ ] no DB public exposure
- [ ] backup
- [ ] restore verification

# Operations

- [ ] ARM64 images
- [ ] Compose health
- [ ] Mac mini deploy
- [ ] Cloudflare route
- [ ] public smoke

# Dogfooding

- [ ] real survey created
- [ ] real external responses collected
- [ ] exported data used for actual analysis
