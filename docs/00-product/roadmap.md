---
title: FormDock Roadmap
status: draft
version: 0.1
last_updated: 2026-08-18
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

별도 application scaffold 승인이 부여됐으며 현재 implementation slice는 1번 scaffold/CI baseline이다. Business feature authorization은 아직 없다.

## Initial Implementation Slices

Phase 0 종료 뒤 다음 순서를 기본으로 한다. 각 항목은 별도 PR이며, 앞 PR의 contract와 검증이 `dev`에 통합된 뒤 다음 항목을 시작한다.

1. Backend/Frontend project scaffold와 CI baseline
2. Creator authentication, JDBC session, one-time bootstrap
3. Survey CRUD와 lifecycle
4. Question Builder backend와 structure lock
5. Question Builder frontend와 preview
6. Public Survey, atomic Response, idempotency
7. Result dashboard와 CSV export
8. Production Compose, deployment, backup/restore, dogfooding readiness

세부 dependency와 boundary는 [Application Scaffold Contract](../03-architecture/scaffold-contract.md)를 따른다.

# Phase 1 — Creator Foundation

- User
- Spring Security session
- Login/Logout
- Survey CRUD
- ownership
- Admin shell

# Phase 2 — Survey Builder

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
