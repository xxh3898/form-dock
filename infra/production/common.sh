#!/usr/bin/env bash

PRODUCTION_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd "$PRODUCTION_DIR/../.." && pwd -P)"
# shellcheck source=../delivery/common.sh
source "$PRODUCTION_DIR/../delivery/common.sh"

FORMDOCK_PRODUCTION_PROJECT='form-dock'
FORMDOCK_PRODUCTION_WEB_PORT='18082'
FORMDOCK_PRODUCTION_EDGE_NETWORK='edge'
FORMDOCK_PRODUCTION_RELEASE_SHA='1648047645720e67d5e928345c875dc53a93ff0e'
FORMDOCK_PRODUCTION_API_IMAGE='ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b'
FORMDOCK_PRODUCTION_WEB_IMAGE='ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551'
FORMDOCK_PRODUCTION_POSTGRES_IMAGE='postgres:18.6-alpine3.23'

formdock_production_die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

formdock_production_require_env() {
  local name="$1"
  [ -n "${!name:-}" ] \
    || formdock_production_die "Required environment variable is missing: $name"
}

formdock_production_require_command() {
  command -v "$1" >/dev/null 2>&1 \
    || formdock_production_die "Required command is unavailable: $1"
}

formdock_production_require_safe_absolute_path() {
  local target="$1"

  [[ "$target" =~ ^/[A-Za-z0-9._/-]+$ ]] \
    || formdock_production_die 'Production paths must be absolute and contain only safe characters.'
  case "/$target/" in
    *'/../'*|*'/./'*) formdock_production_die 'Production paths must not contain dot segments.' ;;
  esac
  [ "$target" != / ] \
    || formdock_production_die 'Filesystem root cannot be used as a FormDock Production path.'
}

formdock_production_owner_uid() {
  local target="$1"
  local owner

  if owner="$(stat -f '%u' "$target" 2>/dev/null)"; then
    printf '%s\n' "$owner"
    return
  fi
  if owner="$(stat -c '%u' "$target" 2>/dev/null)"; then
    printf '%s\n' "$owner"
    return
  fi
  formdock_production_die 'Unable to inspect Production path ownership.'
}

formdock_production_require_owner_mode() {
  local target="$1"
  local expected_mode="$2"

  [ "$(formdock_production_owner_uid "$target")" = "$(id -u)" ] \
    || formdock_production_die 'Production private path must be owned by the current operator.'
  [ "$(formdock_delivery_file_mode "$target")" = "$expected_mode" ] \
    || formdock_production_die "Production private path must use mode $expected_mode."
}

formdock_production_canonical_private_file() {
  local target="$1"
  local parent basename canonical

  formdock_production_require_safe_absolute_path "$target"
  [ -f "$target" ] && [ ! -L "$target" ] \
    || formdock_production_die 'Trusted bootstrap input must be a regular non-symlink file.'
  parent="$(cd -- "$(dirname -- "$target")" && pwd -P)"
  basename="$(basename -- "$target")"
  canonical="$parent/$basename"
  formdock_production_require_owner_mode "$canonical" 600
  printf '%s\n' "$canonical"
}

formdock_production_validate_bootstrap_input() {
  local input_file="$1"
  local canonical enabled email password display_name

  canonical="$(formdock_production_canonical_private_file "$input_file")"
  awk -F= '
    BEGIN {
      allowed["FORMDOCK_BOOTSTRAP_ENABLED"] = 1
      allowed["FORMDOCK_BOOTSTRAP_EMAIL"] = 1
      allowed["FORMDOCK_BOOTSTRAP_PASSWORD"] = 1
      allowed["FORMDOCK_BOOTSTRAP_DISPLAY_NAME"] = 1
    }
    {
      key = $1
      if (!(key in allowed) || seen[key]++) {
        exit 1
      }
      count += 1
    }
    END {
      if (count != 4) {
        exit 1
      }
    }
  ' "$canonical" \
    || formdock_production_die 'Trusted bootstrap input contains missing, duplicate, or unknown keys.'

  enabled="$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_ENABLED)"
  email="$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_EMAIL)"
  password="$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_PASSWORD)"
  display_name="$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_DISPLAY_NAME)"
  [ "$enabled" = true ] \
    || formdock_production_die 'Trusted bootstrap input must explicitly enable initial provisioning.'

  FORMDOCK_VALIDATE_EMAIL="$email" \
  FORMDOCK_VALIDATE_PASSWORD="$password" \
  FORMDOCK_VALIDATE_DISPLAY_NAME="$display_name" \
    python3 - <<'PY' \
    || formdock_production_die 'Trusted bootstrap input violates the Creator provisioning contract.'
import os

email = os.environ["FORMDOCK_VALIDATE_EMAIL"].strip().lower()
password = os.environ["FORMDOCK_VALIDATE_PASSWORD"]
display_name = os.environ["FORMDOCK_VALIDATE_DISPLAY_NAME"].strip()

valid = (
    1 <= len(email) <= 320
    and 1 <= len(display_name) <= 100
    and len(password) >= 15
    and len(password.encode("utf-8")) <= 72
)
raise SystemExit(0 if valid else 1)
PY

  printf '%s\n' "$canonical"
}

