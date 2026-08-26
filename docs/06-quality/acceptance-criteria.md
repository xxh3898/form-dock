---
title: V1 Acceptance Criteria
status: draft
version: 1.7
last_updated: 2026-08-26
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
- [x] 첫 Response와 structure mutation concurrency에서 둘 다 commit되지 않음
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

Phase 3-C evidence는 reviewed tree 그대로 `dev`에 통합돼 Phase 3-D prerequisite를 충족했다.

## Phase 3-D Respondent Frontend Evidence

- [x] Admin guard 밖의 유일한 public `/s/:slug` route와 dedicated strict Public DTO/client
- [x] Intro, ordered step, visible progress, previous/next, final submit와 completion
- [x] six Question type, optional omission, exact text와 NUMBER decimal string 보존
- [x] form instance당 memory-only UUID 하나와 edit/400/413/429/503/network retry 재사용
- [x] stable 400/404/409/413/429/503 code 기반 한국어 UX와 raw server message 미노출
- [x] loading, unavailable, safe GET retry와 single-flight submit
- [x] heading/fieldset/legend/label/describedby/focus/live status와 360px touch layout
- [x] Admin auth/session/CSRF regression 유지와 Public POST Creator CSRF request 0
- [x] backend/Flyway/API/schema/dependency/CI/Phase 4/Production 변경 0

Phase 3-D는 reviewed tree 그대로 `dev`에 통합됐고 exact merged `dev` regression을 통과했다.

## Phase 3 Completion / Integration Evidence

- [x] Phase 3-A/B/C/D reviewed tree와 `dev` integration tree provenance 확인
- [x] exact merged `dev` Hosted Backend 151/151, Frontend 86/86와 Infrastructure SUCCESS
- [x] PostgreSQL 18.6 Testcontainers와 clean Flyway V1→V6, V1→V5 byte 불변성 확인
- [x] anonymous Public GET concealment와 respondent-safe ordered six-type DTO 통합 검증
- [x] canonicalization, first 201, replay 200, conflict 409와 lifecycle ordering 통합 검증
- [x] mutation-first/submit-first가 같은 Survey lock을 사용하고 forbidden dual commit이 없음을 검증
- [x] `/s/:slug` six-type/zero-question/retry/pending/cross-slug/a11y regression 86/86
- [x] Phase 4 Result/CSV, V7, Production, tag와 deploy scope leak 0

통합 상세는 [Phase 3 Completion Evidence](phase-3-completion-evidence.md), Gate 3 full diff/ARM64/Flyway/recovery 근거는 [Phase 3 Main Release Evidence](phase-3-main-release-evidence.md)에 기록한다. Phase 3은 PR #60으로 `main`에 release됐고 annotated tag `v0.3.0`이 repository Release identity다. Phase 4 Results / Export도 Completion/Main Release Evidence를 통과해 PR #79와 annotated `v0.4.0`으로 `main`에 release됐다. 두 Release 모두 Production activation을 포함하지 않는다.

## Phase 4 Entry Contract Evidence

- [x] Phase 3 `COMPLETE + RELEASED`, `v0.3.0` repository identity와 Production non-activation truth 동기화
- [x] owner-scoped Response list의 page 0/size 50, 1..100 bound와 newest-first fixed order 확정
- [x] complete current Question-order detail, unanswered `answer=null`과 `RESPONSE_NOT_FOUND` concealment 확정
- [x] Choice percentage와 Scale average/distribution의 answered-count denominator, scale 2 `HALF_UP` 확정
- [x] Text/Number raw value를 paginated list/detail로 제한하고 NUMBER average를 deferred로 유지
- [x] UTF-8 BOM once, RFC 4180/CRLF, deterministic row/column과 MULTIPLE boolean CSV 확정
- [x] formula-like dynamic string 방어와 memory-bounded read-only export 경계 확정
- [x] existing V5/V6 read authority, V1~V6 변경/V7/new table/index/materialized analytics 0 확정
- [x] Creator session/owner concealment, new CSRF exemption/CORS/Public Response read 0 확정
- [x] Phase 4-A→B→C→D serial slices와 Production exclusion 확정

