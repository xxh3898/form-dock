---
title: Deployment Architecture
status: draft
version: 0.9
last_updated: 2026-08-29
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

Production Compose는 API/Web source를 build하지 않고 required image input을 사용한다. PostgreSQL과 API host port는 publish하지 않고 Web만 configured port를 `127.0.0.1`에 bind한다. Web은 application과 existing external `edge` network에 참여하고 unique alias `form-dock-web`을 사용한다. API는 application/database network에, PostgreSQL은 internal database network에만 참여하며 둘은 `edge`에 연결하지 않는다.

# 2. Release and Production Gates

`dev → main`은 Phase/vertical capability Release Candidate boundary이며 Production deployment가 아니다.

Gate 3는 full release diff, ARM64 target artifact, disposable/test DB Flyway compatibility와 recovery-impact classification을 검증한다. `main`은 intended target에서 build 가능한 release baseline을 뜻하지만 deployed 또는 production-ready 상태를 뜻하지 않는다.

Gate 4/Production Readiness는 required backup/restore action, deployment, health, public smoke와 rollback evidence를 실제 environment에서 검증한다. Live migration, Secret, backup과 activation은 별도 authorization 없이는 수행하지 않는다. 상세 ownership은 [ADR-0005](../08-decisions/adr-0005-release-and-production-gate-separation.md)를 따른다.

Recurring CD는 [ADR-0007](../08-decisions/adr-0007-production-cd-change-gate.md)을 따른다. `main` event는 validation과 누적 change classification을 시작할 뿐 mutation approval이 아니다. Latest successful GitHub `Production` deployment부터 current `main`까지 `MIGRATION_OR_DATA > DEPLOY_CONTROL > UNKNOWN > APPLICATION_ONLY > DOCS_META_ONLY` 우선순위로 분류하고 application-only일 때만 exact kill switch와 protected environment 뒤 candidate가 된다. Baseline 없음/모호함, pending Flyway와 stale/missing backup은 fail closed다.

Phase 5는 `5-A Runtime Foundation → 5-B Backup/Restore/Recovery Readiness → 5-C1 Delivery/Monitoring Foundation → 5-C2 Exact Remote Artifact Publication Evidence → 5-D1 Activation Preflight → 5-D2A Local Production Bootstrap → 5-D2B Public/HomeOps Final Activation` 순서로 완료했다. D2A의 local Secret/config, fresh DB, exact digest deploy와 local acceptance는 `dev`에 통합됐다. Issue #95 D2B는 exact FormDock route, public transport/security, bounded Product canary와 HomeOps service/reporter를 [D2B evidence](../06-quality/phase-5-d2b-public-homeops-activation-evidence.md)로 검증했다.

# 3. External Access

```text
forms.chochiho.cloud
→ containerized Cloudflare Tunnel
→ external edge network
→ http://form-dock-web:8080
→ Web
```

Web은 same-origin `/api`를 API container로 reverse proxy한다. Browser에 별도 API origin을 노출하지 않는다. `127.0.0.1:18082`는 operator health/diagnostic bind이고 Cloudflare origin authority가 아니다.

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

Phase 4 repository Release 기준은 annotated `v0.4.0`과 `main@1648047645720e67d5e928345c875dc53a93ff0e`이다. Gate 3는 API/Web의 native ARM64 build 가능성을 검증했지만 image를 publish하지 않았다. 이후 Issue #89의 5-C2 exact authority가 같은 release SHA/tree의 API/Web full-SHA tags만 GitHub-hosted native ARM64에서 GHCR에 최초 publish했다. Remote digest/platform/OCI source identity와 pull-by-digest canonical Compose acceptance는 [Phase 5-C2 evidence](../06-quality/phase-5-c2-remote-artifact-publication-evidence.md)가 소유한다. 이 artifact publication은 target Mac mini 또는 Production deployment evidence가 아니다.

`FORMDOCK_API_IMAGE`와 `FORMDOCK_WEB_IMAGE`는 exact SHA tag 또는 immutable digest를 전달하는 runtime interface다. Production Compose에 `build:` 또는 hard-coded `latest` authority를 두지 않는다. Phase 5-A isolated smoke는 local-only temporary tag를 사용할 수 있지만 remote publication이나 deployed artifact evidence로 간주하지 않는다.

Recurring CD artifact는 API/Web과 `form-dock-runtime-config`를 GitHub-hosted native `linux/arm64`에서 build한다. Runtime-config는 canonical Compose, non-secret recurring deploy/verification scripts와 revision만 포함하며 actual env/credential은 포함하지 않는다. Host의 immutable `releases/<digest>`와 `pending/current/previous` pointer는 final health/public smoke 성공 뒤에만 전진한다.

