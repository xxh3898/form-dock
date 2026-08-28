# Infrastructure

PostgreSQL, API와 Web의 local/Production Docker Compose 경계다.

## Local development

Repository root에서 실행한다.

```bash
docker compose --env-file .env.example -f infra/compose.yaml config --quiet
docker compose --env-file .env.example -f infra/compose.yaml up --build --wait
docker compose --env-file .env.example -f infra/compose.yaml down
```

Local project는 `dev-form-dock`이다. Web, API와 PostgreSQL port는 `127.0.0.1`에만 bind하고 PostgreSQL data는 development-only named volume에 저장한다. 일반 `down`은 volume을 보존한다.

## Production configuration contract

`compose.production.yaml`은 repository의 Production canonical Compose다. 실제 Production deployment를 수행하지 않고도 다음처럼 static configuration을 검증할 수 있다.

```bash
docker compose \
  --env-file infra/production.env.example \
  -f infra/compose.production.yaml \
  config --quiet
```

Production runtime은 API/Web source를 build하지 않고 `FORMDOCK_API_IMAGE`와 `FORMDOCK_WEB_IMAGE`로 exact SHA tag 또는 immutable digest를 받는다. PostgreSQL과 API는 host port를 publish하지 않고 Web만 configured port를 `127.0.0.1`에 bind한다. Web은 application과 configured external `edge` network에 참여해 alias `form-dock-web`을 제공한다. API는 application/database network에, PostgreSQL은 internal database network에만 참여하며 둘은 `edge`에 연결하지 않는다.

`production.env.example`은 key interface와 non-secret placeholder만 제공한다. 실제 credential이 포함된 env file은 repository 밖의 private path에서 관리하며 commit하지 않는다. Secret storage/injection/rotation mechanism은 Phase 5-A 범위가 아니다. Issue #93 D2A는 repository 밖 owner-only runtime env와 trusted bootstrap input을 사용하는 first local activation 범위만 별도로 승인한다.

PostgreSQL data는 named volume에 저장되고 container recreation과 일반 `down`에서 보존된다. `docker compose down --volumes`는 database volume을 제거하는 destructive operator action이므로 disposable validation project 외에는 실행하지 않는다. Docker volume 자체는 backup이 아니다.

이 contract와 isolated validation은 image publish, live database operation, Cloudflare 설정 또는 Production activation 증거가 아니다.

## Backup 및 복구 준비 상태

Phase 5-B logical backup, checksum/metadata, bounded retention, provider-neutral filesystem copy와 scratch-only restore interface는 [backup tooling guide](backup/README.md)를 따른다. Tooling과 isolated smoke는 live Production DB, schedule, actual off-host provider 또는 Production activation 권한이 아니다.

## Delivery 및 monitoring 준비 상태

Phase 5-C1 deployment state, canonical Compose isolated staging/health와 application-only rollback interface는 [delivery foundation](delivery/README.md)을 따른다. Web/API/PostgreSQL health, disk, completed backup freshness와 explicit HTTP 5xx aggregate의 machine-readable signal은 [monitoring foundation](monitoring/README.md)을 따른다.

Production Compose의 Web/API/PostgreSQL은 Docker `json-file` initial baseline `max-size=10m`, `max-file=5`로 stdout/stderr를 bounded rotation한다. `FORMDOCK_LOG_MAX_SIZE`와 `FORMDOCK_LOG_MAX_FILE`은 non-secret configuration interface이며 persistent traffic/disk evidence에 따른 후속 조정은 별도 operations slice가 소유한다.

Delivery/monitoring smoke는 local-only image ID와 `dev-form-dock-delivery-*` disposable project만 사용한다. GHCR publish, live Secret/env/project/database, notification provider, Cloudflare와 Production activation은 수행하지 않는다.

## Remote artifact publication evidence

Issue #89의 exact GitHub-hosted native ARM64 job은 annotated `v0.4.0` source의 API/Web full-SHA tags만 GHCR에 최초 publish했다. [`published-artifact-smoke.sh`](delivery/test/published-artifact-smoke.sh)는 observed remote digest refs를 다시 pull하고 current canonical Production Compose와 delivery tooling으로 disposable health, same-origin, Flyway V1→V6와 residue를 검증한다. Exact refs와 digests는 [Phase 5-C2 evidence](../docs/06-quality/phase-5-c2-remote-artifact-publication-evidence.md)를 따른다.

이 helper는 GitHub-hosted disposable validation 전용이며 registry credential을 생성·저장하지 않는다. Mac mini D2A pull/deploy, Production env/Secret와 fresh database는 Issue #93의 별도 승인 범위에서 완료했다. Cloudflare/HomeOps/public activation은 Issue #95 D2B evidence 범위에서 별도로 완료했다.

## Production activation

[`production/preflight.sh`](production/README.md)는 Phase 5-D1 Mac mini target을 read-only로 검사하고 fixed sanitized evidence만 출력한다. [`production/activate-first.sh`](production/README.md)는 Issue #93 D2A에서 exact local Production bootstrap, bootstrap finalization, local acceptance와 first backup/scratch restore를 수행했다. Issue #95 D2B의 public route/security/Product/HomeOps 결과는 [D2B evidence](../docs/06-quality/phase-5-d2b-public-homeops-activation-evidence.md)에 기록하며 D2A helper의 mutation scope를 확대하지 않는다.

## Recurring Production CD foundation

`cd/`는 latest successful GitHub Production deployment baseline과 cumulative change classifier를 제공한다. `production/forced-command.sh.example`, `production/deploy-release.sh`, `production/report-homeops-deployment.sh`와 root `runtime-config.Dockerfile`은 exact digest recurring transaction의 installable repository source다. Fixture는 lock, backup/Flyway HOLD, success-only pointer/state 전환과 application rollback의 DB volume 보존을 검증한다.

이 foundation은 repository-only다. Deploy-control 자체는 cumulative classifier에서 HOLD되며 이 PR은 GHCR publish, GitHub Environment/Variable/Secret, SSH/Tailscale 설치와 live Production mutation을 수행하지 않는다. 별도 Ops acceptance가 accepted baseline, kill switch, protected Environment, Secret과 installed forced-command를 검증한 뒤에만 automatic application candidate를 활성화할 수 있다.
