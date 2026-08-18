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

초기 추천:

```text
daily
retain recent 7
```

실제 데이터 중요도/용량에 따라 조정.

# 3. Backup Metadata

- createdAt
- DB version
- app/release SHA
- status
- file checksum 후보

# 4. Restore

복구 절차는 별도 scratch DB에서 최소 1회 검증한다.

# 5. Docker Volume

Docker volume 자체는 backup으로 간주하지 않는다.

# 6. Predeploy Backup

DB/schema 영향 release에는 predeploy backup을 권장.

# 7. Off-host Copy

Mac mini 단일 디스크 위험을 줄이기 위해 추후 외부 복제 정책을 추가한다.
