---
title: Quality Gates
status: draft
version: 0.1
last_updated: 2026-08-18
---

# Gate 0 — Contract

- Product docs reviewed
- Domain invariants agreed
- API/Auth/Data boundaries agreed
- active contradiction 0
- scaffold-blocking unresolved decision 0
- deferred decision에 owner와 결정 시점 명시
- accepted ADR과 authoritative document 동기화

# Gate 1 — PR Validation

Required according to changed paths:

- Backend
- Frontend
- Infrastructure
- Docker build where relevant

# Gate 2 — dev Integration

- merged dev exact SHA
- CI green
- no unresolved high-severity review findings

# Gate 3 — main Release Candidate

- full release diff validation
- ARM64 build
- Flyway compatibility
- backup/restore readiness

# Gate 4 — Production

- deploy success
- API/Web/Postgres health
- public smoke
- no unintended rollback

# Gate 5 — Dogfooding

- real survey end-to-end
- data usable
- operational issues captured

Green workflow 자체보다 실제 required semantics를 우선한다.
