#!/usr/bin/env bash
set -euo pipefail

readonly ALLOWED_REPOSITORY='xxh3898/form-dock'
readonly ALLOWED_ENVIRONMENT='Production'

hold() {
  printf 'status=HOLD\n'
  printf 'reason=%s\n' "$1"
  printf 'baselineSha=\n'
  exit 0
}

invalid() {
  printf 'FORMDOCK_CD_BASELINE_INVALID: %s\n' "$1" >&2
  exit 64
}

is_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] \
    && [ "$1" != 0000000000000000000000000000000000000000 ]
}

repository="${FORMDOCK_BASELINE_REPOSITORY:-$ALLOWED_REPOSITORY}"
environment="${FORMDOCK_BASELINE_ENVIRONMENT:-$ALLOWED_ENVIRONMENT}"
current_sha="${FORMDOCK_BASELINE_CURRENT_SHA:-}"
gh_bin="${FORMDOCK_BASELINE_GH_BIN:-gh}"

[ "$repository" = "$ALLOWED_REPOSITORY" ] \
  || invalid 'Repository is outside the exact allowlist.'
[ "$environment" = "$ALLOWED_ENVIRONMENT" ] \
  || invalid 'Environment is outside the exact allowlist.'
is_sha "$current_sha" || invalid 'Current main must be a non-zero lowercase 40-character SHA.'
command -v "$gh_bin" >/dev/null 2>&1 || invalid 'GitHub API client is unavailable.'
command -v jq >/dev/null 2>&1 || invalid 'jq is unavailable.'

git cat-file -e "${current_sha}^{commit}" 2>/dev/null \
  || invalid 'Current main commit is unavailable in the checkout.'

deployments_raw="$({
  "$gh_bin" api --paginate \
    "/repos/${repository}/deployments?environment=${environment}&per_page=100"
} 2>/dev/null)" || hold GITHUB_DEPLOYMENT_HISTORY_UNAVAILABLE

if [ -z "$deployments_raw" ]; then
  deployments_json='[]'
else
  deployments_json="$(printf '%s\n' "$deployments_raw" | jq -sc 'add')" \
    || hold MALFORMED_DEPLOYMENT_HISTORY
fi

jq -e --arg environment "$environment" '
  type == "array" and
  all(.[];
    .id as $id |
    type == "object" and
    ($id | type == "number") and
    ($id == ($id | floor)) and
    ($id > 0) and
    (.sha | type == "string") and
    (.environment == $environment) and
    (.created_at | type == "string" and test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"))
  )
' <<< "$deployments_json" >/dev/null \
  || hold MALFORMED_DEPLOYMENT_HISTORY

if [ "$(jq 'length' <<< "$deployments_json")" -eq 0 ]; then
  hold NO_ACCEPTED_PRODUCTION_BASELINE
fi

successful_candidates='[]'
while IFS=$'\t' read -r deployment_id deployment_sha created_at; do
  is_sha "$deployment_sha" || hold MALFORMED_DEPLOYMENT_HISTORY
  statuses_raw="$({
    "$gh_bin" api --paginate \
      "/repos/${repository}/deployments/${deployment_id}/statuses?per_page=100"
  } 2>/dev/null)" || hold GITHUB_DEPLOYMENT_STATUS_UNAVAILABLE

  if [ -z "$statuses_raw" ]; then
    statuses_json='[]'
  else
    statuses_json="$(printf '%s\n' "$statuses_raw" | jq -sc 'add')" \
      || hold MALFORMED_DEPLOYMENT_STATUS
  fi

  jq -e '
    type == "array" and
    all(.[];
      .id as $id |
      type == "object" and
      ($id | type == "number") and
      ($id == ($id | floor)) and
      ($id > 0) and
      (.state | type == "string") and
      (.created_at | type == "string" and test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"))
    )
  ' <<< "$statuses_json" >/dev/null \
    || hold MALFORMED_DEPLOYMENT_STATUS

  latest_state="$(jq -r 'sort_by(.created_at, .id) | reverse | .[0].state // ""' <<< "$statuses_json")"
  if [ "$latest_state" = success ]; then
    successful_candidates="$(
      jq --arg id "$deployment_id" --arg sha "$deployment_sha" --arg createdAt "$created_at" \
        '. + [{id: ($id | tonumber), sha: $sha, createdAt: $createdAt}]' \
        <<< "$successful_candidates"
    )"
  fi
done < <(
  jq -r 'sort_by(.created_at, .id) | reverse | .[] | [.id, .sha, .created_at] | @tsv' \
    <<< "$deployments_json"
)

if [ "$(jq 'length' <<< "$successful_candidates")" -eq 0 ]; then
  hold NO_ACCEPTED_PRODUCTION_BASELINE
fi

top_created_at="$(jq -r 'sort_by(.createdAt, .id) | reverse | .[0].createdAt' <<< "$successful_candidates")"
if [ "$(jq --arg value "$top_created_at" '[.[] | select(.createdAt == $value) | .sha] | unique | length' <<< "$successful_candidates")" -ne 1 ]; then
  hold AMBIGUOUS_SUCCESSFUL_PRODUCTION_BASELINE
fi

baseline_sha="$(jq -r 'sort_by(.createdAt, .id) | reverse | .[0].sha' <<< "$successful_candidates")"
is_sha "$baseline_sha" || hold MALFORMED_DEPLOYMENT_HISTORY
git cat-file -e "${baseline_sha}^{commit}" 2>/dev/null \
  || hold BASELINE_NOT_IN_MAIN_HISTORY
git merge-base --is-ancestor "$baseline_sha" "$current_sha" \
  || hold BASELINE_NOT_IN_MAIN_HISTORY

printf 'status=RESOLVED\n'
printf 'reason=NONE\n'
printf 'baselineSha=%s\n' "$baseline_sha"
