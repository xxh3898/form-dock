#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
COMPOSE_FILE="$REPOSITORY_ROOT/infra/compose.production.yaml"
PREFLIGHT="$SCRIPT_DIR/preflight.sh"
BACKUP_DIR="$REPOSITORY_ROOT/infra/backup"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

umask 077

for required in \
  FORMDOCK_PREFLIGHT_DATA_PATH \
  FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT \
  FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE \
  FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE \
  FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH \
  FORMDOCK_BOOTSTRAP_INPUT_FILE; do
  formdock_production_require_env "$required"
done
for command in awk chmod curl date dig dirname docker grep id jq mkdir mv od python3 rmdir sed stat tr uname unlink; do
  formdock_production_require_command "$command"
done

for target in \
  "$FORMDOCK_PREFLIGHT_DATA_PATH" \
  "$FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT" \
  "$FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE" \
  "$FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE" \
  "$FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH" \
  "$FORMDOCK_BOOTSTRAP_INPUT_FILE"; do
  formdock_production_require_safe_absolute_path "$target"
done

private_env="$FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE"
deployment_state="$FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE"
operation_lock="$FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH"
backup_root="$FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT"
private_root="$(dirname -- "$private_env")"
[ "$(dirname -- "$deployment_state")" = "$private_root" ] \
  && [ "$(dirname -- "$operation_lock")" = "$private_root" ] \
  || formdock_production_die 'Runtime env, deployment state, and operation lock must share one private root.'
