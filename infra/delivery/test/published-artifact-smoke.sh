#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
DELIVERY_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
COMPOSE_FILE="$REPOSITORY_ROOT/infra/compose.production.yaml"
# shellcheck source=../common.sh
source "$DELIVERY_DIR/common.sh"

formdock_delivery_require_env FORMDOCK_PUBLICATION_API_DIGEST_REF
formdock_delivery_require_env FORMDOCK_PUBLICATION_WEB_DIGEST_REF
formdock_delivery_require_env FORMDOCK_PUBLICATION_RELEASE_SHA
for command in docker od awk sed grep tr date find mktemp; do
  formdock_delivery_require_command "$command"
done

api_reference="$FORMDOCK_PUBLICATION_API_DIGEST_REF"
web_reference="$FORMDOCK_PUBLICATION_WEB_DIGEST_REF"
release_sha="$FORMDOCK_PUBLICATION_RELEASE_SHA"

[[ "$api_reference" =~ ^ghcr\.io/xxh3898/form-dock-api@sha256:[0-9a-f]{64}$ ]] \
  || formdock_delivery_die 'API reference must be the approved GHCR package at an immutable digest.'
[[ "$web_reference" =~ ^ghcr\.io/xxh3898/form-dock-web@sha256:[0-9a-f]{64}$ ]] \
  || formdock_delivery_die 'Web reference must be the approved GHCR package at an immutable digest.'
[[ "$release_sha" =~ ^[0-9a-f]{40}$ ]] \
  || formdock_delivery_die 'Published release SHA must contain exactly 40 lowercase hexadecimal characters.'

run_suffix="$(date -u '+%H%M%S')-$$"
project="dev-form-dock-delivery-published-$run_suffix"
formdock_delivery_validate_project "$project"
edge_network="${project}-edge"
export FORMDOCK_EDGE_NETWORK="$edge_network"

tmp_base="$(cd "${RUNNER_TEMP:-${TMPDIR:-/tmp}}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-published-artifact.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-published-artifact.*) ;;
  *) formdock_delivery_die 'Published artifact temporary directory escaped the expected root.' ;;
esac
chmod 700 "$temp_root"

env_file="$temp_root/published.env"
state_file="$temp_root/published.state"
configuration_identity_file="$temp_root/configuration.identity"

cleanup() {
  if [ -f "$env_file" ]; then
    FORMDOCK_API_IMAGE="$api_reference" \
    FORMDOCK_WEB_IMAGE="$web_reference" \
      docker compose \
        --project-name "$project" \
        --env-file "$env_file" \
        -f "$COMPOSE_FILE" \
        down --remove-orphans >/dev/null 2>&1 || true
  fi

  volume_name="${project}_postgres-data"
  if docker volume inspect "$volume_name" >/dev/null 2>&1; then
    if [ "$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume_name" 2>/dev/null || true)" = "$project" ]; then
      docker volume rm "$volume_name" >/dev/null 2>&1 || true
    fi
  fi

  if docker network inspect "$edge_network" >/dev/null 2>&1; then
    if [ "$(docker network inspect --format '{{index .Labels "com.formdock.validation.project"}}' "$edge_network" 2>/dev/null || true)" = "$project" ]; then
      docker network rm "$edge_network" >/dev/null 2>&1 || true
    fi
  fi

  case "$temp_root" in
    "$tmp_base"/formdock-published-artifact.*) find "$temp_root" -depth -delete ;;
  esac
}
trap cleanup EXIT

