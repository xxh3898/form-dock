#!/bin/bash
set -Eeuo pipefail

readonly ZERO_SHA=0000000000000000000000000000000000000000
readonly ZERO_DIGEST=sha256:0000000000000000000000000000000000000000000000000000000000000000
readonly EXPECTED_FLYWAY_STATE='1,2,3,4,5,6|6|0'
readonly CANONICAL_APP_DIR=/Users/homeserver/Server/apps/form-dock
readonly PROJECT=form-dock
readonly API_REPOSITORY=ghcr.io/xxh3898/form-dock-api
readonly WEB_REPOSITORY=ghcr.io/xxh3898/form-dock-web

fail() {
  printf 'FormDock recurring deployment failed: %s\n' "$1" >&2
  exit 1
}

is_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] && [ "$1" != "$ZERO_SHA" ]
}

is_digest() {
  [[ "$1" =~ ^sha256:[0-9a-f]{64}$ ]] && [ "$1" != "$ZERO_DIGEST" ]
}

private_mode() {
  local target="$1"
  local mode
  mode="$(stat -f '%Lp' "$target" 2>/dev/null || stat -c '%a' "$target" 2>/dev/null)" \
    || fail 'Unable to inspect private file mode.'
  case "$mode" in
    *00) ;;
    *) fail 'Private deployment file group/other permissions must be zero.' ;;
  esac
}

file_mode() {
  local target="$1"
  if [ "$(uname -s)" = Darwin ]; then
    stat -f '%Lp' "$target"
  else
    stat -c '%a' "$target"
  fi
}

file_owner() {
  local target="$1"
  if [ "$(uname -s)" = Darwin ]; then
    stat -f '%u' "$target"
  else
    stat -c '%u' "$target"
  fi
}

validate_operation_lock() {
  local target="$1"
  [ ! -L "$target" ] && [ -f "$target" ] \
    || return 1
  [ "$(file_owner "$target")" = "$(id -u)" ] \
    && [ "$(file_mode "$target")" = 600 ]
}

prepare_operation_lock() {
  local target="$1"
  if [ ! -e "$target" ] && [ ! -L "$target" ]; then
    (umask 077; set -o noclobber; : > "$target") 2>/dev/null || true
  fi
  validate_operation_lock "$target"
}

require_private_file() {
  local target="$1"
  [ -f "$target" ] && [ ! -L "$target" ] \
    || fail 'Required private deployment file is missing or unsafe.'
  private_mode "$target"
}

value() {
  local file="$1"
  local key="$2"
  awk -v wanted="$key" '
    index($0, wanted "=") == 1 {
      count += 1
      result = substr($0, length(wanted) + 2)
    }
    END {
      if (count != 1) exit 1
      print result
    }
  ' "$file" || fail "Field must occur exactly once: $key"
}

sha256_file() {
  local target="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$target" | awk '{ print $1 }'
  else
    shasum -a 256 "$target" | awk '{ print $1 }'
  fi
}

replace_symlink() {
  local source="$1"
  local destination="$2"
  if [ "$(uname -s)" = Darwin ]; then
    mv -fh -- "$source" "$destination"
  else
    mv -fT -- "$source" "$destination"
  fi
}