위 checklist는 documentation/authorization evidence다. 아래 Results Product acceptance는 각 implementation slice가 `dev`에 통합되고 실제 regression을 통과할 때만 완료 처리한다.

## Phase 4-A Creator Response Read Backend Evidence

- [x] owner-scoped Response list/detail production code와 dedicated DTO 구현
- [x] database-bounded newest-first page와 same-timestamp Response ID tie-break 구현
- [x] current Question 전체, optional unanswered null, six-type Answer 표현 구현
- [x] `RESPONSE_NOT_FOUND`, pagination validation, Survey concealment와 anonymous guard 회귀 test 작성
- [x] list/detail REST Docs와 transport/internal metadata 비노출 검증 작성
- [x] V1~V6 변경, V7/new schema/index, Response write, summary/CSV/frontend scope leak 0
- [x] PR merge와 post-merge exact `dev` Validate

Phase 4-A는 reviewed tree 그대로 `dev`에 통합됐고 exact merged `dev` regression을 통과했다.

## Phase 4-B Result Summary Backend Evidence

- [x] owner-first overview와 Question position 순 common summary 구현
- [x] Choice 전체 Option count, answered-count denominator와 scale 2 `HALF_UP` 구현
- [x] MULTIPLE percentage 합계 100% 초과와 zero-count/no-answer Option 구현
- [x] Scale average, configured 전체 bucket과 zero-count/no-answer 상태 구현
- [x] Text/Number answeredCount-only 및 raw/transport/internal metadata output 0 검증 작성
- [x] exact Survey scope의 고정 grouped SQL과 Response/Question/Option별 query loop 0 확인
- [x] representative/zero/concealment REST Docs와 PostgreSQL integration regression 작성
- [x] V1~V6 변경, V7/schema/index, Product write, CSV/frontend scope leak 0
- [x] PR merge와 post-merge exact `dev` Validate

Phase 4-B는 reviewed tree 그대로 `dev`에 통합됐고 exact merged `dev` regression을 통과했다.

## Phase 4-C CSV Export Backend Evidence

- [x] owner/schema 선행 확인 뒤 CSV header/body를 여는 authenticated export endpoint 구현
- [x] UTF-8 BOM once, RFC 4180/CRLF와 한글/comma/quote/LF/CRLF round-trip 검증 작성
- [x] Question/Option position, Response timestamp/ID tie-break와 six-type canonical cell 구현
- [x] MULTIPLE Option boolean column, optional unanswered와 formula-like dynamic string 방어 구현
- [x] exact Survey-scoped cursor/fetch size 256과 current-Response-only memory boundary 구현
- [x] 257 Response boundary, DRAFT/OPEN/CLOSED, zero/header-only, concealment/auth/read-only regression 작성
- [x] success header, zero-response와 concealment REST Docs 작성
- [x] V1~V6/Flyway/schema/index/dependency/CI/frontend/Product write 변경 0
- [x] PR merge와 post-merge exact `dev` Validate

Phase 4-C backend는 reviewed tree 그대로 `dev`에 통합됐고 exact merged `dev` regression을 통과했다.

## Phase 4-D Results Frontend Evidence

- [x] shared Admin guard 안의 Results overview/detail canonical route와 Survey list navigation 구현
- [x] list/summary/detail strict runtime parser, invalid ID 선행 차단과 stable error mapping 구현
- [x] status/overview, Choice/Scale/Text/Number summary와 newest-first bounded pagination 구현
- [x] six-type detail, optional unanswered, exact multiline text와 read-only control 경계 구현
- [x] same-origin CSV Blob/filename/revoke, JSON error 비다운로드와 pending single-flight 구현
- [x] loading/zero/out-of-range/concealment/transient retry의 한국어 safe state 구현
- [x] semantic heading/table/time/live state, route focus와 360px bounded overflow regression 작성
- [x] backend/Flyway/schema/API/dependency/CI/Public Results/Response mutation 변경 0
- [x] PR merge, same-tree provenance와 post-merge exact `dev` Validate

