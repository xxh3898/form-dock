---
title: Quality Gates
status: draft
version: 1.6
last_updated: 2026-08-27
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

현재 Validate workflow는 모든 `dev` PR에서 세 job을 실행한다. Infrastructure job은 local Compose config/image build를 유지하면서 Production Compose의 required input, no-build, host exposure, network, restart, persistent-volume과 bounded logging static contract를 검증한다. 또한 Phase 5-B backup→scratch restore smoke와 Phase 5-C1 deployment-state, canonical Compose stage/health/application rollback 및 provider-neutral monitoring smoke를 secret-free disposable resource에서 실행한다.

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

Phase 1, Phase 2, Phase 3와 Phase 4의 full-diff, ARM64, Flyway와 recovery 분류 evidence는 각각 [Phase 1 Main Release Evidence](phase-1-main-release-evidence.md), [Phase 2 Main Release Evidence](phase-2-main-release-evidence.md), [Phase 3 Main Release Evidence](phase-3-main-release-evidence.md)와 [Phase 4 Main Release Evidence](phase-4-main-release-evidence.md)에 기록한다. Phase completion provenance는 각 Phase Completion Evidence, Gate ownership의 accepted decision은 [ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)를 따른다.

# Gate 4 — Production Readiness and Activation

- Gate 3가 요구한 recovery action 완료
- schema/data impact와 existing live data가 있을 때 required predeploy backup
- recovery plan이 요구하는 isolated scratch restore verification
- target environment에 적용되는 retention/off-host classification, accepted risk와 live recovery readiness
- deploy success
- API/Web/Postgres health
- public smoke
- no unintended rollback

Gate 4의 operational evidence를 Gate 3 release eligibility로 대체하거나 그 반대로 재사용하지 않는다. Required recovery action이 남아 있으면 schema/data-impacting release를 Production에 activate하지 않는다.

Phase 5는 Gate 4 준비와 activation을 다음 순서로 분리한다.

1. 5-A Production Runtime Foundation: repository-only Production Compose/config와 isolated validation
2. 5-B Backup/Restore/Recovery Readiness: logical backup tooling과 disposable scratch restore evidence
3. 5-C1 Delivery/Monitoring Foundation: repository state/staging/rollback/logging/monitoring contract
4. 5-C2 Exact Remote Artifact Publication Evidence: 별도 Issue가 승인한 exact remote ref publication
5. 5-D1 Production Activation Preflight: read-only target classification과 operations/security contract
6. 5-D2 Production Activation: 별도 승인된 live configuration, data, routing, deploy와 public acceptance

