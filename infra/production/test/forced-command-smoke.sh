#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
FORCED_COMMAND="$SCRIPT_DIR/../forced-command.sh.example"
ZERO_DIGEST=sha256:0000000000000000000000000000000000000000000000000000000000000000
SHA=1111111111111111111111111111111111111111
API_DIGEST=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
WEB_DIGEST=sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
RUNTIME_DIGEST=sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
VALID="deploy-formdock-v1 form-dock $SHA $API_DIGEST $WEB_DIGEST $RUNTIME_DIGEST xxh3898 github-actions[bot] 123456789"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-forced-command.XXXXXX")"
trap 'find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -depth -type d -exec rmdir {} \; 2>/dev/null || true' EXIT

# shellcheck source=../forced-command.sh.example
source "$FORCED_COMMAND"

formdock_parse_forced_command "$VALID"
[ "$FORMDOCK_FORCED_COMMIT_SHA" = "$SHA" ]
[ "$FORMDOCK_FORCED_API_DIGEST" = "$API_DIGEST" ]
[ "$FORMDOCK_FORCED_WEB_DIGEST" = "$WEB_DIGEST" ]
[ "$FORMDOCK_FORCED_RUNTIME_DIGEST" = "$RUNTIME_DIGEST" ]
[ "$FORMDOCK_FORCED_REGISTRY_USER" = 'github-actions[bot]' ]
[ "$FORMDOCK_FORCED_WORKFLOW_RUN_ID" = 123456789 ]

assert_rejected() {
  local command="$1"
  if (formdock_parse_forced_command "$command") >/dev/null 2>&1; then
    printf 'Unexpected accepted command: %s\n' "$command" >&2
    exit 1
  fi
}

assert_rejected "deploy-formdock-v1 other $SHA $API_DIGEST $WEB_DIGEST $RUNTIME_DIGEST xxh3898 github-actions[bot] 123"
assert_rejected "deploy-formdock-v1 form-dock bad $API_DIGEST $WEB_DIGEST $RUNTIME_DIGEST xxh3898 github-actions[bot] 123"
assert_rejected "deploy-formdock-v1 form-dock $SHA $ZERO_DIGEST $WEB_DIGEST $RUNTIME_DIGEST xxh3898 github-actions[bot] 123"
assert_rejected "deploy-formdock-v1 form-dock $SHA $API_DIGEST $WEB_DIGEST $RUNTIME_DIGEST other github-actions[bot] 123"
assert_rejected "$VALID unexpected"
assert_rejected "arbitrary-shell-command"

release="$TEMP_ROOT/release"
mkdir -p "$release/scripts"
printf '%s\n' 'services: {}' > "$release/compose.yaml"
printf '%s\n' "$SHA" > "$release/revision"
for file in deploy-release.sh report-homeops-deployment.sh verify-backup.sh common.sh delivery-common.sh; do
  printf '%s\n' '#!/bin/bash' > "$release/scripts/$file"
done
formdock_validate_release_shape "$release"

printf '%s\n' '# unexpected' > "$release/unexpected"
if (formdock_validate_release_shape "$release") >/dev/null 2>&1; then
  printf '%s\n' 'Unexpected runtime-config entry was accepted.' >&2
  exit 1
fi

printf 'FORMDOCK_FORCED_COMMAND_TEST=PASS\n'
