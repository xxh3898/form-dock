#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
DEPLOY="$SCRIPT_DIR/../deploy-release.sh"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-recurring-deploy.XXXXXX")"
trap 'find "$TEMP_ROOT" -type l -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -depth -type d -exec rmdir {} \; 2>/dev/null || true' EXIT

CURRENT_SHA=1111111111111111111111111111111111111111
CANDIDATE_SHA=2222222222222222222222222222222222222222
CURRENT_API=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
CURRENT_WEB=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
CURRENT_RUNTIME=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
CANDIDATE_API=sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
CANDIDATE_WEB=sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee
CANDIDATE_RUNTIME=sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
OLDER_SHA=3333333333333333333333333333333333333333
OLDER_API=sha256:5555555555555555555555555555555555555555555555555555555555555555
OLDER_WEB=sha256:6666666666666666666666666666666666666666666666666666666666666666
OLDER_RUNTIME=sha256:4444444444444444444444444444444444444444444444444444444444444444
ZERO_SHA=0000000000000000000000000000000000000000
ZERO_DIGEST=sha256:0000000000000000000000000000000000000000000000000000000000000000
CONFIGURATION_REVISION=sha256:9999999999999999999999999999999999999999999999999999999999999999

write_executable() {
  local target="$1"
  shift
  printf '%s\n' "$@" > "$target"
  chmod 700 "$target"
}

prepare_fixture() {
  local root="$1"
  local created_at="$2"
  local flyway_state="$3"
  local candidate_result="$4"
  local app="$root/app"
  local runtime="$app/runtime-config"
  local backup="$root/backup"
  local bin="$root/bin"
  local current_dir="$runtime/releases/${CURRENT_RUNTIME#sha256:}"
  local candidate_dir="$runtime/releases/${CANDIDATE_RUNTIME#sha256:}"

  mkdir -p "$current_dir/scripts" "$candidate_dir/scripts" "$backup" "$bin"
  chmod 700 "$app" "$runtime" "$runtime/releases" "$current_dir" "$candidate_dir" "$backup" "$bin"
  printf '%s\n' 'services: {}' > "$current_dir/compose.yaml"
  printf '%s\n' 'services: {}' > "$candidate_dir/compose.yaml"

  write_executable "$candidate_dir/scripts/verify-backup.sh" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'test -n "${FORMDOCK_BACKUP_ROOT:-}"' \
    'test -n "${FORMDOCK_BACKUP_ID:-}"'
  cp "$SCRIPT_DIR/../report-homeops-deployment.sh" "$candidate_dir/scripts/report-homeops-deployment.sh"
  cp "$SCRIPT_DIR/../../delivery/common.sh" "$candidate_dir/scripts/delivery-common.sh"
  chmod 700 "$candidate_dir/scripts/report-homeops-deployment.sh"

  write_executable "$bin/reporter" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'test "$1" = deployments' \
    'payload="$(cat)"' \
    'if [[ "$payload" == *'"'"'"status":"SUCCESS"'"'"'* ]] && [ "$FORMDOCK_FIXTURE_SUCCESS_REPORT_RESULT" = fail ]; then exit 1; fi' \
    'printf "%s\\n" "$payload" >> "$FORMDOCK_FIXTURE_REPORT_LOG"'
  write_executable "$bin/curl" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'printf "%s\\n" "$*" >> "$FORMDOCK_FIXTURE_CURL_LOG"' \
    'printf "%s" 200'
  write_executable "$bin/docker" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'printf "%s\\n" "$*" >> "$FORMDOCK_FIXTURE_DOCKER_LOG"' \
    'case "$1" in' \
    '  ps)' \
    '    case "$*" in *service=postgres*) printf "%s\\n" postgres-id ;; *service=api*) printf "%s\\n" api-id ;; *service=web*) printf "%s\\n" web-id ;; esac ;;' \
    '  inspect|image)' \
    '    case "$*" in' \
    '      *Architecture*Os*) printf "%s\\n" arm64/linux ;;' \
    '      *org.opencontainers.image.revision*) printf "%s\\n" "$FORMDOCK_FIXTURE_CANDIDATE_SHA" ;;' \
    '      *Destination*postgresql*) printf "%s\\n" form-dock-postgres-data ;;' \
    '      *State.Health*) printf "%s\\n" healthy ;;' \
    '    esac ;;' \
    '  exec) printf "%s\\n" "$FORMDOCK_FIXTURE_FLYWAY_STATE" ;;' \
    '  compose)' \
    '    if [[ "$*" == *"${FORMDOCK_FIXTURE_CANDIDATE_RUNTIME}"* ]] && [[ "$*" == *" up "* ]] && [ "$FORMDOCK_FIXTURE_CANDIDATE_RESULT" = fail ]; then exit 1; fi ;;' \
    'esac'

  cat > "$app/product.env" <<EOF
FORMDOCK_API_IMAGE=ghcr.io/xxh3898/form-dock-api@$CURRENT_API
FORMDOCK_WEB_IMAGE=ghcr.io/xxh3898/form-dock-web@$CURRENT_WEB
FORMDOCK_CONFIGURATION_REVISION=$CONFIGURATION_REVISION
FORMDOCK_WEB_PORT=18082
EOF
  if command -v sha256sum >/dev/null 2>&1; then
    compose_hash="$(sha256sum "$current_dir/compose.yaml" | awk '{ print $1 }')"
  else
    compose_hash="$(shasum -a 256 "$current_dir/compose.yaml" | awk '{ print $1 }')"
  fi
  cat > "$app/deployment.state" <<EOF
formatVersion=1
stateRole=candidate
releaseGitSha=$CURRENT_SHA
apiImageReference=ghcr.io/xxh3898/form-dock-api@$CURRENT_API
apiImageIdentity=$CURRENT_API
webImageReference=ghcr.io/xxh3898/form-dock-web@$CURRENT_WEB
webImageIdentity=$CURRENT_WEB
composeRevision=sha256:$compose_hash
configurationRevision=$CONFIGURATION_REVISION
recordedAt=2026-08-28T00:00:00Z
previousStateSha256=NONE
EOF
  cat > "$runtime/state" <<EOF
formatVersion=1
currentSha=$CURRENT_SHA
currentDigest=$CURRENT_RUNTIME
previousSha=$ZERO_SHA
previousDigest=$ZERO_DIGEST
recordedAt=2026-08-28T00:00:00Z
EOF
  cat > "$app/cd.env" <<EOF
FORMDOCK_BACKUP_ROOT=$backup
FORMDOCK_BACKUP_MAX_AGE_SECONDS=93600
FORMDOCK_PUBLIC_ORIGIN=https://forms.chochiho.cloud
FORMDOCK_HOMEOPS_REPORTER=$bin/reporter
EOF
  chmod 600 "$app/product.env" "$app/deployment.state" "$app/cd.env" "$runtime/state"

  printf 'createdAt=%s\n' "$created_at" > "$backup/formdock-fixture.meta"
  chmod 600 "$backup/formdock-fixture.meta"
  ln -s "releases/${CURRENT_RUNTIME#sha256:}" "$runtime/current"
  ln -s "releases/${CANDIDATE_RUNTIME#sha256:}" "$runtime/pending"

  : > "$root/docker.log"
  : > "$root/curl.log"
  : > "$root/report.log"
  printf '%s\n' "$flyway_state" > "$root/flyway.expected"
  printf '%s\n' "$candidate_result" > "$root/candidate.result"
  printf '%s\n' pass > "$root/success-report.result"
}

