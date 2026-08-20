---
title: Quality Gates
status: draft
version: 0.7
last_updated: 2026-08-20
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

`ARM64 Release Artifact`는 branch protection의 ordinary required check를 대체하지 않는 semantic release job이다. `main` 대상 PR 또는 `release-evidence/* → dev` PR에서만 실행하며 existing API/Web Dockerfile의 native `linux/arm64` build와 image metadata를 검증한다.

PR은 관련 Issue, Phase/authorization, included/excluded scope, 실제 실행한 validation, contract/security/data impact, risk, recovery와 follow-up을 기록한다. 적용되지 않는 category는 `N/A — reason`, 실행하지 않은 check는 `NOT RUN — reason`으로 남기며 근거 없는 `PASS`를 쓰지 않는다.

# Gate 2 — dev Integration

- merged dev exact SHA
- CI green
- no unresolved high-severity review findings
- related implementation Issue completed close
- next Issue planning

Issue completion close는 위 merged dev exact SHA와 CI를 확인한 뒤 수행한다. Green CI나 merge 전 PR 상태만으로 Issue를 닫지 않는다.

# Gate 3 — main Release Candidate

- exact `main...dev` full release diff validation
- target-architecture ARM64 release artifact build
- disposable/test PostgreSQL에서의 Flyway compatibility
- recovery-impact classification

모든 Release Candidate는 PR의 Data/Migration evidence에서 recovery impact를 `NO DATA/SCHEMA IMPACT` 또는 `RECOVERY PLAN REQUIRED`로 분류한다. `RECOVERY PLAN REQUIRED`이면 main promotion 전에 schema/data impact와 Production activation을 막는 recovery action을 명시한다. Gate 3는 plan과 compatibility만 검증하며 live migration, backup 또는 restore를 실행하지 않는다.

Phase 1 main Release Candidate의 full-diff, ARM64, Flyway와 recovery 분류 evidence는 [Phase 1 Main Release Evidence](phase-1-main-release-evidence.md)에 기록한다. Phase completion provenance는 [Phase 1 Completion Evidence](phase-1-completion-evidence.md)와 [Phase 2 Completion Evidence](phase-2-completion-evidence.md), Gate ownership의 accepted decision은 [ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)를 따른다.

# Gate 4 — Production Readiness and Activation

- Gate 3가 요구한 recovery action 완료
- schema/data impact와 existing live data가 있을 때 required predeploy backup
- recovery plan이 요구하는 isolated scratch restore verification
- target environment에 적용되는 retention/off-host copy와 live recovery readiness
- deploy success
- API/Web/Postgres health
- public smoke
- no unintended rollback

Gate 4의 operational evidence를 Gate 3 release eligibility로 대체하거나 그 반대로 재사용하지 않는다. Required recovery action이 남아 있으면 schema/data-impacting release를 Production에 activate하지 않는다.

# Gate 5 — Dogfooding

- real survey end-to-end
- data usable
- operational issues captured

Green workflow 자체보다 실제 required semantics를 우선한다.

# Current Entry Gate

```text
Phase 0                       COMPLETE
Application Scaffold         COMPLETE
Phase 1 Creator Foundation   COMPLETE + RELEASED
Phase 2 Survey Builder       COMPLETE ON DEV — PENDING RELEASE GATE
Phase 3 Public/Response      NOT AUTHORIZED
Production                   NOT AUTHORIZED
```

Phase 2의 `2-A Survey DRAFT Core → 2-B Question/Lock Data Foundation → 2-C Survey Builder Backend Completion → 2-D Survey Builder Frontend + Preview`가 `dev`에 통합됐고 [Phase 2 Completion Evidence](phase-2-completion-evidence.md)가 exact merged dev를 `PASS`로 판정했다. 다음 허용 작업은 full `main...dev`, native ARM64, disposable Flyway compatibility와 V3-V5 recovery-impact classification을 검증하는 별도 Gate 3 Release Candidate evidence다. Phase 3 Public Survey/Response, Result/CSV와 Production은 이 gate로 열리지 않는다.

# Repository Governance

`dev`는 PR을 통해서만 통합하고 `Backend`, `Frontend`, `Infrastructure`를 GitHub Actions source의 required checks로 사용한다. `main`도 PR integration을 요구하며 `Backend`, `Frontend`, `Infrastructure`, `ARM64 Release Artifact`를 required checks로 사용한다. 두 branch 모두 required approving review는 0, strict up-to-date는 off이며 repository administrator에게도 적용한다. Force push와 branch deletion은 허용하지 않는다.

Signed commit, linear history, CODEOWNERS approval, last-push approval와 conversation resolution은 현재 1인 integration/release branch에 요구하지 않는다.

기본 흐름은 `GPT Issue → Codex Issue-to-PR → required checks/READY → GPT exact-head review → user dev merge → merged dev exact SHA/CI 확인 → completed Issue close → next Issue`다. 한 번에 active implementation/governance slice 하나와 `Issue 1 → PR 1 → dev`를 기본으로 하며 oversized Issue는 coding 전에 분리한다.

일반 feature/fix/docs/chore `→ dev` PR은 `Related Issue: #N`으로 관계만 기록한다. GitHub closing keyword의 automatic close를 일반 dev workflow contract로 사용하지 않으며, `dev → main` Release PR의 closing semantics와 구분한다.

Issue는 scope/authorization contract이고 PR은 implementation/evidence다. Green CI와 template conformance는 quality/governance evidence이지만 Phase authorization 위반이나 Product acceptance 미충족을 덮지 못한다. Template은 accepted ADR과 Product/Domain contract보다 우선하지 않는다.

`dev → main`은 Phase/vertical capability release boundary이며 production deployment와 별개다. Template이 `dev`에만 있는 동안에도 GPT/Codex의 normative body structure로 사용하되 GitHub chooser와 PR auto-fill activation은 정상 `dev → main` release까지 deferred한다.
