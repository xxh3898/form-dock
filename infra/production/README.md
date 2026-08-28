# Production Activation

Phase 5-D1의 Mac mini read-only preflight와 Phase 5-D2A의 local Production first-activation authority다.

Recurring CD의 repository source는 `forced-command.sh.example`, `deploy-release.sh`와 `report-homeops-deployment.sh`다. Initial activation helper와 달리 accepted state가 있는 application-only release를 대상으로 하며 [ADR-0007](../../docs/08-decisions/adr-0007-production-cd-change-gate.md)의 cumulative classifier와 별도 Ops activation을 전제로 한다.

```text
restricted SSH exact input
→ immutable runtime-config release/pending
→ operation lock
→ current state + PostgreSQL volume/Flyway V1..V6
→ fresh verified backup
→ exact ARM64 API/Web candidate
→ loopback/public same-origin health
→ success-only previous/current/state commit
```

실패 시 accepted application/runtime-config로 rollback하고 PostgreSQL volume을 보존한다. DB restore/down migration, `down --volumes`, Secret/Cloudflare/HomeOps 설정 mutation은 수행하지 않는다. Installed HomeOps reporter만 호출하며 caller에 HMAC Secret을 전달하지 않는다. 이 파일들의 repository 통합은 host 설치나 Production CD 활성화 권한이 아니다.

별도 activation Ops는 accepted live Compose를 immutable runtime-config release로 설치하고 `current` pointer 및 owner-only `runtime-config/state`를 seed해야 한다. Runtime state는 다음 fixed non-secret field만 사용하고 `currentSha/currentDigest`가 accepted `deployment.state` 및 pointer와 일치해야 한다.

```text
formatVersion=1
currentSha=<accepted application SHA>
currentDigest=sha256:<accepted runtime-config digest>
previousSha=<previous SHA or zero sentinel>
previousDigest=<previous digest or zero sentinel>
recordedAt=<UTC second precision>
```

기존 `deployment.state`는 Phase 5-C1/D2A의 11-field state contract를 그대로 유지한다. Recurring success는 accepted state를 `deployment.previous.state`로 보존하고 새 candidate의 `previousStateSha256`를 그 exact bytes에 연결한다. Runtime-config state를 application deployment state나 Secret-derived configuration revision으로 대체하지 않는다.

Owner-only `cd.env`는 recurring worker에 다음 네 field만 제공한다. Backup freshness는 1시간 이상 7일 이하의 초 단위 값이며 public origin은 exact FormDock HTTPS origin이어야 한다. 실제 private path와 credential은 repository에 기록하지 않는다.

```text
FORMDOCK_BACKUP_ROOT=<absolute private backup directory>
FORMDOCK_BACKUP_MAX_AGE_SECONDS=<3600..604800>
FORMDOCK_PUBLIC_ORIGIN=https://forms.chochiho.cloud
FORMDOCK_HOMEOPS_REPORTER=<installed reporter absolute path>
```

- `preflight.sh`: target/artifact/resource/route 상태를 mutation 없이 분류한다.
- `activate-first.sh`: Issue #93이 별도로 승인한 first local activation, Creator bootstrap/finalization, local acceptance와 첫 backup/scratch restore만 수행한다.

`activate-first.sh`은 Cloudflare route, HomeOps configuration, GHCR artifact 또는 public endpoint를 변경하지 않는다. 이러한 D2B action은 Issue #95의 별도 Production Operations Gate에서 수행했고 helper scope에는 추가하지 않았다.

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

Tag가 아니라 위 immutable digest가 D2A artifact authority다. `preflight.sh`은 caller가 전달한 expected 값이 이 allowlist와 다르면 실행을 거부한다.

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

## D2A first activation

`activate-first.sh`은 아래 path interface를 사용한다. 값은 trusted operator shell에서만 전달하며 Issue, PR 또는 evidence에 private absolute path를 기록하지 않는다.

```text
FORMDOCK_PREFLIGHT_DATA_PATH
FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT
FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE
FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE
FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH
FORMDOCK_BOOTSTRAP_INPUT_FILE
```

Runtime env, deployment state와 operation lock은 동일한 새 private root 아래에 있어야 한다. Private root와 backup root가 이미 존재하거나 exact FormDock container/network/volume/port conflict가 있으면 first activation을 거부한다. Bootstrap input은 repository 밖 owner-owned, non-symlink, mode `600` file이며 다음 네 key만 한 번씩 포함한다.

```text
FORMDOCK_BOOTSTRAP_ENABLED=true
FORMDOCK_BOOTSTRAP_EMAIL=<trusted local input>
FORMDOCK_BOOTSTRAP_PASSWORD=<trusted local input>
FORMDOCK_BOOTSTRAP_DISPLAY_NAME=<trusted local input>
```

Password는 15 Unicode code point 이상, UTF-8 72 byte 이하이고 email/display name은 application normalization limit를 mutation 전에 검증한다. 실제 값은 stdout/stderr, state, evidence와 command argument에 쓰지 않는다.

Activation은 다음 순서를 고정한다.

```text
bootstrap input validation
→ actual D1 preflight
→ atomic private-root claim + operation lock
→ owner-only env/state/backup root
→ exact v0.4.0 digest pull and linux/arm64 verification
→ canonical Compose first start
→ Flyway V1..V6 + one ADMIN bootstrap
→ loopback same-origin login/session/survey-list acceptance
→ bootstrap-disabled API recreation
→ PostgreSQL container/volume and JDBC session preservation
→ final fresh login acceptance
→ first pg_dump -Fc backup and verification
→ disposable scratch restore
→ accepted deployment state commit
→ operation lock release
```

