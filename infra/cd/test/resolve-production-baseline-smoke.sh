#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
RESOLVER="$SCRIPT_DIR/../resolve-production-baseline.sh"
CURRENT_SHA="$(git rev-parse HEAD)"
BASELINE_SHA="$(git rev-list --max-parents=0 HEAD | tail -n 1)"
SECOND_SHA="$(git rev-list --reverse HEAD | sed -n '2p')"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-baseline.XXXXXX")"
MOCK_GH="$TEMP_ROOT/gh"
trap 'find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true; find "$TEMP_ROOT" -depth -type d -exec rmdir {} \; 2>/dev/null || true' EXIT

cat > "$MOCK_GH" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
test "$1" = api
test "$2" = --paginate
endpoint="$3"
case "$endpoint" in
  */deployments\?*) cat "$FORMDOCK_TEST_FIXTURE/deployments.jsons" ;;
  */deployments/*/statuses\?*)
    id="${endpoint#*/deployments/}"
    id="${id%%/*}"
    cat "$FORMDOCK_TEST_FIXTURE/status-$id.jsons"
    ;;
  *) exit 64 ;;
esac
MOCK
chmod 755 "$MOCK_GH"

write_status() {
  local fixture="$1"
  local id="$2"
  local state="$3"
  local created_at="$4"
  printf '[{"id":%s,"state":"%s","created_at":"%s"}]\n' "$id" "$state" "$created_at" \
    > "$fixture/status-$id.jsons"
}

run_case() {
  local fixture="$1"
  FORMDOCK_TEST_FIXTURE="$fixture" \
  FORMDOCK_BASELINE_GH_BIN="$MOCK_GH" \
  FORMDOCK_BASELINE_CURRENT_SHA="$CURRENT_SHA" \
    "$RESOLVER"
}

latest="$TEMP_ROOT/latest"
mkdir "$latest"
cat > "$latest/deployments.jsons" <<JSON
[{"id":3,"sha":"$CURRENT_SHA","environment":"Production","created_at":"2026-08-29T03:00:00Z"},{"id":2,"sha":"$SECOND_SHA","environment":"Production","created_at":"2026-08-29T02:00:00Z"},{"id":1,"sha":"$BASELINE_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]
JSON
write_status "$latest" 3 failure 2026-08-29T03:01:00Z
write_status "$latest" 2 success 2026-08-29T02:01:00Z
write_status "$latest" 1 success 2026-08-29T01:01:00Z
latest_output="$(run_case "$latest")"
grep -Fxq 'status=RESOLVED' <<< "$latest_output"
grep -Fxq "baselineSha=$SECOND_SHA" <<< "$latest_output"

pagination="$TEMP_ROOT/pagination"
mkdir "$pagination"
cat > "$pagination/deployments.jsons" <<JSON
[{"id":10,"sha":"$BASELINE_SHA","environment":"Production","created_at":"2026-08-28T01:00:00Z"}]
[{"id":11,"sha":"$SECOND_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]
JSON
write_status "$pagination" 10 success 2026-08-28T01:01:00Z
write_status "$pagination" 11 success 2026-08-29T01:01:00Z
pagination_output="$(run_case "$pagination")"
grep -Fxq "baselineSha=$SECOND_SHA" <<< "$pagination_output"

no_baseline="$TEMP_ROOT/no-baseline"
mkdir "$no_baseline"
printf '[]\n' > "$no_baseline/deployments.jsons"
no_baseline_output="$(run_case "$no_baseline")"
grep -Fxq 'status=HOLD' <<< "$no_baseline_output"
grep -Fxq 'reason=NO_ACCEPTED_PRODUCTION_BASELINE' <<< "$no_baseline_output"

malformed="$TEMP_ROOT/malformed"
mkdir "$malformed"
printf '[{"id":1,"sha":"bad","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]\n' > "$malformed/deployments.jsons"
malformed_output="$(run_case "$malformed")"
grep -Fxq 'reason=MALFORMED_DEPLOYMENT_HISTORY' <<< "$malformed_output"

pending="$TEMP_ROOT/pending"
mkdir "$pending"
cat > "$pending/deployments.jsons" <<JSON
[{"id":20,"sha":"$SECOND_SHA","environment":"Production","created_at":"2026-08-29T02:00:00Z"},{"id":19,"sha":"$BASELINE_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]
JSON
write_status "$pending" 20 pending 2026-08-29T02:01:00Z
write_status "$pending" 19 success 2026-08-29T01:01:00Z
pending_output="$(run_case "$pending")"
grep -Fxq "baselineSha=$BASELINE_SHA" <<< "$pending_output"

ambiguous="$TEMP_ROOT/ambiguous"
mkdir "$ambiguous"
cat > "$ambiguous/deployments.jsons" <<JSON
[{"id":31,"sha":"$SECOND_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"},{"id":30,"sha":"$BASELINE_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]
JSON
write_status "$ambiguous" 31 success 2026-08-29T01:01:00Z
write_status "$ambiguous" 30 success 2026-08-29T01:01:00Z
ambiguous_output="$(run_case "$ambiguous")"
grep -Fxq 'reason=AMBIGUOUS_SUCCESSFUL_PRODUCTION_BASELINE' <<< "$ambiguous_output"

same_identity="$TEMP_ROOT/same-identity"
mkdir "$same_identity"
cat > "$same_identity/deployments.jsons" <<JSON
[{"id":33,"sha":"$SECOND_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"},{"id":32,"sha":"$SECOND_SHA","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]
JSON
write_status "$same_identity" 33 success 2026-08-29T01:01:00Z
write_status "$same_identity" 32 success 2026-08-29T01:01:00Z
same_identity_output="$(run_case "$same_identity")"
grep -Fxq "baselineSha=$SECOND_SHA" <<< "$same_identity_output"

not_in_history="$TEMP_ROOT/not-in-history"
mkdir "$not_in_history"
unknown_sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
cat > "$not_in_history/deployments.jsons" <<JSON
[{"id":40,"sha":"$unknown_sha","environment":"Production","created_at":"2026-08-29T01:00:00Z"}]
JSON
write_status "$not_in_history" 40 success 2026-08-29T01:01:00Z
not_in_history_output="$(run_case "$not_in_history")"
grep -Fxq 'reason=BASELINE_NOT_IN_MAIN_HISTORY' <<< "$not_in_history_output"

printf 'FORMDOCK_BASELINE_RESOLVER_TEST=PASS\n'