Phase 4-D frontend는 reviewed tree 그대로 `dev`에 통합됐다. 실제 Chrome 360×800 smoke에서 발견된 page-level horizontal overflow는 별도 Issue #73 / PR #74로 수정됐고, 새 exact `dev`에서 overview/detail, bounded table scroll와 keyboard focus를 다시 검증했다. 전체 근거는 [Phase 4 Completion Evidence](phase-4-completion-evidence.md)에 기록한다.

## Phase 4 Completion / Main Release Candidate Evidence

- [x] Phase 4-A→D, responsive blocker와 completion evidence의 reviewed/integrated tree provenance 확인
- [x] exact `main...candidate` full release diff와 expected Phase 4 scope 확인
- [x] native ARM64 runner에서 exact head API/Web `arm64/linux` image build 확인
- [x] V1~V6 main/candidate byte identity, V7+ 0과 clean PostgreSQL 18.6 startup 확인
- [x] released-main V6 fixture → candidate same-schema Results read와 data/Flyway history 보존 확인
- [x] previous-main application rollback boundary `TESTED`와 `NO DATA/SCHEMA IMPACT` 분류 확인
- [x] Product/test/migration/dependency/workflow/Docker/Compose evidence-PR diff 0
- [x] Production/tag/GitHub Release/Secret/live operation 0

상세 Gate 3 근거는 [Phase 4 Main Release Evidence](phase-4-main-release-evidence.md)에 기록한다. Evidence merge와 latest `dev` 검증 뒤 PR #79가 exact tree를 `main`에 release했고 annotated `v0.4.0` tag가 생성됐다. GitHub Release와 Production activation은 수행하지 않았다.

## Phase 5 Entry Contract Evidence

- [x] Phase 4 `COMPLETE + RELEASED — v0.4.0`과 GitHub Release 미생성 사실 동기화
- [x] exact Phase 4 `main` release merge ancestry를 Entry branch와 후속 `dev` merge commit으로 보존
- [x] Mac mini Docker Compose, Web/API/Postgres, same-origin `/api`, private PostgreSQL target 확정
- [x] `infra/compose.yaml` local baseline과 Production canonical Compose 구분
- [x] exact SHA tag 또는 immutable digest artifact identity와 `latest`-only 금지 확정
- [x] Secret non-commit 및 live injection 별도 승인 경계 확정, storage mechanism 임의 결정 0
- [x] fresh Production DB와 existing live DB/data 상태를 live evidence 전까지 추정하지 않음
- [x] `pg_dump -Fc`, checksum/metadata, retention/off-host와 isolated scratch restore ownership 확정
- [x] Postgres/API/Web health와 Cloudflare/public application smoke를 분리
- [x] application image rollback과 Flyway/recovery action 분리
- [x] Phase 5-A→5-B→5-C1→5-C2→5-D serial ownership 확정
- [x] Product/runtime/schema/workflow/Secret/Production mutation 0

위 checklist는 Phase 5 repository/readiness Entry authorization이다. Production Compose 구현, image publish, live backup/migration/deploy, Secret/Cloudflare와 public activation 완료를 뜻하지 않는다.

## Phase 5-A Production Runtime Foundation Evidence

- [x] local `infra/compose.yaml`과 canonical `infra/compose.production.yaml` authority 분리
- [x] API/Web required image input, Production `build:` authority와 hard-coded `latest` 0
- [x] PostgreSQL/API host port 0, Web `127.0.0.1` bind와 Web database network membership 0
- [x] Postgres/API/Web health, healthy startup dependency와 `unless-stopped` restart policy
- [x] PostgreSQL named volume persistence와 destructive volume removal 경계 문서화
- [x] base runtime의 secure Session cookie, Flyway V1→V6와 explicit bootstrap opt-in 유지
- [x] non-secret `infra/production.env.example`, Production real env/Secret commit 0
- [x] isolated Compose project에서 clean startup, health, Web→API와 container recreation persistence 검증
- [x] Infrastructure required job에 Production Compose static contract validation 추가
- [x] Product/API/Flyway/schema/dependency/image publish/live Production mutation 0

