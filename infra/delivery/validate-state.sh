#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

[ "$#" -ge 1 ] && [ "$#" -le 2 ] \
  || formdock_delivery_die 'Usage: validate-state.sh <state-file> [candidate|previous]'

state_file="$1"
expected_role="${2:-}"
formdock_delivery_validate_state "$state_file" "$expected_role"

role="$(formdock_delivery_value "$state_file" stateRole)"
previous_state="$(formdock_delivery_value "$state_file" previousStateSha256)"

printf 'DEPLOYMENT_STATE_VALID=PASS\n'
printf 'STATE_ROLE=%s\n' "$role"
if [ "$previous_state" = NONE ]; then
  printf 'PREVIOUS_STATE=NONE\n'
else
  printf 'PREVIOUS_STATE=LINKED\n'
fi