[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_delivery_die 'Published artifact smoke refuses an existing project container.'
[ -z "$(docker network ls -q --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_delivery_die 'Published artifact smoke refuses an existing project network.'
[ -z "$(docker volume ls -q --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_delivery_die 'Published artifact smoke refuses an existing project volume.'
! docker network inspect "$edge_network" >/dev/null 2>&1 \
  || formdock_delivery_die 'Published artifact smoke refuses an existing external edge fixture network.'

docker network create \
  --label "com.formdock.validation.project=$project" \
  "$edge_network" >/dev/null

docker pull "$api_reference" >/dev/null
docker pull "$web_reference" >/dev/null

api_identity="$(formdock_delivery_image_identity "$api_reference")"
web_identity="$(formdock_delivery_image_identity "$web_reference")"
[ "$(docker image inspect --format '{{.Architecture}}/{{.Os}}' "$api_reference")" = 'arm64/linux' ] \
  || formdock_delivery_die 'Published API image is not linux/arm64.'
[ "$(docker image inspect --format '{{.Architecture}}/{{.Os}}' "$web_reference")" = 'arm64/linux' ] \
  || formdock_delivery_die 'Published Web image is not linux/arm64.'

compose_revision="sha256:$(formdock_delivery_sha256 "$COMPOSE_FILE")"
{
  printf 'scope=isolated\n'
  printf 'database=formdock_delivery\n'
  printf 'bootstrap=false\n'
  printf 'edgeNetwork=%s\n' "$edge_network"
  printf 'artifactSource=%s\n' "$release_sha"
  printf 'logMaxSize=10m\n'
  printf 'logMaxFile=5\n'
} > "$configuration_identity_file"
configuration_revision="sha256:$(formdock_delivery_sha256 "$configuration_identity_file")"
recorded_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

{
  printf 'formatVersion=1\n'
  printf 'stateRole=candidate\n'
  printf 'releaseGitSha=%s\n' "$release_sha"
  printf 'apiImageReference=%s\n' "$api_reference"
  printf 'apiImageIdentity=%s\n' "$api_identity"
  printf 'webImageReference=%s\n' "$web_reference"
  printf 'webImageIdentity=%s\n' "$web_identity"
  printf 'composeRevision=%s\n' "$compose_revision"
  printf 'configurationRevision=%s\n' "$configuration_revision"
  printf 'recordedAt=%s\n' "$recorded_at"
  printf 'previousStateSha256=NONE\n'
} > "$state_file"
chmod 600 "$state_file"

disposable_password="$(LC_ALL=C od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
[ "${#disposable_password}" = 48 ] \
  || formdock_delivery_die 'Unable to generate the disposable database credential.'
{
  printf 'FORMDOCK_DELIVERY_SCOPE=isolated\n'
  printf 'FORMDOCK_DELIVERY_PROJECT=%s\n' "$project"
  printf 'FORMDOCK_CONFIGURATION_REVISION=%s\n' "$configuration_revision"
  printf 'FORMDOCK_WEB_PORT=0\n'
  printf 'FORMDOCK_LOG_MAX_SIZE=10m\n'
  printf 'FORMDOCK_LOG_MAX_FILE=5\n'
  printf 'FORMDOCK_DB_NAME=formdock_delivery\n'
  printf 'FORMDOCK_DB_USERNAME=formdock_delivery\n'
  printf 'FORMDOCK_DB_PASSWORD=%s\n' "$disposable_password"
  printf 'FORMDOCK_BOOTSTRAP_ENABLED=false\n'
  printf 'FORMDOCK_BOOTSTRAP_EMAIL=\n'
  printf 'FORMDOCK_BOOTSTRAP_PASSWORD=\n'
  printf 'FORMDOCK_BOOTSTRAP_DISPLAY_NAME=\n'
} > "$env_file"
chmod 600 "$env_file"

FORMDOCK_DELIVERY_PROJECT="$project" \
FORMDOCK_DELIVERY_ENV_FILE="$env_file" \
FORMDOCK_DEPLOYMENT_STATE_FILE="$state_file" \
  "$DELIVERY_DIR/stage-isolated.sh"

postgres_id="$(
  FORMDOCK_API_IMAGE="$api_reference" \
  FORMDOCK_WEB_IMAGE="$web_reference" \
    docker compose \
      --project-name "$project" \
      --env-file "$env_file" \
      -f "$COMPOSE_FILE" \
      ps -q postgres
)"
[[ "$postgres_id" =~ ^[0-9a-f]{64}$ ]] \
  || formdock_delivery_die 'Published artifact smoke expected one PostgreSQL container.'

flyway_history="$(
  docker exec -i "$postgres_id" sh -ceu '
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
[ "$flyway_history" = '1,2,3,4,5,6|6|0' ] \
  || formdock_delivery_die 'Published artifact Flyway history is unexpected.'

FORMDOCK_API_IMAGE="$api_reference" \
FORMDOCK_WEB_IMAGE="$web_reference" \
  docker compose \
    --project-name "$project" \
    --env-file "$env_file" \
    -f "$COMPOSE_FILE" \
    down --remove-orphans >/dev/null

volume_name="${project}_postgres-data"
[ "$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume_name")" = "$project" ] \
  || formdock_delivery_die 'Published artifact database volume label is unexpected.'
docker volume rm "$volume_name" >/dev/null

[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ]
[ -z "$(docker network ls -q --filter "label=com.docker.compose.project=$project")" ]
[ -z "$(docker volume ls -q --filter "label=com.docker.compose.project=$project")" ]
[ "$(docker network inspect --format '{{index .Labels "com.formdock.validation.project"}}' "$edge_network")" = "$project" ]
docker network rm "$edge_network" >/dev/null
! docker network inspect "$edge_network" >/dev/null 2>&1

case "$temp_root" in
  "$tmp_base"/formdock-published-artifact.*) find "$temp_root" -depth -delete ;;
esac
trap - EXIT

printf 'PUBLICATION_PULL_BY_DIGEST=PASS\n'
printf 'PUBLICATION_API_PLATFORM=linux/arm64\n'
printf 'PUBLICATION_WEB_PLATFORM=linux/arm64\n'
printf 'PUBLICATION_CANONICAL_COMPOSE=PASS\n'
printf 'PUBLICATION_FLYWAY_HISTORY=1,2,3,4,5,6\n'
printf 'PUBLICATION_RESIDUE=0\n'
