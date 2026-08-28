#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

formdock_require_env FORMDOCK_BACKUP_ROOT
formdock_require_env FORMDOCK_BACKUP_ID

backup_root="$(formdock_canonical_private_directory "$FORMDOCK_BACKUP_ROOT")"
formdock_verify_backup_set "$backup_root" "$FORMDOCK_BACKUP_ID"

printf 'BACKUP_ID=%s\n' "$FORMDOCK_BACKUP_ID"
printf 'BACKUP_VERIFICATION=PASS\n'
