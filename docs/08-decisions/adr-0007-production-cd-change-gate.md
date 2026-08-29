---
title: ADR-0007 Production CD Change Gate
status: accepted
version: 1.1
last_updated: 2026-08-29
---

# Status

`accepted`

# Context

FormDock은 `dev → main` Release와 Production activation을 [ADR-0005](adr-0005-release-and-production-gate-separation.md)에 따라 분리한다. Production이 이미 active인 상태에서 `main` push를 CD trigger로 사용하려면, Release merge 자체를 Production mutation 승인으로 오해하지 않으면서 application-only 변경만 bounded candidate로 선별해야 한다.

직전 commit만 비교하면 이전에 HOLD된 deploy-control 또는 migration 변경을 후속 application commit이 우회할 수 있다. 또한 task checkout에 의존하는 Compose/script는 stable Production runtime authority가 될 수 없다.

# Decision

`main` push와 current-main-only `workflow_dispatch`를 Production CD orchestration trigger로 사용하되 mutation authority로 간주하지 않는다.

```text
main event
→ Validate
→ latest successful GitHub Production deployment baseline
→ baseline...current-main cumulative classification
→ kill switch / Production environment
→ eligible application-only candidate
```

## Change gate

분류 우선순위는 다음과 같다.

```text
MIGRATION_OR_DATA > DEPLOY_CONTROL > UNKNOWN > APPLICATION_ONLY > DOCS_META_ONLY
```

- `DOCS_META_ONLY`: validate만 수행하고 package publish와 deploy를 건너뛴다.
- `APPLICATION_ONLY`: 다른 고위험 class가 누적 diff에 없을 때만 candidate가 될 수 있다.
- `DEPLOY_CONTROL`: self-activation을 막기 위해 publish/deploy를 HOLD한다.
- `MIGRATION_OR_DATA`: 별도 migration/recovery Ops 승인 전까지 automatic deploy를 HOLD한다.
- `UNKNOWN`: fail closed로 HOLD한다.

누적 Git diff는 rename detection을 끄고 삭제된 원본 경로와 추가된 목적지 경로를 모두 분류한다. Rename 또는 copy 표현 방식 때문에 deploy-control, migration/data 또는 application 경로의 위험 등급이 낮아져서는 안 된다.

Baseline은 exact `Production` environment의 마지막 successful GitHub Deployment SHA다. Pagination을 포함한 전체 history에서 malformed, ambiguous, missing 또는 current `main` ancestry 밖 baseline은 HOLD한다. 자동으로 tag나 live state를 추정하지 않는다.

## Authorization layers

`MAC_MINI_DEPLOY_ENABLED`가 정확히 `true`가 아니면 package write, Tailscale, SSH와 Production mutation은 모두 0이다. Workflow의 `environment: Production`은 repository contract일 뿐 실제 reviewer/protection 설정의 존재를 증명하지 않는다. Environment, Variable, Secret, SSH key와 Tailscale ACL의 생성·변경은 별도 Ops 작업이다.

## Artifact and host transaction

Eligible candidate는 GitHub-hosted native ARM64 runner에서 API, Web과 runtime-config OCI artifact를 build하고 exact digest를 전달한다. Exact `sha-<main SHA>` tag 세 개가 모두 없을 때만 한 번 publish한다. 세 tag가 모두 존재하면 `linux/arm64`, OCI source/revision/version/component와 runtime-config project label을 검증하고 기존 digest를 재사용한다. Partial set, identity mismatch와 registry/auth/network 불명확 상태는 HOLD하며 기존 SHA tag를 덮어쓰지 않는다. `latest`, QEMU release authority와 Mac mini build는 사용하지 않는다.

Runtime-config artifact는 canonical Production Compose, recurring deployment worker, HomeOps reporter adapter, backup verifier와 exact revision metadata만 포함한다. Secret과 private env는 포함하지 않는다. Mac mini는 `/Users/homeserver/Server/apps/form-dock/runtime-config` 아래 immutable release와 `pending/current/previous` pointer를 관리한다.

Restricted SSH는 exact project, current-main SHA, API/Web/runtime-config digest, registry identity와 workflow run ID만 받는다. Arbitrary shell, image repository, Compose path와 project를 받지 않는다.

Recurring deployment worker는 current operator 소유의 regular non-symlink mode `0600` operation lock, accepted state, PostgreSQL health/volume, Flyway exact `V1..V6`, fresh verified logical backup을 먼저 확인한다. Candidate 활성화 전에 accepted env/state/pointer/runtime와 PostgreSQL volume authority를 snapshot한다.

Candidate activation, local state/pointer commit과 HomeOps terminal `SUCCESS` delivery는 하나의 deployment transaction이다. Candidate health, pointer/env/state 교체, pending pointer 제거 또는 terminal `SUCCESS` 중 하나라도 실패하면 snapshot을 복원하고 accepted application/runtime-config로 rollback한 뒤 PostgreSQL volume, Flyway `V1..V6`, internal/public health를 다시 검증한다. Rollback 성공은 `ROLLED_BACK`, rollback 실패는 `FAILED` terminal event를 사용하며 terminal `SUCCESS`가 실제 전달되기 전에는 candidate를 accepted current로 확정하지 않는다. DB restore/down migration은 자동 실행하지 않는다.

HomeOps event는 installed reporter interface를 통해 전달하며 caller는 HMAC Secret을 받지 않는다. Successful Production deploy job이 생성하는 GitHub Deployment success만 다음 cumulative baseline authority가 된다.

# Consequences

- Deploy-control foundation 자체는 자신의 새 권한으로 publish/deploy되지 않는다.
- 첫 자동 deploy 전에는 별도 activation Ops가 accepted Production baseline, kill switch, protected Environment, Secret과 installed forced-command를 검증해야 한다.
- Application release라도 backup, Flyway 또는 live state가 불명확하면 host에서 fail closed한다.
- 같은 main SHA 재실행은 기존 exact digest를 재사용하며 tag mutation을 만들지 않는다.
- Candidate 또는 state commit 실패는 accepted runtime/state를 복원하고 failed GitHub baseline이 local current를 전진시키지 않는다.
- Application rollback과 database recovery는 계속 분리한다.
- Production deploy 성공은 Phase 6 Dogfooding authorization을 만들지 않는다.

# Rejected Alternatives

- 매 `main` commit만 비교하는 방식은 이전 HOLD 변경을 우회할 수 있어 거절한다.
- `main` merge를 곧바로 Production 승인으로 취급하는 방식은 ADR-0005를 위반해 거절한다.
- branch/SHA를 자유롭게 받는 manual dispatch와 arbitrary SSH command는 provenance와 scope를 약화해 거절한다.
- Mac mini에서 image를 build하거나 mutable tag를 authority로 사용하는 방식은 reproducibility를 약화해 거절한다.

# Revisit When

Migration automation, multi-environment deployment, off-host durability hardening 또는 human approval 제거를 검토할 때 별도 Decision으로 재평가한다.

# References

- [ADR-0005](adr-0005-release-and-production-gate-separation.md)
- [Deployment Architecture](../03-architecture/deployment.md)
- [Quality Gates](../06-quality/quality-gates.md)
- [Deployment Runbook](../07-operations/deployment-runbook.md)
- [Backup & Recovery](../07-operations/backup-recovery.md)
