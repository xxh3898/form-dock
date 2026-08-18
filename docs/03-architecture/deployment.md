---
title: Deployment Architecture
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Runtime

Mac mini Docker Compose.

```text
form-dock-web
form-dock-api
form-dock-postgres
```

# 2. External Access

```text
forms.chochiho.cloud
→ Cloudflare Tunnel
→ Web
```

Public API는 Web reverse proxy를 통해 `/api`로 전달하는 구성을 우선 검토한다.

# 3. Database Exposure

PostgreSQL public port publish 금지.

운영 관리 접근은 SSH/Tailscale 내부에서만 수행.

# 4. Health

```text
Postgres → pg_isready
API      → /actuator/health
Web      → /health
```

API는 Postgres healthy 이후 시작.

Web readiness는 API dependency policy를 별도 확정.

# 5. Images

GHCR exact SHA tag 사용을 권장.

`latest`만을 release identity로 사용하지 않는다.

# 6. Rollback

Application image rollback과 DB migration rollback을 분리한다.

Flyway는 forward-only migration을 기본으로 한다.