validate_runtime_state() {
  local state_file="$1"
  require_private_file "$state_file"
  awk -F= '
    BEGIN {
      allowed["formatVersion"] = 1
      allowed["currentSha"] = 1
      allowed["currentDigest"] = 1
      allowed["previousSha"] = 1
      allowed["previousDigest"] = 1
      allowed["recordedAt"] = 1
    }
    !($1 in allowed) || seen[$1]++ { exit 1 }
    { count += 1 }
    END { if (count != 6) exit 1 }
  ' "$state_file" || fail 'Runtime-config state keys are invalid.'
  [ "$(value "$state_file" formatVersion)" = 1 ] \
    || fail 'Runtime-config state format version is invalid.'

  local current_sha previous_sha current_digest previous_digest recorded_at
  current_sha="$(value "$state_file" currentSha)"
  previous_sha="$(value "$state_file" previousSha)"
  current_digest="$(value "$state_file" currentDigest)"
  previous_digest="$(value "$state_file" previousDigest)"
  recorded_at="$(value "$state_file" recordedAt)"

  is_sha "$current_sha" || fail 'Current runtime-config SHA is invalid.'
  is_digest "$current_digest" || fail 'Current runtime-config digest is invalid.'
  if [ "$previous_sha" != "$ZERO_SHA" ]; then
    is_sha "$previous_sha" || fail 'Previous runtime-config SHA is invalid.'
    is_digest "$previous_digest" || fail 'Previous runtime-config digest is invalid.'
  else
    [ "$previous_digest" = "$ZERO_DIGEST" ] \
      || fail 'Empty previous runtime-config must use the zero digest sentinel.'
  fi
  [[ "$recorded_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
    || fail 'Runtime-config recordedAt is invalid.'
}

validate_operations() {
  local operations_file="$1"
  require_private_file "$operations_file"
  awk -F= '
    BEGIN {
      allowed["FORMDOCK_BACKUP_ROOT"] = 1
      allowed["FORMDOCK_BACKUP_MAX_AGE_SECONDS"] = 1
      allowed["FORMDOCK_PUBLIC_ORIGIN"] = 1
      allowed["FORMDOCK_HOMEOPS_REPORTER"] = 1
    }
    !($1 in allowed) || seen[$1]++ { exit 1 }
    { count += 1 }
    END { if (count != 4) exit 1 }
  ' "$operations_file" || fail 'CD operations configuration keys are invalid.'
}

if [ "$#" -ne 3 ]; then
  fail 'Expected exact main SHA, registry owner and workflow run ID.'
fi

readonly COMMIT_SHA="$1"
readonly REGISTRY_OWNER="$2"
readonly WORKFLOW_RUN_ID="$3"
readonly CANDIDATE_API_DIGEST="${FORMDOCK_API_IMAGE_DIGEST:-$ZERO_DIGEST}"
readonly CANDIDATE_WEB_DIGEST="${FORMDOCK_WEB_IMAGE_DIGEST:-$ZERO_DIGEST}"
readonly CANDIDATE_RUNTIME_DIGEST="${FORMDOCK_RUNTIME_CONFIG_DIGEST:-$ZERO_DIGEST}"
readonly DEPLOY_ACTOR="${FORMDOCK_DEPLOY_ACTOR:-formdock-cd}"
readonly TEST_MODE="${FORMDOCK_DEPLOY_TEST_MODE:-false}"
readonly TEST_FAIL_STEP="${FORMDOCK_DEPLOY_TEST_FAIL_STEP:-}"

is_sha "$COMMIT_SHA" || fail 'Commit SHA must be non-zero lowercase 40-hex.'
[ "$REGISTRY_OWNER" = xxh3898 ] || fail 'Registry owner is outside the FormDock allowlist.'
[[ "$WORKFLOW_RUN_ID" =~ ^[0-9]{1,20}$ ]] || fail 'Workflow run ID is invalid.'
is_digest "$CANDIDATE_API_DIGEST" || fail 'Candidate API digest is invalid.'
is_digest "$CANDIDATE_WEB_DIGEST" || fail 'Candidate Web digest is invalid.'
is_digest "$CANDIDATE_RUNTIME_DIGEST" || fail 'Candidate runtime-config digest is invalid.'
[[ "$DEPLOY_ACTOR" =~ ^[A-Za-z0-9][A-Za-z0-9-]{0,38}(\[bot\])?$ ]] \
  || fail 'Deployment actor is invalid.'

if [ "$TEST_MODE" = fixture ]; then
  APP_DIR="${FORMDOCK_DEPLOY_TEST_ROOT:-}"
  [[ "$APP_DIR" =~ ^/ ]] && [ "$APP_DIR" != / ] \
    || fail 'Fixture application root must be a safe absolute path.'
else
  [ "$TEST_MODE" = false ] || fail 'Unsupported deployment test mode.'
  APP_DIR="$CANONICAL_APP_DIR"
  [ -z "${FORMDOCK_DEPLOY_TEST_ROOT:-}" ] \
    || fail 'Production application root cannot be overridden.'
  [ -z "$TEST_FAIL_STEP" ] \
    || fail 'Production deployment failure injection is forbidden.'
fi
readonly APP_DIR

if [ "$TEST_MODE" = fixture ]; then
  case "$TEST_FAIL_STEP" in
    ''|previous_pointer|current_pointer|product_env|previous_state|deployment_state|runtime_state|pending_unlink|terminal_success) ;;
    *) fail 'Unsupported fixture deployment failure step.' ;;
  esac
fi

readonly RUNTIME_ROOT="$APP_DIR/runtime-config"
readonly RELEASES_DIR="$RUNTIME_ROOT/releases"
readonly PENDING_LINK="$RUNTIME_ROOT/pending"
readonly CURRENT_LINK="$RUNTIME_ROOT/current"
readonly PREVIOUS_LINK="$RUNTIME_ROOT/previous"
readonly RUNTIME_STATE_FILE="$RUNTIME_ROOT/state"
readonly PRODUCT_ENV="$APP_DIR/product.env"
readonly OPERATIONS_ENV="$APP_DIR/cd.env"
readonly STATE_FILE="$APP_DIR/deployment.state"
readonly PREVIOUS_STATE_FILE="$APP_DIR/deployment.previous.state"
readonly OPERATION_LOCK="$APP_DIR/.formdock-operation.lock"
readonly RUNTIME_DIR="${FORMDOCK_RUNTIME_DIR:-}"
readonly DOCKER_BIN="${FORMDOCK_DOCKER_BIN:-/usr/local/bin/docker}"
readonly CURL_BIN="${FORMDOCK_CURL_BIN:-/usr/bin/curl}"
readonly PYTHON_BIN="${FORMDOCK_PYTHON_BIN:-/usr/bin/python3}"

