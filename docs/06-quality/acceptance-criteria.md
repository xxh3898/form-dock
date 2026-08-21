---
title: V1 Acceptance Criteria
status: draft
version: 0.8
last_updated: 2026-08-21
---

# Creator

- [x] 승인된 계정 로그인 가능
- [x] API container restart 후 JDBC-backed Creator session 유지
- [x] login/logout/Admin mutation CSRF 보호
- [x] one-time bootstrap이 secret을 저장/log하지 않고 duplicate user를 만들지 않음
- [x] 타 Creator Survey 접근 차단
- [x] Survey create/edit/duplicate
- [x] DRAFT/OPEN/CLOSED 동작
- [x] 6개 Question type
- [x] structure lock
- [ ] 첫 Response와 structure mutation concurrency에서 둘 다 commit되지 않음
- [x] first OPEN 이후 slug immutable
- [x] OPEN direct delete 거절

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

PR C merge와 post-merge `dev` validation을 포함한 완료 근거는 [Phase 1 Completion Evidence](phase-1-completion-evidence.md)에 기록한다. Phase 1과 Phase 2 Survey Builder는 `COMPLETE + RELEASED`이며 Phase 3 Public Survey/Response contract가 authorized됐다.

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

Phase 2-A는 reviewed tree와 같은 tree로 `dev`에 merge돼 완료됐다. 별도 post-merge push run은 확인된 evidence 없이 PASS로 기록하지 않는다. Phase 2 전체 완료는 A→D 전부 통합된 뒤 별도로 판정한다.

## Phase 2-B Question/Lock Data Foundation Evidence

- [x] V1/V2/V3 변경 없이 V4 Question/Option과 V5 schema-only SurveyResponse clean migration
- [x] six-type Question/Option persistence, DB/domain configuration invariant와 ordered read
- [x] Survey list/detail wire shape를 유지한 real Question/Response COUNT/EXISTS authority
- [x] Product SurveyResponse writer, Answer schema와 Question mutation endpoint 0
- [x] owner/deleted/current-status revalidation과 caller-owned Survey `PESSIMISTIC_WRITE`
- [x] PostgreSQL blocking evidence, bounded timeout/deadlock safe mapping과 partial caller write 0
- [x] Phase 2-A owner/slug/soft-delete/PATCH-delete concurrency regression 유지

Phase 2-B는 exact reviewed tree로 `dev`에 merge돼 Phase 2-C prerequisite를 충족했다.

## Phase 2-C Survey Builder Backend Completion Evidence

- [x] all six Question type create와 complete-state update/Option identity contract
- [x] Question delete/reorder의 UNIQUE-safe zero-based gapless normalization
- [x] 모든 Question mutation의 Survey row lock + real V5 EXISTS, locked 409와 bounded 503/partial write 0
- [x] owner/Question concealment, stable general/configuration error와 new unsafe endpoint CSRF
- [x] DRAFT→OPEN↔CLOSED lifecycle, first-open timestamp와 lock 이후 current structure validation
- [x] DRAFT/OPEN/CLOSED/Response-present source의 atomic deep duplicate와 fresh identity/Response copy 0
- [x] Admin REST Docs와 PostgreSQL 18.6 integration/concurrency regression

Phase 2-C는 exact reviewed tree로 `dev`에 merge돼 Phase 2-D prerequisite를 충족했다.

## Phase 2-D Survey Builder Frontend + Preview Evidence

- [x] shared Admin guard와 `/admin`→`/admin/surveys`, list/create/Builder/Preview nested route
- [x] relative same-origin typed Survey client, memory-only CSRF one-retry와 stable status/code/fieldErrors
- [x] canonical list/create/metadata/lifecycle/duplicate/soft-delete UI와 reserved slug non-link
- [x] all six Question type complete payload, Choice Option identity, NUMBER decimal string와 complete-set reorder
- [x] canonical `structureLocked` controls, stale 409 refetch와 editable metadata/Duplicate recovery
- [x] authenticated read-only Admin Preview와 Public route/request/Response submit 0
- [x] loading/empty/retry, 404/409/503 safe recovery와 semantic/accessibility/narrow-layout baseline
- [x] Vitest/React Testing Library client, route와 representative Creator workflow regression

Phase 2-D는 `dev`에 merge됐고 exact merged dev regression을 통과했다.

## Phase 2 Completion / Integration Evidence

- [x] Phase 2-A/B/C/D reviewed tree와 `dev` merge provenance 확인
- [x] exact merged `dev` Hosted Backend 107/107, Frontend 49/49와 Infrastructure SUCCESS
- [x] PostgreSQL 18.6 Testcontainers와 clean Flyway V1-V5 migration
- [x] owner Survey CRUD/lifecycle, six-type Builder, structure lock, deep duplicate와 Preview 통합 matrix
- [x] V1-V5 immutable, Product SurveyResponse writer/Answer runtime와 `/s/{slug}` route 0
- [x] Phase 3 Public Survey/Response, Result/CSV와 Production authorization leak 0

