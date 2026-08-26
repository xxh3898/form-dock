#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

formdock_delivery_require_env FORMDOCK_DELIVERY_PROJECT
formdock_delivery_require_env FORMDOCK_CANDIDATE_ENV_FILE
formdock_delivery_require_env FORMDOCK_PREVIOUS_ENV_FILE
formdock_delivery_require_env FORMDOCK_CANDIDATE_STATE_FILE
formdock_delivery_require_env FORMDOCK_PREVIOUS_STATE_FILE
formdock_delivery_require_command docker

project="$FORMDOCK_DELIVERY_PROJECT"
formdock_delivery_validate_project "$project"
candidate_env="$(formdock_delivery_validate_env "$FORMDOCK_CANDIDATE_ENV_FILE" "$project")"
previous_env="$(formdock_delivery_validate_env "$FORMDOCK_PREVIOUS_ENV_FILE" "$project")"
formdock_delivery_validate_state "$FORMDOCK_CANDIDATE_STATE_FILE" candidate
formdock_delivery_validate_state "$FORMDOCK_PREVIOUS_STATE_FILE" previous
[ "$(formdock_delivery_value "$FORMDOCK_CANDIDATE_STATE_FILE" configurationRevision)" = "$(formdock_delivery_value "$candidate_env" FORMDOCK_CONFIGURATION_REVISION)" ] \
  && [ "$(formdock_delivery_value "$FORMDOCK_PREVIOUS_STATE_FILE" configurationRevision)" = "$(formdock_delivery_value "$previous_env" FORMDOCK_CONFIGURATION_REVISION)" ] \
  || formdock_delivery_die 'Candidate or previous state does not match its configuration revision.'
for key in FORMDOCK_DB_NAME FORMDOCK_DB_USERNAME FORMDOCK_DB_PASSWORD; do
  [ "$(formdock_delivery_value "$candidate_env" "$key")" = "$(formdock_delivery_value "$previous_env" "$key")" ] \
    || formdock_delivery_die 'Application rollback must preserve the isolated database connection identity.'
done

previous_hash="sha256:$(formdock_delivery_sha256 "$FORMDOCK_PREVIOUS_STATE_FILE")"
[ "$(formdock_delivery_value "$FORMDOCK_CANDIDATE_STATE_FILE" previousStateSha256)" = "$previous_hash" ] \
  || formdock_delivery_die 'Candidate state does not reference the exact previous deployment state.'
[ "sha256:$(formdock_delivery_sha256 "$FORMDOCK_CANDIDATE_STATE_FILE")" != "$previous_hash" ] \
  || formdock_delivery_die 'Candidate and previous deployment states must be distinct.'

candidate_api_identity="$(formdock_delivery_value "$FORMDOCK_CANDIDATE_STATE_FILE" apiImageIdentity)"
candidate_web_identity="$(formdock_delivery_value "$FORMDOCK_CANDIDATE_STATE_FILE" webImageIdentity)"
previous_api_identity="$(formdock_delivery_value "$FORMDOCK_PREVIOUS_STATE_FILE" apiImageIdentity)"
previous_web_identity="$(formdock_delivery_value "$FORMDOCK_PREVIOUS_STATE_FILE" webImageIdentity)"
if [ "$candidate_api_identity" = "$previous_api_identity" ] \
  && [ "$candidate_web_identity" = "$previous_web_identity" ]; then
  formdock_delivery_die 'Rollback requires a distinct previous application image identity.'
fi

project_service_container() {
  local service="$1"
  local ids count

  ids="$(docker ps -aq \
    --filter "label=com.docker.compose.project=$project" \
    --filter "label=com.docker.compose.service=$service")"
  count="$(printf '%s\n' "$ids" | sed '/^$/d' | wc -l | tr -d ' ')"
  [ "$count" = 1 ] \
    || formdock_delivery_die "Rollback requires exactly one existing candidate container for service: $service"
  printf '%s\n' "$ids"
}

candidate_postgres_container="$(project_service_container postgres)"
candidate_api_container="$(project_service_container api)"
candidate_web_container="$(project_service_container web)"
[ "$(docker inspect --format '{{.Image}}' "$candidate_api_container")" = "$candidate_api_identity" ] \
  && [ "$(docker inspect --format '{{.Image}}' "$candidate_web_container")" = "$candidate_web_identity" ] \
  || formdock_delivery_die 'Running candidate image identity does not match candidate deployment state.'

volume_name="${project}_postgres-data"
volume_identity="$(docker volume inspect --format '{{.Name}}' "$volume_name" 2>/dev/null)"
[ "$volume_identity" = "$volume_name" ] \
  || formdock_delivery_die 'Rollback could not identify the existing isolated database volume.'
[ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$candidate_postgres_container")" = "$volume_identity" ] \
  || formdock_delivery_die 'Running candidate PostgreSQL does not use the expected isolated database volume.'

FORMDOCK_DEPLOYMENT_STATE_FILE="$FORMDOCK_PREVIOUS_STATE_FILE" \
FORMDOCK_DELIVERY_ENV_FILE="$previous_env" \
FORMDOCK_DELIVERY_EXPECTED_ROLE=previous \
FORMDOCK_DELIVERY_ALLOW_EXISTING=true \
  "$SCRIPT_DIR/stage-isolated.sh" >/dev/null

[ "$(docker volume inspect --format '{{.Name}}' "$volume_name" 2>/dev/null)" = "$volume_identity" ] \
  || formdock_delivery_die 'Application rollback changed the isolated database volume identity.'

printf 'DELIVERY_ROLLBACK=PASS\n'
printf 'DELIVERY_PREVIOUS_STATE_LINK=PASS\n'
printf 'DELIVERY_CONFIGURATION_ROLLBACK=PASS\n'
printf 'DELIVERY_DATABASE_VOLUME_PRESERVED=PASS\n'