Final runtime env는 bootstrap을 `false`로 두고 email/password/display name을 빈 값으로 유지한다. Initial bootstrap 값은 trusted input에서 process environment로 한 번만 전달하며 final runtime env에 기록하지 않는다.

```bash
FORMDOCK_PREFLIGHT_DATA_PATH='<approved data filesystem>' \
FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT='<new private backup root>' \
FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE='<new private runtime env>' \
FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE='<new private deployment state>' \
FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH='<new private operation lock>' \
FORMDOCK_BOOTSTRAP_INPUT_FILE='<existing trusted mode-600 input>' \
  infra/production/activate-first.sh
```

성공 output은 release/Flyway/acceptance/backup/route/lock의 fixed sanitized status만 포함한다. Private root에는 owner-only `preflight.evidence`, `activation.evidence`, runtime env, deployment state와 operation log를 보존한다.

## Secret/config contract

- Actual credential은 repository 밖 owner-only directory에서 관리하고 env file은 mode `600`으로 만든다.
- Docker Compose에는 explicit `--env-file`로만 전달한다.
- `infra/production.env.example`은 key interface이며 credential source가 아니다.
- Initial Creator bootstrap 값은 D2A operator가 trusted local input으로 전달한다. Password/email/token/private key와 Secret-derived hash를 state, log, Issue, PR 또는 evidence에 기록하지 않는다.
- D2A는 private directory mode `700`, env/state mode `600`을 적용하고 검증한다.

## Operation lock와 state

D2A의 single operation lock은 repository 밖 private directory에서 atomic `mkdir`로 획득한다. Existing lock은 무조건 fail closed하며 자동 삭제하지 않는다. Stale 후보는 owner process 부재, current/candidate state와 진행 중 container operation 부재를 operator가 별도 확인한 뒤 explicit recovery로만 제거한다.

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

현재 classification은 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`이며 predeploy backup은 `NOT REQUIRED — FRESH DB`다. Local private backup root는 D2A에서 생성하고 initial cadence는 daily, retention은 completed set recent 7이다. D2A는 retention apply/schedule을 실행하지 않는다.

```text
offHostDurabilityStatus       DEFERRED_ACCEPTED_RISK
currentIndependentOffHostTarget NONE
firstActivationAllowed       true
```

이는 Production durability, disaster recovery 또는 independent backup PASS가 아니다. Persistent/dogfooding data가 생기면 physical external disk 또는 mounted NAS로 backup→copy→checksum→restore evidence를 만드는 별도 hardening slice가 필요하다. 같은 internal disk의 directory/APFS volume은 independent target이 아니며 iCloud Drive는 사용하더라도 `INTERIM_SYNC_COPY`로만 분류한다.

## Cloudflare와 HomeOps D2 boundary

Current DNS/route는 `forms.chochiho.cloud → http://form-dock-web:8080`으로 active/accepted다. D2A helper는 canonical Compose가 요구하는 existing external `edge` network에 Web만 연결하고 published hostname, Tunnel, DNS와 cloudflared configuration을 변경하지 않는다. API와 PostgreSQL은 `edge`에 연결하지 않는다. Public 연결과 acceptance는 Issue #95의 별도 D2B authority와 [D2B evidence](../../docs/06-quality/phase-5-d2b-public-homeops-activation-evidence.md)가 소유한다.

Monitoring authority는 existing HomeOps다. D2A helper는 HomeOps runtime health를 read-only로 재확인할 뿐 service registration, backup/deploy reporter와 notification eligibility를 변경하지 않는다. Issue #95 D2B는 FormDock public health service와 current deployment/backup reporter event를 accepted interface로 구성했고 signal mapping을 확인했다. Current outbound notification은 `DISABLED_BY_OPERATOR_CHOICE`이며 global switch와 historical incident는 변경하지 않았다.

Initial target thresholds:

```text
execution cadence              300 seconds
disk minimum available         15 percent
backup maximum age             93600 seconds
HTTP 5xx burst                 10 in 300 seconds
```

## Regression

```bash
bash -n \
  infra/production/common.sh \
  infra/production/preflight.sh \
  infra/production/activate-first.sh \
  infra/production/test/preflight-smoke.sh \
  infra/production/test/activate-first-smoke.sh \
  infra/production/test/activate-first-runtime-smoke.sh
infra/production/test/preflight-smoke.sh
infra/production/test/activate-first-smoke.sh
infra/production/test/activate-first-runtime-smoke.sh
```

`activate-first-smoke.sh`은 input/env/state/lock/order/negative-scope contract를 fixture로 검증한다. `activate-first-runtime-smoke.sh`은 local application image, random loopback port와 disposable project/network/volume에서 bootstrap, same-origin login, JDBC session, final Secret 제거와 PostgreSQL volume 보존을 검증한 뒤 exact residue를 정리한다.

Fixture/disposable PASS는 actual Mac D2A PASS가 아니다. Actual D1 evidence는 [Phase 5-D1 evidence](../../docs/06-quality/phase-5-d1-production-activation-preflight-evidence.md), completed local bootstrap 결과는 [Phase 5-D2A evidence](../../docs/06-quality/phase-5-d2a-local-production-bootstrap-evidence.md)에 고정한다.

## Failure preservation

Actual preflight 또는 bootstrap input validation 실패는 mutation 전에 종료한다. Private-root claim 뒤 실패하면 operation lock, runtime env, pending state, operation log와 PostgreSQL volume을 보존하고 accepted deployment state를 기록하지 않는다. `down --volumes`, Docker prune, Flyway down/rewrite와 자동 evidence 삭제를 수행하지 않는다. First activation에는 previous deployment가 없으므로 실패를 rollback success로 표현하지 않는다.