# 7. Deployment identity와 rollback

Application image rollback과 DB migration rollback을 분리한다.

Recurring worker는 operation lock, accepted state, PostgreSQL volume identity, Flyway exact `V1..V6`와 fresh verified backup을 candidate activation보다 먼저 확인한다. Application rollback은 previous API/Web/runtime-config를 적용하고 같은 PostgreSQL volume을 검증하지만 DB restore/down migration을 자동 실행하지 않는다.

Flyway는 forward-only migration을 기본으로 한다.

`infra/delivery/`은 release Git SHA, API/Web reference와 immutable identity, canonical Compose revision, non-secret configuration revision, UTC 기록 시각과 previous state SHA를 fixed allowlist state로 표현한다. Configuration revision은 private env bytes나 Secret-derived hash를 state에 기록하지 않고 configuration management identity만 연결한다. Candidate와 previous는 별도 state이며 first activation은 previous identity를 `NONE`으로 분류한다.

Repository delivery smoke는 canonical Production Compose를 `dev-form-dock-delivery-*` disposable project에 적용하고 health 뒤 exact previous API/Web image와 matching configuration revision으로 rollback한다. Rollback은 동일 PostgreSQL volume과 Flyway V1→V6를 보존하며 volume 삭제나 down migration을 포함하지 않는다. Local Docker image ID는 isolated mechanics evidence일 뿐 GHCR digest 또는 deployed state가 아니다.

# 8. Configuration and Secret Boundary

- Production configuration key는 safe example과 documentation으로만 관리하고 실제 값을 repository default에 하드코딩하지 않는다.
- Password, token, private key와 Cloudflare credential은 frontend bundle, Issue, PR, log와 evidence에 기록하지 않는다.
- Actual Production config는 repository 밖 owner-only directory와 mode `600` env file로 관리하고 Compose에 explicit `--env-file`로 전달한다. Configuration revision은 non-secret management identity이며 Secret bytes/hash를 state에 기록하지 않는다.
- Production `.env` 작성, Secret 조회·생성·변경과 live injection은 해당 operation의 별도 승인 없이는 수행하지 않는다. D2A가 생성한 owner-only runtime env와 trusted bootstrap input 경계는 D2B 뒤에도 유지하며 credential 회전·삭제는 별도 operations decision이다.
- `infra/production.env.example`은 key interface와 non-secret placeholder만 소유한다. 실제 env file은 repository 밖의 private path에서 관리한다.

# 9. Database Environment Boundary

Production activation 전 exact target을 다음 중 하나로 분류한다.

```text
fresh Production DB
existing live Production DB/data
```

Repository evidence만으로 어느 상태인지 추정하지 않는다. Phase 5-D1의 target resource/state absence evidence는 현재 Mac mini를 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`로 분류했다. D1에서는 Production DB credential/SQL을 사용하지 않았으며 Issue #93 D2A가 mutation 직전 re-preflight 뒤 clean Flyway startup과 empty-state acceptance를 검증한다.

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

Backup root, off-host target와 scratch identity는 explicit input이며 live/shared restore target을 받지 않는다. Initial schedule은 daily, retention은 completed recent 7이다. Independent off-host target은 현재 `NONE / DEFERRED_ACCEPTED_RISK`로 승인됐으며 first activation을 막지 않지만 durability/DR PASS가 아니다. Persistent data 이후 separate physical disk 또는 mounted NAS hardening evidence가 필요하다.

# 11. Log와 monitoring 경계

Canonical Production Compose의 Web/API/PostgreSQL은 Docker stdout/stderr `json-file` rotation을 initial `max-size=10m`, `max-file=5` baseline으로 제한한다. Application이 response body 또는 raw survey data를 새로 log하지 않으며 persistent traffic/disk evidence에 따른 후속 조정은 별도 operations slice가 소유한다.

`infra/monitoring/`은 existing Docker health, configured disk availability, Phase 5-B completed backup metadata freshness와 explicit HTTP 5xx aggregate를 fixed NDJSON signal로 변환한다. Current Web/API log format을 근거 없이 해석하지 않는다.

Production monitoring authority는 existing HomeOps다. Initial target은 300초 cadence, disk available 15%, backup max age 93600초, HTTP 5xx 10건/300초다. Issue #95 D2B는 exact public health service, deployment/backup reporter와 `DISK_LOW`/`HTTP_5XX_BURST` signal mapping을 active/accepted로 검증했다. Current outbound notification은 `DISABLED_BY_OPERATOR_CHOICE`다.
