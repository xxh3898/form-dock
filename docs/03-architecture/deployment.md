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

Web은 same-origin `/api`를 API container로 reverse proxy한다. Browser에 별도 API origin을 노출하지 않는다.

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

Web `/health`는 static serving liveness만 확인하고 API dependency 때문에 Web container를 unhealthy로 만들지 않는다. API/Postgres health와 public Web→API smoke는 별도로 검증한다.

# 5. Images

GHCR exact SHA tag 또는 immutable digest를 사용한다.

`latest`만을 release identity로 사용하지 않는다.

# 6. Rollback

Application image rollback과 DB migration rollback을 분리한다.

Flyway는 forward-only migration을 기본으로 한다.