run_deploy() {
  local root="$1"
  local fail_step="${2:-}"
  FORMDOCK_DEPLOY_TEST_MODE=fixture \
  FORMDOCK_DEPLOY_TEST_FAIL_STEP="$fail_step" \
  FORMDOCK_DEPLOY_TEST_ROOT="$root/app" \
  FORMDOCK_RUNTIME_DIR="$root/app/runtime-config/releases/${CANDIDATE_RUNTIME#sha256:}" \
  FORMDOCK_DOCKER_BIN="$root/bin/docker" \
  FORMDOCK_CURL_BIN="$root/bin/curl" \
  FORMDOCK_PYTHON_BIN="$(command -v python3)" \
  FORMDOCK_API_IMAGE_DIGEST="$CANDIDATE_API" \
  FORMDOCK_WEB_IMAGE_DIGEST="$CANDIDATE_WEB" \
  FORMDOCK_RUNTIME_CONFIG_DIGEST="$CANDIDATE_RUNTIME" \
  FORMDOCK_FIXTURE_DOCKER_LOG="$root/docker.log" \
  FORMDOCK_FIXTURE_CURL_LOG="$root/curl.log" \
  FORMDOCK_FIXTURE_REPORT_LOG="$root/report.log" \
  FORMDOCK_FIXTURE_CANDIDATE_SHA="$CANDIDATE_SHA" \
  FORMDOCK_FIXTURE_CANDIDATE_RUNTIME="${CANDIDATE_RUNTIME#sha256:}" \
  FORMDOCK_FIXTURE_FLYWAY_STATE="$(cat "$root/flyway.expected")" \
  FORMDOCK_FIXTURE_CANDIDATE_RESULT="$(cat "$root/candidate.result")" \
  FORMDOCK_FIXTURE_SUCCESS_REPORT_RESULT="$(cat "$root/success-report.result")" \
    "$DEPLOY" "$CANDIDATE_SHA" xxh3898 123456789
}

