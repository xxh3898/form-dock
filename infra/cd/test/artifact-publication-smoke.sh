#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
RESOLVER="$SCRIPT_DIR/../resolve-artifact-publication.sh"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-artifact-publication.XXXXXX")"
trap 'find "$TEMP_ROOT" -type l -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -depth -type d -exec rmdir {} \; 2>/dev/null || true' EXIT

SHA=1111111111111111111111111111111111111111
SOURCE=https://github.com/xxh3898/form-dock
API_DIGEST=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
WEB_DIGEST=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
RUNTIME_DIGEST=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
MOCK_DOCKER="$TEMP_ROOT/docker"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'printf "%s\n" "$*" >> "$FORMDOCK_FIXTURE_DOCKER_LOG"' \
  'component() {' \
  '  case "$1" in *form-dock-api:*) printf "%s\n" api ;; *form-dock-web:*) printf "%s\n" web ;; *form-dock-runtime-config:*) printf "%s\n" runtime-config ;; *) exit 1 ;; esac' \
  '}' \
  'if [ "$1" = pull ]; then' \
  '  target_component="$(component "$2")"' \
  '  case "$FORMDOCK_FIXTURE_STATE:$target_component" in' \
  '    all_absent:*|api_only:web|api_only:runtime-config|api_web_only:runtime-config) printf "%s\n" "manifest unknown" >&2; exit 1 ;;' \
  '    registry_error:*) printf "%s\n" "denied: registry unavailable" >&2; exit 1 ;;' \
  '  esac' \
  '  exit 0' \
  'fi' \
  'test "$1" = image && test "$2" = inspect && test "$3" = --format' \
  'format="$4"' \
  'tag="$5"' \
  'target_component="$(component "$tag")"' \
  'case "$format" in' \
  '  "{{.Architecture}}/{{.Os}}") if [ "$FORMDOCK_FIXTURE_STATE" = platform_mismatch ] && [ "$target_component" = api ]; then printf "%s\n" amd64/linux; else printf "%s\n" arm64/linux; fi ;;' \
  '  *org.opencontainers.image.source*) printf "%s\n" "$FORMDOCK_FIXTURE_SOURCE" ;;' \
  '  *org.opencontainers.image.revision*) if [ "$FORMDOCK_FIXTURE_STATE" = revision_mismatch ] && [ "$target_component" = api ]; then printf "%040d\n" 2; else printf "%s\n" "$FORMDOCK_FIXTURE_SHA"; fi ;;' \
  '  *org.opencontainers.image.version*) printf "%s\n" "sha-$FORMDOCK_FIXTURE_SHA" ;;' \
  '  *io.formdock.image.component*) if [ "$FORMDOCK_FIXTURE_STATE" = component_mismatch ] && [ "$target_component" = web ]; then printf "%s\n" api; else printf "%s\n" "$target_component"; fi ;;' \
  '  *io.formdock.runtime-config.project*) if [ "$FORMDOCK_FIXTURE_STATE" = project_mismatch ]; then printf "%s\n" other; else printf "%s\n" form-dock; fi ;;' \
  '  *RepoDigests*) case "$target_component" in api) digest="$FORMDOCK_FIXTURE_API_DIGEST" ;; web) digest="$FORMDOCK_FIXTURE_WEB_DIGEST" ;; runtime-config) digest="$FORMDOCK_FIXTURE_RUNTIME_DIGEST" ;; esac; printf "%s@%s\n" "${tag%:sha-*}" "$digest" ;;' \
  '  *) exit 1 ;;' \
  'esac' \
  > "$MOCK_DOCKER"
chmod 700 "$MOCK_DOCKER"

run_case() {
  local state="$1"
  : > "$TEMP_ROOT/docker.log"
  FORMDOCK_ARTIFACT_TEST_MODE=fixture \
  FORMDOCK_ARTIFACT_DOCKER_BIN="$MOCK_DOCKER" \
  FORMDOCK_FIXTURE_DOCKER_LOG="$TEMP_ROOT/docker.log" \
  FORMDOCK_FIXTURE_STATE="$state" \
  FORMDOCK_FIXTURE_SOURCE="$SOURCE" \
  FORMDOCK_FIXTURE_SHA="$SHA" \
  FORMDOCK_FIXTURE_API_DIGEST="$API_DIGEST" \
  FORMDOCK_FIXTURE_WEB_DIGEST="$WEB_DIGEST" \
  FORMDOCK_FIXTURE_RUNTIME_DIGEST="$RUNTIME_DIGEST" \
    "$RESOLVER" "$SHA" "$SOURCE"
}

absent_output="$(run_case all_absent)"
grep -Fxq 'mode=PUBLISH' <<< "$absent_output"
grep -Fxq 'reason=ALL_ARTIFACTS_ABSENT' <<< "$absent_output"

present_output="$(run_case all_present)"
grep -Fxq 'mode=REUSE' <<< "$present_output"
grep -Fxq "apiDigest=$API_DIGEST" <<< "$present_output"
grep -Fxq "webDigest=$WEB_DIGEST" <<< "$present_output"
grep -Fxq "runtimeDigest=$RUNTIME_DIGEST" <<< "$present_output"
test "$(grep -Ec '(^| )push( |$)' "$TEMP_ROOT/docker.log" || true)" = 0

replay_output="$(run_case all_present)"
test "$replay_output" = "$present_output"
test "$(grep -Ec '(^| )push( |$)' "$TEMP_ROOT/docker.log" || true)" = 0

for partial in api_only api_web_only; do
  partial_output="$(run_case "$partial")"
  grep -Fxq 'mode=HOLD' <<< "$partial_output"
  grep -Fxq 'reason=PARTIAL_ARTIFACT_SET' <<< "$partial_output"
done

for mismatch in revision_mismatch component_mismatch platform_mismatch project_mismatch; do
  mismatch_output="$(run_case "$mismatch")"
  grep -Fxq 'mode=HOLD' <<< "$mismatch_output"
  grep -Fxq 'reason=ARTIFACT_IDENTITY_MISMATCH' <<< "$mismatch_output"
done

error_output="$(run_case registry_error)"
grep -Fxq 'mode=HOLD' <<< "$error_output"
grep -Fxq 'reason=REGISTRY_STATE_UNAVAILABLE' <<< "$error_output"

printf 'FORMDOCK_ARTIFACT_PUBLICATION_TEST=PASS\n'
