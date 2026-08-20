---
title: FormDock Roadmap
status: draft
version: 0.5
last_updated: 2026-08-20
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

Application scaffold와 Phase 1 Creator Foundation은 완료되어 `main`에 release됐다. Phase 2 Survey Builder contract가 다음 Product boundary로 승인됐다.

## Initial Implementation Slices

Phase 0 종료 뒤 다음 순서를 기본으로 한다. 각 항목은 별도 PR이며, 앞 PR의 contract와 검증이 `dev`에 통합된 뒤 다음 항목을 시작한다.

1. Backend/Frontend project scaffold와 CI baseline — `COMPLETE`
2. Creator authentication, JDBC session, one-time bootstrap — `COMPLETE + RELEASED`
3. Phase 2-A Survey DRAFT Core — `AUTHORIZED — NEXT`
4. Phase 2-B Question/Lock Data Foundation — `AUTHORIZED — AFTER 2-A`
5. Phase 2-C Survey Builder Backend Completion — `AUTHORIZED — AFTER 2-B`
6. Phase 2-D Survey Builder Frontend + Preview — `AUTHORIZED — AFTER 2-C`
7. Public Survey, atomic Response, idempotency — `NOT AUTHORIZED`
8. Result dashboard와 CSV export — `NOT AUTHORIZED`
9. Production Compose, deployment, backup/restore, dogfooding readiness — `NOT AUTHORIZED`

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

Status: `AUTHORIZED`

Authorized boundary:

- owner-scoped Survey CRUD, duplicate, soft delete와 DRAFT/OPEN/CLOSED lifecycle
- first OPEN 이후 immutable slug와 Admin DTO의 reserved public identity
- Question/Option CRUD, 6 types, zero-based gapless ordering과 Admin preview
- first canonical Response 이후 structure lock과 Survey row pessimistic lock
- lock authority를 위한 final `survey_responses` schema, COUNT/EXISTS read와 disposable test fixture

Phase 2는 다음 네 PR을 직렬로 구현한다. Scheduling은 동시 구현 권한이 아니며 각 slice는 직전 PR의 `dev` merge와 exact SHA/Validate 확인 후 시작한다.

1. **Phase 2-A — Survey DRAFT Core**
   - V3 `surveys` schema와 Survey persistence/domain
   - owner-scoped list/create/detail/update와 DRAFT soft-delete
   - DRAFT metadata와 slug allocation
   - stable Survey DTO/error/CSRF contract
   - final Survey DTO shape에서 `questions=[]`, `responseCount=0`, `structureLocked=false`
   - 위 값은 capability 부재의 logical guarantee이며 Question/Response repository, query 또는 stub 없음
   - Question, lifecycle transition, duplicate deep-copy, Public Survey와 Response table 제외
2. **Phase 2-B — Question/Lock Data Foundation**
   - V4 `questions`/`question_options` schema와 persistence
   - V5 final `survey_responses` schema-only canonical lock authority
   - Survey DTO wire shape 변경 없이 Questions는 V4 persistence, count/lock은 real V5 COUNT/EXISTS로 전환
   - 모든 후속 Question structure mutation의 real Response EXISTS와 ADR-0004 pessimistic lock boundary
   - DB/domain invariant tests
   - Product Response writer, Answer schema와 public API 제외
3. **Phase 2-C — Survey Builder Backend Completion**
   - Question create/update/delete/reorder와 six-type validation
   - open/close lifecycle, valid-structure OPEN gate와 slug immutability
   - deep Duplicate Survey, structure-lock behavior와 ownership concealment
   - Admin Survey/Question REST Docs, integration/concurrency evidence
4. **Phase 2-D — Survey Builder Frontend + Preview**
   - owner Survey list/create/edit/delete/duplicate/open/close UI
   - Question Builder, six-type configuration와 ordering
   - structure-locked/safe state-error UX와 Admin-only preview
   - reserved slug 표시만 허용하고 clickable Public Survey route는 제외

Phase 2 완료는 A→D 전부 `dev`에 merge되고 final integration evidence가 검증된 뒤 별도 판단한다.

# Phase 3 — Public Survey & Response

Status: `NOT AUTHORIZED`

- public slug
- OPEN/CLOSED
- step-by-step UX
- progress
- validation
- atomic response
- idempotency

# Phase 4 — Results & Export

Status: `NOT AUTHORIZED`

- response count
- question summaries
- individual response
- CSV

# Phase 5 — Production Readiness

Status: `NOT AUTHORIZED`

- Docker Compose
- Gate 3-approved target artifact deployment/health acceptance
- PostgreSQL
- health
- Cloudflare
- CI/CD
- backup/restore
- security review
- public smoke

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
