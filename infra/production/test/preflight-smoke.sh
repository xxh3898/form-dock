#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PRODUCTION_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
PREFLIGHT="$PRODUCTION_DIR/preflight.sh"

tmp_base="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-preflight-smoke.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-preflight-smoke.*) ;;
  *) printf 'ERROR: Preflight smoke temporary directory escaped the expected root.\n' >&2; exit 1 ;;
esac
chmod 700 "$temp_root"

cleanup() {
  case "$temp_root" in
    "$tmp_base"/formdock-preflight-smoke.*) find "$temp_root" -depth -delete ;;
  esac
}
trap cleanup EXIT

write_fixture() {
  local target="$1"
  {
    printf 'hostArchitecture=arm64\n'
    printf 'macosVersion=26.6.2\n'
    printf 'dockerArchitecture=arm64\n'
    printf 'dockerEngineVersion=29.6.2\n'
    printf 'dockerComposeVersion=5.3.1\n'
    printf 'diskAvailableKiB=59978547\n'
    printf 'diskAvailablePercent=27\n'
    printf 'webPortListenerCount=0\n'
    printf 'projectContainerCount=0\n'
    printf 'projectNetworkCount=0\n'
    printf 'projectVolumeCount=0\n'
    printf 'exactNameConflictCount=0\n'
    printf 'privateConfigPresent=0\n'
    printf 'deploymentStatePresent=0\n'
    printf 'operationLockPresent=0\n'
    printf 'localBackupRootStatus=PARENT_READY_D2_CREATE\n'
    printf 'edgeNetworkStatus=READY\n'
    printf 'edgeAliasConflictCount=0\n'
    printf 'cloudflaredEdgeStatus=ATTACHED\n'
    printf 'cloudflareDnsStatus=NXDOMAIN\n'
    printf 'homeOpsWebStatus=healthy\n'
    printf 'homeOpsApiStatus=healthy\n'
    printf 'homeOpsDbStatus=healthy\n'
    printf 'apiRemoteDigest=sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b\n'
    printf 'apiRemotePlatform=linux/arm64\n'
    printf 'webRemoteDigest=sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551\n'
    printf 'webRemotePlatform=linux/arm64\n'
  } > "$target"
}

replace_fixture_value() {
  local source="$1"
  local target="$2"
  local key="$3"
  local value="$4"
  awk -F= -v wanted="$key" -v replacement="$value" '
    $1 == wanted { print wanted "=" replacement; next }
    { print }
  ' "$source" > "$target"
}

run_fixture() {
  local fixture="$1"
  FORMDOCK_PREFLIGHT_SCOPE=fixture \
  FORMDOCK_PREFLIGHT_FIXTURE_FILE="$fixture" \
  FORMDOCK_PREFLIGHT_EXPECTED_PROJECT=form-dock \
  FORMDOCK_PREFLIGHT_EXPECTED_WEB_PORT=18082 \
  FORMDOCK_PREFLIGHT_EXPECTED_RELEASE_SHA=1648047645720e67d5e928345c875dc53a93ff0e \
  FORMDOCK_PREFLIGHT_EXPECTED_API_IMAGE='ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b' \
  FORMDOCK_PREFLIGHT_EXPECTED_WEB_IMAGE='ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551' \
    "$PREFLIGHT"
}

expect_blocked() {
  local key="$1"
  local value="$2"
  local fixture="$temp_root/blocked-$key.fixture"
  local output exit_code
  replace_fixture_value "$base_fixture" "$fixture" "$key" "$value"
  set +e
  output="$(run_fixture "$fixture" 2>&1)"
  exit_code=$?
  set -e
  [ "$exit_code" = 2 ] && [ "$output" = FORMDOCK_PREFLIGHT_BLOCKED ] || {
    printf 'ERROR: Preflight did not fail closed for %s.\n' "$key" >&2
    exit 1
  }
}

base_fixture="$temp_root/pass.fixture"
write_fixture "$base_fixture"
pass_output="$(run_fixture "$base_fixture")"
[ "$(printf '%s\n' "$pass_output" | wc -l | tr -d ' ')" = 48 ]
printf '%s\n' "$pass_output" | awk -F= '
  NF != 2 || seen[$1]++ { exit 1 }
  { count += 1 }
  END { if (count != 48) exit 1 }
