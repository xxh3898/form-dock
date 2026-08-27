#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
DELIVERY_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
COMPOSE_FILE="$REPOSITORY_ROOT/infra/compose.production.yaml"
# shellcheck source=../common.sh
source "$DELIVERY_DIR/common.sh"

for command in docker git od awk sed; do
  formdock_delivery_require_command "$command"
done

base_api_image='dev-form-dock-api'
base_web_image='dev-form-dock-web'
docker image inspect "$base_api_image" >/dev/null 2>&1 \
  || formdock_delivery_die 'Delivery smoke requires the existing local API baseline image.'
docker image inspect "$base_web_image" >/dev/null 2>&1 \
  || formdock_delivery_die 'Delivery smoke requires the existing local Web baseline image.'

run_suffix="$(date -u '+%H%M%S')-$$"
project="dev-form-dock-delivery-$run_suffix"
formdock_delivery_validate_project "$project"
edge_network="${project}-edge"
export FORMDOCK_EDGE_NETWORK="$edge_network"
candidate_api="form-dock-api:delivery-candidate-$run_suffix"
candidate_web="form-dock-web:delivery-candidate-$run_suffix"
previous_api="form-dock-api:delivery-previous-$run_suffix"
previous_web="form-dock-web:delivery-previous-$run_suffix"

tmp_base="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-delivery-smoke.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-delivery-smoke.*) ;;
  *) formdock_delivery_die 'Delivery smoke temporary directory escaped the expected root.' ;;
esac
chmod 700 "$temp_root"
candidate_env="$temp_root/candidate.env"
previous_env="$temp_root/previous.env"
candidate_state="$temp_root/candidate.state"
previous_state="$temp_root/previous.state"
first_activation_state="$temp_root/first-activation.state"

cleanup() {
  if [ -f "$previous_env" ]; then
    FORMDOCK_API_IMAGE="$previous_api" \
    FORMDOCK_WEB_IMAGE="$previous_web" \
      docker compose \
        --project-name "$project" \
        --env-file "$previous_env" \
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

  for reference in "$candidate_api" "$candidate_web" "$previous_api" "$previous_web"; do
    docker image rm "$reference" >/dev/null 2>&1 || true
  done

  case "$temp_root" in
    "$tmp_base"/formdock-delivery-smoke.*) find "$temp_root" -depth -delete ;;
  esac
}
trap cleanup EXIT

