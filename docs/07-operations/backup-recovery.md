---
title: Backup & Recovery
status: draft
version: 0.5
last_updated: 2026-08-27
---

# Gate Ownership

[ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)에 따라 main Release Candidate는 recovery impact를 다음 중 하나로 분류한다.

```text
NO DATA/SCHEMA IMPACT
RECOVERY PLAN REQUIRED
```

Gate 3는 schema/data impact와 필요한 Production recovery action을 식별하지만 live backup, migration 또는 restore를 실행하지 않는다. Gate 4/Production Readiness가 actual backup, scratch restore, retention/off-host policy와 live recovery evidence를 소유한다.

Phase 5-B는 repository tooling과 disposable scratch evidence를 준비하며 live Production backup/restore 권한을 포함하지 않는다. Issue #93 D2A는 exact `FIRST_ACTIVATION / FRESH_PRODUCTION_DB` target의 first local logical backup, verify와 disposable scratch restore만 별도 승인한다. Retention apply/schedule, off-host copy와 live Production restore는 포함하지 않는다.

# 1. Backup

PostgreSQL logical backup:

```text
pg_dump -Fc
```

Repository authority는 [`infra/backup/`](../../infra/backup/README.md)이다. Backup은 repository 밖의 explicit absolute private directory에 partial artifact로 생성하고 `pg_restore --list`, SHA-256와 allowlist metadata를 통과한 뒤 metadata를 마지막에 finalize한다.

Completed generation:

```text
<backup-id>.dump
<backup-id>.sha256
<backup-id>.meta
```

Metadata는 UTC `createdAt`, PostgreSQL server/tool version, exact application Release SHA, `status=complete`, filename과 SHA-256만 기록한다. Credential, token, private endpoint와 raw Product data를 기록하지 않는다. Existing completed set을 overwrite하지 않으며 failed partial set을 successful generation으로 count하지 않는다.

# 2. Schedule

Production Readiness Phase에서 검증할 초기 baseline:

```text
daily
retain recent 7
```

Phase 5-D1 current first-activation baseline은 daily, completed recent 7로 확정한다. Persistent data volume과 independent target이 준비된 뒤 별도 durability hardening slice에서 capacity/retention을 재검토한다.

Repository retention은 `FORMDOCK_RETENTION_COUNT`를 input으로 받고 default dry-run이다. Explicit apply에서도 verified complete FormDock set만 대상으로 하며 partial/unrelated file과 configured root 밖의 path를 삭제하지 않는다. D2A는 schedule/retention 적용을 승인하지 않으며 실제 적용은 별도 mutation 승인 대상이다.

# 3. Backup Metadata

- createdAt
- DB version
- app/release SHA
- status
- SHA-256 file checksum

# 4. Restore

`RECOVERY PLAN REQUIRED` release의 Production activation 전에는 복구 절차를 별도 scratch DB에서 최소 1회 검증한다. Main promotion만을 위해 live data를 복사하거나 restore하지 않는다.

Scratch restore evidence는 최소 backup checksum, PostgreSQL version, restored Flyway history/data integrity와 application/database health를 기록한다. Scratch target은 live/shared database와 분리한다.

`restore-scratch.sh`은 existing resource를 받지 않고 새 `dev-form-dock-scratch-*` container/network/volume만 만든다. PostgreSQL/API host port를 publish하지 않으며 checksum과 custom-format 검증 뒤 `pg_restore --exit-on-error --no-owner --no-acl`을 실행한다. Flyway success versions `1..6`, optional representative data assertion과 API health를 확인한 후 exact scratch resource를 정리한다.

# 5. Docker Volume

Docker volume 자체는 backup으로 간주하지 않는다.

# 6. Predeploy Backup

Existing live data에 DB/schema impact가 있는 release는 Production migration 전에 verified logical backup을 완료한다. Release Candidate는 필요한 action을 plan으로 기록하고 actual backup은 Gate 4에서 실행한다.

첫 Production activation 전에 target database를 다음처럼 분류한다.

```text
fresh Production DB
existing live Production DB/data
```

Fresh DB이면 clean Flyway startup과 empty-state acceptance를, existing live DB/data이면 current version, record scope, compatibility, verified backup과 recovery precondition을 각각 증명한다. Phase 5-D1 actual read-only evidence는 current target을 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`로 분류했으므로 predeploy backup은 `NOT REQUIRED — FRESH DB`다.

# 7. Off-host Copy

Mac mini 단일 디스크 위험을 줄이기 위해 completed local set을 distinct configured filesystem target에 partial copy하고 checksum/readability 검증 뒤 metadata를 마지막에 finalize한다. Source와 target canonical directory가 같으면 거부한다.

이 interface는 mounted filesystem에 provider-neutral하다. Repository isolated smoke는 별도 temporary directory로 copy semantics만 검증한다.

Current first activation contract:

```text
offHostDurabilityStatus         DEFERRED_ACCEPTED_RISK
currentIndependentOffHostTarget NONE
firstActivationAllowed         true
```

이 accepted risk는 durability/DR/independent backup PASS가 아니다. Persistent/dogfooding data가 생기면 primary Mac storage와 독립된 physical external disk 또는 mounted NAS에 backup→copy→checksum→restore evidence를 만드는 hardening slice를 수행한다. 같은 internal disk의 directory/APFS volume은 independent target이 아니며 iCloud Drive는 `INTERIM_SYNC_COPY`로만 분류한다.

# 8. Phase 5-B 검증 근거 경계

Phase 5-B는 disposable source PostgreSQL/API에서 Flyway V1→V6와 representative Creator/Survey/Question/Response/Answer data를 만든 뒤 다음 serial flow를 검증한다.

```text
backup
→ readability/checksum/metadata
→ retention dry-run/apply
→ off-host filesystem simulation
→ separate scratch restore
→ Flyway/data/API health
→ exact residue cleanup
```

이 evidence는 repository tooling readiness다. Live Production DB access, schedule installation, real NAS/cloud mutation, Production migration과 activation은 모두 0이며 별도 승인 전에는 실행하지 않는다.
