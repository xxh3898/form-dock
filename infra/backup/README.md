# Backup 및 복구 도구

Phase 5-B의 PostgreSQL logical backup, bounded retention, provider-neutral copy와 disposable scratch restore tooling이다. Docker와 Bash를 사용하며 실제 Production DB, schedule, NAS/cloud credential 또는 live restore를 자동으로 선택하지 않는다.

## 산출물 계약

완료된 backup generation은 private backup root 안의 세 파일로 구성된다.

```text
<backup-id>.dump    PostgreSQL custom-format archive (`pg_dump -Fc`)
<backup-id>.sha256  dump filename과 SHA-256
<backup-id>.meta    allowlist metadata, `status=complete`
```

`backup.sh`은 mode `700` staging directory에서 dump readability와 checksum을 검증하고 metadata를 마지막에 finalize한다. Backup root와 artifact는 group/other permission이 없어야 하며 existing completed artifact를 overwrite하지 않는다.

Metadata fields:

```text
formatVersion
status
createdAt
postgresServerVersion
pgDumpVersion
applicationReleaseSha
backupFilename
sha256
```

Password, token, endpoint credential과 raw Product data는 metadata에 기록하지 않는다.

## 운영자 입력 경계

- Backup/off-host root는 repository 밖의 existing absolute private directory여야 한다.
- Database credential은 approved private operator environment로 전달한다. Command argument, Issue, PR 또는 log에 값을 쓰지 않는다.
- `FORMDOCK_RELEASE_SHA`는 exact 40-character application Release SHA다.
- `FORMDOCK_DB_DOCKER_NETWORK`는 source PostgreSQL이 참여한 exact private Docker network다.
- 기본 PostgreSQL client image는 `postgres:18.6-alpine3.23`이다.
- 이 tooling을 live Production에 실행하는 권한은 Phase 5-B에 포함되지 않는다.

## Backup 및 검증

필수 environment interface:

```text
FORMDOCK_BACKUP_ROOT
FORMDOCK_RELEASE_SHA
FORMDOCK_DB_DOCKER_NETWORK
FORMDOCK_DB_HOST
FORMDOCK_DB_PORT              optional, default 5432
FORMDOCK_DB_NAME
FORMDOCK_DB_USERNAME
FORMDOCK_DB_PASSWORD
FORMDOCK_BACKUP_ID            optional generated ID override
FORMDOCK_POSTGRES_IMAGE       optional pinned client image override
```

```bash
infra/backup/backup.sh

FORMDOCK_BACKUP_ID=formdock-... \
  infra/backup/verify.sh
```

`FORMDOCK_BACKUP_ID` 값을 command에 쓰는 것은 credential이 아니지만 path-safe FormDock ID여야 한다. Password는 이미 private environment에 존재해야 한다.

## 보존 정책

Retention은 verified `formdock-*.meta` complete set만 count한다. Partial/unrelated file은 generation으로 세거나 삭제하지 않는다. Default는 dry-run이고 실제 삭제에는 explicit apply가 필요하다.

```bash
FORMDOCK_RETENTION_COUNT=7 \
FORMDOCK_RETENTION_APPLY=false \
  infra/backup/retention.sh
```

Isolated evidence에서만 `FORMDOCK_RETENTION_APPLY=true`를 사용한다. Live count `7`은 initial validation baseline이며 dogfooding disk/data/off-host evidence 전까지 final 운영값이 아니다.

## Provider-neutral off-host 복사

```bash
FORMDOCK_BACKUP_ID=formdock-... \
FORMDOCK_OFF_HOST_TARGET_ROOT=/absolute/private/mounted-target \
  infra/backup/copy-off-host.sh
```

Source와 target은 다른 canonical directory여야 한다. Target에서 partial copy → checksum/custom-format verification → metadata-last finalize 순서를 사용한다. Mounted target이 실제로 primary disk와 독립적인지는 Phase 5-D exact environment에서 별도로 확인한다.

## Disposable scratch 복구

`restore-scratch.sh`은 다음 exact resource만 새로 만든 뒤 항상 정리한다.

```text
dev-form-dock-scratch-<id>-database
dev-form-dock-scratch-<id>-postgres-data
dev-form-dock-scratch-<id>-postgres
dev-form-dock-scratch-<id>-api
```

Existing container/network/volume reuse와 host port publish를 거부한다. Checksum/custom-format 검증 전에 scratch resource를 만들지 않고, restore는 `--exit-on-error --no-owner --no-acl`을 사용한다.

Required interface:

```text
FORMDOCK_BACKUP_ROOT
FORMDOCK_BACKUP_ID
FORMDOCK_SCRATCH_ID           dev-form-dock-scratch-* only
FORMDOCK_SCRATCH_DB_PASSWORD  disposable scratch credential
FORMDOCK_API_IMAGE            already-present exact/local image
FORMDOCK_RESTORE_VERIFY_SQL_FILE optional read-only assertion file
```

Restore가 확인하는 authority는 checksum, Flyway success versions `1..6`, optional representative data와 restored API health다. Scratch result는 live restore 또는 disaster-recovery completion evidence가 아니다.

## 격리 end-to-end 검증

Local application image를 먼저 build한 뒤 repository smoke를 실행한다.

```bash
docker compose --env-file .env.example -f infra/compose.yaml build api
infra/backup/test/recovery-smoke.sh
```

Smoke는 host/public port가 없는 disposable source PostgreSQL/API를 생성하고 representative V1→V6 data를 backup한다. Retention dry-run/apply, local-directory off-host simulation, 별도 scratch restore와 API health를 serial로 검증한 뒤 exact container/network/volume/temp artifact를 제거한다.

## 명시적 비작업

- Docker volume을 backup으로 간주하지 않음
- Production DB, live backup/restore/migration 접근 0
- cron/launchd schedule 설치 0
- NAS/cloud provider, credential 또는 endpoint 선택 0
- GHCR publish, deploy, Cloudflare/public route와 Production activation 0
