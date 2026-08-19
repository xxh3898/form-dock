---
title: V1 Acceptance Criteria
status: draft
version: 0.2
last_updated: 2026-08-19
---

# Creator

- [ ] 승인된 계정 로그인 가능
- [ ] API container restart 후 JDBC-backed Creator session 유지
- [ ] login/logout/Admin mutation CSRF 보호
- [ ] one-time bootstrap이 secret을 저장/log하지 않고 duplicate user를 만들지 않음
- [ ] 타 Creator Survey 접근 차단
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

Frontend Login은 PR C 범위이므로 PR A evidence에 포함하지 않는다.

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

Frontend Login/Admin shell과 end-to-end browser login은 PR C 범위이므로 Phase 1 전체 완료로 표시하지 않는다.

## Phase 1 PR C Evidence

- [x] React Router Declarative Mode의 `/`, `/login`, `/admin`, unknown route 동작
- [x] anonymous `/admin`에서 protected Creator content flash 없이 `/login` 이동
- [x] valid `/me` session restore와 safe Creator identity render
- [x] CSRF-backed login/logout, auth transition 뒤 token refresh와 stale token 1회 retry
- [x] invalid credential, expired session, CSRF와 transient failure의 stable code handling
- [x] accessible labels/autocomplete/alert/keyboard submit과 pending duplicate submit 방지
- [x] password/session identifier storage·log·rendered error 노출 0
- [x] Nginx `/login`·`/admin` SPA fallback과 same-origin `/api` proxy 유지

PR C evidence가 준비되어도 Phase 1 전체 완료는 PR merge와 post-merge `dev` validation 뒤 별도 gate에서 판정한다. Survey Domain은 계속 `NOT AUTHORIZED`다.

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
