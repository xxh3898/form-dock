#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PRODUCTION_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
# shellcheck source=../common.sh
source "$PRODUCTION_DIR/common.sh"

tmp_base="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
temp_root="$(mktemp -d "$tmp_base/formdock-activate-first-smoke.XXXXXX")"
temp_root="$(cd "$temp_root" && pwd -P)"
case "$temp_root" in
  "$tmp_base"/formdock-activate-first-smoke.*) ;;
  *) formdock_production_die 'Activation smoke temporary directory escaped the expected root.' ;;
esac
chmod 700 "$temp_root"

cleanup() {
  case "$temp_root" in
    "$tmp_base"/formdock-activate-first-smoke.*) find "$temp_root" -depth -delete ;;
  esac
}
trap cleanup EXIT

write_bootstrap_input() {
  local target="$1"
  local password="$2"
  {
    printf 'FORMDOCK_BOOTSTRAP_ENABLED=true\n'
    printf 'FORMDOCK_BOOTSTRAP_EMAIL= Creator@Example.test \n'
    printf 'FORMDOCK_BOOTSTRAP_PASSWORD=%s\n' "$password"
    printf 'FORMDOCK_BOOTSTRAP_DISPLAY_NAME=Local Creator\n'
  } > "$target"
  chmod 600 "$target"
}

valid_input="$temp_root/bootstrap.env"
write_bootstrap_input "$valid_input" 'local-fixture-password'
validated_input="$(formdock_production_validate_bootstrap_input "$valid_input")"
[ "$validated_input" = "$valid_input" ]

bad_mode_input="$temp_root/bad-mode.env"
write_bootstrap_input "$bad_mode_input" 'local-fixture-password'
chmod 640 "$bad_mode_input"
if (formdock_production_validate_bootstrap_input "$bad_mode_input") >/dev/null 2>&1; then
  formdock_production_die 'Bootstrap input validator accepted group-readable credentials.'
fi

short_password_input="$temp_root/short.env"
write_bootstrap_input "$short_password_input" 'fourteen-chars'
if (formdock_production_validate_bootstrap_input "$short_password_input") >/dev/null 2>&1; then
  formdock_production_die 'Bootstrap input validator accepted fewer than 15 code points.'
fi

long_utf8_input="$temp_root/long-utf8.env"
FORMDOCK_TEST_INPUT="$long_utf8_input" python3 - <<'PY'
import os

with open(os.environ["FORMDOCK_TEST_INPUT"], "w", encoding="utf-8") as target:
    target.write("FORMDOCK_BOOTSTRAP_ENABLED=true\n")
    target.write("FORMDOCK_BOOTSTRAP_EMAIL=creator@example.test\n")
    target.write("FORMDOCK_BOOTSTRAP_PASSWORD=" + "가" * 25 + "\n")
    target.write("FORMDOCK_BOOTSTRAP_DISPLAY_NAME=Local Creator\n")
PY
chmod 600 "$long_utf8_input"
if (formdock_production_validate_bootstrap_input "$long_utf8_input") >/dev/null 2>&1; then
  formdock_production_die 'Bootstrap input validator accepted more than 72 UTF-8 bytes.'
fi

unknown_input="$temp_root/unknown.env"
cp "$valid_input" "$unknown_input"
printf 'UNEXPECTED=value\n' >> "$unknown_input"
chmod 600 "$unknown_input"
if (formdock_production_validate_bootstrap_input "$unknown_input") >/dev/null 2>&1; then
  formdock_production_die 'Bootstrap input validator accepted an unknown key.'
fi

configuration_identity="$temp_root/configuration.identity"
formdock_production_write_configuration_identity "$configuration_identity"
configuration_revision="sha256:$(formdock_delivery_sha256 "$configuration_identity")"
runtime_env="$temp_root/runtime.env"
database_password='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
formdock_production_write_runtime_env \
  "$runtime_env" "$database_password" "$configuration_revision"
formdock_production_validate_runtime_env "$runtime_env"
[ "$(formdock_delivery_file_mode "$runtime_env")" = 600 ]
[ "$(formdock_delivery_value "$runtime_env" FORMDOCK_BOOTSTRAP_ENABLED)" = false ]
[ -z "$(formdock_delivery_value "$runtime_env" FORMDOCK_BOOTSTRAP_EMAIL)" ]
[ -z "$(formdock_delivery_value "$runtime_env" FORMDOCK_BOOTSTRAP_PASSWORD)" ]
[ -z "$(formdock_delivery_value "$runtime_env" FORMDOCK_BOOTSTRAP_DISPLAY_NAME)" ]

