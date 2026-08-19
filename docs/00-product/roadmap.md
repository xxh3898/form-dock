---
title: FormDock Roadmap
status: draft
version: 0.2
last_updated: 2026-08-19
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

Application scaffold와 post-merge `dev` validation이 완료됐다. 현재 implementation authorization은 Phase 1 Creator Foundation에만 부여됐으며 Survey domain은 아직 열리지 않았다.

## Initial Implementation Slices

Phase 0 종료 뒤 다음 순서를 기본으로 한다. 각 항목은 별도 PR이며, 앞 PR의 contract와 검증이 `dev`에 통합된 뒤 다음 항목을 시작한다.

1. Backend/Frontend project scaffold와 CI baseline — `COMPLETE`
2. Creator authentication, JDBC session, one-time bootstrap — `AUTHORIZED`
3. Survey CRUD와 lifecycle — `NOT AUTHORIZED`
4. Question Builder backend와 structure lock
5. Question Builder frontend와 preview
6. Public Survey, atomic Response, idempotency
7. Result dashboard와 CSV export
8. Production Compose, deployment, backup/restore, dogfooding readiness

세부 dependency와 boundary는 [Application Scaffold Contract](../03-architecture/scaffold-contract.md)를 따른다.

# Phase 1 — Creator Foundation

Status: `AUTHORIZED`

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
3. PR C — Login/Admin shell frontend, protected navigation, Phase 1 integration evidence와 docs — `PENDING`

각 PR은 직전 변경이 `dev`에 병합되고 Validate를 통과한 뒤 시작한다. Survey aggregate가 없으므로 ownership 구현은 Phase 2로 넘긴다.

# Phase 2 — Survey Builder

- Survey CRUD와 DRAFT/OPEN/CLOSED lifecycle
- Creator ownership enforcement
- Question/Option CRUD
- 6 types
- ordering
- Preview
- mutation integrity

# Phase 3 — Public Survey & Response

- public slug
- OPEN/CLOSED
- step-by-step UX
- progress
- validation
- atomic response
- idempotency

# Phase 4 — Results & Export

- response count
- question summaries
- individual response
- CSV

# Phase 5 — Production Readiness

- Docker Compose
- ARM64
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
