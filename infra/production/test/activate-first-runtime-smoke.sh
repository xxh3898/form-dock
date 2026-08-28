#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PRODUCTION_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
COMPOSE_FILE="$REPOSITORY_ROOT/infra/compose.production.yaml"
# shellcheck source=../common.sh
source "$PRODUCTION_DIR/common.sh"

formdock_production_require_command curl
formdock_production_require_command docker
formdock_production_require_command jq

api_image="${FORMDOCK_ACTIVATION_TEST_API_IMAGE:-dev-form-dock-api}"
web_image="${FORMDOCK_ACTIVATION_TEST_WEB_IMAGE:-dev-form-dock-web}"
docker image inspect "$api_image" >/dev/null 2>&1 \
  || formdock_production_die 'Activation runtime smoke requires the existing local API image.'
docker image inspect "$web_image" >/dev/null 2>&1 \
  || formdock_production_die 'Activation runtime smoke requires the existing local Web image.'

run_suffix="$(date -u '+%H%M%S')-$$"
project="dev-form-dock-activation-$run_suffix"
edge_network="$project-edge"
database_password="$(LC_ALL=C od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
bootstrap_email='activation-smoke@example.test'
bootstrap_password='activation-smoke-password'
bootstrap_display_name='Activation Smoke'

tmp_base="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-activation-runtime.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-activation-runtime.*) ;;
  *) formdock_production_die 'Activation runtime temporary directory escaped the expected root.' ;;
esac
chmod 700 "$temp_root"
env_file="$temp_root/runtime.env"
initial_cookie="$temp_root/initial.cookies"
final_cookie="$temp_root/final.cookies"

network_created=false
compose_started=false
temp_created=true

compose_final() {
  (
    unset FORMDOCK_BOOTSTRAP_ENABLED \
      FORMDOCK_BOOTSTRAP_EMAIL \
      FORMDOCK_BOOTSTRAP_PASSWORD \
      FORMDOCK_BOOTSTRAP_DISPLAY_NAME
    docker compose \
      --project-name "$project" \
      --env-file "$env_file" \
      -f "$COMPOSE_FILE" \
      "$@"
  )
}

cleanup() {
  set +e
  if [ "$compose_started" = true ]; then
    compose_final down --remove-orphans >/dev/null 2>&1
    compose_started=false
  fi
  volume_name="${project}_postgres-data"
  if docker volume inspect "$volume_name" >/dev/null 2>&1; then
    if [ "$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume_name" 2>/dev/null)" = "$project" ]; then
      docker volume rm "$volume_name" >/dev/null 2>&1
    fi
  fi
  if [ "$network_created" = true ] && docker network inspect "$edge_network" >/dev/null 2>&1; then
    if [ "$(docker network inspect --format '{{index .Labels "com.formdock.validation.project"}}' "$edge_network" 2>/dev/null)" = "$project" ]; then
      docker network rm "$edge_network" >/dev/null 2>&1
    fi
    network_created=false
  fi
  if [ "$temp_created" = true ]; then
    case "$temp_root" in
      "$tmp_base"/formdock-activation-runtime.*) find "$temp_root" -depth -delete ;;
    esac
    temp_created=false
  fi
}
trap cleanup EXIT

[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ] \
  || formdock_production_die 'Activation runtime smoke refuses an existing project.'
! docker network inspect "$edge_network" >/dev/null 2>&1 \
  || formdock_production_die 'Activation runtime smoke refuses an existing edge fixture network.'
docker network create \
  --label "com.formdock.validation.project=$project" \
  "$edge_network" >/dev/null
network_created=true

{
  printf 'FORMDOCK_API_IMAGE=%s\n' "$api_image"
  printf 'FORMDOCK_WEB_IMAGE=%s\n' "$web_image"
  printf 'FORMDOCK_CONFIGURATION_REVISION=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'
  printf 'FORMDOCK_WEB_PORT=0\n'
  printf 'FORMDOCK_EDGE_NETWORK=%s\n' "$edge_network"
  printf 'FORMDOCK_LOG_MAX_SIZE=10m\n'
  printf 'FORMDOCK_LOG_MAX_FILE=5\n'
  printf 'FORMDOCK_DB_NAME=formdock_activation\n'
  printf 'FORMDOCK_DB_USERNAME=formdock_activation\n'
  printf 'FORMDOCK_DB_PASSWORD=%s\n' "$database_password"
  printf 'FORMDOCK_BOOTSTRAP_ENABLED=false\n'
  printf 'FORMDOCK_BOOTSTRAP_EMAIL=\n'
  printf 'FORMDOCK_BOOTSTRAP_PASSWORD=\n'
  printf 'FORMDOCK_BOOTSTRAP_DISPLAY_NAME=\n'
} > "$env_file"
chmod 600 "$env_file"