bad_runtime_env="$temp_root/bad-runtime.env"
sed 's/^FORMDOCK_EDGE_NETWORK=edge$/FORMDOCK_EDGE_NETWORK=unexpected/' \
  "$runtime_env" > "$bad_runtime_env"
chmod 600 "$bad_runtime_env"
if (formdock_production_validate_runtime_env "$bad_runtime_env") >/dev/null 2>&1; then
  formdock_production_die 'Runtime environment validator accepted a non-canonical edge network.'
fi

state_file="$temp_root/candidate.state"
compose_revision='sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
formdock_production_write_deployment_state \
  "$state_file" "$compose_revision" "$configuration_revision" '2026-08-27T00:00:00Z'
state_output="$($REPOSITORY_ROOT/infra/delivery/validate-state.sh "$state_file" candidate)"
printf '%s\n' "$state_output" | grep -Fxq 'DEPLOYMENT_STATE_VALID=PASS'
printf '%s\n' "$state_output" | grep -Fxq 'PREVIOUS_STATE=NONE'
[ "$(formdock_delivery_value "$state_file" apiImageIdentity)" = "${FORMDOCK_PRODUCTION_API_IMAGE##*@}" ]
[ "$(formdock_delivery_value "$state_file" webImageIdentity)" = "${FORMDOCK_PRODUCTION_WEB_IMAGE##*@}" ]

lock_dir="$temp_root/operation.lock"
mkdir "$lock_dir"
chmod 700 "$lock_dir"
lock_metadata="$lock_dir/metadata"
formdock_production_write_lock_metadata \
  "$lock_metadata" d2a-fixture '2026-08-27T00:00:00Z' PENDING
[ "$(formdock_delivery_file_mode "$lock_metadata")" = 600 ]
[ "$(awk -F= '$1 == "candidateStateSha256" { print $2 }' "$lock_metadata")" = PENDING ]
if mkdir "$lock_dir" >/dev/null 2>&1; then
  formdock_production_die 'Atomic operation lock unexpectedly allowed a second acquisition.'
fi

activation_script="$PRODUCTION_DIR/activate-first.sh"
bootstrap_gate_line="$(grep -n '^bootstrap_input=' "$activation_script" | cut -d: -f1)"
preflight_line="$(grep -n '^preflight_output=' "$activation_script" | cut -d: -f1)"
private_root_line="$(grep -n '^mkdir "\$private_root"' "$activation_script" | cut -d: -f1)"
image_pull_line="$(grep -n '^docker pull --platform linux/arm64' "$activation_script" | head -n 1 | cut -d: -f1)"
[ "$bootstrap_gate_line" -lt "$preflight_line" ] \
  && [ "$preflight_line" -lt "$private_root_line" ] \
  && [ "$private_root_line" -lt "$image_pull_line" ] \
  || formdock_production_die 'Activation ordering no longer gates mutation behind input validation and actual preflight.'

if grep -En \
  '(down[[:space:]]+--volumes|docker[[:space:]]+(system|volume)[[:space:]]+prune|docker[[:space:]]+(login|push)|cloudflared[[:space:]]+(tunnel|route)|curl[^\n]*homeops|docker[[:space:]]+exec[^\n]*homeops)' \
  "$activation_script"; then
  formdock_production_die 'Activation helper contains a forbidden D2A mutation.'
fi
if grep -En 'set[[:space:]]+-x' "$activation_script"; then
  formdock_production_die 'Activation helper must not enable shell trace around Secret handling.'
fi

printf 'ACTIVATION_BOOTSTRAP_INPUT_CONTRACT=PASS\n'
printf 'ACTIVATION_FINAL_ENV_CONTRACT=PASS\n'
printf 'ACTIVATION_FIRST_STATE_CONTRACT=PASS\n'
printf 'ACTIVATION_ATOMIC_LOCK_CONTRACT=PASS\n'
printf 'ACTIVATION_ORDERING_CONTRACT=PASS\n'
printf 'ACTIVATION_NEGATIVE_SCOPE=PASS\n'
