#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

formdock_require_env FORMDOCK_BACKUP_ROOT
formdock_require_env FORMDOCK_BACKUP_ID
formdock_require_env FORMDOCK_SCRATCH_ID
formdock_require_env FORMDOCK_SCRATCH_DB_PASSWORD
formdock_require_env FORMDOCK_API_IMAGE
formdock_require_command docker

[[ "$FORMDOCK_SCRATCH_ID" =~ ^dev-form-dock-scratch-[a-z0-9][a-z0-9-]{0,24}$ ]] \
  || formdock_die 'FORMDOCK_SCRATCH_ID must match dev-form-dock-scratch-[a-z0-9][a-z0-9-]{0,24}.'

backup_root="$(formdock_canonical_private_directory "$FORMDOCK_BACKUP_ROOT")"
formdock_validate_backup_id "$FORMDOCK_BACKUP_ID"
formdock_verify_backup_set "$backup_root" "$FORMDOCK_BACKUP_ID"

verify_sql="${FORMDOCK_RESTORE_VERIFY_SQL_FILE:-}"
if [ -n "$verify_sql" ]; then
  case "$verify_sql" in
    /*) ;;
    *) formdock_die 'FORMDOCK_RESTORE_VERIFY_SQL_FILE must be absolute.' ;;
  esac
  [ -f "$verify_sql" ] && [ ! -L "$verify_sql" ] \
    || formdock_die 'Restore verification SQL must be a regular non-symlink file.'
fi

postgres_image="${FORMDOCK_POSTGRES_IMAGE:-postgres:18.6-alpine3.23}"
docker image inspect "$FORMDOCK_API_IMAGE" >/dev/null 2>&1 \
  || formdock_die 'FORMDOCK_API_IMAGE must already exist locally; remote pull is not performed.'

network="$FORMDOCK_SCRATCH_ID-database"
volume="$FORMDOCK_SCRATCH_ID-postgres-data"
postgres_container="$FORMDOCK_SCRATCH_ID-postgres"
api_container="$FORMDOCK_SCRATCH_ID-api"

for container in "$postgres_container" "$api_container"; do
  if docker container inspect "$container" >/dev/null 2>&1; then
    formdock_die "Scratch restore refuses to reuse an existing container: $container"
  fi
done
if docker network inspect "$network" >/dev/null 2>&1; then
  formdock_die "Scratch restore refuses to reuse an existing network: $network"
fi
if docker volume inspect "$volume" >/dev/null 2>&1; then
  formdock_die "Scratch restore refuses to reuse an existing volume: $volume"
fi

network_created=false
volume_created=false
postgres_created=false
api_created=false

cleanup_scratch() {
  if [ "$api_created" = true ]; then
    docker rm -f "$api_container" >/dev/null 2>&1 || true
    api_created=false
  fi
  if [ "$postgres_created" = true ]; then
    docker rm -f "$postgres_container" >/dev/null 2>&1 || true
    postgres_created=false
  fi
  if [ "$volume_created" = true ]; then
    docker volume rm "$volume" >/dev/null 2>&1 || true
    volume_created=false
  fi
  if [ "$network_created" = true ]; then
    docker network rm "$network" >/dev/null 2>&1 || true
    network_created=false
  fi
}
trap cleanup_scratch EXIT

docker network create \
  --label com.formdock.scope=scratch-restore \
  --label "com.formdock.scratch-id=$FORMDOCK_SCRATCH_ID" \
  "$network" >/dev/null
network_created=true

docker volume create \
  --label com.formdock.scope=scratch-restore \
  --label "com.formdock.scratch-id=$FORMDOCK_SCRATCH_ID" \
  "$volume" >/dev/null
volume_created=true

(
  export POSTGRES_DB=formdock
  export POSTGRES_USER=formdock
  export POSTGRES_PASSWORD="$FORMDOCK_SCRATCH_DB_PASSWORD"
  docker run -d \
    --name "$postgres_container" \
    --label com.formdock.scope=scratch-restore \
    --label "com.formdock.scratch-id=$FORMDOCK_SCRATCH_ID" \
    --network "$network" \
    --mount "type=volume,source=$volume,target=/var/lib/postgresql" \
    --env POSTGRES_DB \
    --env POSTGRES_USER \
    --env POSTGRES_PASSWORD \
    --health-cmd='pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    --health-interval=2s \
    --health-timeout=2s \
    --health-retries=30 \
    "$postgres_image"
) >/dev/null
postgres_created=true
formdock_wait_for_healthy_container "$postgres_container" 90

[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$postgres_container")" = 0 ] \
  || formdock_die 'Scratch PostgreSQL must not publish host ports.'

docker exec -i "$postgres_container" sh -ceu '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  exec pg_restore \
    --exit-on-error \
    --no-owner \
    --no-acl \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB"
' < "$backup_root/$FORMDOCK_BACKUP_ID.dump" >/dev/null

flyway_state="$(
  docker exec -i "$postgres_container" sh -ceu '
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
[ "$flyway_state" = '1,2,3,4,5,6|6|0' ] \
  || formdock_die "Restored Flyway history is unexpected: $flyway_state"

if [ -n "$verify_sql" ]; then
  docker exec -i "$postgres_container" sh -ceu '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    exec psql --no-password --quiet --set ON_ERROR_STOP=1 \
      --username "$POSTGRES_USER" \
      --dbname "$POSTGRES_DB"
  ' < "$verify_sql" >/dev/null
fi

(
  export SERVER_ADDRESS=0.0.0.0
  export SERVER_PORT=8080
  export SPRING_DATASOURCE_URL="jdbc:postgresql://$postgres_container:5432/formdock"
  export SPRING_DATASOURCE_USERNAME=formdock
  export SPRING_DATASOURCE_PASSWORD="$FORMDOCK_SCRATCH_DB_PASSWORD"
  export FORMDOCK_BOOTSTRAP_ENABLED=false
  docker run -d \
    --name "$api_container" \
    --label com.formdock.scope=scratch-restore \
    --label "com.formdock.scratch-id=$FORMDOCK_SCRATCH_ID" \
    --network "$network" \
    --env SERVER_ADDRESS \
    --env SERVER_PORT \
    --env SPRING_DATASOURCE_URL \
    --env SPRING_DATASOURCE_USERNAME \
    --env SPRING_DATASOURCE_PASSWORD \
    --env FORMDOCK_BOOTSTRAP_ENABLED \
    "$FORMDOCK_API_IMAGE"
) >/dev/null
api_created=true
formdock_wait_for_healthy_container "$api_container" 120

[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$api_container")" = 0 ] \
  || formdock_die 'Scratch API must not publish host ports.'
docker exec "$api_container" sh -ceu \
  'wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '\''"status":"UP"'\''' \
  || formdock_die 'Restored API health verification failed.'

cleanup_scratch
trap - EXIT

for container in "$postgres_container" "$api_container"; do
  ! docker container inspect "$container" >/dev/null 2>&1 \
    || formdock_die "Scratch container residue remains: $container"
done
! docker network inspect "$network" >/dev/null 2>&1 \
  || formdock_die "Scratch network residue remains: $network"
! docker volume inspect "$volume" >/dev/null 2>&1 \
  || formdock_die "Scratch volume residue remains: $volume"

printf 'SCRATCH_POSTGRES_HEALTH=PASS\n'
printf 'SCRATCH_FLYWAY_HISTORY=1,2,3,4,5,6\n'
printf 'SCRATCH_REPRESENTATIVE_DATA=%s\n' "$([ -n "$verify_sql" ] && printf PASS || printf NOT_REQUESTED)"
printf 'SCRATCH_API_HEALTH=PASS\n'
printf 'SCRATCH_RESIDUE=0\n'
