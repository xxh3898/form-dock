---
title: Deployment Architecture
status: draft
version: 0.5
last_updated: 2026-08-26
---

# 1. Runtime

Mac mini Docker Compose.

```text
form-dock-web
form-dock-api
form-dock-postgres
```

Repository는 runtime authority를 분리한다.

```text
infra/compose.yaml             local dev-form-dock baseline
infra/compose.production.yaml Production canonical Compose contract
```

Production Compose는 API/Web source를 build하지 않고 required image input을 사용한다. PostgreSQL과 API host port는 publish하지 않고 Web만 configured port를 `127.0.0.1`에 bind한다. Web은 application network에만, API는 application/database network에, PostgreSQL은 internal database network에만 참여한다.

# 2. Release and Production Gates

`dev → main`은 Phase/vertical capability Release Candidate boundary이며 Production deployment가 아니다.

Gate 3는 full release diff, ARM64 target artifact, disposable/test DB Flyway compatibility와 recovery-impact classification을 검증한다. `main`은 intended target에서 build 가능한 release baseline을 뜻하지만 deployed 또는 production-ready 상태를 뜻하지 않는다.

Gate 4/Production Readiness는 required backup/restore action, deployment, health, public smoke와 rollback evidence를 실제 environment에서 검증한다. Live migration, Secret, backup과 activation은 별도 authorization 없이는 수행하지 않는다. 상세 ownership은 [ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)를 따른다.

Phase 5 Entry가 승인하는 것은 `5-A Runtime Foundation → 5-B Backup/Restore/Recovery Readiness → 5-C Delivery/Monitoring Readiness`의 repository/isolated 준비다. `5-D Production Activation Gate`는 별도 명시적 live-operation 승인 전까지 시작하지 않는다.

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

Phase 4 repository Release 기준은 annotated `v0.4.0`과 `main@1648047645720e67d5e928345c875dc53a93ff0e`이다. Gate 3는 API/Web의 native ARM64 build 가능성을 검증했지만 image를 publish하지 않았다. 실제 GHCR publication과 deployed digest evidence는 Phase 5-C에서 exact artifact/ref를 승인하는 별도 Issue 범위다.

`FORMDOCK_API_IMAGE`와 `FORMDOCK_WEB_IMAGE`는 exact SHA tag 또는 immutable digest를 전달하는 runtime interface다. Production Compose에 `build:` 또는 hard-coded `latest` authority를 두지 않는다. Phase 5-A isolated smoke는 local-only temporary tag를 사용할 수 있지만 remote publication이나 deployed artifact evidence로 간주하지 않는다.

# 7. Rollback

Application image rollback과 DB migration rollback을 분리한다.

Flyway는 forward-only migration을 기본으로 한다.

# 8. Configuration and Secret Boundary

- Production configuration key는 safe example과 documentation으로만 관리하고 실제 값을 repository default에 하드코딩하지 않는다.
- Password, token, private key와 Cloudflare credential은 frontend bundle, Issue, PR, log와 evidence에 기록하지 않는다.
- Secret storage/injection/rotation mechanism은 Phase 5-C 또는 5-D 전에 별도 operations/security contract로 확정한다. 이번 Entry는 새 Secret architecture를 선택하지 않는다.
- Production `.env` 작성, Secret 조회·생성·변경과 live injection은 Phase 5-D 별도 승인 전까지 금지한다.
- `infra/production.env.example`은 key interface와 non-secret placeholder만 소유한다. 실제 env file은 repository 밖의 private path에서 관리한다.

# 9. Database Environment Boundary

Production activation 전 exact target을 다음 중 하나로 분류한다.

```text
fresh Production DB
existing live Production DB/data
```

Repository evidence만으로 어느 상태인지 추정하지 않는다. Phase 5-A~C는 live DB에 접속하지 않으며 5-D가 exact environment, required backup, migration과 rollback/recovery precondition을 결정한다.

# 10. 복구 도구 경계

`infra/backup/`은 Docker 기반 PostgreSQL 18.6 logical backup과 disposable recovery validation authority다.

```text
source private Docker network
→ pg_dump -Fc
→ private partial artifact
→ pg_restore --list + SHA-256 + allowlist metadata
→ completed local set
→ bounded retention / provider-neutral filesystem copy
→ new dev-form-dock-scratch-* restore
→ Flyway V1→V6 + representative data + API health
```

Backup root, off-host target와 scratch identity는 explicit input이며 live/shared restore target을 받지 않는다. Actual off-host independence, final retention, schedule, Production credential과 live DB action은 Phase 5-D exact environment evidence 전까지 확정하지 않는다.
