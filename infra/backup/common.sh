#!/usr/bin/env bash

formdock_die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

formdock_require_command() {
  command -v "$1" >/dev/null 2>&1 || formdock_die "Required command is unavailable: $1"
}

formdock_require_env() {
  local name="$1"
  [ -n "${!name:-}" ] || formdock_die "Required environment variable is missing: $name"
}

formdock_validate_backup_id() {
  local backup_id="$1"
  [[ "$backup_id" =~ ^formdock-[A-Za-z0-9][A-Za-z0-9._-]{0,95}$ ]] \
    || formdock_die 'Backup ID must match formdock-[A-Za-z0-9][A-Za-z0-9._-]{0,95}.'
}

formdock_validate_release_sha() {
  local release_sha="$1"
  [[ "$release_sha" =~ ^[0-9a-fA-F]{40}$ ]] \
    || formdock_die 'FORMDOCK_RELEASE_SHA must be an exact 40-character Git SHA.'
}

formdock_validate_port() {
  local port="$1"
  [[ "$port" =~ ^[0-9]+$ ]] || formdock_die 'Database port must be numeric.'
  [ "$port" -ge 1 ] && [ "$port" -le 65535 ] \
    || formdock_die 'Database port must be between 1 and 65535.'
}

formdock_file_mode() {
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

  formdock_die "Unable to inspect file permissions: $target"
}

formdock_require_private_mode() {
  local target="$1"
  local mode
  mode="$(formdock_file_mode "$target")"

  case "$mode" in
    *00) ;;
    *) formdock_die "Group/other permissions must be zero for: $target" ;;
  esac
}

formdock_canonical_private_directory() {
  local target="$1"
  local canonical

  while [ "$target" != / ] && [ "${target%/}" != "$target" ]; do
    target="${target%/}"
  done

  case "$target" in
    /*) ;;
    *) formdock_die "Configured directory must be absolute: $target" ;;
  esac

  [ -d "$target" ] || formdock_die "Configured directory does not exist: $target"
  [ ! -L "$target" ] || formdock_die "Configured directory must not be a symbolic link: $target"
  canonical="$(cd -- "$target" && pwd -P)"
  [ "$canonical" != / ] || formdock_die 'Filesystem root cannot be used as a FormDock backup directory.'
  formdock_require_private_mode "$canonical"
  printf '%s\n' "$canonical"
}

formdock_sha256() {
  local target="$1"

  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$target" | awk '{print $1}'
    return
  fi

  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$target" | awk '{print $1}'
    return
  fi

  formdock_die 'Neither sha256sum nor shasum is available.'
}

formdock_metadata_value() {
  local metadata_file="$1"
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
  ' "$metadata_file" || formdock_die "Metadata field must occur exactly once: $key"
}

formdock_validate_metadata_keys() {
  local metadata_file="$1"

  awk -F= '
    BEGIN {
      allowed["formatVersion"] = 1
      allowed["status"] = 1
      allowed["createdAt"] = 1
      allowed["postgresServerVersion"] = 1
      allowed["pgDumpVersion"] = 1
      allowed["applicationReleaseSha"] = 1
      allowed["backupFilename"] = 1
      allowed["sha256"] = 1
    }
    {
      if (!($1 in allowed) || seen[$1]++) {
        exit 1
      }
      count += 1
    }
    END {
      if (count != 8) {
        exit 1
      }
    }
  ' "$metadata_file" || formdock_die "Metadata contains missing, duplicate, or unknown fields: $metadata_file"
}

formdock_verify_backup_set() {
  local backup_root="$1"
  local backup_id="$2"
  local postgres_image="${FORMDOCK_POSTGRES_IMAGE:-postgres:18.6-alpine3.23}"
  local dump_file="$backup_root/$backup_id.dump"
  local checksum_file="$backup_root/$backup_id.sha256"
  local metadata_file="$backup_root/$backup_id.meta"
  local expected_filename="$backup_id.dump"
  local status created_at server_version tool_version release_sha metadata_filename stored_hash checksum_line actual_hash

  formdock_validate_backup_id "$backup_id"
  formdock_require_command docker
  [ -f "$dump_file" ] || formdock_die "Backup dump is missing: $dump_file"
  [ -f "$checksum_file" ] || formdock_die "Backup checksum is missing: $checksum_file"
  [ -f "$metadata_file" ] || formdock_die "Backup metadata is missing: $metadata_file"
  [ ! -L "$dump_file" ] && [ ! -L "$checksum_file" ] && [ ! -L "$metadata_file" ] \
    || formdock_die 'Backup artifacts must not be symbolic links.'

  formdock_require_private_mode "$dump_file"
  formdock_require_private_mode "$checksum_file"
  formdock_require_private_mode "$metadata_file"
  formdock_validate_metadata_keys "$metadata_file"

  [ "$(formdock_metadata_value "$metadata_file" formatVersion)" = 1 ] \
    || formdock_die 'Unsupported backup metadata format version.'
  status="$(formdock_metadata_value "$metadata_file" status)"
  [ "$status" = complete ] || formdock_die 'Backup metadata status is not complete.'

  created_at="$(formdock_metadata_value "$metadata_file" createdAt)"
  [[ "$created_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
    || formdock_die 'Backup metadata createdAt must be UTC with second precision.'

  server_version="$(formdock_metadata_value "$metadata_file" postgresServerVersion)"
  tool_version="$(formdock_metadata_value "$metadata_file" pgDumpVersion)"
  [ -n "$server_version" ] && [ -n "$tool_version" ] \
    || formdock_die 'Backup metadata version fields must not be empty.'

  release_sha="$(formdock_metadata_value "$metadata_file" applicationReleaseSha)"
  formdock_validate_release_sha "$release_sha"
  metadata_filename="$(formdock_metadata_value "$metadata_file" backupFilename)"
  [ "$metadata_filename" = "$expected_filename" ] \
    || formdock_die 'Backup metadata filename does not match the backup ID.'

  stored_hash="$(formdock_metadata_value "$metadata_file" sha256)"
  [[ "$stored_hash" =~ ^[0-9a-f]{64}$ ]] || formdock_die 'Backup metadata SHA-256 is invalid.'
  checksum_line="$(cat "$checksum_file")"
  [ "$checksum_line" = "$stored_hash  $expected_filename" ] \
    || formdock_die 'Backup checksum file does not match metadata.'
  actual_hash="$(formdock_sha256 "$dump_file")"
  [ "$actual_hash" = "$stored_hash" ] || formdock_die 'Backup bytes do not match SHA-256 metadata.'

  docker run --rm -i "$postgres_image" pg_restore --list >/dev/null < "$dump_file" \
    || formdock_die 'Backup is not a readable PostgreSQL custom-format archive.'
}

formdock_wait_for_healthy_container() {
  local container="$1"
  local attempts="${2:-90}"
  local current=0
  local status

  while [ "$current" -lt "$attempts" ]; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    case "$status" in
      healthy) return 0 ;;
      exited|dead) formdock_die "Container stopped before becoming healthy: $container" ;;
    esac
    current=$((current + 1))
    sleep 1
  done

  formdock_die "Container did not become healthy within ${attempts}s: $container"
}