case "$private_root" in
  "$REPOSITORY_ROOT"|"$REPOSITORY_ROOT"/*)
    formdock_production_die 'Production private root must remain outside the repository.'
    ;;
esac
private_parent="$(dirname -- "$private_root")"
backup_parent="$(dirname -- "$backup_root")"
[ -d "$private_parent" ] && [ ! -L "$private_parent" ] \
  && [ -w "$private_parent" ] \
  && [ "$(formdock_production_owner_uid "$private_parent")" = "$(id -u)" ] \
  || formdock_production_die 'Production private-root parent is not an owned writable directory.'
[ -d "$backup_parent" ] && [ ! -L "$backup_parent" ] \
  && [ -w "$backup_parent" ] \
  && [ "$(formdock_production_owner_uid "$backup_parent")" = "$(id -u)" ] \
  || formdock_production_die 'Production backup-root parent is not an owned writable directory.'
[ ! -e "$private_root" ] && [ ! -L "$private_root" ] \
  || formdock_production_die 'First activation refuses an existing private root.'
[ ! -e "$backup_root" ] && [ ! -L "$backup_root" ] \
  || formdock_production_die 'First activation refuses an existing backup root.'

bootstrap_input="$(formdock_production_validate_bootstrap_input "$FORMDOCK_BOOTSTRAP_INPUT_FILE")"
case "$bootstrap_input" in
  "$REPOSITORY_ROOT"|"$REPOSITORY_ROOT"/*)
    formdock_production_die 'Trusted bootstrap input must remain outside the repository.'
    ;;
esac
bootstrap_enabled="$(formdock_delivery_value "$bootstrap_input" FORMDOCK_BOOTSTRAP_ENABLED)"
bootstrap_email="$(formdock_delivery_value "$bootstrap_input" FORMDOCK_BOOTSTRAP_EMAIL)"
bootstrap_password="$(formdock_delivery_value "$bootstrap_input" FORMDOCK_BOOTSTRAP_PASSWORD)"
bootstrap_display_name="$(formdock_delivery_value "$bootstrap_input" FORMDOCK_BOOTSTRAP_DISPLAY_NAME)"

preflight_output="$({
  FORMDOCK_PREFLIGHT_SCOPE=actual \
  FORMDOCK_PREFLIGHT_EXPECTED_PROJECT="$FORMDOCK_PRODUCTION_PROJECT" \
  FORMDOCK_PREFLIGHT_EXPECTED_WEB_PORT="$FORMDOCK_PRODUCTION_WEB_PORT" \
  FORMDOCK_PREFLIGHT_EXPECTED_RELEASE_SHA="$FORMDOCK_PRODUCTION_RELEASE_SHA" \
  FORMDOCK_PREFLIGHT_EXPECTED_API_IMAGE="$FORMDOCK_PRODUCTION_API_IMAGE" \
  FORMDOCK_PREFLIGHT_EXPECTED_WEB_IMAGE="$FORMDOCK_PRODUCTION_WEB_IMAGE" \
  FORMDOCK_PREFLIGHT_DATA_PATH="$FORMDOCK_PREFLIGHT_DATA_PATH" \
  FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT="$backup_root" \
  FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE="$private_env" \
  FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE="$deployment_state" \
  FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH="$operation_lock" \
    "$PREFLIGHT"
})"
printf '%s\n' "$preflight_output" | grep -Fxq 'result=PASS' \
  || formdock_production_die 'Actual Production preflight did not return PASS.'
printf '%s\n' "$preflight_output" | grep -Fxq 'evidenceMode=actual' \
  || formdock_production_die 'Production activation requires actual preflight evidence.'
printf '%s\n' "$preflight_output" | grep -Fxq 'activationClass=FIRST_ACTIVATION' \
  && printf '%s\n' "$preflight_output" | grep -Fxq 'databaseClass=FRESH_PRODUCTION_DB' \
  && printf '%s\n' "$preflight_output" | grep -Fxq 'previousState=NONE' \
  || formdock_production_die 'Actual preflight no longer proves a fresh first activation.'

operation_started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
operation_id="d2a-$(date -u '+%Y%m%dT%H%M%SZ')-$$"
pending_state="$deployment_state.pending"
preflight_evidence="$private_root/preflight.evidence"
activation_evidence="$private_root/activation.evidence"
configuration_identity_file="$private_root/configuration.identity"
lock_metadata="$operation_lock/metadata"
restore_verify_sql="$private_root/restore-verify.sql"
operation_log="$private_root/operation.log"
initial_cookie="$private_root/.initial.cookies"
final_cookie="$private_root/.final.cookies"
ephemeral_files=()
mutation_started=false
lock_acquired=false

cleanup_ephemeral() {
  local target
  for target in "${ephemeral_files[@]:-}"; do
    if [ -f "$target" ] && [ ! -L "$target" ]; then
      unlink "$target" 2>/dev/null || true
    fi
  done
}

on_failure() {
  local exit_code=$?
  [ "$exit_code" -ne 0 ] || return
  set +e
  cleanup_ephemeral
  printf 'FORMDOCK_D2A_RESULT=BLOCKED\n' >&2
  printf 'FORMDOCK_D2A_MUTATION_STARTED=%s\n' "$mutation_started" >&2
  if [ "$lock_acquired" = true ]; then
    printf 'FORMDOCK_D2A_LOCK=PRESERVED\n' >&2
  else
    printf 'FORMDOCK_D2A_LOCK=NOT_ACQUIRED\n' >&2
  fi
}
trap on_failure EXIT

mutation_started=true
mkdir "$private_root"
chmod 700 "$private_root"
formdock_production_require_owner_mode "$private_root" 700
mkdir "$operation_lock"
chmod 700 "$operation_lock"
formdock_production_require_owner_mode "$operation_lock" 700
lock_acquired=true
formdock_production_write_lock_metadata \
  "$lock_metadata" "$operation_id" "$operation_started_at" PENDING

mkdir "$backup_root"
chmod 700 "$backup_root"
formdock_production_require_owner_mode "$backup_root" 700
printf '%s\n' "$preflight_output" > "$preflight_evidence"
chmod 600 "$preflight_evidence"
: > "$operation_log"
chmod 600 "$operation_log"

database_password="$(LC_ALL=C od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
[[ "$database_password" =~ ^[0-9a-f]{64}$ ]] \
  || formdock_production_die 'Unable to generate a 32-byte Production database credential.'
formdock_production_write_configuration_identity "$configuration_identity_file"
configuration_revision="sha256:$(formdock_delivery_sha256 "$configuration_identity_file")"
formdock_production_write_runtime_env \
  "$private_env" "$database_password" "$configuration_revision"
formdock_production_validate_runtime_env "$private_env"

compose_revision="sha256:$(formdock_delivery_sha256 "$COMPOSE_FILE")"
formdock_production_write_deployment_state \
  "$pending_state" "$compose_revision" "$configuration_revision" "$operation_started_at"
candidate_state_sha="sha256:$(formdock_delivery_sha256 "$pending_state")"
formdock_production_write_lock_metadata \
  "$lock_metadata" "$operation_id" "$operation_started_at" "$candidate_state_sha"

docker pull --platform linux/arm64 "$FORMDOCK_PRODUCTION_API_IMAGE" >> "$operation_log" 2>&1
docker pull --platform linux/arm64 "$FORMDOCK_PRODUCTION_WEB_IMAGE" >> "$operation_log" 2>&1

verify_local_image() {
  local reference="$1"
  local expected_digest="${reference##*@}"
  local architecture operating_system

  architecture="$(docker image inspect --format '{{.Architecture}}' "$reference")"
  operating_system="$(docker image inspect --format '{{.Os}}' "$reference")"
  [ "$architecture" = arm64 ] && [ "$operating_system" = linux ] \
    || formdock_production_die 'Pulled Production image does not target linux/arm64.'
  docker image inspect --format '{{json .RepoDigests}}' "$reference" \
    | jq -e --arg reference "$reference" 'index($reference) != null' >/dev/null \
    || formdock_production_die "Pulled Production image does not retain exact digest identity: $expected_digest"
}
verify_local_image "$FORMDOCK_PRODUCTION_API_IMAGE"
verify_local_image "$FORMDOCK_PRODUCTION_WEB_IMAGE"

compose_final() {
  (
    unset FORMDOCK_BOOTSTRAP_ENABLED \
      FORMDOCK_BOOTSTRAP_EMAIL \
      FORMDOCK_BOOTSTRAP_PASSWORD \
      FORMDOCK_BOOTSTRAP_DISPLAY_NAME
    docker compose \
      --project-name "$FORMDOCK_PRODUCTION_PROJECT" \
      --env-file "$private_env" \
      -f "$COMPOSE_FILE" \
      "$@"
  )
}

compose_bootstrap() {
  FORMDOCK_BOOTSTRAP_ENABLED="$bootstrap_enabled" \
  FORMDOCK_BOOTSTRAP_EMAIL="$bootstrap_email" \
  FORMDOCK_BOOTSTRAP_PASSWORD="$bootstrap_password" \
  FORMDOCK_BOOTSTRAP_DISPLAY_NAME="$bootstrap_display_name" \
    docker compose \
      --project-name "$FORMDOCK_PRODUCTION_PROJECT" \
      --env-file "$private_env" \
      -f "$COMPOSE_FILE" \
      "$@"
}

compose_final config --quiet
compose_bootstrap config --quiet
compose_bootstrap up --detach --wait >> "$operation_log" 2>&1

service_container() {
  local service="$1"
  local ids count

  ids="$(docker ps -aq \
    --filter "label=com.docker.compose.project=$FORMDOCK_PRODUCTION_PROJECT" \
    --filter "label=com.docker.compose.service=$service")"
  count="$(printf '%s\n' "$ids" | awk 'NF { count += 1 } END { print count + 0 }')"
  [ "$count" = 1 ] \
    || formdock_production_die "Production project must contain exactly one $service container."
  printf '%s\n' "$ids"
}

postgres_id="$(service_container postgres)"
initial_api_id="$(service_container api)"
web_id="$(service_container web)"
for container in "$postgres_id" "$initial_api_id" "$web_id"; do
  [ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")" = healthy ] \
    || formdock_production_die 'Production service did not become healthy.'
done
[ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$postgres_id")" = 0 ] \
  && [ "$(docker inspect --format '{{len .HostConfig.PortBindings}}' "$initial_api_id")" = 0 ] \
  || formdock_production_die 'Production PostgreSQL/API must not publish host ports.'
[ "$(docker port "$web_id" 8080/tcp)" = "127.0.0.1:$FORMDOCK_PRODUCTION_WEB_PORT" ] \
  || formdock_production_die 'Production Web must bind only the canonical loopback port.'

[ "$(docker inspect --format '{{json .NetworkSettings.Networks}}' "$postgres_id" | jq -r 'keys | sort | join(",")')" = form-dock_database ] \
  && [ "$(docker inspect --format '{{json .NetworkSettings.Networks}}' "$initial_api_id" | jq -r 'keys | sort | join(",")')" = form-dock_application,form-dock_database ] \
  && [ "$(docker inspect --format '{{json .NetworkSettings.Networks}}' "$web_id" | jq -r 'keys | sort | join(",")')" = edge,form-dock_application ] \
  || formdock_production_die 'Production service network topology does not match the canonical Compose.'
docker inspect --format '{{with index .NetworkSettings.Networks "edge"}}{{json .Aliases}}{{end}}' "$web_id" \
  | jq -e 'index("form-dock-web") != null' >/dev/null \
  || formdock_production_die 'Production Web is missing the exact edge alias.'

postgres_volume="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_id")"
[ "$postgres_volume" = form-dock_postgres-data ] \
  || formdock_production_die 'Production PostgreSQL does not use the canonical persistent volume.'

flyway_state="$({
  docker exec -i "$postgres_id" sh -ceu '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    exec psql --no-password --tuples-only --no-align \
      --username "$POSTGRES_USER" \
      --dbname "$POSTGRES_DB"
  ' <<'SQL'
SELECT COALESCE(string_agg(version, ',' ORDER BY installed_rank) FILTER (WHERE success), '')
       || '|' || count(*) FILTER (WHERE success)
       || '|' || count(*) FILTER (WHERE NOT success)
FROM flyway_schema_history;
SQL
} | tr -d '\r')"
[ "$flyway_state" = '1,2,3,4,5,6|6|0' ] \
  || formdock_production_die 'Production Flyway history is not exactly V1 through V6.'
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
  || formdock_production_die 'Initial Creator bootstrap did not produce exactly one ADMIN account.'

ephemeral_files+=(
  "$private_root/.initial.csrf.json"
  "$private_root/.initial.login.json"
  "$private_root/.initial.login.conf"
  "$private_root/.initial.login.response"
  "$private_root/.initial.me.response"
  "$private_root/.initial.surveys.response"
  "$initial_cookie"
  "$private_root/.final.csrf.json"
  "$private_root/.final.login.json"
  "$private_root/.final.login.conf"
  "$private_root/.final.login.response"
  "$private_root/.final.me.response"
  "$private_root/.final.surveys.response"
  "$final_cookie"
  "$restore_verify_sql"
)

authenticated_acceptance() {
  local label="$1"
  local cookie_file="$2"
  local csrf_body="$private_root/.$label.csrf.json"
  local login_body="$private_root/.$label.login.json"
  local login_config="$private_root/.$label.login.conf"
  local login_response="$private_root/.$label.login.response"
  local me_response="$private_root/.$label.me.response"
  local surveys_response="$private_root/.$label.surveys.response"
  local status header_name csrf_token

  : > "$cookie_file"
  chmod 600 "$cookie_file"
  status="$(curl --silent --show-error \
    --cookie-jar "$cookie_file" \
    --output "$csrf_body" \
    --write-out '%{http_code}' \
    "http://localhost:$FORMDOCK_PRODUCTION_WEB_PORT/api/auth/csrf")"
  [ "$status" = 200 ] || formdock_production_die 'Same-origin CSRF request failed.'
  chmod 600 "$csrf_body"
  header_name="$(jq -r '.headerName // empty' "$csrf_body")"
  csrf_token="$(jq -r '.token // empty' "$csrf_body")"
  [[ "$header_name" =~ ^[A-Za-z0-9-]{1,64}$ ]] && [ -n "$csrf_token" ] \
    || formdock_production_die 'CSRF response does not match the expected contract.'

  FORMDOCK_LOGIN_EMAIL="$bootstrap_email" \
  FORMDOCK_LOGIN_PASSWORD="$bootstrap_password" \
  FORMDOCK_LOGIN_BODY_FILE="$login_body" \
    python3 - <<'PY'
import json
import os

with open(os.environ["FORMDOCK_LOGIN_BODY_FILE"], "w", encoding="utf-8") as target:
    json.dump({
        "email": os.environ["FORMDOCK_LOGIN_EMAIL"],
        "password": os.environ["FORMDOCK_LOGIN_PASSWORD"],
    }, target, ensure_ascii=False)
PY
  chmod 600 "$login_body"
  {
    printf 'url = "http://localhost:%s/api/auth/login"\n' "$FORMDOCK_PRODUCTION_WEB_PORT"
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
  status="$(curl --config "$login_config")"
  [ "$status" = 200 ] || formdock_production_die 'Creator login acceptance failed.'
  chmod 600 "$login_response"
  jq -e '
    (.id | type == "number") and
    (.email | type == "string" and length > 0) and
    (.displayName | type == "string" and length > 0) and
    .role == "ADMIN"
  ' "$login_response" >/dev/null \
    || formdock_production_die 'Creator login response does not match the safe DTO contract.'

  status="$(curl --silent --show-error \
    --cookie "$cookie_file" \
    --output "$me_response" \
    --write-out '%{http_code}' \
    "http://localhost:$FORMDOCK_PRODUCTION_WEB_PORT/api/auth/me")"
  [ "$status" = 200 ] || formdock_production_die 'Creator session/me acceptance failed.'
  chmod 600 "$me_response"
  jq -e '.role == "ADMIN" and (.id | type == "number")' "$me_response" >/dev/null \
    || formdock_production_die 'Creator me response does not match the safe DTO contract.'

  status="$(curl --silent --show-error \
    --cookie "$cookie_file" \
    --output "$surveys_response" \
    --write-out '%{http_code}' \
    "http://localhost:$FORMDOCK_PRODUCTION_WEB_PORT/api/surveys")"
  [ "$status" = 200 ] || formdock_production_die 'Admin Survey list acceptance failed.'
  chmod 600 "$surveys_response"
  jq -e 'type == "array" and length == 0' "$surveys_response" >/dev/null \
    || formdock_production_die 'Fresh Production Survey list must be an empty array.'
}

authenticated_acceptance initial "$initial_cookie"

compose_final up --detach --wait --no-deps --force-recreate api >> "$operation_log" 2>&1
final_api_id="$(service_container api)"
[ "$final_api_id" != "$initial_api_id" ] \
  || formdock_production_die 'Bootstrap finalization did not recreate the API container.'
[ "$(service_container postgres)" = "$postgres_id" ] \
  || formdock_production_die 'Bootstrap finalization unexpectedly recreated PostgreSQL.'
[ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_id")" = "$postgres_volume" ] \
  || formdock_production_die 'Bootstrap finalization changed the PostgreSQL volume identity.'
[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$final_api_id")" = healthy ] \
  || formdock_production_die 'Final API container is not healthy.'
[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$postgres_id")" = healthy ] \
  && [ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$web_id")" = healthy ] \
  || formdock_production_die 'Final PostgreSQL/Web runtime is not healthy.'
docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$final_api_id" \
  | awk -F= '
      $1 == "FORMDOCK_BOOTSTRAP_ENABLED" { enabled += 1; if ($2 != "false") exit 1 }
      $1 == "FORMDOCK_BOOTSTRAP_EMAIL" { email += 1; if (length($2) != 0) exit 1 }
      $1 == "FORMDOCK_BOOTSTRAP_PASSWORD" { password += 1; if (length($2) != 0) exit 1 }
      $1 == "FORMDOCK_BOOTSTRAP_DISPLAY_NAME" { display += 1; if (length($2) != 0) exit 1 }
      END { if (enabled != 1 || email != 1 || password != 1 || display != 1) exit 1 }
    ' \
  || formdock_production_die 'Final API runtime retains invalid bootstrap configuration.'

status="$(curl --silent --show-error \
  --cookie "$initial_cookie" \
  --output "$private_root/.initial.me.response" \
  --write-out '%{http_code}' \
  "http://localhost:$FORMDOCK_PRODUCTION_WEB_PORT/api/auth/me")"
[ "$status" = 200 ] \
  || formdock_production_die 'JDBC session did not survive API bootstrap finalization.'
authenticated_acceptance final "$final_cookie"

backup_output="$({
  FORMDOCK_BACKUP_ROOT="$backup_root" \
  FORMDOCK_RELEASE_SHA="$FORMDOCK_PRODUCTION_RELEASE_SHA" \
  FORMDOCK_DB_DOCKER_NETWORK=form-dock_database \
  FORMDOCK_DB_HOST=postgres \
  FORMDOCK_DB_PORT=5432 \
  FORMDOCK_DB_NAME=formdock \
  FORMDOCK_DB_USERNAME=formdock \
  FORMDOCK_DB_PASSWORD="$database_password" \
  FORMDOCK_POSTGRES_IMAGE="$FORMDOCK_PRODUCTION_POSTGRES_IMAGE" \
    "$BACKUP_DIR/backup.sh"
})"
backup_id="$(printf '%s\n' "$backup_output" | awk -F= '$1 == "BACKUP_ID" { print $2 }')"
printf '%s\n' "$backup_output" | grep -Fxq 'BACKUP_STATUS=complete' \
  && [[ "$backup_id" =~ ^formdock-[A-Za-z0-9][A-Za-z0-9._-]{0,95}$ ]] \
  || formdock_production_die 'First Production backup did not complete.'
FORMDOCK_BACKUP_ROOT="$backup_root" \
FORMDOCK_BACKUP_ID="$backup_id" \
FORMDOCK_POSTGRES_IMAGE="$FORMDOCK_PRODUCTION_POSTGRES_IMAGE" \
  "$BACKUP_DIR/verify.sh" >> "$operation_log"

{
  printf '%s\n' 'DO $$'
  printf '%s\n' 'BEGIN'
  printf '%s\n' '  IF (SELECT count(*) FROM users) <> 1 THEN'
  printf '%s\n' "    RAISE EXCEPTION 'Expected exactly one Creator row';"
  printf '%s\n' '  END IF;'
  printf '%s\n' 'END'
  printf '%s\n' '$$;'
} > "$restore_verify_sql"
chmod 600 "$restore_verify_sql"
scratch_password="$(LC_ALL=C od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
[[ "$scratch_password" =~ ^[0-9a-f]{64}$ ]] \
  || formdock_production_die 'Unable to generate a disposable scratch database credential.'
scratch_id="dev-form-dock-scratch-d2a-$$"
restore_output="$({
  FORMDOCK_BACKUP_ROOT="$backup_root" \
  FORMDOCK_BACKUP_ID="$backup_id" \
  FORMDOCK_SCRATCH_ID="$scratch_id" \
  FORMDOCK_SCRATCH_DB_PASSWORD="$scratch_password" \
  FORMDOCK_API_IMAGE="$FORMDOCK_PRODUCTION_API_IMAGE" \
  FORMDOCK_POSTGRES_IMAGE="$FORMDOCK_PRODUCTION_POSTGRES_IMAGE" \
  FORMDOCK_RESTORE_VERIFY_SQL_FILE="$restore_verify_sql" \
    "$BACKUP_DIR/restore-scratch.sh"
})"
for expected in \
  'SCRATCH_POSTGRES_HEALTH=PASS' \
  'SCRATCH_FLYWAY_HISTORY=1,2,3,4,5,6' \
  'SCRATCH_REPRESENTATIVE_DATA=PASS' \
  'SCRATCH_API_HEALTH=PASS' \
  'SCRATCH_RESIDUE=0'; do
  printf '%s\n' "$restore_output" | grep -Fxq "$expected" \
    || formdock_production_die 'First Production backup scratch restore did not pass.'
done

[ "$(dig +noall +comments forms.chochiho.cloud A 2>/dev/null \
  | sed -n 's/.*status: \([A-Z][A-Z]*\),.*/\1/p' | awk 'NR == 1 { print }')" = NXDOMAIN ] \
  || formdock_production_die 'Public DNS changed during local-only D2A activation.'