5-A~5-D1의 PASS는 5-D2 live operation 권한이 아니다. Remote artifact publish는 5-C2 exact Issue 전까지, Secret, live DB/backup/restore, Cloudflare/HomeOps와 Production mutation은 각 작업의 exact target을 승인하는 별도 Issue 전까지 수행하지 않는다.

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
Phase 2 Survey Builder       COMPLETE + RELEASED
Phase 3 Public Survey/Response COMPLETE + RELEASED
Phase 4 Results / Export     COMPLETE + RELEASED — v0.4.0
Phase 4 Gate 3               PASS + RELEASED
Phase 5 Production Readiness AUTHORIZED — repository/readiness slices only
Phase 5-A Runtime Foundation COMPLETE + DEV INTEGRATED
Phase 5-B Backup/Restore     COMPLETE + DEV INTEGRATED
Phase 5-C1 Delivery/Monitoring COMPLETE + DEV INTEGRATED
Phase 5-C2 Remote Artifact   COMPLETE + DEV INTEGRATED
Phase 5-D1 Preflight         PASS — DEV INTEGRATION PENDING
Phase 5-D2 Activation        NOT AUTHORIZED
Production Activation       NOT AUTHORIZED
GitHub Release               NOT REQUIRED / NOT CREATED
```

Phase 2의 `2-A Survey DRAFT Core → 2-B Question/Lock Data Foundation → 2-C Survey Builder Backend Completion → 2-D Survey Builder Frontend + Preview`가 `dev`에 통합됐고 [Phase 2 Completion Evidence](phase-2-completion-evidence.md)가 exact merged dev를 `PASS`로 판정했다. [Phase 2 Main Release Evidence](phase-2-main-release-evidence.md)는 full diff, native ARM64, disposable V2→V5 Flyway compatibility와 `RECOVERY PLAN REQUIRED` classification을 `PASS`로 판정했고 exact tree가 `main`에 release됐다. 이 release는 Production activation이 아니다.

Phase 3의 `3-A Public Survey Read → 3-B Response Data/Canonicalization → 3-C Atomic Public Submit → 3-D Respondent Frontend`는 [Phase 3 Completion Evidence](phase-3-completion-evidence.md)와 [Phase 3 Main Release Evidence](phase-3-main-release-evidence.md)의 exact integration/full diff/native ARM64/disposable V5→V6 검증 뒤 PR #60으로 `main`에 release됐다. Annotated tag `v0.3.0`은 repository Release identity이며 Production evidence가 아니다.

Phase 4의 `4-A Creator Response Read Backend → 4-B Result Summary Backend → 4-C CSV Export Backend → 4-D Results Frontend`는 [Phase 4 Completion Evidence](phase-4-completion-evidence.md)의 exact integration/application acceptance와 [Phase 4 Main Release Evidence](phase-4-main-release-evidence.md)의 full diff, native ARM64, same V1→V6 compatibility 및 `NO DATA/SCHEMA IMPACT` 검증을 통과했다. PR #79의 verified merge commit이 exact tree를 `main`에 release했고 annotated `v0.4.0`이 repository identity다. GitHub Release와 Production activation은 수행하지 않았다.

Phase 5 Entry PR은 exact Phase 4 `main` release merge commit에서 시작해 `dev`에 merge됐고 release ancestry와 required checks가 확인됐다. Phase 5-A~5-C2의 Production Compose, recovery, delivery/monitoring과 exact remote artifact evidence도 `dev`에 통합됐다. [5-D1 evidence](phase-5-d1-production-activation-preflight-evidence.md)는 Mac target/artifact/first-activation/private config/lock/Cloudflare/HomeOps contract를 read-only로 PASS 판정해 `dev` 통합을 기다린다. 5-D2 live activation은 승인되지 않았다.

# Repository Governance

`dev`는 PR을 통해서만 통합하고 `Backend`, `Frontend`, `Infrastructure`를 GitHub Actions source의 required checks로 사용한다. `main`도 PR integration을 요구하며 `Backend`, `Frontend`, `Infrastructure`, `ARM64 Release Artifact`를 required checks로 사용한다. 두 branch 모두 required approving review는 0, strict up-to-date는 off이며 repository administrator에게도 적용한다. Force push와 branch deletion은 허용하지 않는다.

Signed commit, linear history, CODEOWNERS approval, last-push approval와 conversation resolution은 현재 1인 integration/release branch에 요구하지 않는다.

기본 흐름은 `GPT Issue → Codex Issue-to-PR → required checks/READY → GPT exact-head review → user dev merge → merged dev exact SHA/CI 확인 → completed Issue close → next Issue`다. 한 번에 active implementation/governance slice 하나와 `Issue 1 → PR 1 → dev`를 기본으로 하며 oversized Issue는 coding 전에 분리한다.

일반 feature/fix/docs/chore `→ dev` PR은 `Related Issue: #N`으로 관계만 기록한다. GitHub closing keyword의 automatic close를 일반 dev workflow contract로 사용하지 않으며, `dev → main` Release PR의 closing semantics와 구분한다.

Issue는 scope/authorization contract이고 PR은 implementation/evidence다. Green CI와 template conformance는 quality/governance evidence이지만 Phase authorization 위반이나 Product acceptance 미충족을 덮지 못한다. Template은 accepted ADR과 Product/Domain contract보다 우선하지 않는다.

`dev → main`은 Phase/vertical capability release boundary이며 production deployment와 별개다. Template이 `dev`에만 있는 동안에도 GPT/Codex의 normative body structure로 사용하되 GitHub chooser와 PR auto-fill activation은 정상 `dev → main` release까지 deferred한다.

`main` release merge commit을 `dev` ancestry로 동기화하는 PR은 lineage 보존이 목적이므로 반드시 GitHub의 **Create a merge commit**으로 통합한다. Squash merge와 rebase merge는 동기화할 ancestry를 제거하므로 해당 PR에서 금지한다.
