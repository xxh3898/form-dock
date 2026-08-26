---
title: Monitoring
status: draft
version: 0.3
last_updated: 2026-08-26
---

# 1. Health authority

- Postgres `pg_isready`
- API `/actuator/health`
- Web `/health`

# 2. External

Cloudflare/public endpoint smoke.

# 3. Logs

Canonical Production Compose의 Web/API/PostgreSQL은 Docker stdout/stderr `json-file`과 initial `max-size=10m`, `max-file=5` baseline을 사용한다. Exact target disk와 traffic evidence를 확인한 final 보존량은 Phase 5-D가 조정한다.

민감 정보/응답 원문을 application log에 남기지 않는다.

# 4. Repository signal contract

Executable authority는 [`infra/monitoring/`](../../infra/monitoring/README.md)이다.

```text
WEB_UNHEALTHY
API_UNHEALTHY
DB_UNHEALTHY
DISK_LOW
BACKUP_STALE_OR_FAILED
HTTP_5XX_BURST
```

Service signal은 Docker health를, backup signal은 Phase 5-B completed metadata/checksum과 freshness를 authority로 사용한다. Disk와 backup threshold는 configuration input이다.

Current log format을 근거 없이 반복 5xx 판정에 사용하지 않는다. `HTTP_5XX_BURST`는 future log/edge metrics adapter가 제공하는 explicit count/window만 평가한다.

# 5. Event와 notification boundary

각 signal은 credential, container/path/URL과 raw response를 제외한 fixed NDJSON event를 stdout에 출력한다. Exit `0`은 all OK, `2`는 하나 이상 ALERT, `64`는 invalid configuration이다.

```text
monitoring signal
→ non-secret structured event
→ future notification adapter
```

구체 notification provider/channel/credential은 deferred다. Repository event contract의 PASS를 실제 alert delivery PASS로 표현하지 않는다.

# 6. Phase 5 Ownership

Phase 5-C1은 tool-neutral monitoring/log rotation/health acceptance와 signal→future adapter contract를 구현한다. Netdata, Uptime Kuma 또는 외부 SaaS를 Product requirement로 자동 선택하지 않는다.

Phase 5-D는 별도 live-operation 승인 뒤 실제 target에서 다음을 확인한다.

- Postgres `pg_isready`, API `/actuator/health`, Web `/health`
- Cloudflare/public Web과 same-origin Web→API
- 대표 anonymous Public Survey/Response와 Creator login/Admin/Results smoke
- backup failure, disk low와 repeated 5xx alert path

Monitoring 도구 설치·설정 변경, notification credential과 public endpoint mutation은 Phase 5-C1 권한에 포함되지 않는다.