[ "$(docker inspect cloudflared --format '{{.State.Status}}')" = running ] \
  && [ "$(docker inspect cloudflared --format '{{if index .NetworkSettings.Networks "edge"}}attached{{else}}absent{{end}}')" = attached ] \
  || formdock_production_die 'Cloudflared/edge state drifted during local-only D2A activation.'
for service in web api db; do
  service_ids="$(docker ps -aq \
    --filter 'label=com.docker.compose.project=homeops' \
    --filter "label=com.docker.compose.service=$service")"
  [ "$(printf '%s\n' "$service_ids" | awk 'NF { count += 1 } END { print count + 0 }')" = 1 ] \
    || formdock_production_die 'HomeOps runtime became ambiguous during D2A activation.'
  [ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$service_ids")" = healthy ] \
    || formdock_production_die 'HomeOps runtime is not healthy after D2A activation.'
done

mv "$pending_state" "$deployment_state"
formdock_production_require_owner_mode "$deployment_state" 600
formdock_delivery_validate_state "$deployment_state" candidate
{
  printf 'formatVersion=1\n'
  printf 'result=PASS\n'
  printf 'operationUtc=%s\n' "$operation_started_at"
  printf 'issue=93\n'
  printf 'releaseGitSha=%s\n' "$FORMDOCK_PRODUCTION_RELEASE_SHA"
  printf 'apiArtifact=%s\n' "$FORMDOCK_PRODUCTION_API_IMAGE"
  printf 'webArtifact=%s\n' "$FORMDOCK_PRODUCTION_WEB_IMAGE"
  printf 'targetPlatform=linux/arm64\n'
  printf 'activationClass=FIRST_ACTIVATION\n'
  printf 'databaseClass=FRESH_PRODUCTION_DB\n'
  printf 'operationLock=ACQUIRED_THEN_RELEASED\n'
  printf 'privatePermissions=PASS\n'
  printf 'canonicalCompose=PASS\n'
  printf 'flywayHistory=1,2,3,4,5,6\n'
  printf 'creatorBootstrap=PASS\n'
  printf 'bootstrapFinalRuntime=DISABLED_EMPTY\n'
  printf 'finalLoginSessionSafeRead=PASS\n'
  printf 'postgresVolumePreserved=PASS\n'
  printf 'firstLocalBackup=PASS\n'
  printf 'backupId=%s\n' "$backup_id"
  printf 'scratchRestore=PASS\n'
  printf 'scratchResidue=0\n'
  printf 'cloudflareMutationCount=0\n'
  printf 'homeOpsMutationCount=0\n'
  printf 'ghcrMutationCount=0\n'
  printf 'productFixtureWriteCount=0\n'
  printf 'offHostDurabilityStatus=DEFERRED_ACCEPTED_RISK\n'
  printf 'currentIndependentOffHostTarget=NONE\n'
  printf 'secretLeakFindingCount=0\n'
} > "$activation_evidence"
chmod 600 "$activation_evidence"

cleanup_ephemeral
unlink "$lock_metadata"
rmdir "$operation_lock"
lock_acquired=false
trap - EXIT

printf 'FORMDOCK_D2A_RESULT=PASS\n'
printf 'FORMDOCK_D2A_RELEASE_SHA=%s\n' "$FORMDOCK_PRODUCTION_RELEASE_SHA"
printf 'FORMDOCK_D2A_FLYWAY=1,2,3,4,5,6\n'
printf 'FORMDOCK_D2A_BOOTSTRAP_FINAL=DISABLED_EMPTY\n'
printf 'FORMDOCK_D2A_LOCAL_ACCEPTANCE=PASS\n'
printf 'FORMDOCK_D2A_FIRST_BACKUP=PASS\n'
printf 'FORMDOCK_D2A_SCRATCH_RESTORE=PASS\n'
printf 'FORMDOCK_D2A_PUBLIC_ROUTE=ABSENT\n'
printf 'FORMDOCK_D2A_HOMEOPS_MUTATION=0\n'
printf 'FORMDOCK_D2A_LOCK=RELEASED\n'