[ -x "$DOCKER_BIN" ] && [ -x "$CURL_BIN" ] && [ -x "$PYTHON_BIN" ] \
  || fail 'Required deployment executable is unavailable.'
require_private_file "$PRODUCT_ENV"
validate_operations "$OPERATIONS_ENV"
[ -d "$RUNTIME_DIR" ] && [ ! -L "$RUNTIME_DIR" ] \
  || fail 'Candidate runtime directory is missing or unsafe.'
[ "$RUNTIME_DIR" = "$RELEASES_DIR/${CANDIDATE_RUNTIME_DIGEST#sha256:}" ] \
  || fail 'Candidate runtime directory does not match its digest.'
[ -L "$PENDING_LINK" ] \
  && [ "$(readlink "$PENDING_LINK")" = "releases/${CANDIDATE_RUNTIME_DIGEST#sha256:}" ] \
  || fail 'Runtime pending pointer does not match the candidate.'

# shellcheck source=../../infra/delivery/common.sh
source "$RUNTIME_DIR/scripts/delivery-common.sh"
require_private_file "$STATE_FILE"
formdock_delivery_validate_state "$STATE_FILE" candidate
validate_runtime_state "$RUNTIME_STATE_FILE"

current_sha="$(value "$STATE_FILE" releaseGitSha)"
current_api_digest="$(value "$STATE_FILE" apiImageIdentity)"
current_web_digest="$(value "$STATE_FILE" webImageIdentity)"
current_api_image="$(value "$STATE_FILE" apiImageReference)"
current_web_image="$(value "$STATE_FILE" webImageReference)"
current_configuration_revision="$(value "$STATE_FILE" configurationRevision)"
current_runtime_digest="$(value "$RUNTIME_STATE_FILE" currentDigest)"
[ "$(value "$RUNTIME_STATE_FILE" currentSha)" = "$current_sha" ] \
  || fail 'Runtime-config state does not match accepted deployment SHA.'
[ "$current_api_image" = "$API_REPOSITORY@$current_api_digest" ] \
  && [ "$current_web_image" = "$WEB_REPOSITORY@$current_web_digest" ] \
  || fail 'Accepted deployment image references are outside the exact allowlist.'
[ "$(value "$PRODUCT_ENV" FORMDOCK_API_IMAGE)" = "$current_api_image" ] \
  && [ "$(value "$PRODUCT_ENV" FORMDOCK_WEB_IMAGE)" = "$current_web_image" ] \
  && [ "$(value "$PRODUCT_ENV" FORMDOCK_CONFIGURATION_REVISION)" = "$current_configuration_revision" ] \
  || fail 'Production environment identity does not match accepted deployment state.'
[ -L "$CURRENT_LINK" ] \
  && [ "$(readlink "$CURRENT_LINK")" = "releases/${current_runtime_digest#sha256:}" ] \
  || fail 'Runtime current pointer does not match deployment state.'
current_runtime_dir="$RELEASES_DIR/${current_runtime_digest#sha256:}"
[ -f "$current_runtime_dir/compose.yaml" ] && [ ! -L "$current_runtime_dir/compose.yaml" ] \
  || fail 'Accepted current runtime config is unavailable.'
[ "sha256:$(sha256_file "$current_runtime_dir/compose.yaml")" = "$(value "$STATE_FILE" composeRevision)" ] \
  || fail 'Accepted runtime Compose does not match deployment state.'

backup_root="$(value "$OPERATIONS_ENV" FORMDOCK_BACKUP_ROOT)"
backup_max_age="$(value "$OPERATIONS_ENV" FORMDOCK_BACKUP_MAX_AGE_SECONDS)"
public_origin="$(value "$OPERATIONS_ENV" FORMDOCK_PUBLIC_ORIGIN)"
homeops_reporter="$(value "$OPERATIONS_ENV" FORMDOCK_HOMEOPS_REPORTER)"
[[ "$backup_root" =~ ^/[A-Za-z0-9._/-]+$ ]] && [ "$backup_root" != / ] \
  || fail 'Backup root is invalid.'
[[ "$backup_max_age" =~ ^[0-9]+$ ]] && [ "$backup_max_age" -ge 3600 ] \
  && [ "$backup_max_age" -le 604800 ] \
  || fail 'Backup freshness policy is invalid.'
