---
title: Monitoring
status: draft
version: 0.4
last_updated: 2026-08-27
---

# 1. Health authority

- Postgres `pg_isready`
- API `/actuator/health`
- Web `/health`

# 2. External

Cloudflare/public endpoint smoke.

# 3. Logs

Canonical Production Compose의 Web/API/PostgreSQL은 Docker stdout/stderr `json-file`과 initial `max-size=10m`, `max-file=5` baseline을 사용한다. Persistent traffic과 disk evidence에 따른 후속 조정은 별도 operations slice가 소유한다.

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
→ D2-approved HomeOps reporter
→ HomeOps incident history
```

Production monitoring authority는 existing HomeOps다. Service health authority는 exact public HTTPS URL의 HomeOps monitored service이고 backup/deploy event ingestion은 D2가 reporter를 명시적으로 구성할 때만 활성화한다. Current outbound notification은 `DISABLED_BY_OPERATOR_CHOICE`이며 provider 존재나 repository event PASS를 실제 notification delivery PASS로 표현하지 않는다.

# 6. Phase 5 Ownership

Phase 5-C1은 tool-neutral monitoring/log rotation/health acceptance와 signal boundary를 구현했다. Phase 5-D1 owner decision은 Production monitoring/incident authority를 existing HomeOps로 고정했다.

Phase 5-D2는 별도 live-operation 승인 뒤 실제 target에서 다음을 확인한다.

- Postgres `pg_isready`, API `/actuator/health`, Web `/health`
- Cloudflare/public Web과 same-origin Web→API
- 대표 anonymous Public Survey/Response와 Creator login/Admin/Results smoke
- backup failure, disk low와 repeated 5xx alert path

Initial target thresholds:

```text
execution cadence       300 seconds
disk available minimum  15 percent
backup maximum age      93600 seconds
HTTP 5xx burst          10 in 300 seconds
```

HomeOps service/reporter/notification eligibility 변경, notification credential과 public endpoint mutation은 D1 권한에 포함되지 않는다. D2도 exact HomeOps mutation scope를 별도 승인받아야 하며 historical replay와 global notification switch를 자동 변경하지 않는다.