FORMDOCK_BOOTSTRAP_ENABLED=true \
FORMDOCK_BOOTSTRAP_EMAIL="$bootstrap_email" \
FORMDOCK_BOOTSTRAP_PASSWORD="$bootstrap_password" \
FORMDOCK_BOOTSTRAP_DISPLAY_NAME="$bootstrap_display_name" \
  docker compose \
    --project-name "$project" \
    --env-file "$env_file" \
    -f "$COMPOSE_FILE" \
    up --detach --wait >/dev/null
compose_started=true

service_container() {
  local service="$1"
  local ids count
  ids="$(docker ps -aq \
    --filter "label=com.docker.compose.project=$project" \
    --filter "label=com.docker.compose.service=$service")"
  count="$(printf '%s\n' "$ids" | awk 'NF { count += 1 } END { print count + 0 }')"
  [ "$count" = 1 ] || formdock_production_die 'Activation runtime smoke service identity is ambiguous.'
  printf '%s\n' "$ids"
}

postgres_id="$(service_container postgres)"
initial_api_id="$(service_container api)"
web_id="$(service_container web)"
postgres_volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_id")"
web_binding="$(docker port "$web_id" 8080/tcp)"
case "$web_binding" in
  127.0.0.1:*) ;;
  *) formdock_production_die 'Activation runtime smoke Web is not loopback-only.' ;;
esac
web_port="${web_binding##*:}"
[[ "$web_port" =~ ^[0-9]+$ ]] && [ "$web_port" -ge 1 ] && [ "$web_port" -le 65535 ] \
  || formdock_production_die 'Activation runtime smoke did not receive a valid random Web port.'

creator_state="$({
  docker exec -i "$postgres_id" sh -ceu '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    exec psql --no-password --tuples-only --no-align \
      --username "$POSTGRES_USER" \
      --dbname "$POSTGRES_DB"
  ' <<'SQL'
SELECT count(*) || '|' || count(*) FILTER (WHERE role = 'ADMIN') FROM users;
SQL
} | tr -d '\r')"
[ "$creator_state" = '1|1' ] \
  || formdock_production_die 'Activation runtime smoke bootstrap did not create one ADMIN.'

authenticated_acceptance() {
  local label="$1"
  local cookie_file="$2"
  local csrf_body="$temp_root/$label-csrf.json"
  local login_body="$temp_root/$label-login.json"
  local login_config="$temp_root/$label-login.conf"
  local login_response="$temp_root/$label-login-response.json"
  local me_response="$temp_root/$label-me.json"
  local surveys_response="$temp_root/$label-surveys.json"
  local status header_name csrf_token

  : > "$cookie_file"
  chmod 600 "$cookie_file"
  status="$(curl --silent --show-error \
    --cookie-jar "$cookie_file" \
    --output "$csrf_body" \
    --write-out '%{http_code}' \
    "http://localhost:$web_port/api/auth/csrf")"
  [ "$status" = 200 ] || formdock_production_die 'Activation runtime CSRF acceptance failed.'
  header_name="$(jq -r '.headerName // empty' "$csrf_body")"
  csrf_token="$(jq -r '.token // empty' "$csrf_body")"
  [ -n "$header_name" ] && [ -n "$csrf_token" ] \
    || formdock_production_die 'Activation runtime CSRF response is invalid.'

  FORMDOCK_LOGIN_BODY="$login_body" \
  FORMDOCK_LOGIN_EMAIL="$bootstrap_email" \
  FORMDOCK_LOGIN_PASSWORD="$bootstrap_password" \
    python3 - <<'PY'
import json
import os

with open(os.environ["FORMDOCK_LOGIN_BODY"], "w", encoding="utf-8") as target:
    json.dump({
        "email": os.environ["FORMDOCK_LOGIN_EMAIL"],
        "password": os.environ["FORMDOCK_LOGIN_PASSWORD"],
    }, target)
PY
  chmod 600 "$login_body"
  {
    printf 'url = "http://localhost:%s/api/auth/login"\n' "$web_port"
    printf 'request = "POST"\n'
    printf 'header = "Content-Type: application/json"\n'
    printf 'header = "%s: %s"\n' "$header_name" "$csrf_token"
    printf 'cookie = "%s"\n' "$cookie_file"
    printf 'cookie-jar = "%s"\n' "$cookie_file"
    printf 'data-binary = "@%s"\n' "$login_body"
    printf 'silent\n'
    printf 'show-error\n'
    printf 'output = "%s"\n' "$login_response"
    printf 'write-out = "%%{http_code}"\n'
  } > "$login_config"
  chmod 600 "$login_config"
  [ "$(curl --config "$login_config")" = 200 ] \
    || formdock_production_die 'Activation runtime Creator login failed.'
  jq -e '.role == "ADMIN"' "$login_response" >/dev/null \
    || formdock_production_die 'Activation runtime login DTO is invalid.'
  status="$(curl --silent --show-error \
    --cookie "$cookie_file" \
    --output "$me_response" \
    --write-out '%{http_code}' \
    "http://localhost:$web_port/api/auth/me")"
  [ "$status" = 200 ] || formdock_production_die 'Activation runtime session/me failed.'
  status="$(curl --silent --show-error \
    --cookie "$cookie_file" \
    --output "$surveys_response" \
    --write-out '%{http_code}' \
    "http://localhost:$web_port/api/surveys")"
  [ "$status" = 200 ] && jq -e 'type == "array" and length == 0' "$surveys_response" >/dev/null \
    || formdock_production_die 'Activation runtime empty Survey list failed.'
}