[ "$public_origin" = https://forms.chochiho.cloud ] \
  || fail 'Public smoke origin is outside the FormDock allowlist.'
[ -x "$homeops_reporter" ] && [ -f "$homeops_reporter" ] && [ ! -L "$homeops_reporter" ] \
  || fail 'Installed HomeOps reporter is unavailable or unsafe.'
[ -d "$backup_root" ] && [ ! -L "$backup_root" ] \
  || fail 'Backup root is unavailable or unsafe.'

umask 077
prepare_operation_lock "$OPERATION_LOCK" \
  || fail 'Operation lock must be a current-owner 0600 regular non-symlink file.'
exec 9>>"$OPERATION_LOCK"
validate_operation_lock "$OPERATION_LOCK" \
  || fail 'Operation lock identity changed during acquisition.'
if command -v lockf >/dev/null 2>&1; then
  if lockf -s -t 0 9; then
    :
  else
    lock_status="$?"
    [ "$lock_status" -eq 75 ] && exit 75
    fail 'Operation lock failed.'
  fi
elif command -v flock >/dev/null 2>&1; then
  flock -n 9 || exit 75
else
  fail 'Neither lockf nor flock is available.'
fi

latest_metadata=''
latest_created_at=''
for metadata in "$backup_root"/formdock-*.meta; do
  [ -f "$metadata" ] && [ ! -L "$metadata" ] || continue
  created_at="$(value "$metadata" createdAt)"
  if [ -z "$latest_created_at" ] || [[ "$created_at" > "$latest_created_at" ]]; then
    latest_created_at="$created_at"
    latest_metadata="$metadata"
  fi
done
[ -n "$latest_metadata" ] || fail 'No completed Production backup evidence is available.'
backup_id="$(basename "$latest_metadata" .meta)"
FORMDOCK_BACKUP_CREATED_AT="$latest_created_at" \
FORMDOCK_BACKUP_MAX_AGE_SECONDS="$backup_max_age" \
  "$PYTHON_BIN" - <<'PY' || fail 'Latest completed backup is stale or has an invalid timestamp.'
import datetime
import os

created = datetime.datetime.strptime(
    os.environ["FORMDOCK_BACKUP_CREATED_AT"], "%Y-%m-%dT%H:%M:%SZ"
).replace(tzinfo=datetime.timezone.utc)
age = (datetime.datetime.now(datetime.timezone.utc) - created).total_seconds()
limit = int(os.environ["FORMDOCK_BACKUP_MAX_AGE_SECONDS"])
raise SystemExit(0 if 0 <= age <= limit else 1)
PY
FORMDOCK_BACKUP_ROOT="$backup_root" \
FORMDOCK_BACKUP_ID="$backup_id" \
  "$RUNTIME_DIR/scripts/verify-backup.sh" >/dev/null \
  || fail 'Latest completed backup verification failed.'

service_id() {
  local service="$1"
  local ids
  ids="$($DOCKER_BIN ps -aq \
    --filter "label=com.docker.compose.project=$PROJECT" \
    --filter "label=com.docker.compose.service=$service")"
  if [ "$(printf '%s\n' "$ids" | awk 'NF { count += 1 } END { print count + 0 }')" != 1 ]; then
    printf 'FormDock recurring deployment failed: Production service identity is ambiguous: %s\n' "$service" >&2
    return 1
  fi
  printf '%s\n' "$ids"
}

postgres_id="$(service_id postgres)"
[ "$($DOCKER_BIN inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$postgres_id")" = healthy ] \
  || fail 'Production PostgreSQL is not healthy.'
postgres_volume="$($DOCKER_BIN inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres_id")"
[ -n "$postgres_volume" ] || fail 'Production PostgreSQL volume identity is missing.'
flyway_state="$({
  "$DOCKER_BIN" exec -i "$postgres_id" sh -ceu '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    exec psql --no-password --tuples-only --no-align \
      --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
  ' <<'SQL'
SELECT COALESCE(string_agg(version, ',' ORDER BY installed_rank) FILTER (WHERE success), '')
       || '|' || count(*) FILTER (WHERE success)
       || '|' || count(*) FILTER (WHERE NOT success)
FROM flyway_schema_history;
SQL
} | tr -d '\r')"
[ "$flyway_state" = "$EXPECTED_FLYWAY_STATE" ] \
  || fail 'Production Flyway is pending, failed, or outside the V1..V6 contract.'

candidate_api_image="$API_REPOSITORY@$CANDIDATE_API_DIGEST"
candidate_web_image="$WEB_REPOSITORY@$CANDIDATE_WEB_DIGEST"
current_api_image="$API_REPOSITORY@$current_api_digest"
current_web_image="$WEB_REPOSITORY@$current_web_digest"

compose_at() {
  local runtime_dir="$1"
  local api_image="$2"
  local web_image="$3"
  shift 3
  FORMDOCK_API_IMAGE="$api_image" \
  FORMDOCK_WEB_IMAGE="$web_image" \
    "$DOCKER_BIN" compose \
      --project-name "$PROJECT" \
      --env-file "$PRODUCT_ENV" \
      -f "$runtime_dir/compose.yaml" \
      "$@"
}

