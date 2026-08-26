# Delivery Foundation

Phase 5-C1의 deployment identity, canonical Compose isolated staging, health와 application rollback 경계다. 이 tooling은 local-only image와 disposable resource만 검증하며 GHCR publication 또는 Production activation을 수행하지 않는다.

## Deployment state

State는 줄바꿈으로 구분한 fixed key/value 형식이며 다음 11개 field만 허용한다.

```text
formatVersion=1
stateRole=candidate|previous
releaseGitSha=<40 lowercase hexadecimal Git SHA>
apiImageReference=<exact tag or digest reference>
apiImageIdentity=sha256:<64 lowercase hexadecimal characters>
webImageReference=<exact tag or digest reference>
webImageIdentity=sha256:<64 lowercase hexadecimal characters>
composeRevision=sha256:<infra/compose.production.yaml bytes>
configurationRevision=sha256:<non-secret configuration revision identity>
recordedAt=<UTC second precision>
previousStateSha256=NONE|sha256:<previous state file bytes>
```

`candidate.recordedAt`은 staging 기록 시각이고 `previous.recordedAt`은 해당 previous identity가 activation evidence로 기록된 시각이다. `configurationRevision`은 private env bytes나 Secret-derived hash가 아니라 configuration management가 부여한 non-secret revision identity다. Candidate는 previous state file bytes의 SHA-256를 참조한다. 첫 activation은 `previousStateSha256=NONE`으로 명시하며 previous가 있다고 가장하지 않는다.

State는 password, token, private env path를 표현할 자유 field가 없고 unknown/duplicate/partial field, `latest` reference와 불완전 identity를 거부한다.

```bash
infra/delivery/validate-state.sh /absolute/path/candidate.state candidate
```

Local isolated smoke의 `sha256:` identity는 actual local Docker image ID다. Remote registry digest 또는 published/deployed artifact evidence가 아니다.

## Isolated staging

`stage-isolated.sh`은 다음 input만 받는다.

```text
FORMDOCK_DELIVERY_PROJECT=dev-form-dock-delivery-<unique-id>
FORMDOCK_DELIVERY_ENV_FILE=<repository 밖의 mode 600 disposable env>
FORMDOCK_DEPLOYMENT_STATE_FILE=<candidate state>
```

Disposable env는 `FORMDOCK_DELIVERY_SCOPE=isolated`, matching project/config revision, `formdock_delivery` DB name/user, 24자 이상의 generated credential, bounded log values, loopback Web port와 disabled Creator bootstrap만 허용한다. Image ref는 env가 아니라 validated state에서 주입한다.

Stage는 canonical `infra/compose.production.yaml` revision과 local image ID를 확인하고 initial project resource가 없을 때만 `docker compose up --wait`를 수행한다. `health-check.sh`은 다음을 확인한다.

- PostgreSQL/API host port 0
- Web host bind `127.0.0.1` only
- canonical application/internal database network topology
- PostgreSQL `pg_isready`, API Actuator와 Web `/health`
- Web container의 safe same-origin `/api/auth/csrf` reverse proxy 응답
- running container image ID와 state identity 일치

Canonical `form-dock` project, live/shared volume, Production env와 public endpoint는 대상이 아니다.

## Application rollback

`rollback-isolated.sh`은 candidate state의 `previousStateSha256`와 exact previous state bytes를 대조하고 running candidate image와 PostgreSQL volume identity를 검증한 뒤, candidate health가 실패한 경우에도 exact previous API/Web state와 matching previous configuration revision을 같은 disposable project에 다시 적용한다. Candidate/previous private env는 동일 disposable DB connection identity를 유지해야 한다. Regression은 PostgreSQL container/volume identity와 Flyway history를 모두 보존한다.

Application rollback은 database rollback이 아니다. Flyway file 수정, destructive down migration, DB volume 삭제와 Production `down --volumes`는 rollback command에 포함하지 않는다.

Activation은 Phase 5-D의 별도 승인 경계다. 5-C1은 `stage → health → 별도 activation 승인` 순서와 `health failure → exact previous application rollback` command boundary만 제공하며 live activate/rollback command를 실행하지 않는다.

## Regression

`test/delivery-smoke.sh`은 current local application images에서 distinct candidate/previous fixture images와 non-secret configuration revisions를 만들고 다음을 serial로 검증한다.

```text
state validation
→ candidate canonical staging
→ health / same-origin acceptance
→ monitoring signals
→ exact previous application rollback
→ DB volume / Flyway V1..V6 preservation
→ exact disposable resource cleanup
```

Smoke는 random disposable DB credential을 private temporary env에만 기록하고 종료 시 exact project container/network/volume, fixture image와 temporary state를 제거한다.