이 checklist는 Phase 5-A reviewed tree의 repository/isolated evidence이며 exact tree로 `dev`에 통합됐다. Actual image publication, Secret, live database, Cloudflare와 Production activation은 완료되지 않았다.

## Phase 5-B Backup/Restore/Recovery 준비 근거

- [x] `pg_dump -Fc` private partial artifact와 custom-format readability 검증
- [x] SHA-256, allowlist metadata, metadata-last finalization과 completed set overwrite 0
- [x] macOS/Linux SHA-256 fallback, quoted absolute path와 private permission discipline
- [x] verified completed set만 count하는 configured retention dry-run/apply와 bounded deletion
- [x] partial/unrelated file delete 0과 path/identity validation fail-closed
- [x] provider-neutral distinct filesystem target의 partial copy/checksum/metadata-last finalize
- [x] checksum mismatch restore-before-resource-creation 거절
- [x] new `dev-form-dock-scratch-*` container/network/volume only와 host port 0
- [x] `pg_restore --exit-on-error --no-owner --no-acl`, Flyway V1→V6와 representative data 보존
- [x] restored API health와 source/scratch/temp residue 0
- [x] Infrastructure required job에 secret-free disposable recovery smoke 추가
- [x] Product/API/Flyway/schema/dependency/live Production mutation 0

이 checklist는 Phase 5-B reviewed tree의 repository/disposable evidence이며 exact tree로 `dev`에 통합됐다. Live schedule, actual off-host target, Production DB backup/restore/migration과 activation은 완료되지 않았다.

## Phase 5-C1 Delivery/Monitoring Foundation 준비 근거

- [x] fixed allowlist deployment state와 release/image/Compose/non-secret config/timestamp/previous identity 표현
- [x] partial/unknown/duplicate field, malformed identity와 `latest` authority 거절
- [x] canonical Production Compose와 local exact image ID를 사용하는 unique disposable staging
- [x] PostgreSQL/API host port 0, Web loopback-only, canonical network와 same-origin Web→API health
- [x] candidate/previous state SHA linkage, first activation `NONE`과 distinct image/config application rollback
- [x] rollback 중 PostgreSQL volume/Flyway V1→V6 보존, destructive DB rollback 0
- [x] Web/API/PostgreSQL bounded Docker `json-file` rotation baseline
- [x] health/disk/completed backup freshness/explicit 5xx aggregate의 provider-neutral NDJSON/exit contract
- [x] notification provider/credential, GHCR publish, Product/API/Flyway/schema/dependency/live Production mutation 0
- [x] isolated staging container/network/volume/temp state residue 0

이 checklist는 Phase 5-C1 PR head의 repository/disposable evidence다. `dev` merge와 post-merge exact checks 전에는 5-C2 remote artifact publication Issue를 시작하지 않는다. 5-C1 local image ID는 published digest 또는 Production activation 증거가 아니다.

# Respondent

- [x] 비로그인 OPEN Survey 접근
- [x] 모바일 단계별 응답
- [x] progress
- [x] required/type validation
- [x] atomic submit
- [x] retry duplicate 방지
- [x] same payload replay 200 / conflicting replay 409
- [x] CLOSED 신규 submit 409 / 기존 동일 replay 200, unavailable public GET 404
- [x] completion

# Results

- [x] total count
- [x] individual response
- [x] choice summary
- [x] scale summary
- [x] text/number display
- [x] CSV
- [x] MULTIPLE_CHOICE option별 boolean column
- [x] CSV formula injection 방어

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