validate_image() {
  local reference="$1"
  [ "$($DOCKER_BIN image inspect --format '{{.Architecture}}/{{.Os}}' "$reference")" = arm64/linux ] \
    || return 1
  [ "$($DOCKER_BIN image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$reference")" = "$COMMIT_SHA" ]
}

http_200() {
  local protocol="$1"
  local url="$2"
  local status
  status="$($CURL_BIN \
    --silent --show-error --max-time 20 \
    --output /dev/null --write-out '%{http_code}' \
    --proto "=$protocol" \
    "$url")" || return 1
  [ "$status" = 200 ]
}

runtime_health() {
  local expected_volume="$1"
  local postgres api web port
  postgres="$(service_id postgres)"
  api="$(service_id api)"
  web="$(service_id web)"
  for id in "$postgres" "$api" "$web"; do
    [ "$($DOCKER_BIN inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")" = healthy ] \
      || return 1
  done
  [ "$($DOCKER_BIN inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$postgres")" = "$expected_volume" ] \
    || return 1
  port="$(value "$PRODUCT_ENV" FORMDOCK_WEB_PORT)"
  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  http_200 http "http://127.0.0.1:$port/health" \
    && http_200 http "http://127.0.0.1:$port/api/auth/csrf" \
    && http_200 https "$public_origin/health" \
    && http_200 https "$public_origin/api/auth/csrf"
}

report_event() {
  local status="$1"
  local finished_at="$2"
  FORMDOCK_HOMEOPS_REPORTER="$homeops_reporter" \
  FORMDOCK_API_IMAGE_DIGEST="$CANDIDATE_API_DIGEST" \
  FORMDOCK_WEB_IMAGE_DIGEST="$CANDIDATE_WEB_DIGEST" \
  FORMDOCK_DEPLOY_ACTOR="$DEPLOY_ACTOR" \
    "$RUNTIME_DIR/scripts/report-homeops-deployment.sh" \
      "$status" "$COMMIT_SHA" "$current_sha" "$WORKFLOW_RUN_ID" \
      "$started_at" "$finished_at"
}

rollback_current() {
  compose_at "$current_runtime_dir" "$current_api_image" "$current_web_image" \
    config --quiet \
    && compose_at "$current_runtime_dir" "$current_api_image" "$current_web_image" \
      pull api web \
    && compose_at "$current_runtime_dir" "$current_api_image" "$current_web_image" \
      up -d --wait --no-deps api web \
    && runtime_health "$postgres_volume"
}

deployment_event_started=false
deployment_event_final=false
transaction_active=false
transaction_completed=false
transaction_failure_step=''
rollback_snapshot_dir=''
snapshot_previous_state_present=false
snapshot_previous_link_present=false
snapshot_current_link_target=''
snapshot_previous_link_target=''
snapshot_pending_link_target=''
snapshot_compose_sha=''
state_temp=''
previous_state_temp=''
runtime_state_temp=''
env_temp=''
current_temp=''
previous_temp=''

verify_database_authority() {
  local id volume state
  id="$(service_id postgres)" || return 1
  [ "$("$DOCKER_BIN" inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id")" = healthy ] \
    || return 1
  volume="$("$DOCKER_BIN" inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "$id")" \
    || return 1
  [ "$volume" = "$postgres_volume" ] || return 1
  state="$({
    "$DOCKER_BIN" exec -i "$id" sh -ceu '
      export PGPASSWORD="$POSTGRES_PASSWORD"
      exec psql --no-password --tuples-only --no-align \
        --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
    ' <<'SQL'
SELECT COALESCE(string_agg(version, ',' ORDER BY installed_rank) FILTER (WHERE success), '')
       || '|' || count(*) FILTER (WHERE success)
       || '|' || count(*) FILTER (WHERE NOT success)
FROM flyway_schema_history;
SQL
  } | tr -d '\r')" || return 1
  [ "$state" = "$EXPECTED_FLYWAY_STATE" ]
}

cleanup_transaction() {
  [ -n "$state_temp" ] && [ -e "$state_temp" ] && unlink "$state_temp" 2>/dev/null || true
  [ -n "$previous_state_temp" ] && [ -e "$previous_state_temp" ] && unlink "$previous_state_temp" 2>/dev/null || true
  [ -n "$runtime_state_temp" ] && [ -e "$runtime_state_temp" ] && unlink "$runtime_state_temp" 2>/dev/null || true
  [ -n "$env_temp" ] && [ -e "$env_temp" ] && unlink "$env_temp" 2>/dev/null || true
  if [ -n "$current_temp" ] && { [ -e "$current_temp" ] || [ -L "$current_temp" ]; }; then
    unlink "$current_temp" 2>/dev/null || true
  fi
  if [ -n "$previous_temp" ] && { [ -e "$previous_temp" ] || [ -L "$previous_temp" ]; }; then
    unlink "$previous_temp" 2>/dev/null || true
  fi
}