formdock_production_write_configuration_identity() {
  local target="$1"

  {
    printf 'scope=production-first-activation\n'
    printf 'releaseGitSha=%s\n' "$FORMDOCK_PRODUCTION_RELEASE_SHA"
    printf 'project=%s\n' "$FORMDOCK_PRODUCTION_PROJECT"
    printf 'webPort=%s\n' "$FORMDOCK_PRODUCTION_WEB_PORT"
    printf 'edgeNetwork=%s\n' "$FORMDOCK_PRODUCTION_EDGE_NETWORK"
    printf 'databaseName=formdock\n'
    printf 'databaseUsername=formdock\n'
    printf 'logMaxSize=10m\n'
    printf 'logMaxFile=5\n'
    printf 'bootstrapFinal=false-empty\n'
  } > "$target"
  chmod 600 "$target"
}

formdock_production_write_runtime_env() {
  local target="$1"
  local database_password="$2"
  local configuration_revision="$3"

  [[ "$database_password" =~ ^[0-9a-f]{64}$ ]] \
    || formdock_production_die 'Generated database credential must contain 64 lowercase hexadecimal characters.'
  [[ "$configuration_revision" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_production_die 'Configuration revision must be a non-secret sha256 identity.'

  {
    printf 'FORMDOCK_API_IMAGE=%s\n' "$FORMDOCK_PRODUCTION_API_IMAGE"
    printf 'FORMDOCK_WEB_IMAGE=%s\n' "$FORMDOCK_PRODUCTION_WEB_IMAGE"
    printf 'FORMDOCK_CONFIGURATION_REVISION=%s\n' "$configuration_revision"
    printf 'FORMDOCK_WEB_PORT=%s\n' "$FORMDOCK_PRODUCTION_WEB_PORT"
    printf 'FORMDOCK_EDGE_NETWORK=%s\n' "$FORMDOCK_PRODUCTION_EDGE_NETWORK"
    printf 'FORMDOCK_LOG_MAX_SIZE=10m\n'
    printf 'FORMDOCK_LOG_MAX_FILE=5\n'
    printf 'FORMDOCK_DB_NAME=formdock\n'
    printf 'FORMDOCK_DB_USERNAME=formdock\n'
    printf 'FORMDOCK_DB_PASSWORD=%s\n' "$database_password"
    printf 'FORMDOCK_BOOTSTRAP_ENABLED=false\n'
    printf 'FORMDOCK_BOOTSTRAP_EMAIL=\n'
    printf 'FORMDOCK_BOOTSTRAP_PASSWORD=\n'
    printf 'FORMDOCK_BOOTSTRAP_DISPLAY_NAME=\n'
  } > "$target"
  chmod 600 "$target"
}

formdock_production_validate_runtime_env() {
  local env_file="$1"
  local password

  [ -f "$env_file" ] && [ ! -L "$env_file" ] \
    || formdock_production_die 'Production runtime environment must be a regular non-symlink file.'
  formdock_production_require_owner_mode "$env_file" 600
  awk -F= '
    BEGIN {
      allowed["FORMDOCK_API_IMAGE"] = 1
      allowed["FORMDOCK_WEB_IMAGE"] = 1
      allowed["FORMDOCK_CONFIGURATION_REVISION"] = 1
      allowed["FORMDOCK_WEB_PORT"] = 1
      allowed["FORMDOCK_EDGE_NETWORK"] = 1
      allowed["FORMDOCK_LOG_MAX_SIZE"] = 1
      allowed["FORMDOCK_LOG_MAX_FILE"] = 1
      allowed["FORMDOCK_DB_NAME"] = 1
      allowed["FORMDOCK_DB_USERNAME"] = 1
      allowed["FORMDOCK_DB_PASSWORD"] = 1
      allowed["FORMDOCK_BOOTSTRAP_ENABLED"] = 1
      allowed["FORMDOCK_BOOTSTRAP_EMAIL"] = 1
      allowed["FORMDOCK_BOOTSTRAP_PASSWORD"] = 1
      allowed["FORMDOCK_BOOTSTRAP_DISPLAY_NAME"] = 1
    }
    {
      if (!($1 in allowed) || seen[$1]++) {
        exit 1
      }
      count += 1
    }
    END {
      if (count != 14) {
        exit 1
      }
    }
  ' "$env_file" \
    || formdock_production_die 'Production runtime environment contains missing, duplicate, or unknown keys.'

  [ "$(formdock_delivery_value "$env_file" FORMDOCK_API_IMAGE)" = "$FORMDOCK_PRODUCTION_API_IMAGE" ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_WEB_IMAGE)" = "$FORMDOCK_PRODUCTION_WEB_IMAGE" ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_WEB_PORT)" = "$FORMDOCK_PRODUCTION_WEB_PORT" ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_EDGE_NETWORK)" = "$FORMDOCK_PRODUCTION_EDGE_NETWORK" ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_LOG_MAX_SIZE)" = 10m ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_LOG_MAX_FILE)" = 5 ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_DB_NAME)" = formdock ] \
    && [ "$(formdock_delivery_value "$env_file" FORMDOCK_DB_USERNAME)" = formdock ] \
    || formdock_production_die 'Production runtime environment does not match the canonical target.'

  [[ "$(formdock_delivery_value "$env_file" FORMDOCK_CONFIGURATION_REVISION)" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_production_die 'Production configuration revision is invalid.'
  password="$(formdock_delivery_value "$env_file" FORMDOCK_DB_PASSWORD)"
  [[ "$password" =~ ^[0-9a-f]{64}$ ]] \
    || formdock_production_die 'Production database credential does not match the generated format.'
  [ "$(formdock_delivery_value "$env_file" FORMDOCK_BOOTSTRAP_ENABLED)" = false ] \
    && [ -z "$(formdock_delivery_value "$env_file" FORMDOCK_BOOTSTRAP_EMAIL)" ] \
    && [ -z "$(formdock_delivery_value "$env_file" FORMDOCK_BOOTSTRAP_PASSWORD)" ] \
    && [ -z "$(formdock_delivery_value "$env_file" FORMDOCK_BOOTSTRAP_DISPLAY_NAME)" ] \
    || formdock_production_die 'Final Production runtime environment must not retain bootstrap credentials.'
}

formdock_production_write_deployment_state() {
  local target="$1"
  local compose_revision="$2"
  local configuration_revision="$3"
  local recorded_at="$4"

  {
    printf 'formatVersion=1\n'
    printf 'stateRole=candidate\n'
    printf 'releaseGitSha=%s\n' "$FORMDOCK_PRODUCTION_RELEASE_SHA"
    printf 'apiImageReference=%s\n' "$FORMDOCK_PRODUCTION_API_IMAGE"
    printf 'apiImageIdentity=%s\n' "${FORMDOCK_PRODUCTION_API_IMAGE##*@}"
    printf 'webImageReference=%s\n' "$FORMDOCK_PRODUCTION_WEB_IMAGE"
    printf 'webImageIdentity=%s\n' "${FORMDOCK_PRODUCTION_WEB_IMAGE##*@}"
    printf 'composeRevision=%s\n' "$compose_revision"
    printf 'configurationRevision=%s\n' "$configuration_revision"
    printf 'recordedAt=%s\n' "$recorded_at"
    printf 'previousStateSha256=NONE\n'
  } > "$target"
  chmod 600 "$target"
  formdock_delivery_validate_state "$target" candidate
}

formdock_production_write_lock_metadata() {
  local target="$1"
  local operation_id="$2"
  local started_at="$3"
  local candidate_state_sha="$4"

  if [ "$candidate_state_sha" != PENDING ]; then
    [[ "$candidate_state_sha" =~ ^sha256:[0-9a-f]{64}$ ]] \
      || formdock_production_die 'Lock candidate state identity must be PENDING or sha256.'
  fi
  {
    printf 'formatVersion=1\n'
    printf 'operationId=%s\n' "$operation_id"
    printf 'processId=%s\n' "$$"
    printf 'startedAt=%s\n' "$started_at"
    printf 'releaseGitSha=%s\n' "$FORMDOCK_PRODUCTION_RELEASE_SHA"
    printf 'candidateStateSha256=%s\n' "$candidate_state_sha"
  } > "$target"
  chmod 600 "$target"
}
