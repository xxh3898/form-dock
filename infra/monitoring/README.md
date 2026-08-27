# Monitoring Foundation

Phase 5-C1의 provider-neutral runtime signal 경계다. Phase 5-D1 owner decision은 Production monitoring authority를 HomeOps로 고정했지만 HomeOps configuration, credential, agent 설치와 alert delivery는 포함하지 않는다.

## Signals

`check-runtime.sh`은 고정 순서로 다음 signal을 평가한다.

```text
WEB_UNHEALTHY
API_UNHEALTHY
DB_UNHEALTHY
DISK_LOW
BACKUP_STALE_OR_FAILED
HTTP_5XX_BURST
```

- Web/API/PostgreSQL은 canonical Docker health status를 authority로 사용한다.
- Disk는 configured filesystem의 available percentage를 threshold와 비교한다.
- Backup은 Phase 5-B completed metadata allowlist, matching dump/checksum bytes와 `createdAt` freshness를 확인한다. `pg_restore` readability의 authority는 `infra/backup/verify.sh`에 유지한다.
- HTTP 5xx는 current Web/API log format을 추측하지 않는다. 외부의 bounded aggregate가 제공하는 count/window를 input으로 받아 threshold만 평가한다.

## Inputs

```text
FORMDOCK_MONITOR_WEB_CONTAINER
FORMDOCK_MONITOR_API_CONTAINER
FORMDOCK_MONITOR_DB_CONTAINER
FORMDOCK_MONITOR_DISK_PATH
FORMDOCK_MONITOR_DISK_MIN_AVAILABLE_PERCENT       default 15
FORMDOCK_MONITOR_BACKUP_ROOT
FORMDOCK_MONITOR_BACKUP_MAX_AGE_SECONDS           default 93600
FORMDOCK_MONITOR_HTTP_5XX_COUNT                    required aggregate
FORMDOCK_MONITOR_HTTP_5XX_THRESHOLD                default 10
FORMDOCK_MONITOR_HTTP_5XX_WINDOW_SECONDS           default 300
```

Phase 5-D1은 current first-activation target의 initial 값으로 disk `15%`, backup `93600`초, 5xx `10/300`초와 300초 execution cadence를 확정했다. Persistent traffic/data evidence가 생기면 별도 operations slice에서 조정한다.

## Event and exit contract

각 signal은 stdout에 한 줄의 non-secret NDJSON event를 출력한다.

```json
{"formatVersion":1,"signal":"API_UNHEALTHY","status":"OK","observed":"healthy","threshold":"healthy","eventAt":"2026-08-26T00:00:00Z"}
```

Container/path/URL, credential, response body와 raw survey data는 event에 포함하지 않는다.

```text
exit 0   모든 signal OK
exit 2   하나 이상의 signal ALERT
exit 64  input/config invalid
```

HomeOps reporter는 D2가 exact mutation scope를 별도 승인받았을 때 이 NDJSON/exit boundary를 ingestion할 수 있다. Current outbound notification은 `DISABLED_BY_OPERATOR_CHOICE`이며 global switch, credential과 historical replay를 이 repository에서 변경하지 않는다.

## Regression

`test/monitoring-smoke.sh`은 healthy disposable services와 completed backup fixture를 사용해 six-signal OK를 확인하고 각 signal의 ALERT와 invalid configuration exit를 독립 검증한다. 실제 notification을 보내지 않는다.
