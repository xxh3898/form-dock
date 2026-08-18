---
title: FormDock Roadmap
status: draft
version: 0.1
last_updated: 2026-08-18
---

# Roadmap Principle

각 Phase는 실제 사용 가능한 vertical capability를 완성한 뒤 다음 단계로 이동한다.

# Phase 0 — Foundation & Contracts

Status: `CURRENT`

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
V1 implementation contract approved
```

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
