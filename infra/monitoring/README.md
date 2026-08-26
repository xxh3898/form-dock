# Monitoring Foundation

Phase 5-C1의 provider-neutral runtime signal과 future notification adapter 경계다. 실제 monitoring provider, webhook/email credential, agent 설치와 Production alert delivery는 포함하지 않는다.

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

Default는 repository initial baseline일 뿐 target disk capacity, traffic과 backup schedule을 반영한 final live threshold가 아니다. Phase 5-D에서 exact environment evidence로 조정한다.

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

Future notification adapter는 이 NDJSON/exit boundary를 읽을 수 있지만 provider 선택과 delivery credential은 별도 decision/승인 전까지 구현하지 않는다.

## Regression

`test/monitoring-smoke.sh`은 healthy disposable services와 completed backup fixture를 사용해 six-signal OK를 확인하고 각 signal의 ALERT와 invalid configuration exit를 독립 검증한다. 실제 notification을 보내지 않는다.
