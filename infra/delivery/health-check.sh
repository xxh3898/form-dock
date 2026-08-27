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
formdock_delivery_validate_project "$project"
env_file="$(formdock_delivery_validate_env "$FORMDOCK_DELIVERY_ENV_FILE" "$project")"
formdock_delivery_validate_state "$FORMDOCK_DEPLOYMENT_STATE_FILE" "${FORMDOCK_DELIVERY_EXPECTED_ROLE:-}"
[ "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" configurationRevision)" = "$(formdock_delivery_value "$env_file" FORMDOCK_CONFIGURATION_REVISION)" ] \
  || formdock_delivery_die 'Running state configuration revision does not match the isolated environment.'

api_reference="$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" apiImageReference)"
web_reference="$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" webImageReference)"

compose() {
  FORMDOCK_API_IMAGE="$api_reference" \
  FORMDOCK_WEB_IMAGE="$web_reference" \
    docker compose \
      --project-name "$project" \
      --env-file "$env_file" \
      -f "$COMPOSE_FILE" \
      "$@"
}

container_id() {
  local service="$1"
  local id
  id="$(compose ps -q "$service")"
  [[ "$id" =~ ^[0-9a-f]{64}$ ]] \
    || formdock_delivery_die "Expected one isolated container for service: $service"
  printf '%s\n' "$id"
}

postgres_id="$(container_id postgres)"
api_id="$(container_id api)"
web_id="$(container_id web)"

for id in "$postgres_id" "$api_id" "$web_id"; do
  [ "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$id")" = "$project" ] \
    || formdock_delivery_die 'Container does not belong to the requested isolated project.'
  [ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")" = healthy ] \
    || formdock_delivery_die 'Isolated service health is not healthy.'
  [ "$(docker inspect --format '{{.HostConfig.LogConfig.Type}}' "$id")" = json-file ] \
    && [ "$(docker inspect --format '{{index .HostConfig.LogConfig.Config "max-size"}}' "$id")" = "$(formdock_delivery_value "$env_file" FORMDOCK_LOG_MAX_SIZE)" ] \
    && [ "$(docker inspect --format '{{index .HostConfig.LogConfig.Config "max-file"}}' "$id")" = "$(formdock_delivery_value "$env_file" FORMDOCK_LOG_MAX_FILE)" ] \
    || formdock_delivery_die 'Running service log rotation does not match the bounded configuration.'
done

[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$postgres_id")" = 0 ] \
  || formdock_delivery_die 'Isolated PostgreSQL must not publish a host port.'
[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$api_id")" = 0 ] \
  || formdock_delivery_die 'Isolated API must not publish a host port.'
[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$web_id")" = 1 ] \
  || formdock_delivery_die 'Isolated Web must publish exactly one host port.'
docker port "$web_id" 8080/tcp | grep -Eq '^127\.0\.0\.1:[0-9]+$' \
  || formdock_delivery_die 'Isolated Web port must bind only to 127.0.0.1.'

postgres_networks="$(docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' "$postgres_id" | sed '/^$/d' | sort)"
api_networks="$(docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' "$api_id" | sed '/^$/d' | sort)"
web_networks="$(docker inspect --format '{{range $name, $network := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' "$web_id" | sed '/^$/d' | sort)"
edge_network="${FORMDOCK_EDGE_NETWORK:-edge}"

[ "$postgres_networks" = "${project}_database" ] \
  || formdock_delivery_die "PostgreSQL network topology does not match the canonical contract: $postgres_networks"
[ "$api_networks" = "${project}_application
${project}_database" ] \
  || formdock_delivery_die "API network topology does not match the canonical contract: $api_networks"
[ "$web_networks" = "$edge_network
${project}_application" ] \
  || formdock_delivery_die "Web network topology does not match the canonical contract: $web_networks"
[ "$(docker network inspect --format '{{.Internal}}' "$edge_network")" = false ] \
  || formdock_delivery_die 'Web edge network must remain external and non-internal.'
[ "$(docker inspect --format '{{with index .NetworkSettings.Networks "'"$edge_network"'"}}{{range .Aliases}}{{println .}}{{end}}{{end}}' "$web_id" \
    | grep -c '^form-dock-web$')" = 1 ] \
  || formdock_delivery_die 'Web edge network must expose the unique form-dock-web alias.'
[ "$(docker network inspect --format '{{.Internal}}' "${project}_database")" = true ] \
  || formdock_delivery_die 'Database network must remain internal.'

[ "$(docker inspect --format '{{.Image}}' "$api_id")" = "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" apiImageIdentity)" ] \
  || formdock_delivery_die 'Running API image identity does not match deployment state.'
[ "$(docker inspect --format '{{.Image}}' "$web_id")" = "$(formdock_delivery_value "$FORMDOCK_DEPLOYMENT_STATE_FILE" webImageIdentity)" ] \
  || formdock_delivery_die 'Running Web image identity does not match deployment state.'

docker exec "$postgres_id" sh -ceu 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null \
  || formdock_delivery_die 'PostgreSQL pg_isready acceptance failed.'
docker exec "$api_id" wget -qO- http://127.0.0.1:8080/actuator/health \
  | grep -q '"status":"UP"' \
  || formdock_delivery_die 'API Actuator health acceptance failed.'
docker exec "$web_id" wget -qO- http://127.0.0.1:8080/health \
  | grep -q '^ok$' \
  || formdock_delivery_die 'Web health acceptance failed.'
docker exec "$web_id" wget -qO- http://127.0.0.1:8080/api/auth/csrf \
  | grep -q '"headerName":"X-CSRF-TOKEN"' \
  || formdock_delivery_die 'Same-origin Web to API acceptance failed.'

printf 'DELIVERY_POSTGRES_HEALTH=PASS\n'
printf 'DELIVERY_API_HEALTH=PASS\n'
printf 'DELIVERY_WEB_HEALTH=PASS\n'
printf 'DELIVERY_SAME_ORIGIN_API=PASS\n'
printf 'DELIVERY_HOST_EXPOSURE=PASS\n'
printf 'DELIVERY_NETWORK_TOPOLOGY=PASS\n'
printf 'DELIVERY_LOG_ROTATION=PASS\n'