file_mode() {
  if [ "$(uname -s)" = Darwin ]; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

file_owner() {
  if [ "$(uname -s)" = Darwin ]; then
    stat -f '%u' "$1"
  else
    stat -c '%u' "$1"
  fi
}

fixture_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{ print $1 }'
  else
    shasum -a 256 "$1" | awk '{ print $1 }'
  fi
}

install_previous_authority() {
  local root="$1"
  local older_dir="$root/app/runtime-config/releases/${OLDER_RUNTIME#sha256:}"
  local compose_hash
  mkdir -p "$older_dir"
  chmod 700 "$older_dir"
  printf '%s\n' 'services: {}' > "$older_dir/compose.yaml"
  compose_hash="$(fixture_sha256 "$older_dir/compose.yaml")"
  cat > "$root/app/deployment.previous.state" <<EOF
formatVersion=1
stateRole=previous
releaseGitSha=$OLDER_SHA
apiImageReference=ghcr.io/xxh3898/form-dock-api@$OLDER_API
apiImageIdentity=$OLDER_API
webImageReference=ghcr.io/xxh3898/form-dock-web@$OLDER_WEB
webImageIdentity=$OLDER_WEB
composeRevision=sha256:$compose_hash
configurationRevision=$CONFIGURATION_REVISION
recordedAt=2026-08-27T00:00:00Z
previousStateSha256=NONE
EOF
  chmod 600 "$root/app/deployment.previous.state"
  cat > "$root/app/runtime-config/state" <<EOF
formatVersion=1
currentSha=$CURRENT_SHA
currentDigest=$CURRENT_RUNTIME
previousSha=$OLDER_SHA
previousDigest=$OLDER_RUNTIME
recordedAt=2026-08-28T00:00:00Z
EOF
  chmod 600 "$root/app/runtime-config/state"
  ln -s "releases/${OLDER_RUNTIME#sha256:}" "$root/app/runtime-config/previous"
  cp "$root/app/deployment.previous.state" "$root/previous-state.before"
}

assert_no_destructive_volume_operation() {
  local root="$1"
  if grep -Eq 'down.*--volumes|--volumes.*down|volume (rm|remove)|system prune' "$root/docker.log"; then
    printf '%s\n' 'Deployment attempted a destructive Docker volume operation.' >&2
    exit 1
  fi
}

assert_accepted_rollback() {
  local root="$1"
  local previous_mode="${2:-absent}"
  grep -Fxq "releaseGitSha=$CURRENT_SHA" "$root/app/deployment.state"
  grep -Fxq "currentSha=$CURRENT_SHA" "$root/app/runtime-config/state"
  grep -Fxq "currentDigest=$CURRENT_RUNTIME" "$root/app/runtime-config/state"
  grep -Fxq "FORMDOCK_API_IMAGE=ghcr.io/xxh3898/form-dock-api@$CURRENT_API" "$root/app/product.env"
  grep -Fxq "FORMDOCK_WEB_IMAGE=ghcr.io/xxh3898/form-dock-web@$CURRENT_WEB" "$root/app/product.env"
  [ "$(readlink "$root/app/runtime-config/current")" = "releases/${CURRENT_RUNTIME#sha256:}" ]
  [ "$(readlink "$root/app/runtime-config/pending")" = "releases/${CANDIDATE_RUNTIME#sha256:}" ]
  if [ "$previous_mode" = present ]; then
    [ "$(readlink "$root/app/runtime-config/previous")" = "releases/${OLDER_RUNTIME#sha256:}" ]
    cmp -s "$root/previous-state.before" "$root/app/deployment.previous.state"
    grep -Fxq "previousSha=$OLDER_SHA" "$root/app/runtime-config/state"
    grep -Fxq "previousDigest=$OLDER_RUNTIME" "$root/app/runtime-config/state"
  else
    [ ! -e "$root/app/runtime-config/previous" ] && [ ! -L "$root/app/runtime-config/previous" ]
    [ ! -e "$root/app/deployment.previous.state" ] && [ ! -L "$root/app/deployment.previous.state" ]
  fi
  grep -q '"status":"ROLLED_BACK"' "$root/report.log"
  ! grep -q '"status":"FAILED"' "$root/report.log"
  grep -q "${CURRENT_RUNTIME#sha256:}/compose.yaml.*up -d --wait --no-deps api web" "$root/docker.log"
  [ "$(grep -c '^exec ' "$root/docker.log")" -ge 2 ]
  assert_no_destructive_volume_operation "$root"
}

