---
title: ADR-0005 Release and Production Gate Separation
status: accepted
version: 1.0
last_updated: 2026-08-19
---

# Status

`accepted`

# Context

FormDock은 `dev → main`을 Phase 또는 vertical capability의 release boundary로 사용하고 Production deployment와 activation을 별도 Gate로 관리한다. Intended runtime은 ARM64 Mac mini이므로 `main`에 도달하는 release artifact는 target architecture에서 build 가능해야 한다.

기존 Gate 3는 full release diff, ARM64 build, Flyway compatibility와 `backup/restore readiness`를 모두 요구했다. 반면 actual backup, scratch restore, retention과 off-host copy는 Production Readiness에서 검증하도록 정해져 있었다. 이 상태에서는 repository release eligibility가 아직 존재하지 않는 Production environment의 operational evidence에 의존한다.

# Options Considered

1. Gate 3에서 full operational backup/restore readiness까지 요구한다.
2. Gate 3의 release eligibility와 Gate 4/Phase 5의 operational recovery readiness를 분리한다.
3. ARM64와 recovery evidence를 모두 Production Readiness로 옮긴다.

# Decision

Option 2를 채택한다.

```text
Gate 3           repository/main Release Candidate eligibility
Gate 4 / Phase 5 Production operational readiness and activation
```

## Gate 3 — Release Candidate Eligibility

Gate 3는 다음 evidence를 소유한다.

1. exact `main...dev` full release diff validation
2. intended release artifact의 target-architecture ARM64 build evidence
3. disposable/test PostgreSQL에서의 Flyway compatibility evidence
4. recovery-impact classification

모든 Release Candidate는 PR의 Data/Migration evidence에서 다음 중 하나를 명시한다.

```text
NO DATA/SCHEMA IMPACT
RECOVERY PLAN REQUIRED
```

`RECOVERY PLAN REQUIRED`이면 main promotion 전에 schema/data impact와 Production activation을 막는 backup, restore, rollback 또는 forward-recovery action을 이름 붙인다. Gate 3는 plan과 compatibility를 검증하지만 live database migration, backup 또는 restore를 실행하지 않고 operational readiness가 완료됐다고 기록하지 않는다.

## Gate 4 / Phase 5 — Production Readiness and Activation

Gate 4는 Gate 3가 식별한 recovery action의 실제 실행과 evidence를 소유한다. Release impact에 따라 다음을 포함한다.

- existing live data의 predeploy logical backup
- isolated scratch database restore verification
- retention과 off-host copy policy
- deployment state와 application/database rollback evidence
- API/Web/PostgreSQL health와 public smoke

Required recovery action이 완료되지 않으면 schema/data-impacting release를 Production에 activate하지 않는다.

`main`은 target-buildable Phase baseline을 뜻하며 deployed, production-ready 또는 다음 Product Phase authorized를 뜻하지 않는다. Phase completion과 main release는 Survey Domain/Phase 2 또는 Production authorization을 자동으로 만들지 않는다.

# Rationale

- ARM64 compatibility는 live operation이 아니라 release artifact의 속성이므로 main promotion 전에 검증한다.
- Flyway compatibility를 disposable/test database에서 확인해 live migration 없이 release safety를 평가한다.
- Recovery risk는 main 전에 드러내되 아직 존재하지 않는 Production environment의 actual operation을 release precondition으로 만들지 않는다.
- Production activation은 실제 data와 environment를 기준으로 더 엄격한 operational evidence를 요구한다.

# Consequences

- Release PR은 항상 recovery-impact classification을 포함한다.
- Schema/data impact가 있으면 Release PR에서 recovery plan을 명시하고 Production Gate에서 action completion을 증명한다.
- Gate 3와 Gate 4 문서를 함께 유지해야 하며 어느 한쪽의 PASS를 다른 쪽의 PASS로 재사용하지 않는다.
- Phase 1 main Release Candidate는 full release diff와 ARM64 evidence가 마련될 때까지 계속 blocked다.
- Current Phase 1 V1/V2 Flyway schema는 `RECOVERY PLAN REQUIRED`로 분류하지만 이 decision 자체는 backup, restore 또는 migration을 실행하지 않는다.

# Rejected Alternatives

Option 1은 strongest pre-main operational safety와 하나의 단순한 checklist를 제공하지만 Phase release와 Production activation을 합치고 후행 Production Readiness가 Phase cadence를 막는다.

Option 3은 빠른 main promotion과 낮은 release CI cost를 제공하지만 intended ARM64 target에서 build되지 않는 artifact가 main에 도달할 수 있어 거절한다.

# Revisit When

Release automation, multiple production environments 또는 real-data migration policy가 도입되어 Gate ownership이나 evidence retention을 확장해야 할 때 재검토한다.

# References

- [Quality Gates](../06-quality/quality-gates.md)
- [Deployment Architecture](../03-architecture/deployment.md)
- [Backup & Recovery](../07-operations/backup-recovery.md)
