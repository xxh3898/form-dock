---
title: Deployment Architecture
status: draft
version: 0.2
last_updated: 2026-08-19
---

# 1. Runtime

Mac mini Docker Compose.

```text
form-dock-web
form-dock-api
form-dock-postgres
```

현재 repository의 `infra/compose.yaml`은 `dev-form-dock` local development baseline이다. Loopback port와 development-only volume을 사용하며 production canonical Compose로 간주하지 않는다.

# 2. Release and Production Gates

`dev → main`은 Phase/vertical capability Release Candidate boundary이며 Production deployment가 아니다.

Gate 3는 full release diff, ARM64 target artifact, disposable/test DB Flyway compatibility와 recovery-impact classification을 검증한다. `main`은 intended target에서 build 가능한 release baseline을 뜻하지만 deployed 또는 production-ready 상태를 뜻하지 않는다.

Gate 4/Production Readiness는 required backup/restore action, deployment, health, public smoke와 rollback evidence를 실제 environment에서 검증한다. Live migration, Secret, backup과 activation은 별도 authorization 없이는 수행하지 않는다. 상세 ownership은 [ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)를 따른다.

# 3. External Access

```text
forms.chochiho.cloud
→ Cloudflare Tunnel
→ Web
```

Web은 same-origin `/api`를 API container로 reverse proxy한다. Browser에 별도 API origin을 노출하지 않는다.

# 4. Database Exposure

PostgreSQL public port publish 금지.

운영 관리 접근은 SSH/Tailscale 내부에서만 수행.

Local Compose의 diagnostic DB port는 `127.0.0.1`에만 bind하며 LAN/public exposure가 아니다.

# 5. Health

```text
Postgres → pg_isready
API      → /actuator/health
Web      → /health
```

API는 Postgres healthy 이후 시작.

Web `/health`는 static serving liveness만 확인하고 API dependency 때문에 Web container를 unhealthy로 만들지 않는다. API/Postgres health와 public Web→API smoke는 별도로 검증한다.

# 6. Images

GHCR exact SHA tag 또는 immutable digest를 사용한다.

`latest`만을 release identity로 사용하지 않는다.

# 7. Rollback

Application image rollback과 DB migration rollback을 분리한다.

Flyway는 forward-only migration을 기본으로 한다.
