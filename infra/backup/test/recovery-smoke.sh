#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
# shellcheck source=../common.sh
source "$BACKUP_DIR/common.sh"

formdock_require_command docker

api_image="${FORMDOCK_API_IMAGE:-dev-form-dock-api}"
postgres_image="${FORMDOCK_POSTGRES_IMAGE:-postgres:18.6-alpine3.23}"
release_sha="${FORMDOCK_RELEASE_SHA:-$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)}"
formdock_validate_release_sha "$release_sha"
docker image inspect "$api_image" >/dev/null 2>&1 \
  || formdock_die "Recovery smoke requires an existing local API image: $api_image"

run_suffix="$$"
source_id="dev-form-dock-recovery-$run_suffix"
source_network="$source_id-database"
source_volume="$source_id-postgres-data"
source_postgres="$source_id-postgres"
source_api="$source_id-api"
scratch_id="dev-form-dock-scratch-recovery-$run_suffix"
test_password='formdock-recovery-smoke-only'

tmp_base="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-recovery-smoke.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-recovery-smoke.*) ;;
  *) formdock_die 'Recovery smoke temporary directory escaped the expected root.' ;;
esac
chmod 700 "$temp_root"
backup_root="$temp_root/backups"
off_host_root="$temp_root/off-host"
corrupt_root="$temp_root/corrupt"
mkdir "$backup_root" "$off_host_root" "$corrupt_root"
chmod 700 "$backup_root" "$off_host_root" "$corrupt_root"

source_network_created=false
source_volume_created=false
source_postgres_created=false
source_api_created=false
temp_created=true

cleanup_source() {
  if [ "$source_api_created" = true ]; then
    docker rm -f "$source_api" >/dev/null 2>&1 || true
    source_api_created=false
  fi
  if [ "$source_postgres_created" = true ]; then
    docker rm -f "$source_postgres" >/dev/null 2>&1 || true
    source_postgres_created=false
  fi
  if [ "$source_volume_created" = true ]; then
    docker volume rm "$source_volume" >/dev/null 2>&1 || true
    source_volume_created=false
  fi
  if [ "$source_network_created" = true ]; then
    docker network rm "$source_network" >/dev/null 2>&1 || true
    source_network_created=false
  fi
}

cleanup_temp() {
  if [ "$temp_created" = true ]; then
    case "$temp_root" in
      "$tmp_base"/formdock-recovery-smoke.*)
        find "$temp_root" -depth -delete
        ;;
      *) formdock_die 'Refusing to remove an unexpected recovery smoke path.' ;;
    esac
    temp_created=false
  fi
}

cleanup_all() {
  cleanup_source
  cleanup_temp
}
trap cleanup_all EXIT

for container in "$source_postgres" "$source_api"; do
  ! docker container inspect "$container" >/dev/null 2>&1 \
    || formdock_die "Recovery smoke refuses existing container: $container"
done
! docker network inspect "$source_network" >/dev/null 2>&1 \
  || formdock_die "Recovery smoke refuses existing network: $source_network"
! docker volume inspect "$source_volume" >/dev/null 2>&1 \
  || formdock_die "Recovery smoke refuses existing volume: $source_volume"

docker network create \
  --label com.formdock.scope=recovery-smoke \
  --label "com.formdock.run-id=$source_id" \
  "$source_network" >/dev/null
source_network_created=true
docker volume create \
  --label com.formdock.scope=recovery-smoke \
  --label "com.formdock.run-id=$source_id" \
  "$source_volume" >/dev/null
source_volume_created=true

(
  export POSTGRES_DB=formdock
  export POSTGRES_USER=formdock
  export POSTGRES_PASSWORD="$test_password"
  docker run -d \
    --name "$source_postgres" \
    --label com.formdock.scope=recovery-smoke \
    --label "com.formdock.run-id=$source_id" \
    --network "$source_network" \
    --mount "type=volume,source=$source_volume,target=/var/lib/postgresql" \
    --env POSTGRES_DB \
    --env POSTGRES_USER \
    --env POSTGRES_PASSWORD \
    --health-cmd='pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    --health-interval=2s \
    --health-timeout=2s \
    --health-retries=30 \
    "$postgres_image"
) >/dev/null
source_postgres_created=true
formdock_wait_for_healthy_container "$source_postgres" 90

