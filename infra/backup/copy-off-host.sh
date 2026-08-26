#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

umask 077

formdock_require_env FORMDOCK_BACKUP_ROOT
formdock_require_env FORMDOCK_BACKUP_ID
formdock_require_env FORMDOCK_OFF_HOST_TARGET_ROOT

source_root="$(formdock_canonical_private_directory "$FORMDOCK_BACKUP_ROOT")"
target_root="$(formdock_canonical_private_directory "$FORMDOCK_OFF_HOST_TARGET_ROOT")"
[ "$source_root" != "$target_root" ] || formdock_die 'Off-host target must differ from the local backup root.'
[ -w "$target_root" ] || formdock_die "Off-host target is not writable: $target_root"
formdock_validate_backup_id "$FORMDOCK_BACKUP_ID"
formdock_verify_backup_set "$source_root" "$FORMDOCK_BACKUP_ID"

final_dump="$target_root/$FORMDOCK_BACKUP_ID.dump"
final_checksum="$target_root/$FORMDOCK_BACKUP_ID.sha256"
final_metadata="$target_root/$FORMDOCK_BACKUP_ID.meta"
for target in "$final_dump" "$final_checksum" "$final_metadata"; do
  [ ! -e "$target" ] && [ ! -L "$target" ] \
    || formdock_die "Refusing to overwrite existing off-host artifact: $target"
done

stage_dir="$target_root/.partial-$FORMDOCK_BACKUP_ID"
mkdir "$stage_dir" 2>/dev/null || formdock_die 'Unable to claim off-host staging directory.'
chmod 700 "$stage_dir"
stage_dump="$stage_dir/$FORMDOCK_BACKUP_ID.dump"
stage_checksum="$stage_dir/$FORMDOCK_BACKUP_ID.sha256"
stage_metadata="$stage_dir/$FORMDOCK_BACKUP_ID.meta"

cleanup_stage() {
  rm -f "$stage_metadata" "$stage_checksum" "$stage_dump"
  rmdir "$stage_dir" 2>/dev/null || true
}
trap cleanup_stage EXIT

cp "$source_root/$FORMDOCK_BACKUP_ID.dump" "$stage_dump"
cp "$source_root/$FORMDOCK_BACKUP_ID.sha256" "$stage_checksum"
cp "$source_root/$FORMDOCK_BACKUP_ID.meta" "$stage_metadata"
chmod 600 "$stage_dump" "$stage_checksum" "$stage_metadata"

formdock_verify_backup_set "$stage_dir" "$FORMDOCK_BACKUP_ID"

mv "$stage_dump" "$final_dump"
mv "$stage_checksum" "$final_checksum"
mv "$stage_metadata" "$final_metadata"
rmdir "$stage_dir"
trap - EXIT

formdock_verify_backup_set "$target_root" "$FORMDOCK_BACKUP_ID"
printf 'OFF_HOST_BACKUP_ID=%s\n' "$FORMDOCK_BACKUP_ID"
printf 'OFF_HOST_COPY=PASS\n'
