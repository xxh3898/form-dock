---
title: FormDock Roadmap
status: draft
version: 2.1
last_updated: 2026-08-27
---

# Roadmap Principle

각 Phase는 실제 사용 가능한 vertical capability를 완성한 뒤 다음 단계로 이동한다.

# Phase 0 — Foundation & Contracts

Status: `COMPLETE`

Deliverables:

- Product docs
- Domain model
- ERD/data dictionary
- API/auth contract
- architecture
- quality gates
- operations
- ADR

Exit:

```text
V1 implementation contract reviewed and merged to dev
Separate application scaffold authorization granted
```

Phase 0 contract merge는 scaffold eligibility를 만들지만 구현 승인을 자동으로 부여하지 않는다.

Application scaffold와 Phase 1~4 capability는 `main`에 release됐다. Phase 3 repository Release identity는 annotated `v0.3.0`, Phase 4 Results / Export identity는 annotated `v0.4.0`이며 둘 다 Production 배포를 뜻하지 않는다. Phase 5는 serial Gate로 진행하며 Issue #93은 D2A local Production bootstrap만 승인한다. D2B와 Production completion은 별도 Gate다.

## Initial Implementation Slices

Phase 0 종료 뒤 다음 순서를 기본으로 한다. 각 항목은 별도 PR이며, 앞 PR의 contract와 검증이 `dev`에 통합된 뒤 다음 항목을 시작한다.

1. Backend/Frontend project scaffold와 CI baseline — `COMPLETE`
2. Creator authentication, JDBC session, one-time bootstrap — `COMPLETE + RELEASED`
3. Phase 2-A Survey DRAFT Core — `COMPLETE + RELEASED`
4. Phase 2-B Question/Lock Data Foundation — `COMPLETE + RELEASED`
5. Phase 2-C Survey Builder Backend Completion — `COMPLETE + RELEASED`
6. Phase 2-D Survey Builder Frontend + Preview — `COMPLETE + RELEASED`
7. Phase 2 Completion / Integration Evidence + Gate 3 release — `PASS + RELEASED`
8. Public Survey, atomic Response, idempotency — `COMPLETE + RELEASED`
9. Result dashboard와 CSV export — `COMPLETE + RELEASED — v0.4.0`
10. Production Runtime/Recovery/Delivery readiness — `AUTHORIZED — serial slices only`
11. Local Production Bootstrap — `LOCAL ACTIVE + ACCEPTED — DEV INTEGRATION PENDING`
12. Public/HomeOps Final Activation — `NOT AUTHORIZED`

세부 dependency와 boundary는 [Application Scaffold Contract](../03-architecture/scaffold-contract.md)를 따른다.

# Phase 1 — Creator Foundation

Status: `COMPLETE`

Included:

- persistent `User` model used as the authenticated Creator principal
- one-time environment bootstrap
- `users`와 Spring Session JDBC Flyway schema
- CSRF, Login, Logout, Current Creator
- Creator-only Admin API/route protection
- 최소 Login/Admin shell

Excluded:

- Survey CRUD와 lifecycle
- Survey ownership enforcement
- Question/Response/Result/CSV
- public signup, password reset, OAuth, team/workspace와 추가 RBAC

## Phase 1 Implementation Slices

1. PR A — Creator persistence, `users`/Spring Session Flyway schema, one-time bootstrap — `COMPLETE`
2. PR B — Login/Logout/Me/CSRF backend, session security, REST Docs와 integration tests — `COMPLETE`
3. PR C — Login/Admin shell frontend, protected navigation, Phase 1 integration evidence와 docs — `COMPLETE`

각 PR은 직전 변경이 `dev`에 병합되고 Validate를 통과한 뒤 시작한다. Survey aggregate가 없으므로 ownership 구현은 Phase 2로 넘긴다.

PR A/B/C의 merge와 post-merge `dev` validation을 포함한 Phase 1 완료 근거는 [Phase 1 Completion Evidence](../06-quality/phase-1-completion-evidence.md)에 기록한다.

Phase 1은 [Phase 1 Main Release Evidence](../06-quality/phase-1-main-release-evidence.md)의 final release diff, native ARM64, Flyway와 recovery-impact evidence를 거쳐 `main`에 release됐다. [ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)에 따라 이 release는 Production activation이 아니며, V1/V2 schema의 actual recovery action은 Gate 4가 소유한다.

# Phase 2 — Survey Builder

Status: `COMPLETE + RELEASED`

Authorized boundary:

- owner-scoped Survey CRUD, duplicate, soft delete와 DRAFT/OPEN/CLOSED lifecycle
- first OPEN 이후 immutable slug와 Admin DTO의 reserved public identity
- Question/Option CRUD, 6 types, zero-based gapless ordering과 Admin preview
- first canonical Response 이후 structure lock과 Survey row pessimistic lock
- lock authority를 위한 final `survey_responses` schema, COUNT/EXISTS read와 disposable test fixture