'

for expected in \
  'result=PASS' \
  'evidenceMode=fixture' \
  'activationClass=FIRST_ACTIVATION' \
  'databaseClass=FRESH_PRODUCTION_DB' \
  'offHostDurabilityStatus=DEFERRED_ACCEPTED_RISK' \
  'currentIndependentOffHostTarget=NONE' \
  'firstActivationAllowed=true' \
  'cloudflareRouteState=ROUTE_ABSENT_DNS_NXDOMAIN' \
  'monitoringProvider=HomeOps' \
  'outboundNotification=DISABLED_BY_OPERATOR_CHOICE' \
  'remainingD1Blockers=0' \
  'productionMutationCount=0' \
  'secretValueReadCount=0'; do
  printf '%s\n' "$pass_output" | grep -Fxq "$expected"
done

printf '%s\n' "$pass_output" \
  | grep -Eqi '(/Users/|password=|token=|private.?key=|secret=)' \
  && {
    printf 'ERROR: Preflight output contains a forbidden sensitive field.\n' >&2
    exit 1
  }

expect_blocked hostArchitecture x86_64
expect_blocked webPortListenerCount 1
expect_blocked projectContainerCount 1
expect_blocked exactNameConflictCount 1
expect_blocked privateConfigPresent 1
expect_blocked deploymentStatePresent 1
expect_blocked operationLockPresent 1
expect_blocked localBackupRootStatus AMBIGUOUS
expect_blocked edgeNetworkStatus AMBIGUOUS
expect_blocked edgeAliasConflictCount 1
expect_blocked cloudflaredEdgeStatus AMBIGUOUS
expect_blocked cloudflareDnsStatus NOERROR
expect_blocked homeOpsApiStatus unhealthy
expect_blocked apiRemoteDigest sha256:0000000000000000000000000000000000000000000000000000000000000000

duplicate_fixture="$temp_root/duplicate.fixture"
cp "$base_fixture" "$duplicate_fixture"
printf 'hostArchitecture=arm64\n' >> "$duplicate_fixture"
set +e
duplicate_output="$(run_fixture "$duplicate_fixture" 2>&1)"
duplicate_exit=$?
set -e
[ "$duplicate_exit" = 64 ] && [ "$duplicate_output" = FORMDOCK_PREFLIGHT_CONFIG_INVALID ]

unknown_fixture="$temp_root/unknown.fixture"
cp "$base_fixture" "$unknown_fixture"
printf 'unexpectedField=value\n' >> "$unknown_fixture"
set +e
unknown_output="$(run_fixture "$unknown_fixture" 2>&1)"
unknown_exit=$?
set -e
[ "$unknown_exit" = 64 ] && [ "$unknown_output" = FORMDOCK_PREFLIGHT_CONFIG_INVALID ]

if grep -En \
  'docker[[:space:]]+(compose[[:space:]]+)?(build|create|exec|kill|login|logout|pause|pull|push|restart|rm|rmi|run|start|stop|tag|unpause|update)' \
  "$PREFLIGHT"; then
  printf 'ERROR: Read-only preflight contains a Docker mutation command.\n' >&2
  exit 1
fi
if grep -En '(cloudflared[[:space:]]+(tunnel|route)|curl[[:space:]].*(-X|--request)|docker[[:space:]]+compose[[:space:]]+(up|down|pull))' \
  "$PREFLIGHT"; then
  printf 'ERROR: Read-only preflight contains a target mutation command.\n' >&2
  exit 1
fi

printf 'PREFLIGHT_FIXTURE_PASS=PASS\n'
printf 'PREFLIGHT_AMBIGUOUS_STATE=PASS\n'
printf 'PREFLIGHT_FIXED_OUTPUT=PASS\n'
printf 'PREFLIGHT_SECRET_BOUNDARY=PASS\n'
printf 'PREFLIGHT_MUTATION_AUDIT=PASS\n'
