---
title: Quality Gates
status: draft
version: 0.3
last_updated: 2026-08-19
---

# Gate 0 — Contract

- 하나의 reviewable slice를 정의하는 Issue contract
- Current Phase와 implementation authorization evidence
- IN / OUT / explicitly unauthorized scope
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

PR은 관련 Issue, Phase/authorization, included/excluded scope, 실제 실행한 validation, contract/security/data impact, risk, recovery와 follow-up을 기록한다. 적용되지 않는 category는 `N/A — reason`, 실행하지 않은 check는 `NOT RUN — reason`으로 남기며 근거 없는 `PASS`를 쓰지 않는다.

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

기본 흐름은 `GPT Issue → Codex Issue-to-PR → required checks → GPT exact-head review → user merge`다. 한 번에 active implementation/governance slice 하나와 `Issue 1 → PR 1 → dev`를 기본으로 하며 oversized Issue는 coding 전에 분리한다.

Issue는 scope/authorization contract이고 PR은 implementation/evidence다. Green CI와 template conformance는 quality/governance evidence이지만 Phase authorization 위반이나 Product acceptance 미충족을 덮지 못한다. Template은 accepted ADR과 Product/Domain contract보다 우선하지 않는다.

`dev → main`은 Phase/vertical capability release boundary이며 production deployment와 별개다. Template이 `dev`에만 있는 동안에도 GPT/Codex의 normative body structure로 사용하되 GitHub chooser와 PR auto-fill activation은 정상 `dev → main` release까지 deferred한다.