상세 integration evidence는 [Phase 2 Completion Evidence](phase-2-completion-evidence.md), Gate 3와 release provenance는 [Phase 2 Main Release Evidence](phase-2-main-release-evidence.md)에 기록한다. Phase 2 exact tree는 `main`에 release됐지만 Production activation은 수행하지 않았다. 실제 first Public Response insert와 structure mutation의 양방향 concurrency criterion은 Phase 3-C가 Product Response writer를 구현한 뒤 검증하므로 위 Creator checklist에서 아직 완료 처리하지 않는다.

## Phase 3 Entry Contract Evidence

- [x] Phase 2 `COMPLETE + RELEASED`와 Production non-activation truth 동기화
- [x] OPEN/not-deleted Public GET 200, unavailable lifecycle/identity identical 404와 respondent-safe DTO 확정
- [x] V1~V5 immutable, V6 `answers`/`answer_options` ownership과 relational constraint 확정
- [x] fixed canonical JSON/order, UTF-8 SHA-256, clientSubmissionId exclusion과 201/200/409 replay 확정
- [x] same Survey pessimistic lock, replay-before-new-OPEN와 atomic aggregate rollback 확정
- [x] mutation-first/submit-first PostgreSQL evidence와 bounded 503 criterion 확정
- [x] exact Public POST CSRF exemption, 1 MiB/413와 ephemeral non-tracking 429 contract 확정
- [x] `/s/:slug`, 360px step/progress/completion과 memory-only retry identity 확정
- [x] Phase 3-A→B→C→D serial slices와 Phase 4 Results/CSV/Production exclusion 확정

위 Phase 3 Entry checklist는 documentation/authorization evidence다. 그 checklist 자체가 Phase 3 runtime, V6, Public API와 respondent UI acceptance 완료를 뜻하지 않는다.

## Phase 3-A Public Survey Read Backend Evidence

- [x] anonymous exact `GET /api/public/surveys/{slug}`에서 OPEN + not-deleted Survey 조회
- [x] DRAFT/CLOSED/deleted/unknown slug의 동일 `404 SURVEY_NOT_FOUND` concealment
- [x] internal Survey ID/owner/Admin metadata/count/lock/auth field가 없는 respondent-safe DTO
- [x] six Question type, ordered Question/Option, unused `null`, non-Choice `[]`와 plain decimal NUMBER bound
- [x] Admin anonymous 401와 unsafe CSRF 유지, broad public matcher/CSRF exemption/CORS 추가 0
- [x] PostgreSQL 18.6 V3/V4 row integration과 Spring REST Docs success/error evidence
- [x] V1~V5/Flyway/dependency 변경, SurveyResponse Product write, V6와 frontend route 0

Phase 3-A는 reviewed tree 그대로 `dev`에 통합돼 Phase 3-B prerequisite를 충족했다.

## Phase 3-B Response Data & Canonicalization Foundation Evidence

- [x] V1~V5 불변과 clean V1→V6 migration, V6 소유 table `answers`/`answer_options` 2개만 추가
- [x] Answer/AnswerOption FK, unique, CHECK, CASCADE/NO ACTION과 existing V5 정의 불변
- [x] exact text 보존, optional omission, Question/Option 정렬과 SCALE/NUMBER canonical string
- [x] fixed compact JSON UTF-8 literal vector와 SHA-256 lowercase hex, transport metadata 제외
- [x] caller-owned SurveyResponse/Answer transaction과 aggregate failure rollback
- [x] same/different hash replay와 concurrent unique race의 canonical row 재조회 수렴
- [x] Public POST/controller, Survey lock orchestration, HTTP mapping, frontend와 Phase 4 기능 0

Phase 3-B evidence는 reviewed tree 그대로 `dev`에 통합돼 Phase 3-C prerequisite를 충족했다.

## Phase 3-C Atomic Public Submission Backend Evidence

- [x] exact anonymous Public Response POST와 exact CSRF exemption, broad public matcher/CORS 추가 0
- [x] first create 201, canonical replay 200, conflicting replay 409와 canonical identity/timestamp 유지
- [x] DRAFT/deleted/unknown concealment, CLOSED replay-before-new-OPEN lifecycle ordering
- [x] six Question type, required/value/ownership, text code-point와 `NUMERIC(19,4)` boundary validation
- [x] 1 MiB actual raw body limit, JSON-only 415와 bounded direct-peer in-memory rate guard
- [x] same-Survey pessimistic lock의 mutation-first/submit-first, bounded 503와 same-ID retry
- [x] SurveyResponse/Answer/AnswerOption atomic aggregate, partial write와 duplicate aggregate 0
- [x] V1~V6/Flyway/dependency/frontend/Phase 4/Production 변경 0

이 evidence는 current Phase 3-C implementation tree 기준이다. `dev` 통합이나 Phase 3-D authorization을 미리 주장하지 않으며 user merge와 latest merged `dev` validation은 별도 gate다.

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
