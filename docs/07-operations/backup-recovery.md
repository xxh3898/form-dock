---
title: Backup & Recovery
status: draft
version: 0.2
last_updated: 2026-08-19
---

# Gate Ownership

[ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)에 따라 main Release Candidate는 recovery impact를 다음 중 하나로 분류한다.

```text
NO DATA/SCHEMA IMPACT
RECOVERY PLAN REQUIRED
```

Gate 3는 schema/data impact와 필요한 Production recovery action을 식별하지만 live backup, migration 또는 restore를 실행하지 않는다. Gate 4/Production Readiness가 actual backup, scratch restore, retention/off-host policy와 live recovery evidence를 소유한다.

# 1. Backup

PostgreSQL logical backup:

```text
pg_dump -Fc
```

# 2. Schedule

Production Readiness Phase에서 검증할 초기 baseline:

```text
daily
retain recent 7
```

실제 보존 수치는 dogfooding data volume, disk, off-host copy 정책을 확인한 뒤 이 문서에서 확정한다. Application scaffold blocker가 아니다.

# 3. Backup Metadata

- createdAt
- DB version
- app/release SHA
- status
- SHA-256 file checksum

# 4. Restore

`RECOVERY PLAN REQUIRED` release의 Production activation 전에는 복구 절차를 별도 scratch DB에서 최소 1회 검증한다. Main promotion만을 위해 live data를 복사하거나 restore하지 않는다.

# 5. Docker Volume

Docker volume 자체는 backup으로 간주하지 않는다.

# 6. Predeploy Backup

Existing live data에 DB/schema impact가 있는 release는 Production migration 전에 verified logical backup을 완료한다. Release Candidate는 필요한 action을 plan으로 기록하고 actual backup은 Gate 4에서 실행한다.

# 7. Off-host Copy

Mac mini 단일 디스크 위험을 줄이기 위한 off-host copy 정책은 Production Readiness Phase까지 deferred한다. 확정 전에는 local backup만으로 재해 복구가 완료됐다고 간주하지 않는다.
