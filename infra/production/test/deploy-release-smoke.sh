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
    'printf "%s\\n" "$payload" >> "$FORMDOCK_FIXTURE_REPORT_LOG"' \
    'if [[ "$payload" == *'"'"'"status":"SUCCESS"'"'"'* ]] && [ "$FORMDOCK_FIXTURE_SUCCESS_REPORT_RESULT" = fail ]; then exit 1; fi'
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
  FORMDOCK_DEPLOY_TEST_MODE=fixture \
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
if grep -Eq 'down.*--volumes|--volumes.*down' "$success/docker.log"; then
  printf '%s\n' 'Deployment attempted destructive volume removal.' >&2
  exit 1
fi

report_failure="$TEMP_ROOT/report-failure"
prepare_fixture "$report_failure" "$now" '1,2,3,4,5,6|6|0' pass
printf '%s\n' fail > "$report_failure/success-report.result"
if run_deploy "$report_failure" >/dev/null 2>&1; then
  printf '%s\n' 'Failed HomeOps terminal evidence unexpectedly passed.' >&2
  exit 1
fi
grep -Fxq "releaseGitSha=$CANDIDATE_SHA" "$report_failure/app/deployment.state"
grep -q '"status":"RUNNING"' "$report_failure/report.log"
grep -q '"status":"SUCCESS"' "$report_failure/report.log"
grep -q '"status":"FAILED"' "$report_failure/report.log"

failure="$TEMP_ROOT/failure"
prepare_fixture "$failure" "$now" '1,2,3,4,5,6|6|0' fail
if run_deploy "$failure" >/dev/null 2>&1; then
  printf '%s\n' 'Candidate health failure unexpectedly succeeded.' >&2
  exit 1
fi
grep -Fxq "releaseGitSha=$CURRENT_SHA" "$failure/app/deployment.state"
grep -Fxq "currentSha=$CURRENT_SHA" "$failure/app/runtime-config/state"
grep -Fxq "FORMDOCK_API_IMAGE=ghcr.io/xxh3898/form-dock-api@$CURRENT_API" "$failure/app/product.env"
[ "$(readlink "$failure/app/runtime-config/current")" = "releases/${CURRENT_RUNTIME#sha256:}" ]
grep -q '"status":"ROLLED_BACK"' "$failure/report.log"
grep -q '"status":"REQUESTED"' "$failure/report.log"
grep -q '"status":"RUNNING"' "$failure/report.log"
grep -q "${CURRENT_RUNTIME#sha256:}/compose.yaml" "$failure/docker.log"
if grep -Eq 'down.*--volumes|--volumes.*down' "$failure/docker.log"; then
  printf '%s\n' 'Rollback attempted destructive volume removal.' >&2
  exit 1
fi

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

if "$DEPLOY" bad xxh3898 123 >/dev/null 2>&1; then
  printf '%s\n' 'Invalid deployment input unexpectedly passed.' >&2
  exit 1
fi

printf 'FORMDOCK_RECURRING_DEPLOY_TEST=PASS\n'
