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

Production runtime은 API/Web source를 build하지 않고 `FORMDOCK_API_IMAGE`와 `FORMDOCK_WEB_IMAGE`로 exact SHA tag 또는 immutable digest를 받는다. PostgreSQL과 API는 host port를 publish하지 않고 Web만 configured port를 `127.0.0.1`에 bind한다. Web은 application network에만, API는 application/database network에, PostgreSQL은 internal database network에만 참여한다.

`production.env.example`은 key interface와 non-secret placeholder만 제공한다. 실제 credential이 포함된 env file은 repository 밖의 private path에서 관리하며 commit하지 않는다. Secret storage/injection/rotation mechanism은 Phase 5-A 범위가 아니다.

PostgreSQL data는 named volume에 저장되고 container recreation과 일반 `down`에서 보존된다. `docker compose down --volumes`는 database volume을 제거하는 destructive operator action이므로 disposable validation project 외에는 실행하지 않는다. Docker volume 자체는 backup이 아니다.

이 contract와 isolated validation은 image publish, live database operation, Cloudflare 설정 또는 Production activation 증거가 아니다.

## Backup 및 복구 준비 상태

Phase 5-B logical backup, checksum/metadata, bounded retention, provider-neutral filesystem copy와 scratch-only restore interface는 [backup tooling guide](backup/README.md)를 따른다. Tooling과 isolated smoke는 live Production DB, schedule, actual off-host provider 또는 Production activation 권한이 아니다.
