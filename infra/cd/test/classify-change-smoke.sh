#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
CLASSIFIER="$SCRIPT_DIR/../classify-change.sh"
CURRENT_SHA="$(git rev-parse HEAD)"
BASELINE_SHA="$(git rev-list --max-parents=0 HEAD | tail -n 1)"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-classifier.XXXXXX")"
trap 'find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true; rmdir "$TEMP_ROOT" 2>/dev/null || true' EXIT

assert_case() {
  local name="$1"
  local expected="$2"
  shift 2
  local paths_file="$TEMP_ROOT/$name.paths"
  local output

  printf '%s\n' "$@" > "$paths_file"
  output="$(
    FORMDOCK_CLASSIFIER_PATHS_FILE="$paths_file" \
      "$CLASSIFIER" "$BASELINE_SHA" "$CURRENT_SHA"
  )"
  grep -Fxq "classification=$expected" <<< "$output"
}

assert_case docs DOCS_META_ONLY docs/README.md README.md backend/README.md frontend/README.md infra/README.md
assert_case application APPLICATION_ONLY backend/src/main/java/com/formdock/App.java
assert_case docs_app APPLICATION_ONLY docs/README.md frontend/src/App.tsx
assert_case deploy_control DEPLOY_CONTROL .github/workflows/publish-and-deploy.yml
assert_case runtime_config DEPLOY_CONTROL runtime-config.Dockerfile
assert_case migration MIGRATION_OR_DATA backend/src/main/resources/db/migration/V7__future.sql
assert_case unknown UNKNOWN scripts/unclassified-tool.sh
assert_case priority_migration MIGRATION_OR_DATA infra/production/deploy-release.sh backend/src/main/resources/db/migration/V7__future.sql
assert_case cumulative_deploy_control DEPLOY_CONTROL infra/production/deploy-release.sh backend/src/main/java/com/formdock/App.java
assert_case cumulative_migration MIGRATION_OR_DATA backend/src/main/resources/db/migration/V7__future.sql frontend/src/App.tsx

empty_file="$TEMP_ROOT/empty.paths"
: > "$empty_file"
empty_output="$(FORMDOCK_CLASSIFIER_PATHS_FILE="$empty_file" "$CLASSIFIER" "$BASELINE_SHA" "$CURRENT_SHA")"
grep -Fxq 'classification=DOCS_META_ONLY' <<< "$empty_output"
grep -Fxq 'changedFileCount=0' <<< "$empty_output"
grep -Fxq 'deployEligible=false' <<< "$empty_output"

printf 'FORMDOCK_CLASSIFIER_TEST=PASS\n'