Phase 2는 다음 네 PR을 직렬로 구현한다. Scheduling은 동시 구현 권한이 아니며 각 slice는 직전 PR의 `dev` merge와 exact SHA/Validate 확인 후 시작한다.

1. **Phase 2-A — Survey DRAFT Core — COMPLETE + RELEASED**
   - V3 `surveys` schema와 Survey persistence/domain
   - owner-scoped list/create/detail/update와 DRAFT soft-delete
   - DRAFT metadata와 slug allocation
   - stable Survey DTO/error/CSRF contract
   - final Survey DTO shape에서 `questions=[]`, `responseCount=0`, `structureLocked=false`
   - 위 값은 capability 부재의 logical guarantee이며 Question/Response repository, query 또는 stub 없음
   - Question, lifecycle transition, duplicate deep-copy, Public Survey와 Response table 제외
2. **Phase 2-B — Question/Lock Data Foundation — COMPLETE + RELEASED**
   - V4 `questions`/`question_options` schema와 persistence
   - V5 final `survey_responses` schema-only canonical lock authority
   - Survey DTO wire shape 변경 없이 Questions는 V4 persistence, count/lock은 real V5 COUNT/EXISTS로 전환
   - 모든 후속 Question structure mutation의 real Response EXISTS와 ADR-0004 pessimistic lock boundary
   - transaction-local PostgreSQL lock timeout과 safe 503 mapping으로 bounded lock wait 보장
   - DB/domain invariant tests
   - Product Response writer, Answer schema와 public API 제외
3. **Phase 2-C — Survey Builder Backend Completion — COMPLETE + RELEASED**
   - Question create/update/delete/reorder와 six-type validation
   - open/close lifecycle, valid-structure OPEN gate와 slug immutability
   - deep Duplicate Survey, structure-lock behavior와 ownership concealment
   - Admin Survey/Question REST Docs, integration/concurrency evidence
4. **Phase 2-D — Survey Builder Frontend + Preview — COMPLETE + RELEASED**
   - owner Survey list/create/edit/delete/duplicate/open/close UI
   - Question Builder, six-type configuration와 ordering
   - structure-locked/safe state-error UX와 Admin-only preview
   - reserved slug 표시만 허용하고 clickable Public Survey route는 제외

Phase 2-D는 typed same-origin client, shared Admin session guard와 page-local canonical Survey state를 사용한다. Mutation은 backend가 반환한 canonical detail 또는 explicit refetch로 UI를 갱신하고, stale structure-lock 409는 real detail을 다시 읽어 structural controls를 잠근다. `/s/{slug}`와 SurveyResponse write/submit은 포함하지 않는다.

Phase 2-A→D는 모두 `dev`에 merge됐고 [Phase 2 Completion Evidence](../06-quality/phase-2-completion-evidence.md)가 exact merged dev의 integration을, [Phase 2 Main Release Evidence](../06-quality/phase-2-main-release-evidence.md)가 full diff, native ARM64와 disposable V2→V5 compatibility를 `PASS`로 판정했다. 해당 exact tree는 release merge를 통해 `main`에 반영됐다. 이 release는 Production deployment나 live migration을 수행하지 않았다.

# Phase 3 — Public Survey & Response

Status: `COMPLETE + RELEASED`

Authorized boundary:

- OPEN + not-deleted Survey의 anonymous public read와 lifecycle concealment
- respondent-safe ordered Question/Option DTO
- existing V5 `survey_responses`를 사용하는 first Product Response insert
- V6 `answers`/`answer_options` relational persistence
- canonical payload SHA-256, `clientSubmissionId` idempotency와 replay
- Survey row pessimistic lock 안의 atomic Response aggregate
- exact Public Response POST CSRF exemption, 1 MiB body limit와 ephemeral rate limit
- `/s/:slug` mobile-first step/progress/completion과 memory-only retry identity

Phase 3는 다음 네 PR을 직렬로 구현한다. 각 slice는 직전 PR이 user-merged되고 latest `dev` exact SHA/Validate가 확인된 뒤에만 시작한다.

1. **Phase 3-A — Public Survey Read Backend — COMPLETE + DEV INTEGRATED**
   - `GET /api/public/surveys/{slug}`
   - OPEN/not-deleted visibility와 unavailable-state 404 concealment
   - respondent-safe ordered six-type DTO, REST Docs와 PostgreSQL integration tests
   - Response writer, V6, respondent frontend 제외
2. **Phase 3-B — Response Data & Canonicalization Foundation — COMPLETE + DEV INTEGRATED**
   - V6 `answers`/`answer_options`, Answer persistence와 existing V5 SurveyResponse Product adapter
   - canonical JSON/payload hash와 idempotency repository primitives
   - Public POST/controller와 frontend 제외
