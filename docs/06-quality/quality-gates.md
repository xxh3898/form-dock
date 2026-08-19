---
title: Quality Gates
status: draft
version: 0.2
last_updated: 2026-08-19
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

`dev` 대상 PR의 required checks:

- Backend
- Frontend
- Infrastructure

현재 Validate workflow는 모든 `dev` PR에서 세 job을 실행하며 Infrastructure job이 Compose config와 image build를 검증한다.

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

# Current Entry Gate

```text
Phase 0                       COMPLETE
Application Scaffold         COMPLETE
Phase 1 Creator Foundation   AUTHORIZED
Survey Domain                NOT AUTHORIZED
```

# Repository Governance

`dev`는 PR을 통해서만 통합하고 `Backend`, `Frontend`, `Infrastructure`를 GitHub Actions source의 required checks로 사용한다. Required approving review는 0이며 repository administrator에게도 적용한다. Force push와 branch deletion은 허용하지 않는다.

Signed commit, linear history, CODEOWNERS approval, last-push approval, conversation resolution과 strict up-to-date는 현재 1인 integration branch에 요구하지 않는다. `main` protection은 release workflow와 required release checks가 정의되는 시점에 별도 적용한다.
