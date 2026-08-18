---
title: Backup & Recovery
status: draft
version: 0.1
last_updated: 2026-08-18
---

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

복구 절차는 별도 scratch DB에서 최소 1회 검증한다.

# 5. Docker Volume

Docker volume 자체는 backup으로 간주하지 않는다.

# 6. Predeploy Backup

DB/schema 영향 release에는 predeploy backup을 권장.

# 7. Off-host Copy

Mac mini 단일 디스크 위험을 줄이기 위한 off-host copy 정책은 Production Readiness Phase까지 deferred한다. 확정 전에는 local backup만으로 재해 복구가 완료됐다고 간주하지 않는다.
