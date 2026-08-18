---
title: Deployment Runbook
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Target

Mac mini.

# 2. Release Inputs

- exact Git commit SHA
- API image digest
- Web image digest
- runtime compose/config revision

# 3. Preflight

- current health
- disk
- Docker
- backup readiness
- DB compatibility
- operation lock

# 4. Deploy

권장 흐름:

```text
Validate
→ Build/Publish
→ Predeploy backup
→ Pull
→ Stage
→ Activate
→ Health
→ Public smoke
→ Commit deployment state
```

# 5. Failure

activation/health/public smoke 실패 시 이전 image/config rollback 경로를 유지한다.

# 6. Database

새 Flyway migration이 있으면 backward compatibility를 별도 검토한다.

# 7. Manual Deploy

초기 V1에서는 manual deployment를 허용할 수 있으나 exact SHA/digest를 기록한다.