cleanup_snapshot() {
  [ -n "$rollback_snapshot_dir" ] || return 0
  for file in product.env deployment.state deployment.previous.state runtime.state; do
    [ -e "$rollback_snapshot_dir/$file" ] && unlink "$rollback_snapshot_dir/$file" 2>/dev/null || true
  done
  rmdir "$rollback_snapshot_dir" 2>/dev/null || true
  rollback_snapshot_dir=''
}

prepare_accepted_snapshot() {
  rollback_snapshot_dir="$(mktemp -d "$APP_DIR/.accepted-rollback.XXXXXX")" || return 1
  chmod 700 "$rollback_snapshot_dir" || return 1
  cp "$PRODUCT_ENV" "$rollback_snapshot_dir/product.env" || return 1
  cp "$STATE_FILE" "$rollback_snapshot_dir/deployment.state" || return 1
  cp "$RUNTIME_STATE_FILE" "$rollback_snapshot_dir/runtime.state" || return 1
  chmod 600 "$rollback_snapshot_dir/product.env" \
    "$rollback_snapshot_dir/deployment.state" \
    "$rollback_snapshot_dir/runtime.state" || return 1

  if [ -e "$PREVIOUS_STATE_FILE" ] || [ -L "$PREVIOUS_STATE_FILE" ]; then
    require_private_file "$PREVIOUS_STATE_FILE"
    formdock_delivery_validate_state "$PREVIOUS_STATE_FILE" previous
    cp "$PREVIOUS_STATE_FILE" "$rollback_snapshot_dir/deployment.previous.state" || return 1
    chmod 600 "$rollback_snapshot_dir/deployment.previous.state" || return 1
    snapshot_previous_state_present=true
  fi

  snapshot_current_link_target="$(readlink "$CURRENT_LINK")" || return 1
  if [ -e "$PREVIOUS_LINK" ] || [ -L "$PREVIOUS_LINK" ]; then
    [ -L "$PREVIOUS_LINK" ] || return 1
    snapshot_previous_link_target="$(readlink "$PREVIOUS_LINK")" || return 1
    snapshot_previous_link_present=true
  fi
  snapshot_pending_link_target="$(readlink "$PENDING_LINK")" || return 1
  snapshot_compose_sha="$(sha256_file "$current_runtime_dir/compose.yaml")" || return 1
}

restore_private_snapshot() {
  local snapshot="$1"
  local destination="$2"
  local temporary
  temporary="$(mktemp "$(dirname "$destination")/.restore-$(basename "$destination").XXXXXX")" \
    || return 1
  cp "$snapshot" "$temporary" \
    && chmod 600 "$temporary" \
    && mv -f -- "$temporary" "$destination"
}

restore_symlink_snapshot() {
  local present="$1"
  local target="$2"
  local destination="$3"
  local label="$4"
  local temporary="$RUNTIME_ROOT/.restore-$label.$$"

  if [ "$present" = true ]; then
    { [ ! -e "$temporary" ] && [ ! -L "$temporary" ]; } || return 1
    ln -s "$target" "$temporary" && replace_symlink "$temporary" "$destination"
    return
  fi

  if [ -e "$destination" ] || [ -L "$destination" ]; then
    [ -L "$destination" ] && unlink "$destination"
  fi
}

restore_optional_previous_state() {
  if [ "$snapshot_previous_state_present" = true ]; then
    restore_private_snapshot \
      "$rollback_snapshot_dir/deployment.previous.state" "$PREVIOUS_STATE_FILE"
    return
  fi
  if [ -e "$PREVIOUS_STATE_FILE" ] || [ -L "$PREVIOUS_STATE_FILE" ]; then
    [ -f "$PREVIOUS_STATE_FILE" ] && [ ! -L "$PREVIOUS_STATE_FILE" ] \
      && unlink "$PREVIOUS_STATE_FILE"
  fi
}

