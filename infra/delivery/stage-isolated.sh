#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="$REPOSITORY_ROOT/infra/compose.production.yaml"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

formdock_delivery_require_env FORMDOCK_DELIVERY_PROJECT
formdock_delivery_require_env FORMDOCK_DELIVERY_ENV_FILE
formdock_delivery_require_env FORMDOCK_DEPLOYMENT_STATE_FILE
formdock_delivery_require_command docker

project="$FORMDOCK_DELIVERY_PROJECT"
expected_role="${FORMDOCK_DELIVERY_EXPECTED_ROLE:-candidate}"
allow_existing="${FORMDOCK_DELIVERY_ALLOW_EXISTING:-false}"
formdock_delivery_validate_project "$project"
env_file="$(formdock_delivery_validate_env "$FORMDOCK_DELIVERY_ENV_FILE" "$project")"
case "$env_file" in
  "$REPOSITORY_ROOT"/*) formdock_delivery_die 'Isolated environment file must remain outside the repository.' ;;
esac
formdock_delivery_validate_state "$FORMDOCK_DEPLOYMENT_STATE_FILE" "$expected_role"

compose_revision="sha256:$(formdock_delivery_sha256 "$COMPOSE_FILE")"
[ "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" composeRevision)" = "$compose_revision" ] \
  || formdock_delivery_die 'Deployment state Compose revision does not match the canonical Production Compose.'
[ "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" configurationRevision)" = "$(formdock_delivery_value "$env_file" FORMDOCK_CONFIGURATION_REVISION)" ] \
  || formdock_delivery_die 'Deployment state configuration revision does not match the isolated environment.'

api_reference="$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" apiImageReference)"
web_reference="$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" webImageReference)"
[ "$(formdock_delivery_image_identity "$api_reference")" = "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" apiImageIdentity)" ] \
  || formdock_delivery_die 'Local API image identity does not match deployment state.'
[ "$(formdock_delivery_image_identity "$web_reference")" = "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" webImageIdentity)" ] \
  || formdock_delivery_die 'Local Web image identity does not match deployment state.'

existing_containers="$(docker ps -aq --filter "label=com.docker.compose.project=$project")"
existing_networks="$(docker network ls -q --filter "label=com.docker.compose.project=$project")"
existing_volumes="$(docker volume ls -q --filter "label=com.docker.compose.project=$project")"

case "$allow_existing" in
  false)
    [ -z "$existing_containers$existing_networks$existing_volumes" ] \
      || formdock_delivery_die 'Initial isolated stage refuses an existing project resource.'
    ! docker network inspect "${project}_application" >/dev/null 2>&1 \
      && ! docker network inspect "${project}_database" >/dev/null 2>&1 \
      && ! docker volume inspect "${project}_postgres-data" >/dev/null 2>&1 \
      || formdock_delivery_die 'Initial isolated stage refuses a pre-existing exact network or volume name.'
    ;;
  true)
    [ "$(printf '%s\n' "$existing_containers" | sed '/^$/d' | wc -l | tr -d ' ')" = 3 ] \
      || formdock_delivery_die 'Existing isolated project must contain exactly three service containers.'
    [ "$(printf '%s\n' "$existing_networks" | sed '/^$/d' | wc -l | tr -d ' ')" = 2 ] \
      && [ "$(printf '%s\n' "$existing_volumes" | sed '/^$/d' | wc -l | tr -d ' ')" = 1 ] \
      || formdock_delivery_die 'Existing isolated project network/volume set is incomplete or ambiguous.'
    [ "$(docker network inspect --format '{{index .Labels "com.docker.compose.project"}}' "${project}_application")" = "$project" ] \
      && [ "$(docker network inspect --format '{{index .Labels "com.docker.compose.project"}}' "${project}_database")" = "$project" ] \
      && [ "$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "${project}_postgres-data")" = "$project" ] \
      || formdock_delivery_die 'Existing isolated resource labels do not match the requested project.'
    ;;
  *) formdock_delivery_die 'FORMDOCK_DELIVERY_ALLOW_EXISTING must be true or false.' ;;
esac

FORMDOCK_API_IMAGE="$api_reference" \
FORMDOCK_WEB_IMAGE="$web_reference" \
  docker compose \
    --project-name "$project" \
    --env-file "$env_file" \
    -f "$COMPOSE_FILE" \
    config --quiet

FORMDOCK_API_IMAGE="$api_reference" \
FORMDOCK_WEB_IMAGE="$web_reference" \
  docker compose \
    --project-name "$project" \
    --env-file "$env_file" \
    -f "$COMPOSE_FILE" \
    up --detach --wait

FORMDOCK_DELIVERY_EXPECTED_ROLE="$expected_role" \
  "$SCRIPT_DIR/health-check.sh"

printf 'DELIVERY_STAGE=PASS\n'
printf 'DELIVERY_STATE_ROLE=%s\n' "$expected_role"