now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
success="$TEMP_ROOT/success"
prepare_fixture "$success" "$now" '1,2,3,4,5,6|6|0' pass
success_output="$(run_deploy "$success")"
grep -Fxq 'FORMDOCK_DEPLOY_RESULT=PASS' <<< "$success_output"
grep -Fxq "releaseGitSha=$CANDIDATE_SHA" "$success/app/deployment.state"
grep -Fxq "currentSha=$CANDIDATE_SHA" "$success/app/runtime-config/state"
grep -Fxq "previousSha=$CURRENT_SHA" "$success/app/runtime-config/state"
grep -Fxq 'stateRole=previous' "$success/app/deployment.previous.state"
grep -Fxq "FORMDOCK_API_IMAGE=ghcr.io/xxh3898/form-dock-api@$CANDIDATE_API" "$success/app/product.env"
[ "$(readlink "$success/app/runtime-config/current")" = "releases/${CANDIDATE_RUNTIME#sha256:}" ]
[ "$(readlink "$success/app/runtime-config/previous")" = "releases/${CURRENT_RUNTIME#sha256:}" ]
[ ! -e "$success/app/runtime-config/pending" ] && [ ! -L "$success/app/runtime-config/pending" ]
grep -q '"status":"SUCCESS"' "$success/report.log"
grep -q '"status":"REQUESTED"' "$success/report.log"
grep -q '"status":"RUNNING"' "$success/report.log"
grep -q -- '--proto =http http://127.0.0.1:18082/health' "$success/curl.log"
grep -q -- '--proto =https https://forms.chochiho.cloud/health' "$success/curl.log"
test "$(file_mode "$success/app/.formdock-operation.lock")" = 600
test "$(file_owner "$success/app/.formdock-operation.lock")" = "$(id -u)"
assert_no_destructive_volume_operation "$success"

for fail_step in \
  previous_pointer \
  current_pointer \
  product_env \
  previous_state \
  deployment_state \
  runtime_state \
  pending_unlink \
  terminal_success; do
  injected="$TEMP_ROOT/fail-$fail_step"
  prepare_fixture "$injected" "$now" '1,2,3,4,5,6|6|0' pass
  if run_deploy "$injected" "$fail_step" >/dev/null 2>&1; then
    printf 'Injected deployment failure unexpectedly passed: %s\n' "$fail_step" >&2
    exit 1
  fi
  assert_accepted_rollback "$injected"
done

existing_previous="$TEMP_ROOT/existing-previous"
prepare_fixture "$existing_previous" "$now" '1,2,3,4,5,6|6|0' pass
install_previous_authority "$existing_previous"
if run_deploy "$existing_previous" deployment_state >/dev/null 2>&1; then
  printf '%s\n' 'Existing previous authority failure injection unexpectedly passed.' >&2
  exit 1
fi
assert_accepted_rollback "$existing_previous" present

report_failure="$TEMP_ROOT/report-failure"
prepare_fixture "$report_failure" "$now" '1,2,3,4,5,6|6|0' pass
printf '%s\n' fail > "$report_failure/success-report.result"
if run_deploy "$report_failure" >/dev/null 2>&1; then
  printf '%s\n' 'Failed HomeOps terminal evidence unexpectedly passed.' >&2
  exit 1
fi
grep -q '"status":"RUNNING"' "$report_failure/report.log"
! grep -q '"status":"SUCCESS"' "$report_failure/report.log"
assert_accepted_rollback "$report_failure"

failure="$TEMP_ROOT/failure"
prepare_fixture "$failure" "$now" '1,2,3,4,5,6|6|0' fail
if run_deploy "$failure" >/dev/null 2>&1; then
  printf '%s\n' 'Candidate health failure unexpectedly succeeded.' >&2
  exit 1
fi
grep -q '"status":"REQUESTED"' "$failure/report.log"
grep -q '"status":"RUNNING"' "$failure/report.log"
assert_accepted_rollback "$failure"

pending_migration="$TEMP_ROOT/pending-migration"
prepare_fixture "$pending_migration" "$now" '1,2,3,4,5,6,7|7|0' pass
if run_deploy "$pending_migration" >/dev/null 2>&1; then
  printf '%s\n' 'Pending Flyway migration unexpectedly passed.' >&2
  exit 1