verify_restored_snapshot() {
  cmp -s "$rollback_snapshot_dir/product.env" "$PRODUCT_ENV" \
    && cmp -s "$rollback_snapshot_dir/deployment.state" "$STATE_FILE" \
    && cmp -s "$rollback_snapshot_dir/runtime.state" "$RUNTIME_STATE_FILE" \
    && [ -L "$CURRENT_LINK" ] \
    && [ "$(readlink "$CURRENT_LINK")" = "$snapshot_current_link_target" ] \
    && [ -L "$PENDING_LINK" ] \
    && [ "$(readlink "$PENDING_LINK")" = "$snapshot_pending_link_target" ] \
    && [ -d "$current_runtime_dir" ] \
    && [ ! -L "$current_runtime_dir" ] \
    && [ "$(sha256_file "$current_runtime_dir/compose.yaml")" = "$snapshot_compose_sha" ] \
    || return 1

  if [ "$snapshot_previous_state_present" = true ]; then
    cmp -s "$rollback_snapshot_dir/deployment.previous.state" "$PREVIOUS_STATE_FILE" \
      || return 1
  else
    [ ! -e "$PREVIOUS_STATE_FILE" ] && [ ! -L "$PREVIOUS_STATE_FILE" ] \
      || return 1
  fi
  if [ "$snapshot_previous_link_present" = true ]; then
    [ -L "$PREVIOUS_LINK" ] \
      && [ "$(readlink "$PREVIOUS_LINK")" = "$snapshot_previous_link_target" ] \
      || return 1
  else
    [ ! -e "$PREVIOUS_LINK" ] && [ ! -L "$PREVIOUS_LINK" ] \
      || return 1
  fi
}

compensate_transaction() {
  local result=0
  restore_private_snapshot "$rollback_snapshot_dir/product.env" "$PRODUCT_ENV" || result=1
  restore_private_snapshot "$rollback_snapshot_dir/deployment.state" "$STATE_FILE" || result=1
  restore_private_snapshot "$rollback_snapshot_dir/runtime.state" "$RUNTIME_STATE_FILE" || result=1
  restore_optional_previous_state || result=1
  restore_symlink_snapshot true "$snapshot_current_link_target" "$CURRENT_LINK" current || result=1
  restore_symlink_snapshot "$snapshot_previous_link_present" "$snapshot_previous_link_target" "$PREVIOUS_LINK" previous || result=1
  restore_symlink_snapshot true "$snapshot_pending_link_target" "$PENDING_LINK" pending || result=1
  rollback_current || result=1
  verify_restored_snapshot || result=1
  verify_database_authority || result=1
  return "$result"
}