authenticated_acceptance initial "$initial_cookie"

compose_final up --detach --wait --no-deps --force-recreate api >/dev/null
final_api_id="$(service_container api)"
[ "$final_api_id" != "$initial_api_id" ] \
  || formdock_production_die 'Activation runtime smoke did not recreate the API for finalization.'
[ "$(service_container postgres)" = "$postgres_id" ] \
  && [ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_id")" = "$postgres_volume" ] \
  || formdock_production_die 'Activation runtime finalization replaced PostgreSQL or its volume.'
docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$final_api_id" \
  | awk -F= '
      $1 == "FORMDOCK_BOOTSTRAP_ENABLED" { enabled += 1; if ($2 != "false") exit 1 }
      $1 == "FORMDOCK_BOOTSTRAP_EMAIL" { email += 1; if (length($2) != 0) exit 1 }
      $1 == "FORMDOCK_BOOTSTRAP_PASSWORD" { password += 1; if (length($2) != 0) exit 1 }
      $1 == "FORMDOCK_BOOTSTRAP_DISPLAY_NAME" { display += 1; if (length($2) != 0) exit 1 }
      END { if (enabled != 1 || email != 1 || password != 1 || display != 1) exit 1 }
    ' \
  || formdock_production_die 'Activation runtime final API retained bootstrap credentials.'
[ "$(curl --silent --show-error \
  --cookie "$initial_cookie" \
  --output "$temp_root/session-after-finalization.json" \
  --write-out '%{http_code}' \
  "http://localhost:$web_port/api/auth/me")" = 200 ] \
  || formdock_production_die 'Activation runtime JDBC session did not survive API recreation.'
authenticated_acceptance final "$final_cookie"

compose_final down --remove-orphans >/dev/null
compose_started=false
volume_name="${project}_postgres-data"
[ "$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume_name")" = "$project" ] \
  || formdock_production_die 'Activation runtime smoke volume label is not isolated.'
docker volume rm "$volume_name" >/dev/null
docker network rm "$edge_network" >/dev/null
network_created=false
cleanup
trap - EXIT

[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ]
[ -z "$(docker network ls -q --filter "label=com.docker.compose.project=$project")" ]
[ -z "$(docker volume ls -q --filter "label=com.docker.compose.project=$project")" ]
! docker network inspect "$edge_network" >/dev/null 2>&1
[ ! -e "$temp_root" ]

printf 'ACTIVATION_RUNTIME_BOOTSTRAP=PASS\n'
printf 'ACTIVATION_RUNTIME_SAME_ORIGIN_LOGIN=PASS\n'
printf 'ACTIVATION_RUNTIME_SESSION_PRESERVATION=PASS\n'
printf 'ACTIVATION_RUNTIME_FINAL_SECRET_REMOVAL=PASS\n'
printf 'ACTIVATION_RUNTIME_POSTGRES_VOLUME_PRESERVATION=PASS\n'
printf 'ACTIVATION_RUNTIME_RESIDUE=0\n'