[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_delivery_die 'Delivery smoke refuses an existing project container.'
[ -z "$(docker network ls -q --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_delivery_die 'Delivery smoke refuses an existing project network.'
[ -z "$(docker volume ls -q --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_delivery_die 'Delivery smoke refuses an existing project volume.'
! docker network inspect "$edge_network" >/dev/null 2>&1 \
  || formdock_delivery_die 'Delivery smoke refuses an existing external edge fixture network.'

docker network create \
  --label "com.formdock.validation.project=$project" \
  "$edge_network" >/dev/null

build_fixture_image() {
  local base="$1"
  local target="$2"
  local role="$3"
  local fixture_dir="$temp_root/image-$role-$(basename "${target%%:*}")"
  mkdir "$fixture_dir"
  {
    printf 'FROM %s\n' "$base"
    printf 'LABEL com.formdock.delivery.fixture=%s\n' "$role"
  } > "$fixture_dir/Dockerfile"
  docker build --pull=false --quiet --tag "$target" --file "$fixture_dir/Dockerfile" "$fixture_dir" >/dev/null
}

build_fixture_image "$base_api_image" "$candidate_api" candidate-api
build_fixture_image "$base_web_image" "$candidate_web" candidate-web
build_fixture_image "$base_api_image" "$previous_api" previous-api
build_fixture_image "$base_web_image" "$previous_web" previous-web

candidate_api_identity="$(formdock_delivery_image_identity "$candidate_api")"
candidate_web_identity="$(formdock_delivery_image_identity "$candidate_web")"
previous_api_identity="$(formdock_delivery_image_identity "$previous_api")"
previous_web_identity="$(formdock_delivery_image_identity "$previous_web")"
[ "$candidate_api_identity" != "$previous_api_identity" ] \
  || formdock_delivery_die 'API candidate/previous fixture identities must differ.'
[ "$candidate_web_identity" != "$previous_web_identity" ] \
  || formdock_delivery_die 'Web candidate/previous fixture identities must differ.'

release_sha="$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
[[ "$release_sha" =~ ^[0-9a-f]{40}$ ]] \
  || formdock_delivery_die 'Delivery smoke requires an exact Git SHA.'
compose_revision="sha256:$(formdock_delivery_sha256 "$COMPOSE_FILE")"
recorded_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
candidate_config_identity_file="$temp_root/candidate-config.identity"
previous_config_identity_file="$temp_root/previous-config.identity"
{
  printf 'scope=isolated\n'
  printf 'database=formdock_delivery\n'
  printf 'bootstrap=false\n'
  printf 'edgeNetwork=%s\n' "$edge_network"
  printf 'revisionLabel=candidate\n'
  printf 'logMaxSize=10m\n'
  printf 'logMaxFile=5\n'
} > "$candidate_config_identity_file"
{
  printf 'scope=isolated\n'
  printf 'database=formdock_delivery\n'
  printf 'bootstrap=false\n'
  printf 'edgeNetwork=%s\n' "$edge_network"
  printf 'revisionLabel=previous\n'
  printf 'logMaxSize=10m\n'
  printf 'logMaxFile=5\n'
} > "$previous_config_identity_file"
candidate_config_revision="sha256:$(formdock_delivery_sha256 "$candidate_config_identity_file")"
previous_config_revision="sha256:$(formdock_delivery_sha256 "$previous_config_identity_file")"
[ "$candidate_config_revision" != "$previous_config_revision" ] \
  || formdock_delivery_die 'Candidate/previous configuration revision fixtures must differ.'

write_state() {
  local target="$1"
  local role="$2"
  local api_reference="$3"
  local api_identity="$4"
  local web_reference="$5"
  local web_identity="$6"
  local configuration_revision="$7"
  local previous_sha="$8"
  {
    printf 'formatVersion=1\n'
    printf 'stateRole=%s\n' "$role"
    printf 'releaseGitSha=%s\n' "$release_sha"
    printf 'apiImageReference=%s\n' "$api_reference"
    printf 'apiImageIdentity=%s\n' "$api_identity"
    printf 'webImageReference=%s\n' "$web_reference"
    printf 'webImageIdentity=%s\n' "$web_identity"
    printf 'composeRevision=%s\n' "$compose_revision"
    printf 'configurationRevision=%s\n' "$configuration_revision"
    printf 'recordedAt=%s\n' "$recorded_at"
    printf 'previousStateSha256=%s\n' "$previous_sha"
  } > "$target"
  chmod 600 "$target"
}

write_state "$previous_state" previous \
  "$previous_api" "$previous_api_identity" \
  "$previous_web" "$previous_web_identity" "$previous_config_revision" NONE
previous_state_sha="sha256:$(formdock_delivery_sha256 "$previous_state")"
write_state "$candidate_state" candidate \
  "$candidate_api" "$candidate_api_identity" \
  "$candidate_web" "$candidate_web_identity" "$candidate_config_revision" "$previous_state_sha"
write_state "$first_activation_state" candidate \
  "$candidate_api" "$candidate_api_identity" \
  "$candidate_web" "$candidate_web_identity" "$candidate_config_revision" NONE

"$DELIVERY_DIR/validate-state.sh" "$candidate_state" candidate >/dev/null
"$DELIVERY_DIR/validate-state.sh" "$previous_state" previous >/dev/null
first_output="$("$DELIVERY_DIR/validate-state.sh" "$first_activation_state" candidate)"
grep -q '^PREVIOUS_STATE=NONE$' <<< "$first_output"

missing_state="$temp_root/missing.state"
grep -v '^webImageIdentity=' "$candidate_state" > "$missing_state"
if "$DELIVERY_DIR/validate-state.sh" "$missing_state" candidate >/dev/null 2>&1; then
  formdock_delivery_die 'Deployment state validator accepted a partial state.'
fi
unknown_state="$temp_root/unknown.state"
cp "$candidate_state" "$unknown_state"
printf 'unexpectedField=value\n' >> "$unknown_state"
if "$DELIVERY_DIR/validate-state.sh" "$unknown_state" candidate >/dev/null 2>&1; then
  formdock_delivery_die 'Deployment state validator accepted an unknown field.'
fi
duplicate_state="$temp_root/duplicate.state"
cp "$candidate_state" "$duplicate_state"
printf 'stateRole=candidate\n' >> "$duplicate_state"
if "$DELIVERY_DIR/validate-state.sh" "$duplicate_state" candidate >/dev/null 2>&1; then
  formdock_delivery_die 'Deployment state validator accepted a duplicate field.'
fi
latest_state="$temp_root/latest.state"
sed 's|^apiImageReference=.*$|apiImageReference=form-dock-api:latest|' "$candidate_state" > "$latest_state"
if "$DELIVERY_DIR/validate-state.sh" "$latest_state" candidate >/dev/null 2>&1; then
  formdock_delivery_die 'Deployment state validator accepted latest-only authority.'
fi
invalid_timestamp_state="$temp_root/invalid-timestamp.state"
sed 's|^recordedAt=.*$|recordedAt=2026-99-99T00:00:00Z|' "$candidate_state" > "$invalid_timestamp_state"
if "$DELIVERY_DIR/validate-state.sh" "$invalid_timestamp_state" candidate >/dev/null 2>&1; then
  formdock_delivery_die 'Deployment state validator accepted an invalid UTC timestamp.'
fi

disposable_password="$(LC_ALL=C od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
[ "${#disposable_password}" = 48 ] \
  || formdock_delivery_die 'Unable to generate the disposable database credential.'
write_env() {
  local target="$1"
  local configuration_revision="$2"
  local log_max_size="$3"
  local log_max_file="$4"
  {
    printf 'FORMDOCK_DELIVERY_SCOPE=isolated\n'
    printf 'FORMDOCK_DELIVERY_PROJECT=%s\n' "$project"
    printf 'FORMDOCK_CONFIGURATION_REVISION=%s\n' "$configuration_revision"
    printf 'FORMDOCK_WEB_PORT=0\n'
    printf 'FORMDOCK_LOG_MAX_SIZE=%s\n' "$log_max_size"
    printf 'FORMDOCK_LOG_MAX_FILE=%s\n' "$log_max_file"
    printf 'FORMDOCK_DB_NAME=formdock_delivery\n'
    printf 'FORMDOCK_DB_USERNAME=formdock_delivery\n'
    printf 'FORMDOCK_DB_PASSWORD=%s\n' "$disposable_password"
    printf 'FORMDOCK_BOOTSTRAP_ENABLED=false\n'
    printf 'FORMDOCK_BOOTSTRAP_EMAIL=\n'
    printf 'FORMDOCK_BOOTSTRAP_PASSWORD=\n'
    printf 'FORMDOCK_BOOTSTRAP_DISPLAY_NAME=\n'
  } > "$target"
  chmod 600 "$target"
}

write_env "$candidate_env" "$candidate_config_revision" 10m 5
write_env "$previous_env" "$previous_config_revision" 10m 5

if FORMDOCK_DELIVERY_PROJECT="$project" \
  FORMDOCK_CANDIDATE_ENV_FILE="$candidate_env" \
  FORMDOCK_PREVIOUS_ENV_FILE="$previous_env" \
  FORMDOCK_CANDIDATE_STATE_FILE="$first_activation_state" \
  FORMDOCK_PREVIOUS_STATE_FILE="$previous_state" \
  "$DELIVERY_DIR/rollback-isolated.sh" >/dev/null 2>&1; then
  formdock_delivery_die 'Rollback unexpectedly accepted a first-activation state without previous identity.'
fi
bad_link_state="$temp_root/bad-link.state"
sed 's|^previousStateSha256=.*$|previousStateSha256=sha256:0000000000000000000000000000000000000000000000000000000000000000|' \
  "$candidate_state" > "$bad_link_state"
if FORMDOCK_DELIVERY_PROJECT="$project" \
  FORMDOCK_CANDIDATE_ENV_FILE="$candidate_env" \
  FORMDOCK_PREVIOUS_ENV_FILE="$previous_env" \
  FORMDOCK_CANDIDATE_STATE_FILE="$bad_link_state" \
  FORMDOCK_PREVIOUS_STATE_FILE="$previous_state" \
  "$DELIVERY_DIR/rollback-isolated.sh" >/dev/null 2>&1; then
  formdock_delivery_die 'Rollback unexpectedly accepted a mismatched previous state identity.'
fi

FORMDOCK_DELIVERY_PROJECT="$project" \
FORMDOCK_DELIVERY_ENV_FILE="$candidate_env" \
FORMDOCK_DEPLOYMENT_STATE_FILE="$candidate_state" \
  "$DELIVERY_DIR/stage-isolated.sh" >/dev/null

compose_candidate() {
  FORMDOCK_API_IMAGE="$candidate_api" \
  FORMDOCK_WEB_IMAGE="$candidate_web" \
    docker compose \
      --project-name "$project" \
      --env-file "$candidate_env" \
      -f "$COMPOSE_FILE" \
      "$@"
}

postgres_id="$(compose_candidate ps -q postgres)"
api_id="$(compose_candidate ps -q api)"
web_id="$(compose_candidate ps -q web)"
volume_before="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_id")"
[ "$volume_before" = "${project}_postgres-data" ] \
  || formdock_delivery_die 'Candidate stage did not use the isolated project database volume.'

flyway_before="$(
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
[ "$flyway_before" = '1,2,3,4,5,6|6|0' ] \
  || formdock_delivery_die 'Candidate stage Flyway history is unexpected.'

FORMDOCK_MONITOR_TEST_WEB_CONTAINER="$web_id" \
FORMDOCK_MONITOR_TEST_API_CONTAINER="$api_id" \
FORMDOCK_MONITOR_TEST_DB_CONTAINER="$postgres_id" \
  "$REPOSITORY_ROOT/infra/monitoring/test/monitoring-smoke.sh" >/dev/null

docker stop "$api_id" >/dev/null
[ "$(docker inspect --format '{{.State.Status}}' "$api_id")" = exited ] \
  || formdock_delivery_die 'Delivery smoke could not establish an unhealthy candidate state.'

FORMDOCK_DELIVERY_PROJECT="$project" \
FORMDOCK_CANDIDATE_ENV_FILE="$candidate_env" \
FORMDOCK_PREVIOUS_ENV_FILE="$previous_env" \
FORMDOCK_CANDIDATE_STATE_FILE="$candidate_state" \
FORMDOCK_PREVIOUS_STATE_FILE="$previous_state" \
  "$DELIVERY_DIR/rollback-isolated.sh" >/dev/null

compose_previous() {
  FORMDOCK_API_IMAGE="$previous_api" \
  FORMDOCK_WEB_IMAGE="$previous_web" \
    docker compose \
      --project-name "$project" \
      --env-file "$previous_env" \
      -f "$COMPOSE_FILE" \
      "$@"
}

postgres_after="$(compose_previous ps -q postgres)"
api_after="$(compose_previous ps -q api)"
web_after="$(compose_previous ps -q web)"
[ "$postgres_after" = "$postgres_id" ] \
  || formdock_delivery_die 'Application rollback unexpectedly recreated PostgreSQL.'
[ "$(docker inspect --format '{{.Image}}' "$api_after")" = "$previous_api_identity" ]
[ "$(docker inspect --format '{{.Image}}' "$web_after")" = "$previous_web_identity" ]
volume_after="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_after")"
[ "$volume_after" = "$volume_before" ] \
  || formdock_delivery_die 'Application rollback replaced the database volume.'

flyway_after="$(
  docker exec -i "$postgres_after" sh -ceu '
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
[ "$flyway_after" = "$flyway_before" ] \
  || formdock_delivery_die 'Application rollback changed Flyway history.'

compose_previous down --remove-orphans >/dev/null
volume_name="${project}_postgres-data"
[ "$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume_name")" = "$project" ]
docker volume rm "$volume_name" >/dev/null

[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ]
[ -z "$(docker network ls -q --filter "label=com.docker.compose.project=$project")" ]
[ -z "$(docker volume ls -q --filter "label=com.docker.compose.project=$project")" ]
[ "$(docker network inspect --format '{{index .Labels "com.formdock.validation.project"}}' "$edge_network")" = "$project" ]
docker network rm "$edge_network" >/dev/null
! docker network inspect "$edge_network" >/dev/null 2>&1

for reference in "$candidate_api" "$candidate_web" "$previous_api" "$previous_web"; do
  docker image rm "$reference" >/dev/null
done

case "$temp_root" in
  "$tmp_base"/formdock-delivery-smoke.*) find "$temp_root" -depth -delete ;;
esac
trap - EXIT

printf 'DELIVERY_STATE_CONTRACT=PASS\n'
printf 'DELIVERY_FIRST_ACTIVATION_CLASSIFICATION=PASS\n'
printf 'DELIVERY_CANONICAL_COMPOSE=PASS\n'
printf 'DELIVERY_HEALTH_ACCEPTANCE=PASS\n'
printf 'DELIVERY_UNHEALTHY_CANDIDATE_ROLLBACK=PASS\n'
printf 'DELIVERY_APPLICATION_ROLLBACK=PASS\n'
printf 'DELIVERY_CONFIGURATION_ROLLBACK=PASS\n'
printf 'DELIVERY_DATABASE_VOLUME_PRESERVED=PASS\n'
printf 'DELIVERY_POSTGRES_CONTAINER_PRESERVED=PASS\n'
printf 'DELIVERY_FLYWAY_HISTORY=1,2,3,4,5,6\n'
printf 'DELIVERY_MONITORING_SIGNALS=PASS\n'
printf 'DELIVERY_RESIDUE=0\n'