on_exit() {
  local status="$?"
  local finished_at
  trap - EXIT INT TERM
  set +e
  cleanup_transaction
  if [ "$status" -ne 0 ] && [ "$transaction_active" = true ] && [ "$transaction_completed" = false ]; then
    finished_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    if compensate_transaction; then
      if report_event ROLLED_BACK "$finished_at"; then
        deployment_event_final=true
      fi
    else
      if report_event FAILED "$finished_at"; then
        deployment_event_final=true
      fi
    fi
  fi
  cleanup_snapshot
  if [ "$status" -ne 0 ] \
    && [ "$deployment_event_started" = true ] \
    && [ "$deployment_event_final" = false ]; then
    if report_event FAILED "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"; then
      deployment_event_final=true
    fi
  fi
  exit "$status"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

run_transaction_step() {
  local step="$1"
  shift
  if [ "$TEST_FAIL_STEP" = "$step" ]; then
    transaction_failure_step="$step"
    return 1
  fi
  "$@" || {
    transaction_failure_step="$step"
    return 1
  }
}

started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
report_event REQUESTED ''
deployment_event_started=true

if [ "$current_sha" = "$COMMIT_SHA" ] \
  && [ "$current_api_digest" = "$CANDIDATE_API_DIGEST" ] \
  && [ "$current_web_digest" = "$CANDIDATE_WEB_DIGEST" ] \
  && [ "$current_runtime_digest" = "$CANDIDATE_RUNTIME_DIGEST" ]; then
  report_event RUNNING ''
  runtime_health "$postgres_volume" || fail 'Accepted deployment replay health failed.'
  unlink "$PENDING_LINK"
  report_event SUCCESS "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  deployment_event_final=true
  trap - EXIT INT TERM
  printf 'FORMDOCK_DEPLOY_RESULT=PASS_REPLAY\n'
  exit 0
fi

compose_at "$RUNTIME_DIR" "$candidate_api_image" "$candidate_web_image" config --quiet
compose_at "$RUNTIME_DIR" "$candidate_api_image" "$candidate_web_image" pull api web
validate_image "$candidate_api_image" && validate_image "$candidate_web_image" \
  || fail 'Candidate image platform or revision is invalid.'
prepare_accepted_snapshot || fail 'Accepted deployment rollback authority could not be captured.'
report_event RUNNING ''
transaction_active=true

if ! compose_at "$RUNTIME_DIR" "$candidate_api_image" "$candidate_web_image" \
  up -d --wait --no-deps api web \
  || ! runtime_health "$postgres_volume"; then
  fail 'Candidate verification failed; accepted transaction compensation required.'
fi

state_temp="$(mktemp "$APP_DIR/.deployment-state.XXXXXX")"
previous_state_temp="$(mktemp "$APP_DIR/.deployment-previous-state.XXXXXX")"
runtime_state_temp="$(mktemp "$RUNTIME_ROOT/.runtime-state.XXXXXX")"
env_temp="$(mktemp "$APP_DIR/.product-env.XXXXXX")"
current_temp="$RUNTIME_ROOT/.current.$$"
previous_temp="$RUNTIME_ROOT/.previous.$$"

recorded_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
awk -F= '
  $1 == "stateRole" { print "stateRole=previous"; role_count += 1; next }
  { print }
  END { if (role_count != 1) exit 1 }
' "$STATE_FILE" > "$previous_state_temp" \
  || fail 'Accepted deployment state could not be converted to previous state.'
chmod 600 "$previous_state_temp"
formdock_delivery_validate_state "$previous_state_temp" previous
previous_state_digest="sha256:$(sha256_file "$previous_state_temp")"
candidate_compose_revision="sha256:$(sha256_file "$RUNTIME_DIR/compose.yaml")"
{
  printf 'formatVersion=1\n'
  printf 'stateRole=candidate\n'
  printf 'releaseGitSha=%s\n' "$COMMIT_SHA"
  printf 'apiImageReference=%s\n' "$candidate_api_image"
  printf 'apiImageIdentity=%s\n' "$CANDIDATE_API_DIGEST"
  printf 'webImageReference=%s\n' "$candidate_web_image"
  printf 'webImageIdentity=%s\n' "$CANDIDATE_WEB_DIGEST"
  printf 'composeRevision=%s\n' "$candidate_compose_revision"
  printf 'configurationRevision=%s\n' "$current_configuration_revision"
  printf 'recordedAt=%s\n' "$recorded_at"
  printf 'previousStateSha256=%s\n' "$previous_state_digest"
} > "$state_temp"
chmod 600 "$state_temp"
formdock_delivery_validate_state "$state_temp" candidate

{
  printf 'formatVersion=1\n'
  printf 'currentSha=%s\n' "$COMMIT_SHA"
  printf 'currentDigest=%s\n' "$CANDIDATE_RUNTIME_DIGEST"
  printf 'previousSha=%s\n' "$current_sha"
  printf 'previousDigest=%s\n' "$current_runtime_digest"
  printf 'recordedAt=%s\n' "$recorded_at"
} > "$runtime_state_temp"
chmod 600 "$runtime_state_temp"
validate_runtime_state "$runtime_state_temp"

awk -F= \
  -v api="$candidate_api_image" \
  -v web="$candidate_web_image" '
    $1 == "FORMDOCK_API_IMAGE" { print "FORMDOCK_API_IMAGE=" api; api_count += 1; next }
    $1 == "FORMDOCK_WEB_IMAGE" { print "FORMDOCK_WEB_IMAGE=" web; web_count += 1; next }
    $1 == "FORMDOCK_CONFIGURATION_REVISION" { print; configuration_count += 1; next }
    { print }
    END { if (api_count != 1 || web_count != 1 || configuration_count != 1) exit 1 }
  ' "$PRODUCT_ENV" > "$env_temp" \
  || fail 'Production environment does not contain the required identity fields.'
chmod 600 "$env_temp"

ln -s "releases/${current_runtime_digest#sha256:}" "$previous_temp"
ln -s "releases/${CANDIDATE_RUNTIME_DIGEST#sha256:}" "$current_temp"
run_transaction_step previous_pointer replace_symlink "$previous_temp" "$PREVIOUS_LINK" \
  && run_transaction_step current_pointer replace_symlink "$current_temp" "$CURRENT_LINK" \
  && run_transaction_step product_env mv -f -- "$env_temp" "$PRODUCT_ENV" \
  && run_transaction_step previous_state mv -f -- "$previous_state_temp" "$PREVIOUS_STATE_FILE" \
  && run_transaction_step deployment_state mv -f -- "$state_temp" "$STATE_FILE" \
  && run_transaction_step runtime_state mv -f -- "$runtime_state_temp" "$RUNTIME_STATE_FILE" \
  && run_transaction_step pending_unlink unlink "$PENDING_LINK" \
  || fail "Deployment state commit failed at ${transaction_failure_step:-unknown}; accepted transaction compensation required."

run_transaction_step terminal_success report_event SUCCESS "$recorded_at" \
  || fail 'Terminal SUCCESS delivery failed; accepted transaction compensation required.'
deployment_event_final=true
transaction_completed=true
transaction_active=false
cleanup_transaction
cleanup_snapshot
trap - EXIT INT TERM
printf 'FORMDOCK_DEPLOY_RESULT=PASS\n'
printf 'FORMDOCK_DEPLOY_RELEASE_SHA=%s\n' "$COMMIT_SHA"
printf 'FORMDOCK_DEPLOY_DATABASE_VOLUME=PRESERVED\n'
printf 'FORMDOCK_DEPLOY_FLYWAY=1,2,3,4,5,6\n'
