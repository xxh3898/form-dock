#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

umask 077

formdock_require_env FORMDOCK_BACKUP_ROOT
formdock_require_env FORMDOCK_RETENTION_COUNT

[[ "$FORMDOCK_RETENTION_COUNT" =~ ^[1-9][0-9]*$ ]] \
  || formdock_die 'FORMDOCK_RETENTION_COUNT must be a positive integer.'

backup_root="$(formdock_canonical_private_directory "$FORMDOCK_BACKUP_ROOT")"
[ -w "$backup_root" ] || formdock_die "Backup root is not writable: $backup_root"
apply="${FORMDOCK_RETENTION_APPLY:-false}"
case "$apply" in
  true|false) ;;
  *) formdock_die 'FORMDOCK_RETENTION_APPLY must be true or false.' ;;
esac

index_file="$(mktemp "${TMPDIR:-/tmp}/formdock-retention-index.XXXXXX")"
sorted_file="$(mktemp "${TMPDIR:-/tmp}/formdock-retention-sorted.XXXXXX")"
chmod 600 "$index_file" "$sorted_file"
cleanup_index() {
  rm -f "$sorted_file" "$index_file"
}
trap cleanup_index EXIT

complete_count=0
for metadata_file in "$backup_root"/formdock-*.meta; do
  [ -f "$metadata_file" ] || continue
  [ ! -L "$metadata_file" ] || formdock_die 'Retention refuses symbolic-link metadata.'
  backup_id="$(basename "$metadata_file" .meta)"
  formdock_validate_backup_id "$backup_id"
  formdock_verify_backup_set "$backup_root" "$backup_id"
  created_at="$(formdock_metadata_value "$metadata_file" createdAt)"
  printf '%s\t%s\n' "$created_at" "$backup_id" >> "$index_file"
  complete_count=$((complete_count + 1))
done

LC_ALL=C sort -r "$index_file" > "$sorted_file"

position=0
candidate_count=0
deleted_count=0
while IFS=$'\t' read -r created_at backup_id; do
  [ -n "$backup_id" ] || continue
  position=$((position + 1))
  if [ "$position" -le "$FORMDOCK_RETENTION_COUNT" ]; then
    continue
  fi

  candidate_count=$((candidate_count + 1))
  printf 'RETENTION_CANDIDATE=%s\n' "$backup_id"
  [ "$apply" = true ] || continue

  formdock_validate_backup_id "$backup_id"
  metadata_file="$backup_root/$backup_id.meta"
  checksum_file="$backup_root/$backup_id.sha256"
  dump_file="$backup_root/$backup_id.dump"
  [ -f "$metadata_file" ] && [ -f "$checksum_file" ] && [ -f "$dump_file" ] \
    || formdock_die "Retention candidate changed during execution: $backup_id"

  rm -f "$metadata_file"
  rm -f "$checksum_file" "$dump_file"
  deleted_count=$((deleted_count + 1))
done < "$sorted_file"

printf 'RETENTION_COMPLETE_SETS=%s\n' "$complete_count"
printf 'RETENTION_KEEP=%s\n' "$FORMDOCK_RETENTION_COUNT"
printf 'RETENTION_CANDIDATES=%s\n' "$candidate_count"
printf 'RETENTION_DELETED=%s\n' "$deleted_count"
printf 'RETENTION_APPLIED=%s\n' "$apply"