(
  export SERVER_ADDRESS=0.0.0.0
  export SERVER_PORT=8080
  export SPRING_DATASOURCE_URL="jdbc:postgresql://$source_postgres:5432/formdock"
  export SPRING_DATASOURCE_USERNAME=formdock
  export SPRING_DATASOURCE_PASSWORD="$test_password"
  export FORMDOCK_BOOTSTRAP_ENABLED=false
  docker run -d \
    --name "$source_api" \
    --label com.formdock.scope=recovery-smoke \
    --label "com.formdock.run-id=$source_id" \
    --network "$source_network" \
    --env SERVER_ADDRESS \
    --env SERVER_PORT \
    --env SPRING_DATASOURCE_URL \
    --env SPRING_DATASOURCE_USERNAME \
    --env SPRING_DATASOURCE_PASSWORD \
    --env FORMDOCK_BOOTSTRAP_ENABLED \
    "$api_image"
) >/dev/null
source_api_created=true
formdock_wait_for_healthy_container "$source_api" 120

[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$source_postgres")" = 0 ] \
  || formdock_die 'Source PostgreSQL must not publish host ports.'
[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$source_api")" = 0 ] \
  || formdock_die 'Source API must not publish host ports.'

source_flyway_state="$(
  docker exec -i "$source_postgres" sh -ceu '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    exec psql --no-password --tuples-only --no-align \
      --username "$POSTGRES_USER" \
      --dbname "$POSTGRES_DB"
  ' <<'SQL' | tr -d '\r'
SELECT COALESCE(string_agg(version, ',' ORDER BY installed_rank) FILTER (WHERE success), '')
       || '|' || count(*) FILTER (WHERE success)
       || '|' || count(*) FILTER (WHERE NOT success)
FROM flyway_schema_history;
SQL
)"
[ "$source_flyway_state" = '1,2,3,4,5,6|6|0' ] \
  || formdock_die "Source Flyway history is unexpected: $source_flyway_state"

docker exec -i "$source_postgres" sh -ceu '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  exec psql --no-password --quiet --set ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB"
' < "$SCRIPT_DIR/representative-fixture.sql" >/dev/null

create_backup() {
  local backup_id="$1"
  (
    export FORMDOCK_BACKUP_ROOT="$backup_root"
    export FORMDOCK_BACKUP_ID="$backup_id"
    export FORMDOCK_RELEASE_SHA="$release_sha"
    export FORMDOCK_DB_DOCKER_NETWORK="$source_network"
    export FORMDOCK_DB_HOST="$source_postgres"
    export FORMDOCK_DB_PORT=5432
    export FORMDOCK_DB_NAME=formdock
    export FORMDOCK_DB_USERNAME=formdock
    export FORMDOCK_DB_PASSWORD="$test_password"
    export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
    "$BACKUP_DIR/backup.sh"
  )
}

backup_id_1="formdock-recovery-001-$run_suffix"
backup_id_2="formdock-recovery-002-$run_suffix"
backup_id_3="formdock-recovery-003-$run_suffix"
create_backup "$backup_id_1"
create_backup "$backup_id_2"
create_backup "$backup_id_3"

if create_backup "$backup_id_3" >/dev/null 2>&1; then
  formdock_die 'Backup unexpectedly overwrote an existing completed set.'
fi

(
  export FORMDOCK_BACKUP_ROOT="$backup_root"
  export FORMDOCK_BACKUP_ID="$backup_id_3"
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  "$BACKUP_DIR/verify.sh"
)

printf 'partial-only\n' > "$backup_root/formdock-partial-$run_suffix.dump"
printf 'unrelated\n' > "$backup_root/do-not-delete.txt"
chmod 600 "$backup_root/formdock-partial-$run_suffix.dump" "$backup_root/do-not-delete.txt"

retention_dry_run="$(
  export FORMDOCK_BACKUP_ROOT="$backup_root"
  export FORMDOCK_RETENTION_COUNT=2
  export FORMDOCK_RETENTION_APPLY=false
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  "$BACKUP_DIR/retention.sh"
)"
grep -q '^RETENTION_CANDIDATES=1$' <<< "$retention_dry_run"
grep -q '^RETENTION_DELETED=0$' <<< "$retention_dry_run"
[ -f "$backup_root/$backup_id_1.meta" ] || formdock_die 'Retention dry-run deleted a completed set.'

retention_apply="$(
  export FORMDOCK_BACKUP_ROOT="$backup_root"
  export FORMDOCK_RETENTION_COUNT=2
  export FORMDOCK_RETENTION_APPLY=true
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  "$BACKUP_DIR/retention.sh"
)"
grep -q '^RETENTION_CANDIDATES=1$' <<< "$retention_apply"
grep -q '^RETENTION_DELETED=1$' <<< "$retention_apply"
[ ! -e "$backup_root/$backup_id_1.meta" ] \
  && [ ! -e "$backup_root/$backup_id_1.sha256" ] \
  && [ ! -e "$backup_root/$backup_id_1.dump" ] \
  || formdock_die 'Retention did not remove the exact oldest completed set.'
