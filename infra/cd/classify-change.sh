#!/usr/bin/env bash
set -euo pipefail

die() {
  printf 'FORMDOCK_CD_CLASSIFIER_INVALID: %s\n' "$1" >&2
  exit 64
}

is_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] \
    && [ "$1" != 0000000000000000000000000000000000000000 ]
}

classify_path() {
  local path="$1"

  case "$path" in
    backend/src/main/resources/db/migration/*)
      printf '%s\n' MIGRATION_OR_DATA
      ;;
    .github/workflows/*|.github/actions/*|infra/compose.production.yaml|infra/production.env.example|infra/production/*|infra/delivery/*|infra/backup/*|infra/monitoring/*|infra/cd/*|runtime-config.Dockerfile)
      printf '%s\n' DEPLOY_CONTROL
      ;;
    backend/README.md|frontend/README.md|infra/README.md)
      printf '%s\n' DOCS_META_ONLY
      ;;
    backend/*|frontend/*)
      printf '%s\n' APPLICATION_ONLY
      ;;
    README.md|AGENTS.md|LICENSE|LICENSE.*|docs/*|.github/ISSUE_TEMPLATE/*|.github/pull_request_template.md|.github/PULL_REQUEST_TEMPLATE.md|.github/PULL_REQUEST_TEMPLATE/*|.editorconfig|.gitattributes|.gitignore)
      printf '%s\n' DOCS_META_ONLY
      ;;
    *)
      printf '%s\n' UNKNOWN
      ;;
  esac
}

priority() {
  case "$1" in
    DOCS_META_ONLY) printf '%s\n' 0 ;;
    APPLICATION_ONLY) printf '%s\n' 1 ;;
    UNKNOWN) printf '%s\n' 2 ;;
    DEPLOY_CONTROL) printf '%s\n' 3 ;;
    MIGRATION_OR_DATA) printf '%s\n' 4 ;;
    *) die 'Unexpected internal classification.' ;;
  esac
}

if [ "$#" -ne 2 ]; then
  die 'Expected baseline SHA and current main SHA.'
fi

baseline_sha="$1"
current_sha="$2"
is_sha "$baseline_sha" || die 'Baseline must be a non-zero lowercase 40-character SHA.'
is_sha "$current_sha" || die 'Current main must be a non-zero lowercase 40-character SHA.'

paths_file="${FORMDOCK_CLASSIFIER_PATHS_FILE:-}"
if [ -n "$paths_file" ]; then
  [ -f "$paths_file" ] && [ ! -L "$paths_file" ] \
    || die 'Fixture paths file must be a regular non-symlink file.'
  input_command=(cat -- "$paths_file")
else
  git cat-file -e "${baseline_sha}^{commit}" 2>/dev/null \
    || die 'Baseline commit is unavailable.'
  git cat-file -e "${current_sha}^{commit}" 2>/dev/null \
    || die 'Current main commit is unavailable.'
  git merge-base --is-ancestor "$baseline_sha" "$current_sha" \
    || die 'Baseline is not an ancestor of current main.'
  input_command=(git diff --name-only "${baseline_sha}..${current_sha}")
fi

classification=DOCS_META_ONLY
classification_priority=0
changed_count=0

while IFS= read -r path; do
  [ -n "$path" ] || continue
  case "$path" in
    /*|../*|*/../*|*/..|.) die 'Changed path is not repository-relative.' ;;
  esac
  path_class="$(classify_path "$path")"
  path_priority="$(priority "$path_class")"
  if [ "$path_priority" -gt "$classification_priority" ]; then
    classification="$path_class"
    classification_priority="$path_priority"
  fi
  changed_count=$((changed_count + 1))
done < <("${input_command[@]}")

if [ "$classification" = APPLICATION_ONLY ]; then
  deploy_eligible=true
  hold_reason=NONE
else
  deploy_eligible=false
  case "$classification" in
    DOCS_META_ONLY) hold_reason=NO_APPLICATION_CHANGE ;;
    DEPLOY_CONTROL) hold_reason=DEPLOY_CONTROL_REQUIRES_SEPARATE_ACCEPTANCE ;;
    MIGRATION_OR_DATA) hold_reason=MIGRATION_OR_DATA_REQUIRES_SEPARATE_OPS ;;
    UNKNOWN) hold_reason=UNKNOWN_PATH_FAIL_CLOSED ;;
  esac
fi

printf 'classification=%s\n' "$classification"
printf 'changedFileCount=%s\n' "$changed_count"
printf 'deployEligible=%s\n' "$deploy_eligible"
printf 'holdReason=%s\n' "$hold_reason"
