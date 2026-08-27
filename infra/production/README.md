# Production Activation Preflight

Phase 5-D1의 Mac mini read-only preflight authority다. 이 directory는 Production activation helper가 아니며 image pull, Compose activation, database write, backup/restore, Cloudflare 또는 HomeOps mutation을 수행하지 않는다.

## Canonical target

```text
release SHA        1648047645720e67d5e928345c875dc53a93ff0e
API image          ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b
Web image          ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551
Compose project    form-dock
Web operator port  127.0.0.1:18082
public hostname    forms.chochiho.cloud
Cloudflare origin  http://form-dock-web:8080
```

Tag가 아니라 위 immutable digest가 D2 artifact authority다. `preflight.sh`은 caller가 전달한 expected 값이 이 allowlist와 다르면 실행을 거부한다.

## Actual read-only 실행

Actual mode는 repository 밖 경로의 존재 여부와 parent readiness만 확인하며 파일 내용을 읽거나 경로를 출력하지 않는다. 아래 path 값은 Issue/PR/log에 복사하지 않고 trusted operator shell에서만 전달한다.

```bash
FORMDOCK_PREFLIGHT_SCOPE=actual \
FORMDOCK_PREFLIGHT_EXPECTED_PROJECT=form-dock \
FORMDOCK_PREFLIGHT_EXPECTED_WEB_PORT=18082 \
FORMDOCK_PREFLIGHT_EXPECTED_RELEASE_SHA=1648047645720e67d5e928345c875dc53a93ff0e \
FORMDOCK_PREFLIGHT_EXPECTED_API_IMAGE='ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b' \
FORMDOCK_PREFLIGHT_EXPECTED_WEB_IMAGE='ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551' \
FORMDOCK_PREFLIGHT_DATA_PATH='<private data filesystem path>' \
FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT='<private FormDock backup root>' \
FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE='<private env file path>' \
FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE='<private deployment state path>' \
FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH='<private operation lock path>' \
  infra/production/preflight.sh
```

PASS output은 fixed sanitized key/value만 포함한다. `actual`과 `fixture` evidence mode를 구분하며 ambiguous/unexpected state는 exit `2`, invalid input은 exit `64`다.

## Secret/config contract

- Actual credential은 repository 밖 owner-only directory에서 관리하고 env file은 mode `600`으로 만든다.
- Docker Compose에는 explicit `--env-file`로만 전달한다.
- `infra/production.env.example`은 key interface이며 credential source가 아니다.
- Initial Creator bootstrap 값은 D2 operator가 private env에 입력한다. Password/email/token/private key와 Secret-derived hash를 state, log, Issue, PR 또는 evidence에 기록하지 않는다.
- D2는 private directory mode `700`, env/state mode `600`을 적용하고 검증한다.

## Operation lock와 state

D2의 single operation lock은 repository 밖 private directory에서 atomic `mkdir`로 획득한다. Existing lock은 무조건 fail closed하며 자동 삭제하지 않는다. Stale 후보는 owner process 부재, current/candidate state와 진행 중 container operation 부재를 operator가 별도 확인한 뒤 explicit recovery로만 제거한다.

Lock metadata는 non-secret allowlist만 사용한다.

```text
formatVersion
operationId
processId
startedAt
releaseGitSha
candidateStateSha256
```

First activation의 previous state는 `NONE`이다. Candidate/current/previous deployment state는 `infra/delivery/` contract를 재사용하고 PostgreSQL volume을 보존한다. Rollback에 `down --volumes`나 destructive Flyway down migration을 결합하지 않는다.

## Backup accepted risk

현재 classification은 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`이며 predeploy backup은 `NOT REQUIRED — FRESH DB`다. Local private backup root는 D2에서 생성하고 initial cadence는 daily, retention은 completed set recent 7이다.

```text
offHostDurabilityStatus       DEFERRED_ACCEPTED_RISK
currentIndependentOffHostTarget NONE
firstActivationAllowed       true
```

이는 Production durability, disaster recovery 또는 independent backup PASS가 아니다. Persistent/dogfooding data가 생기면 physical external disk 또는 mounted NAS로 backup→copy→checksum→restore evidence를 만드는 별도 hardening slice가 필요하다. 같은 internal disk의 directory/APFS volume은 independent target이 아니며 iCloud Drive는 사용하더라도 `INTERIM_SYNC_COPY`로만 분류한다.

## Cloudflare와 HomeOps D2 boundary

Current DNS는 `ROUTE_ABSENT / DNS_NXDOMAIN`이다. D2는 existing external `edge` network에 연결된 containerized cloudflared에 published hostname을 만들고 origin을 `http://form-dock-web:8080`으로 고정한다. API와 PostgreSQL은 `edge`에 연결하지 않는다. D1은 network, Tunnel, DNS 또는 route를 변경하지 않는다.

Monitoring authority는 existing HomeOps다. D2가 별도 exact HomeOps mutation 승인을 받으면 public HTTPS service registration과 backup/deploy ingestion reporter를 구성할 수 있다. Current outbound notification은 `DISABLED_BY_OPERATOR_CHOICE`이며 FormDock first activation을 이유로 global switch를 변경하거나 historical incident를 replay하지 않는다.

Initial target thresholds:

```text
execution cadence              300 seconds
disk minimum available         15 percent
backup maximum age             93600 seconds
HTTP 5xx burst                 10 in 300 seconds
```

## Regression

```bash
bash -n infra/production/preflight.sh infra/production/test/preflight-smoke.sh
infra/production/test/preflight-smoke.sh
```

Fixture PASS는 Mac target PASS가 아니다. Actual sanitized evidence는 [Phase 5-D1 evidence](../../docs/06-quality/phase-5-d1-production-activation-preflight-evidence.md)에 고정한다.
