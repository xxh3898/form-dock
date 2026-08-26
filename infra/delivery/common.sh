#!/usr/bin/env bash

formdock_delivery_die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

formdock_delivery_require_command() {
  command -v "$1" >/dev/null 2>&1 \
    || formdock_delivery_die "Required command is unavailable: $1"
}

formdock_delivery_require_env() {
  local name="$1"
  [ -n "${!name:-}" ] \
    || formdock_delivery_die "Required environment variable is missing: $name"
}

formdock_delivery_sha256() {
  local target="$1"

  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$target" | awk '{print $1}'
    return
  fi

  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$target" | awk '{print $1}'
    return
  fi

  formdock_delivery_die 'Neither sha256sum nor shasum is available.'
}

formdock_delivery_file_mode() {
  local target="$1"
  local mode

  if mode="$(stat -f '%Lp' "$target" 2>/dev/null)"; then
    printf '%s\n' "$mode"
    return
  fi

  if mode="$(stat -c '%a' "$target" 2>/dev/null)"; then
    printf '%s\n' "$mode"
    return
  fi

  formdock_delivery_die 'Unable to inspect file permissions.'
}

formdock_delivery_canonical_private_file() {
  local target="$1"
  local parent basename canonical mode

  case "$target" in
    /*) ;;
    *) formdock_delivery_die 'Isolated environment file must use an absolute path.' ;;
  esac

  [ -f "$target" ] || formdock_delivery_die 'Isolated environment file does not exist.'
  [ ! -L "$target" ] || formdock_delivery_die 'Isolated environment file must not be a symbolic link.'
  parent="$(cd -- "$(dirname -- "$target")" && pwd -P)"
  basename="$(basename -- "$target")"
  canonical="$parent/$basename"
  mode="$(formdock_delivery_file_mode "$canonical")"
  case "$mode" in
    *00) ;;
    *) formdock_delivery_die 'Isolated environment file group/other permissions must be zero.' ;;
  esac
  printf '%s\n' "$canonical"
}

formdock_delivery_value() {
  local file="$1"
  local key="$2"

  awk -v wanted="$key" '
    index($0, wanted "=") == 1 {
      count += 1
      value = substr($0, length(wanted) + 2)
    }
    END {
      if (count != 1) {
        exit 1
      }
      print value
    }
  ' "$file" || formdock_delivery_die "Field must occur exactly once: $key"
}

formdock_delivery_validate_image_reference() {
  local reference="$1"
  local lowercase

  [[ "$reference" =~ ^[a-z0-9][A-Za-z0-9._/:@-]{0,255}$ ]] \
    || formdock_delivery_die 'Image reference contains an unsupported character or prefix.'
  lowercase="$(printf '%s' "$reference" | tr '[:upper:]' '[:lower:]')"
  case "$lowercase" in
    latest|*:latest) formdock_delivery_die 'latest-only image authority is not allowed.' ;;
  esac
  if [[ "$reference" =~ @sha256:[0-9a-f]{64}$ ]]; then
    return
  fi
  [[ "$reference" =~ :[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] \
    || formdock_delivery_die 'Image reference must contain an explicit tag or sha256 digest.'
}

formdock_delivery_validate_utc_timestamp() {
  local value="$1"
  local normalized

  [[ "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
    || formdock_delivery_die 'recordedAt must be UTC with second precision.'
  if normalized="$(date -u -d "$value" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null)"; then
    [ "$normalized" = "$value" ] \
      || formdock_delivery_die 'recordedAt is not a canonical UTC timestamp.'
    return
  fi
  if normalized="$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$value" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null)"; then
    [ "$normalized" = "$value" ] \
      || formdock_delivery_die 'recordedAt is not a canonical UTC timestamp.'
    return
  fi
  formdock_delivery_die 'recordedAt is not a valid UTC timestamp.'
}

formdock_delivery_validate_state() {
  local state_file="$1"
  local expected_role="${2:-}"
  local role release_sha api_reference api_identity web_reference web_identity
  local compose_revision configuration_revision recorded_at previous_state_sha

  [ -f "$state_file" ] || formdock_delivery_die 'Deployment state file does not exist.'
  [ ! -L "$state_file" ] || formdock_delivery_die 'Deployment state file must not be a symbolic link.'

  awk -F= '
    BEGIN {
      allowed["formatVersion"] = 1
      allowed["stateRole"] = 1
      allowed["releaseGitSha"] = 1
      allowed["apiImageReference"] = 1
      allowed["apiImageIdentity"] = 1
      allowed["webImageReference"] = 1
      allowed["webImageIdentity"] = 1
      allowed["composeRevision"] = 1
      allowed["configurationRevision"] = 1
      allowed["recordedAt"] = 1
      allowed["previousStateSha256"] = 1
    }
    {
      if (!($1 in allowed) || seen[$1]++) {
        exit 1
      }
      count += 1
    }
    END {
      if (count != 11) {
        exit 1
      }
    }
  ' "$state_file" \
    || formdock_delivery_die 'Deployment state contains missing, duplicate, or unknown fields.'

  [ "$(formdock_delivery_value "$state_file" formatVersion)" = 1 ] \
    || formdock_delivery_die 'Unsupported deployment state format version.'

  role="$(formdock_delivery_value "$state_file" stateRole)"
  case "$role" in
    candidate|previous) ;;
    *) formdock_delivery_die 'Deployment state role must be candidate or previous.' ;;
  esac
  if [ -n "$expected_role" ] && [ "$role" != "$expected_role" ]; then
    formdock_delivery_die 'Deployment state role does not match the requested operation.'
  fi

  release_sha="$(formdock_delivery_value "$state_file" releaseGitSha)"
  [[ "$release_sha" =~ ^[0-9a-f]{40}$ ]] \
    || formdock_delivery_die 'Release Git SHA must contain exactly 40 lowercase hexadecimal characters.'

  api_reference="$(formdock_delivery_value "$state_file" apiImageReference)"
  web_reference="$(formdock_delivery_value "$state_file" webImageReference)"
  formdock_delivery_validate_image_reference "$api_reference"
  formdock_delivery_validate_image_reference "$web_reference"

  api_identity="$(formdock_delivery_value "$state_file" apiImageIdentity)"
  web_identity="$(formdock_delivery_value "$state_file" webImageIdentity)"
  [[ "$api_identity" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_delivery_die 'API image identity must be sha256:<64 lowercase hexadecimal characters>.'
  [[ "$web_identity" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_delivery_die 'Web image identity must be sha256:<64 lowercase hexadecimal characters>.'

  compose_revision="$(formdock_delivery_value "$state_file" composeRevision)"
  [[ "$compose_revision" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_delivery_die 'Compose revision must be sha256:<64 lowercase hexadecimal characters>.'

  configuration_revision="$(formdock_delivery_value "$state_file" configurationRevision)"
  [[ "$configuration_revision" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_delivery_die 'Configuration revision must be sha256:<64 lowercase hexadecimal characters>.'

  recorded_at="$(formdock_delivery_value "$state_file" recordedAt)"
  formdock_delivery_validate_utc_timestamp "$recorded_at"

  previous_state_sha="$(formdock_delivery_value "$state_file" previousStateSha256)"
  if [ "$previous_state_sha" != NONE ]; then
    [[ "$previous_state_sha" =~ ^sha256:[0-9a-f]{64}$ ]] \
      || formdock_delivery_die 'Previous state identity must be NONE or sha256:<64 lowercase hexadecimal characters>.'
  fi
}

formdock_delivery_validate_project() {
  local project="$1"
  [[ "$project" =~ ^dev-form-dock-delivery-[a-z0-9][a-z0-9-]{0,47}$ ]] \
    || formdock_delivery_die 'Isolated project must match dev-form-dock-delivery-[a-z0-9][a-z0-9-]{0,47}.'
}

formdock_delivery_validate_env() {
  local env_file="$1"
  local project="$2"
  local canonical port password

  canonical="$(formdock_delivery_canonical_private_file "$env_file")"
  awk -F= '
    BEGIN {
      allowed["FORMDOCK_DELIVERY_SCOPE"] = 1
      allowed["FORMDOCK_DELIVERY_PROJECT"] = 1
      allowed["FORMDOCK_CONFIGURATION_REVISION"] = 1
      allowed["FORMDOCK_WEB_PORT"] = 1
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
      if (count != 13) {
        exit 1
      }
    }
  ' "$canonical" \
    || formdock_delivery_die 'Isolated environment contains missing, duplicate, or unknown keys.'

  [ "$(formdock_delivery_value "$canonical" FORMDOCK_DELIVERY_SCOPE)" = isolated ] \
    || formdock_delivery_die 'Environment scope must be isolated.'
  [ "$(formdock_delivery_value "$canonical" FORMDOCK_DELIVERY_PROJECT)" = "$project" ] \
    || formdock_delivery_die 'Environment project does not match the isolated project.'
  [[ "$(formdock_delivery_value "$canonical" FORMDOCK_CONFIGURATION_REVISION)" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || formdock_delivery_die 'Environment configuration revision must be a sha256 identity.'
  [ "$(formdock_delivery_value "$canonical" FORMDOCK_DB_NAME)" = formdock_delivery ] \
    || formdock_delivery_die 'Isolated database name must be formdock_delivery.'
  [ "$(formdock_delivery_value "$canonical" FORMDOCK_DB_USERNAME)" = formdock_delivery ] \
    || formdock_delivery_die 'Isolated database user must be formdock_delivery.'

  password="$(formdock_delivery_value "$canonical" FORMDOCK_DB_PASSWORD)"
  [[ "$password" =~ ^[A-Za-z0-9._~-]{24,128}$ ]] \
    || formdock_delivery_die 'Disposable database credential must be 24-128 safe characters.'

  port="$(formdock_delivery_value "$canonical" FORMDOCK_WEB_PORT)"
  [[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 0 ] && [ "$port" -le 65535 ] \
    || formdock_delivery_die 'Isolated Web port must be between 0 and 65535.'

  [[ "$(formdock_delivery_value "$canonical" FORMDOCK_LOG_MAX_SIZE)" =~ ^[1-9][0-9]{0,3}[kKmMgG]$ ]] \
    || formdock_delivery_die 'Isolated log max-size must be a bounded Docker size value.'
  port="$(formdock_delivery_value "$canonical" FORMDOCK_LOG_MAX_FILE)"
  [[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 1 ] && [ "$port" -le 100 ] \
    || formdock_delivery_die 'Isolated log max-file must be between 1 and 100.'

  [ "$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_ENABLED)" = false ] \
    || formdock_delivery_die 'Creator bootstrap must remain disabled in delivery validation.'
  [ -z "$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_EMAIL)" ] \
    && [ -z "$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_PASSWORD)" ] \
    && [ -z "$(formdock_delivery_value "$canonical" FORMDOCK_BOOTSTRAP_DISPLAY_NAME)" ] \
    || formdock_delivery_die 'Creator bootstrap values must be empty in delivery validation.'

  printf '%s\n' "$canonical"
}

formdock_delivery_image_identity() {
  local reference="$1"
  docker image inspect --format '{{.Id}}' "$reference" 2>/dev/null \
    || formdock_delivery_die 'Required local delivery image is unavailable.'
}