3. **Phase 3-C — Atomic Public Submission Backend — COMPLETE + DEV INTEGRATED**
   - `POST /api/public/surveys/{slug}/responses`
   - same-Survey pessimistic lock, replay-before-new-OPEN, full validation와 atomic aggregate
   - exact CSRF exemption, 1 MiB/413, ephemeral 429 guard와 two-direction concurrency evidence
   - respondent frontend 제외
4. **Phase 3-D — Respondent Frontend — COMPLETE + DEV INTEGRATED**
   - `/s/:slug` Intro/step/progress/submit/completion
   - all-six-type input, 360px/accessibility와 memory-only `clientSubmissionId` retry
   - Results/CSV와 Production 제외

Phase 3-A→D는 [Phase 3 Completion Evidence](../06-quality/phase-3-completion-evidence.md)와 [Phase 3 Main Release Evidence](../06-quality/phase-3-main-release-evidence.md)의 integration, full release diff, native ARM64, disposable V5→V6 compatibility와 `RECOVERY PLAN REQUIRED` 검증을 거쳐 PR #60으로 `main`에 release됐다. Annotated tag `v0.3.0`은 이 repository Release의 identity다. Release와 tag는 Production activation 또는 recovery action 완료를 뜻하지 않는다.

# Phase 4 — Results & Export

Status: `COMPLETE + RELEASED — v0.4.0`

Authorized boundary:

- Creator-owned newest-first paginated Response list
- complete current Question order의 individual Response detail
- Survey overview와 bounded Question summary
- Choice counts/percentages와 Scale average/distribution
- Text/Number raw answer의 paginated list/detail browsing
- Survey-level memory-bounded CSV export
- shared Admin guard 안의 Results list/detail UI

Existing V5 `survey_responses`와 V6 `answers`/`answer_options`를 read-only authority로 사용한다. V1~V6 변경, V7, 새 table/index/materialized analytics, Response edit/delete/exclude, Public Response read, arbitrary search/filter/sort, advanced statistics와 NUMBER average는 포함하지 않는다.

Phase 4는 다음 네 PR을 직렬로 구현한다. 각 slice는 직전 PR이 `dev`에 merge되고 exact SHA/Validate가 확인된 뒤에만 시작한다.

1. **Phase 4-A — Creator Response Read Backend — COMPLETE + DEV INTEGRATED**
   - owner-scoped paginated list와 individual detail
   - fixed pagination/order, complete Question-order DTO와 concealment
   - REST Docs와 PostgreSQL integration tests
   - summary, CSV와 frontend 제외
2. **Phase 4-B — Result Summary Backend — COMPLETE + DEV INTEGRATED**
   - overview, Choice counts/percentages와 Scale average/distribution
   - bounded Text/Number semantics와 grouped query evidence
   - CSV와 frontend 제외
3. **Phase 4-C — CSV Export Backend — COMPLETE + DEV INTEGRATED**
   - UTF-8 BOM, RFC 4180/CRLF와 deterministic columns/rows
   - MULTIPLE_CHOICE boolean columns, formula injection 방어와 memory-bounded export
   - frontend 제외
4. **Phase 4-D — Results Frontend — COMPLETE + DEV INTEGRATED**
   - `/admin/surveys/:surveyId/responses`
   - `/admin/surveys/:surveyId/responses/:responseId`
   - overview/summary/list/detail/CSV action과 safe state/accessibility

4-A→D는 [Phase 4 Completion Evidence](../06-quality/phase-4-completion-evidence.md)의 provenance, full regression, isolated vertical smoke, 360×800 Chrome과 LibreOffice CSV 검증을 통과했다. [Phase 4 Main Release Evidence](../06-quality/phase-4-main-release-evidence.md)는 full diff, native ARM64, released-main same V6 compatibility와 previous-main rollback boundary를 검증해 Gate 3를 `PASS`로 판정했다. PR #79의 merge commit `1648047645720e67d5e928345c875dc53a93ff0e`이 exact tree를 `main`에 release했고 annotated `v0.4.0`이 repository Release identity다. GitHub Release는 필요하지 않아 생성하지 않았고 Production activation도 수행하지 않았다.

# Phase 5 — Production Readiness

Status: `AUTHORIZED — repository/readiness slices only`

Phase 5는 Gate 3-approved repository Release를 실제 Production에 안전하게 적용할 준비와 별도 activation evidence를 소유한다. `main` Release/tag는 Gate 4 PASS 또는 Production activation을 뜻하지 않는다.

Target boundary:

```text
Host           Mac mini
Runtime        Docker Compose
Services       form-dock-web / form-dock-api / form-dock-postgres
Public path    forms.chochiho.cloud → Cloudflare Tunnel → Web
Browser API    same-origin /api
Database       public port publish 금지
Admin DB       SSH/Tailscale internal only
```