[ -f "$backup_root/$backup_id_2.meta" ] && [ -f "$backup_root/$backup_id_3.meta" ] \
  || formdock_die 'Retention removed a kept completed set.'
[ -f "$backup_root/formdock-partial-$run_suffix.dump" ] \
  && [ -f "$backup_root/do-not-delete.txt" ] \
  || formdock_die 'Retention removed a partial or unrelated file.'

(
  export FORMDOCK_BACKUP_ROOT="$backup_root"
  export FORMDOCK_BACKUP_ID="$backup_id_3"
  export FORMDOCK_OFF_HOST_TARGET_ROOT="$off_host_root"
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  "$BACKUP_DIR/copy-off-host.sh"
)

if (
  export FORMDOCK_BACKUP_ROOT="$backup_root"
  export FORMDOCK_BACKUP_ID="$backup_id_3"
  export FORMDOCK_OFF_HOST_TARGET_ROOT="$off_host_root"
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  "$BACKUP_DIR/copy-off-host.sh"
) >/dev/null 2>&1; then
  formdock_die 'Off-host copy unexpectedly overwrote an existing completed set.'
fi

cp "$off_host_root/$backup_id_3.dump" "$corrupt_root/$backup_id_3.dump"
cp "$off_host_root/$backup_id_3.sha256" "$corrupt_root/$backup_id_3.sha256"
cp "$off_host_root/$backup_id_3.meta" "$corrupt_root/$backup_id_3.meta"
chmod 600 "$corrupt_root/$backup_id_3.dump" "$corrupt_root/$backup_id_3.sha256" "$corrupt_root/$backup_id_3.meta"
printf 'checksum-corruption\n' >> "$corrupt_root/$backup_id_3.dump"

checksum_scratch_id="$scratch_id-checksum"
if (
  export FORMDOCK_BACKUP_ROOT="$corrupt_root"
  export FORMDOCK_BACKUP_ID="$backup_id_3"
  export FORMDOCK_SCRATCH_ID="$checksum_scratch_id"
  export FORMDOCK_SCRATCH_DB_PASSWORD="$test_password"
  export FORMDOCK_API_IMAGE="$api_image"
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  "$BACKUP_DIR/restore-scratch.sh"
) >/dev/null 2>&1; then
  formdock_die 'Scratch restore unexpectedly accepted a checksum mismatch.'
fi
! docker container inspect "$checksum_scratch_id-postgres" >/dev/null 2>&1 \
  || formdock_die 'Checksum failure created a scratch PostgreSQL container.'
! docker network inspect "$checksum_scratch_id-database" >/dev/null 2>&1 \
  || formdock_die 'Checksum failure created a scratch network.'
! docker volume inspect "$checksum_scratch_id-postgres-data" >/dev/null 2>&1 \
  || formdock_die 'Checksum failure created a scratch volume.'

cleanup_source
for container in "$source_postgres" "$source_api"; do
  ! docker container inspect "$container" >/dev/null 2>&1 \
    || formdock_die "Source container residue remains: $container"
done
! docker network inspect "$source_network" >/dev/null 2>&1 \
  || formdock_die "Source network residue remains: $source_network"
! docker volume inspect "$source_volume" >/dev/null 2>&1 \
  || formdock_die "Source volume residue remains: $source_volume"

(
  export FORMDOCK_BACKUP_ROOT="$off_host_root"
  export FORMDOCK_BACKUP_ID="$backup_id_3"
  export FORMDOCK_SCRATCH_ID="$scratch_id"
  export FORMDOCK_SCRATCH_DB_PASSWORD="$test_password"
  export FORMDOCK_API_IMAGE="$api_image"
  export FORMDOCK_POSTGRES_IMAGE="$postgres_image"
  export FORMDOCK_RESTORE_VERIFY_SQL_FILE="$SCRIPT_DIR/verify-representative-data.sql"
  "$BACKUP_DIR/restore-scratch.sh"
)

cleanup_temp
trap - EXIT
[ ! -e "$temp_root" ] || formdock_die 'Recovery smoke temporary artifact residue remains.'

printf 'RECOVERY_SOURCE_FLYWAY=1,2,3,4,5,6\n'
printf 'RECOVERY_BACKUP_CUSTOM_FORMAT=PASS\n'
printf 'RECOVERY_CHECKSUM_METADATA=PASS\n'
printf 'RECOVERY_RETENTION=PASS\n'
printf 'RECOVERY_OFF_HOST_SIMULATION=PASS\n'
printf 'RECOVERY_SCRATCH_RESTORE=PASS\n'
printf 'RECOVERY_API_HEALTH=PASS\n'
printf 'RECOVERY_RESIDUE=0\n'
