#!/usr/bin/env bash
set -euo pipefail

monitor_invalid() {
  printf 'MONITORING_CONFIG_INVALID\n' >&2
  exit 64
}

monitor_require_env() {
  local name="$1"
  [ -n "${!name:-}" ] || monitor_invalid
}

monitor_is_uint() {
  [[ "$1" =~ ^[0-9]+$ ]] && [ "${#1}" -le 10 ]
}

monitor_file_mode() {
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
  return 1
}

monitor_private_file() {
  local target="$1"
  local mode
  [ -f "$target" ] && [ ! -L "$target" ] || return 1
  mode="$(monitor_file_mode "$target")" || return 1
  case "$mode" in
    *00) return 0 ;;
    *) return 1 ;;
  esac
}

monitor_private_directory() {
  local target="$1"
  local mode
  [ -d "$target" ] && [ ! -L "$target" ] || return 1
  mode="$(monitor_file_mode "$target")" || return 1
  case "$mode" in
    *00) return 0 ;;
    *) return 1 ;;
  esac
}

monitor_sha256() {
  local target="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$target" | awk '{print $1}'
    return
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$target" | awk '{print $1}'
    return
  fi
  return 1
}

monitor_metadata_value() {
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
  ' "$file"
}

monitor_validate_metadata_keys() {
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
  ' "$1"
}

monitor_epoch() {
  local value="$1"
  local epoch
  if epoch="$(date -u -d "$value" '+%s' 2>/dev/null)"; then
    printf '%s\n' "$epoch"
    return
  fi
  if epoch="$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$value" '+%s' 2>/dev/null)"; then
    printf '%s\n' "$epoch"
    return
  fi
  return 1
}

monitor_container_health() {
  local container="$1"
  local status
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
  if [ "$status" = healthy ]; then
    printf 'healthy\n'
  else
    printf 'unhealthy\n'
  fi
}

