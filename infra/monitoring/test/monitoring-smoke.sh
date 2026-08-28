#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
MONITORING_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"

for required in \
  FORMDOCK_MONITOR_TEST_WEB_CONTAINER \
  FORMDOCK_MONITOR_TEST_API_CONTAINER \
  FORMDOCK_MONITOR_TEST_DB_CONTAINER; do
  [ -n "${!required:-}" ] || {
    printf 'ERROR: Missing monitoring smoke input: %s\n' "$required" >&2
    exit 1
  }
done

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

tmp_base="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-monitoring-smoke.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-monitoring-smoke.*) ;;
  *) printf 'ERROR: Monitoring smoke temporary directory escaped the expected root.\n' >&2; exit 1 ;;
esac
chmod 700 "$temp_root"
fresh_root="$temp_root/fresh"
stale_root="$temp_root/stale"
mkdir "$fresh_root" "$stale_root"
chmod 700 "$fresh_root" "$stale_root"

cleanup() {
  case "$temp_root" in
    "$tmp_base"/formdock-monitoring-smoke.*) find "$temp_root" -depth -delete ;;
  esac
}
trap cleanup EXIT

write_backup_fixture() {
  local root="$1"
  local backup_id="$2"
  local created_at="$3"
  local hash
  printf 'completed-backup-fixture\n' > "$root/$backup_id.dump"
  chmod 600 "$root/$backup_id.dump"
  hash="$(sha256_file "$root/$backup_id.dump")"
  printf '%s  %s.dump\n' "$hash" "$backup_id" > "$root/$backup_id.sha256"
  {
    printf 'formatVersion=1\n'
    printf 'status=complete\n'
    printf 'createdAt=%s\n' "$created_at"
    printf 'postgresServerVersion=18.6\n'
    printf 'pgDumpVersion=18.6\n'
    printf 'applicationReleaseSha=0000000000000000000000000000000000000000\n'
    printf 'backupFilename=%s.dump\n' "$backup_id"
    printf 'sha256=%s\n' "$hash"
  } > "$root/$backup_id.meta"
  chmod 600 "$root/$backup_id.sha256" "$root/$backup_id.meta"
}

write_backup_fixture "$fresh_root" formdock-monitoring-fresh "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
write_backup_fixture "$stale_root" formdock-monitoring-stale '2000-01-01T00:00:00Z'

run_monitor() {
  FORMDOCK_MONITOR_WEB_CONTAINER="${MONITOR_WEB_OVERRIDE:-$FORMDOCK_MONITOR_TEST_WEB_CONTAINER}" \
  FORMDOCK_MONITOR_API_CONTAINER="${MONITOR_API_OVERRIDE:-$FORMDOCK_MONITOR_TEST_API_CONTAINER}" \
  FORMDOCK_MONITOR_DB_CONTAINER="${MONITOR_DB_OVERRIDE:-$FORMDOCK_MONITOR_TEST_DB_CONTAINER}" \
  FORMDOCK_MONITOR_DISK_PATH="$temp_root" \
  FORMDOCK_MONITOR_DISK_MIN_AVAILABLE_PERCENT="${MONITOR_DISK_THRESHOLD_OVERRIDE:-1}" \
  FORMDOCK_MONITOR_BACKUP_ROOT="${MONITOR_BACKUP_OVERRIDE:-$fresh_root}" \
  FORMDOCK_MONITOR_BACKUP_MAX_AGE_SECONDS=3600 \
  FORMDOCK_MONITOR_HTTP_5XX_COUNT="${MONITOR_5XX_COUNT_OVERRIDE:-0}" \
  FORMDOCK_MONITOR_HTTP_5XX_THRESHOLD=10 \
  FORMDOCK_MONITOR_HTTP_5XX_WINDOW_SECONDS=300 \
    "$MONITORING_DIR/check-runtime.sh"
}

healthy_output="$(run_monitor)"
[ "$(printf '%s\n' "$healthy_output" | wc -l | tr -d ' ')" = 6 ]
[ "$(printf '%s\n' "$healthy_output" | grep -c '"status":"OK"')" = 6 ]
for signal in WEB_UNHEALTHY API_UNHEALTHY DB_UNHEALTHY DISK_LOW BACKUP_STALE_OR_FAILED HTTP_5XX_BURST; do
  printf '%s\n' "$healthy_output" | grep -q "\"signal\":\"$signal\""
done

expect_alert() {
  local signal="$1"
  shift
  local output exit_code
  set +e
  output="$(env "$@" bash -c 'run_monitor' 2>/dev/null)"
  exit_code=$?
  set -e
  [ "$exit_code" = 2 ] || {
    printf 'ERROR: Monitoring alert did not return exit 2 for %s.\n' "$signal" >&2
    exit 1
  }
  printf '%s\n' "$output" | grep -q "\"signal\":\"$signal\",\"status\":\"ALERT\""
}
export -f run_monitor
export MONITORING_DIR temp_root fresh_root
export FORMDOCK_MONITOR_TEST_WEB_CONTAINER FORMDOCK_MONITOR_TEST_API_CONTAINER FORMDOCK_MONITOR_TEST_DB_CONTAINER

missing_container='0000000000000000000000000000000000000000000000000000000000000000'
expect_alert WEB_UNHEALTHY MONITOR_WEB_OVERRIDE="$missing_container"
expect_alert API_UNHEALTHY MONITOR_API_OVERRIDE="$missing_container"
expect_alert DB_UNHEALTHY MONITOR_DB_OVERRIDE="$missing_container"
expect_alert DISK_LOW MONITOR_DISK_THRESHOLD_OVERRIDE=100
expect_alert BACKUP_STALE_OR_FAILED MONITOR_BACKUP_OVERRIDE="$stale_root"
expect_alert HTTP_5XX_BURST MONITOR_5XX_COUNT_OVERRIDE=10

set +e
invalid_output="$(MONITOR_DISK_THRESHOLD_OVERRIDE=invalid run_monitor 2>&1)"
invalid_exit=$?
set -e
[ "$invalid_exit" = 64 ]
[ "$invalid_output" = MONITORING_CONFIG_INVALID ]

printf '%s\n' "$healthy_output" | grep -Eqi 'password|token|response.body' \
  && {
    printf 'ERROR: Monitoring output contains a forbidden sensitive field name.\n' >&2
    exit 1
  }

printf 'MONITORING_HEALTH_SIGNALS=PASS\n'
printf 'MONITORING_DISK_SIGNAL=PASS\n'
printf 'MONITORING_BACKUP_SIGNAL=PASS\n'
printf 'MONITORING_HTTP_5XX_ADAPTER=PASS\n'
printf 'MONITORING_EVENT_BOUNDARY=PASS\n'
printf 'MONITORING_INVALID_CONFIG=PASS\n'