`infra/compose.yaml`은 `dev-form-dock` local baseline이고 `infra/compose.production.yaml`은 Production canonical runtime contract다. Production artifact는 required image input으로 exact release SHA tag 또는 immutable digest를 사용하고 `latest`만으로 식별하지 않는다. Secret 값과 live configuration은 repository에 commit하지 않는다.

## Phase 5 Serial Slices

1. **Phase 5-A — Production Runtime Foundation — COMPLETE + DEV INTEGRATED**
   - production canonical Compose/config contract
   - Web/API/Postgres internal network, exposure, health/startup dependency와 persistent paths
   - immutable image reference input과 safe configuration-key documentation
   - isolated production-config validation
   - image publish, Secret 값, live DB/backup, Cloudflare와 Production activation 제외
2. **Phase 5-B — Backup / Restore / Recovery Readiness — COMPLETE + DEV INTEGRATED**
   - `pg_dump -Fc`, checksum/metadata와 safe backup tooling/runbook
   - retention/off-host contract와 isolated scratch restore evidence
   - fresh Production DB와 existing live DB/data를 구분한 recovery boundary
   - live Production backup/restore/migration 제외
3. **Phase 5-C1 — Delivery / Monitoring Foundation — COMPLETE + DEV INTEGRATED**
   - deployment identity와 canonical Compose isolated staging/health/application rollback contract
   - bounded Docker log rotation과 provider-neutral health/disk/backup/5xx signal boundary
   - local-only image ID evidence이며 remote artifact publication evidence 아님
   - Cloudflare, Secret과 Production activation 제외
4. **Phase 5-C2 — Exact Remote Artifact Publication Evidence — COMPLETE + DEV INTEGRATED**
   - Issue #89가 승인한 exact `v0.4.0` API/Web full-SHA tags만 GitHub-hosted native ARM64에서 GHCR에 최초 publish
   - remote digest/platform/visibility 검증과 digest pull canonical Compose acceptance는 [Phase 5-C2 evidence](../06-quality/phase-5-c2-remote-artifact-publication-evidence.md)에 기록
   - Production deployment/activation 제외
5. **Phase 5-D1 — Production Activation Preflight — COMPLETE + DEV INTEGRATED**
   - Mac mini, exact artifact, project/port, first activation/fresh DB를 sanitized read-only evidence로 확인
   - repository 밖 private config와 atomic operation lock, backup accepted risk, Cloudflare edge와 HomeOps integration contract 확정
   - target/Cloudflare/HomeOps/Secret/DB/backup mutation 0
6. **Phase 5-D2A — Local Production Bootstrap — LOCAL ACTIVE + ACCEPTED / DEV INTEGRATION PENDING**
   - exact environment와 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB` evidence에 따른 local Secret/config injection
   - exact artifact deploy, Flyway V1→V6, Creator bootstrap/finalization과 loopback acceptance
   - first local logical backup/verify와 disposable scratch restore
   - Cloudflare/HomeOps/public route, off-host copy와 Production completion 제외
7. **Phase 5-D2B — Public/HomeOps Final Activation — NOT AUTHORIZED**
   - Cloudflare/public routing과 exact public Product smoke
   - HomeOps FormDock service/reporter integration과 final rollback acceptance
   - 별도 명시적 live-operation 승인 전 시작 금지

각 slice는 직전 변경이 `dev`에 merge되고 exact SHA/required checks가 확인된 뒤 하나씩 시작한다. 5-C1의 local image evidence는 5-C2 remote publication evidence로 재사용하지 않았고, published digest를 독립 pull해 acceptance했다. 5-D1 evidence의 `dev` merge는 live operation 권한이 아니며 Issue #93만 D2A를 좁게 승인했다. [D2A evidence](../06-quality/phase-5-d2a-local-production-bootstrap-evidence.md) 범위의 local runtime은 active/accepted이고 changeset의 `dev` 통합을 기다린다. D2A PASS도 D2B, public readiness 또는 Production completion 권한이 아니다. 5-A→D2B 완료 뒤 별도 Gate 4 Completion Evidence를 작성한다.

# Phase 6 — Dogfooding

첫 실사용 후보:

```text
Cubing Hub V2.3 validation survey
```

# V1 Complete

Phase 0~6 완료 시:

```text
FormDock V1 — COMPLETE
```

# V1.1 Candidate Backlog

- Template
- QR 공유
- DATE question
- better charts
- response search/filter
- custom completion message
- expiration
- response limit

모두 현재 commitment가 아니다.

# Explicitly Not Scheduled

- Payment
- Subscription
- Marketplace
- Enterprise
- Native mobile app
- Full Google Forms replacement