monitor_backup_status() {
  local root="$1"
  local max_age="$2"
  local newest_at='' metadata_file backup_id dump_file checksum_file
  local status created_at filename stored_hash checksum_line actual_hash
  local server_version tool_version release_sha
  local invalid=false now_epoch created_epoch age

  monitor_private_directory "$root" || {
    printf 'stale_or_failed\n'
    return
  }

  shopt -s nullglob
  for metadata_file in "$root"/formdock-*.meta; do
    monitor_private_file "$metadata_file" || {
      invalid=true
      continue
    }
    monitor_validate_metadata_keys "$metadata_file" || {
      invalid=true
      continue
    }
    backup_id="$(basename "$metadata_file" .meta)"
    [[ "$backup_id" =~ ^formdock-[A-Za-z0-9][A-Za-z0-9._-]{0,95}$ ]] || {
      invalid=true
      continue
    }
    dump_file="$root/$backup_id.dump"
    checksum_file="$root/$backup_id.sha256"
    monitor_private_file "$dump_file" && monitor_private_file "$checksum_file" || {
      invalid=true
      continue
    }

    status="$(monitor_metadata_value "$metadata_file" status 2>/dev/null || true)"
    created_at="$(monitor_metadata_value "$metadata_file" createdAt 2>/dev/null || true)"
    server_version="$(monitor_metadata_value "$metadata_file" postgresServerVersion 2>/dev/null || true)"
    tool_version="$(monitor_metadata_value "$metadata_file" pgDumpVersion 2>/dev/null || true)"
    release_sha="$(monitor_metadata_value "$metadata_file" applicationReleaseSha 2>/dev/null || true)"
    filename="$(monitor_metadata_value "$metadata_file" backupFilename 2>/dev/null || true)"
    stored_hash="$(monitor_metadata_value "$metadata_file" sha256 2>/dev/null || true)"
    [ "$(monitor_metadata_value "$metadata_file" formatVersion 2>/dev/null || true)" = 1 ] \
      && [ "$status" = complete ] \
      && [[ "$created_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
      && [ -n "$server_version" ] \
      && [ -n "$tool_version" ] \
      && [[ "$release_sha" =~ ^[0-9a-fA-F]{40}$ ]] \
      && [ "$filename" = "$backup_id.dump" ] \
      && [[ "$stored_hash" =~ ^[0-9a-f]{64}$ ]] || {
        invalid=true
        continue
      }
    checksum_line="$(cat "$checksum_file")"
    actual_hash="$(monitor_sha256 "$dump_file" 2>/dev/null || true)"
    [ "$checksum_line" = "$stored_hash  $backup_id.dump" ] \
      && [ "$actual_hash" = "$stored_hash" ] || {
        invalid=true
        continue
      }
    if [ -z "$newest_at" ] || [[ "$created_at" > "$newest_at" ]]; then
      newest_at="$created_at"
    fi
  done
  shopt -u nullglob

  [ "$invalid" = false ] && [ -n "$newest_at" ] || {
    printf 'stale_or_failed\n'
    return
  }
  now_epoch="$(date -u '+%s')"
  created_epoch="$(monitor_epoch "$newest_at" 2>/dev/null || true)"
  monitor_is_uint "$created_epoch" || {
    printf 'stale_or_failed\n'
    return
  }
  if [ "$created_epoch" -gt $((now_epoch + 60)) ]; then
    printf 'stale_or_failed\n'
    return
  fi
  age=$((now_epoch - created_epoch))
  if [ "$age" -le "$max_age" ]; then
    printf 'fresh\n'
  else
    printf 'stale_or_failed\n'
  fi
}

monitor_emit() {
  local signal="$1"
  local status="$2"
  local observed="$3"
  local threshold="$4"
  printf '{"formatVersion":1,"signal":"%s","status":"%s","observed":"%s","threshold":"%s","eventAt":"%s"}\n' \
    "$signal" "$status" "$observed" "$threshold" "$event_at"
}

for required in \
  FORMDOCK_MONITOR_WEB_CONTAINER \
  FORMDOCK_MONITOR_API_CONTAINER \
  FORMDOCK_MONITOR_DB_CONTAINER \
  FORMDOCK_MONITOR_DISK_PATH \
  FORMDOCK_MONITOR_BACKUP_ROOT \
  FORMDOCK_MONITOR_HTTP_5XX_COUNT; do
  monitor_require_env "$required"
done

disk_min_available="${FORMDOCK_MONITOR_DISK_MIN_AVAILABLE_PERCENT:-15}"
backup_max_age="${FORMDOCK_MONITOR_BACKUP_MAX_AGE_SECONDS:-93600}"
http_5xx_threshold="${FORMDOCK_MONITOR_HTTP_5XX_THRESHOLD:-10}"
http_5xx_window="${FORMDOCK_MONITOR_HTTP_5XX_WINDOW_SECONDS:-300}"

monitor_is_uint "$disk_min_available" && [ "$disk_min_available" -le 100 ] || monitor_invalid
monitor_is_uint "$backup_max_age" && [ "$backup_max_age" -ge 1 ] || monitor_invalid
monitor_is_uint "$FORMDOCK_MONITOR_HTTP_5XX_COUNT" || monitor_invalid
monitor_is_uint "$http_5xx_threshold" && [ "$http_5xx_threshold" -ge 1 ] || monitor_invalid
monitor_is_uint "$http_5xx_window" && [ "$http_5xx_window" -ge 1 ] || monitor_invalid
[ -e "$FORMDOCK_MONITOR_DISK_PATH" ] || monitor_invalid
command -v docker >/dev/null 2>&1 || monitor_invalid

event_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
alert=false

web_health="$(monitor_container_health "$FORMDOCK_MONITOR_WEB_CONTAINER")"
if [ "$web_health" = healthy ]; then
  monitor_emit WEB_UNHEALTHY OK healthy healthy
else
  monitor_emit WEB_UNHEALTHY ALERT unhealthy healthy
  alert=true
fi

api_health="$(monitor_container_health "$FORMDOCK_MONITOR_API_CONTAINER")"
if [ "$api_health" = healthy ]; then
  monitor_emit API_UNHEALTHY OK healthy healthy
else
  monitor_emit API_UNHEALTHY ALERT unhealthy healthy
  alert=true
fi

db_health="$(monitor_container_health "$FORMDOCK_MONITOR_DB_CONTAINER")"
if [ "$db_health" = healthy ]; then
  monitor_emit DB_UNHEALTHY OK healthy healthy
else
  monitor_emit DB_UNHEALTHY ALERT unhealthy healthy
  alert=true
fi

disk_available="$(df -Pk "$FORMDOCK_MONITOR_DISK_PATH" 2>/dev/null | awk 'NR == 2 {gsub(/%/, "", $5); print 100 - $5}')"
monitor_is_uint "$disk_available" || monitor_invalid
if [ "$disk_available" -ge "$disk_min_available" ]; then
  monitor_emit DISK_LOW OK "$disk_available" "$disk_min_available"
else
  monitor_emit DISK_LOW ALERT "$disk_available" "$disk_min_available"
  alert=true
fi

backup_status="$(monitor_backup_status "$FORMDOCK_MONITOR_BACKUP_ROOT" "$backup_max_age")"
if [ "$backup_status" = fresh ]; then
  monitor_emit BACKUP_STALE_OR_FAILED OK fresh "$backup_max_age"
else
  monitor_emit BACKUP_STALE_OR_FAILED ALERT stale_or_failed "$backup_max_age"
  alert=true
fi

if [ "$FORMDOCK_MONITOR_HTTP_5XX_COUNT" -lt "$http_5xx_threshold" ]; then
  monitor_emit HTTP_5XX_BURST OK "$FORMDOCK_MONITOR_HTTP_5XX_COUNT" "${http_5xx_threshold}/${http_5xx_window}s"
else
  monitor_emit HTTP_5XX_BURST ALERT "$FORMDOCK_MONITOR_HTTP_5XX_COUNT" "${http_5xx_threshold}/${http_5xx_window}s"
  alert=true
fi

if [ "$alert" = true ]; then
  exit 2
fi
