#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
CLASSIFIER="$SCRIPT_DIR/../classify-change.sh"
CURRENT_SHA="$(git rev-parse HEAD)"
BASELINE_SHA="$(git rev-list --max-parents=0 HEAD | tail -n 1)"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-classifier.XXXXXX")"
trap 'find "$TEMP_ROOT" -type l -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -depth -type d -exec rmdir {} \; 2>/dev/null || true' EXIT

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

assert_git_rename_case() {
  local name="$1"
  local source_path="$2"
  local destination_path="$3"
  local expected="$4"
  local repository="$TEMP_ROOT/git-$name"
  local baseline current output

  mkdir -p "$repository/$(dirname "$source_path")"
  git -C "$repository" init -q
  git -C "$repository" config user.name 'FormDock Fixture'
  git -C "$repository" config user.email 'fixture@example.test'
  printf '%s\n' "$name" > "$repository/$source_path"
  git -C "$repository" add -- "$source_path"
  git -C "$repository" commit -qm 'fixture baseline'
  baseline="$(git -C "$repository" rev-parse HEAD)"

  mkdir -p "$repository/$(dirname "$destination_path")"
  git -C "$repository" mv -- "$source_path" "$destination_path"
  git -C "$repository" commit -qm 'fixture rename'
  current="$(git -C "$repository" rev-parse HEAD)"

  output="$(cd "$repository" && "$CLASSIFIER" "$baseline" "$current")"
  grep -Fxq "classification=$expected" <<< "$output"
  grep -Fxq 'changedFileCount=2' <<< "$output"
}

assert_git_rename_case deploy_to_docs \
  .github/workflows/deploy.yml docs/deploy.yml DEPLOY_CONTROL
assert_git_rename_case migration_to_application \
  backend/src/main/resources/db/migration/V7__fixture.sql \
  backend/src/main/java/com/formdock/Fixture.java MIGRATION_OR_DATA
assert_git_rename_case application_to_docs \
  frontend/src/Fixture.tsx docs/fixture.tsx APPLICATION_ONLY
assert_git_rename_case docs_to_docs \
  docs/old.md docs/new.md DOCS_META_ONLY

empty_file="$TEMP_ROOT/empty.paths"
: > "$empty_file"
empty_output="$(FORMDOCK_CLASSIFIER_PATHS_FILE="$empty_file" "$CLASSIFIER" "$BASELINE_SHA" "$CURRENT_SHA")"
grep -Fxq 'classification=DOCS_META_ONLY' <<< "$empty_output"
grep -Fxq 'changedFileCount=0' <<< "$empty_output"
grep -Fxq 'deployEligible=false' <<< "$empty_output"

printf 'FORMDOCK_CLASSIFIER_TEST=PASS\n'
