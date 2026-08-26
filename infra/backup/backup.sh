#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

umask 077

formdock_require_env FORMDOCK_BACKUP_ROOT
formdock_require_env FORMDOCK_RELEASE_SHA
formdock_require_env FORMDOCK_DB_DOCKER_NETWORK
formdock_require_env FORMDOCK_DB_HOST
formdock_require_env FORMDOCK_DB_NAME
formdock_require_env FORMDOCK_DB_USERNAME
formdock_require_env FORMDOCK_DB_PASSWORD
formdock_require_command docker

backup_root="$(formdock_canonical_private_directory "$FORMDOCK_BACKUP_ROOT")"
[ -w "$backup_root" ] || formdock_die "Backup root is not writable: $backup_root"
formdock_validate_release_sha "$FORMDOCK_RELEASE_SHA"

db_port="${FORMDOCK_DB_PORT:-5432}"
formdock_validate_port "$db_port"
docker network inspect "$FORMDOCK_DB_DOCKER_NETWORK" >/dev/null 2>&1 \
  || formdock_die 'Configured database Docker network does not exist.'

created_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
backup_id="${FORMDOCK_BACKUP_ID:-formdock-${timestamp}-${FORMDOCK_RELEASE_SHA:0:12}-$$}"
formdock_validate_backup_id "$backup_id"

final_dump="$backup_root/$backup_id.dump"
final_checksum="$backup_root/$backup_id.sha256"
final_metadata="$backup_root/$backup_id.meta"
for target in "$final_dump" "$final_checksum" "$final_metadata"; do
  [ ! -e "$target" ] && [ ! -L "$target" ] || formdock_die "Refusing to overwrite existing backup artifact: $target"
done

stage_dir="$backup_root/.partial-$backup_id"
mkdir "$stage_dir" 2>/dev/null || formdock_die "Unable to claim backup ID staging directory: $backup_id"
chmod 700 "$stage_dir"
stage_dump="$stage_dir/$backup_id.dump"
stage_checksum="$stage_dir/$backup_id.sha256"
stage_metadata="$stage_dir/$backup_id.meta"

cleanup_stage() {
  rm -f "$stage_metadata" "$stage_checksum" "$stage_dump"
  rmdir "$stage_dir" 2>/dev/null || true
}
trap cleanup_stage EXIT

postgres_image="${FORMDOCK_POSTGRES_IMAGE:-postgres:18.6-alpine3.23}"

server_version="$({
  export PGPASSWORD="$FORMDOCK_DB_PASSWORD"
  docker run --rm \
    --network "$FORMDOCK_DB_DOCKER_NETWORK" \
    --env PGPASSWORD \
    "$postgres_image" \
    psql --no-password --tuples-only --no-align \
      --host "$FORMDOCK_DB_HOST" \
      --port "$db_port" \
      --username "$FORMDOCK_DB_USERNAME" \
      --dbname "$FORMDOCK_DB_NAME" \
      --command 'SHOW server_version'
} | tr -d '\r' | awk 'NF {print; exit}')"
[ -n "$server_version" ] || formdock_die 'Unable to read PostgreSQL server version.'

tool_version="$(docker run --rm "$postgres_image" pg_dump --version | tr -d '\r')"

(
  export PGPASSWORD="$FORMDOCK_DB_PASSWORD"
  docker run --rm \
    --network "$FORMDOCK_DB_DOCKER_NETWORK" \
    --env PGPASSWORD \
    "$postgres_image" \
    pg_dump --format=custom --no-password \
      --host "$FORMDOCK_DB_HOST" \
      --port "$db_port" \
      --username "$FORMDOCK_DB_USERNAME" \
      --dbname "$FORMDOCK_DB_NAME"
) > "$stage_dump"

[ -s "$stage_dump" ] || formdock_die 'pg_dump produced an empty artifact.'
chmod 600 "$stage_dump"
docker run --rm -i "$postgres_image" pg_restore --list >/dev/null < "$stage_dump" \
  || formdock_die 'pg_dump output failed custom-format readability validation.'

checksum="$(formdock_sha256 "$stage_dump")"
[[ "$checksum" =~ ^[0-9a-f]{64}$ ]] || formdock_die 'Unable to calculate a valid SHA-256 checksum.'
release_sha_lower="$(printf '%s' "$FORMDOCK_RELEASE_SHA" | tr '[:upper:]' '[:lower:]')"
printf '%s  %s\n' "$checksum" "$backup_id.dump" > "$stage_checksum"
chmod 600 "$stage_checksum"

{
  printf 'formatVersion=1\n'
  printf 'status=complete\n'
  printf 'createdAt=%s\n' "$created_at"
  printf 'postgresServerVersion=%s\n' "$server_version"
  printf 'pgDumpVersion=%s\n' "$tool_version"
  printf 'applicationReleaseSha=%s\n' "$release_sha_lower"
  printf 'backupFilename=%s.dump\n' "$backup_id"
  printf 'sha256=%s\n' "$checksum"
} > "$stage_metadata"
chmod 600 "$stage_metadata"

formdock_verify_backup_set "$stage_dir" "$backup_id"

mv "$stage_dump" "$final_dump"
mv "$stage_checksum" "$final_checksum"
mv "$stage_metadata" "$final_metadata"
rmdir "$stage_dir"
trap - EXIT

printf 'BACKUP_ID=%s\n' "$backup_id"
printf 'BACKUP_STATUS=complete\n'