fi
test ! -s "$pending_migration/report.log"

stale_backup="$TEMP_ROOT/stale-backup"
prepare_fixture "$stale_backup" '2020-01-01T00:00:00Z' '1,2,3,4,5,6|6|0' pass
if run_deploy "$stale_backup" >/dev/null 2>&1; then
  printf '%s\n' 'Stale backup unexpectedly passed.' >&2
  exit 1
fi
test ! -s "$stale_backup/report.log"

missing_backup="$TEMP_ROOT/missing-backup"
prepare_fixture "$missing_backup" "$now" '1,2,3,4,5,6|6|0' pass
unlink "$missing_backup/backup/formdock-fixture.meta"
if run_deploy "$missing_backup" >/dev/null 2>&1; then
  printf '%s\n' 'Missing backup evidence unexpectedly passed.' >&2
  exit 1
fi
test ! -s "$missing_backup/report.log"

locked="$TEMP_ROOT/locked"
prepare_fixture "$locked" "$now" '1,2,3,4,5,6|6|0' pass
touch "$locked/app/.formdock-operation.lock"
chmod 600 "$locked/app/.formdock-operation.lock"
(
  exec 8>>"$locked/app/.formdock-operation.lock"
  if command -v lockf >/dev/null 2>&1; then
    lockf -s 8
  else
    flock -x 8
  fi
  : > "$locked/lock-held"
  sleep 10
) &
lock_holder=$!
for _ in 1 2 3 4 5; do
  [ -f "$locked/lock-held" ] && break
  sleep 1
done
[ -f "$locked/lock-held" ]
set +e
run_deploy "$locked" >/dev/null 2>&1
lock_status="$?"
set -e
kill "$lock_holder" 2>/dev/null || true
wait "$lock_holder" 2>/dev/null || true
[ "$lock_status" -eq 75 ]
test ! -s "$locked/report.log"

symlink_lock="$TEMP_ROOT/symlink-lock"
prepare_fixture "$symlink_lock" "$now" '1,2,3,4,5,6|6|0' pass
printf '%s\n' unchanged > "$symlink_lock/lock-target"
ln -s "$symlink_lock/lock-target" "$symlink_lock/app/.formdock-operation.lock"
if run_deploy "$symlink_lock" >/dev/null 2>&1; then
  printf '%s\n' 'Symlink operation lock unexpectedly passed.' >&2
  exit 1
fi
grep -Fxq unchanged "$symlink_lock/lock-target"
test ! -s "$symlink_lock/report.log"

directory_lock="$TEMP_ROOT/directory-lock"
prepare_fixture "$directory_lock" "$now" '1,2,3,4,5,6|6|0' pass
mkdir "$directory_lock/app/.formdock-operation.lock"
if run_deploy "$directory_lock" >/dev/null 2>&1; then
  printf '%s\n' 'Directory operation lock unexpectedly passed.' >&2
  exit 1
fi
test -d "$directory_lock/app/.formdock-operation.lock"
test ! -s "$directory_lock/report.log"

permissive_lock="$TEMP_ROOT/permissive-lock"
prepare_fixture "$permissive_lock" "$now" '1,2,3,4,5,6|6|0' pass
touch "$permissive_lock/app/.formdock-operation.lock"
chmod 644 "$permissive_lock/app/.formdock-operation.lock"
if run_deploy "$permissive_lock" >/dev/null 2>&1; then
  printf '%s\n' 'Permissive operation lock unexpectedly passed.' >&2
  exit 1
fi
test "$(file_mode "$permissive_lock/app/.formdock-operation.lock")" = 644
test ! -s "$permissive_lock/report.log"

valid_lock="$TEMP_ROOT/valid-lock"
prepare_fixture "$valid_lock" "$now" '1,2,3,4,5,6|6|0' pass
touch "$valid_lock/app/.formdock-operation.lock"
chmod 600 "$valid_lock/app/.formdock-operation.lock"
valid_lock_output="$(run_deploy "$valid_lock")"
grep -Fxq 'FORMDOCK_DEPLOY_RESULT=PASS' <<< "$valid_lock_output"
test "$(file_mode "$valid_lock/app/.formdock-operation.lock")" = 600

if "$DEPLOY" bad xxh3898 123 >/dev/null 2>&1; then
  printf '%s\n' 'Invalid deployment input unexpectedly passed.' >&2
  exit 1
fi

printf 'FORMDOCK_RECURRING_DEPLOY_TEST=PASS\n'
