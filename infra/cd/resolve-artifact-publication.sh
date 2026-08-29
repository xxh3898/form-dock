#!/usr/bin/env bash
set -euo pipefail

readonly ZERO_DIGEST=sha256:0000000000000000000000000000000000000000000000000000000000000000
readonly API_IMAGE=ghcr.io/xxh3898/form-dock-api
readonly WEB_IMAGE=ghcr.io/xxh3898/form-dock-web
readonly RUNTIME_IMAGE=ghcr.io/xxh3898/form-dock-runtime-config
readonly EXPECTED_SOURCE=https://github.com/xxh3898/form-dock

fail() {
  printf 'FORMDOCK_ARTIFACT_PUBLICATION_INVALID: %s\n' "$1" >&2
  exit 64
}

is_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] \
    && [ "$1" != 0000000000000000000000000000000000000000 ]
}

is_digest() {
  [[ "$1" =~ ^sha256:[0-9a-f]{64}$ ]] && [ "$1" != "$ZERO_DIGEST" ]
}

emit() {
  printf 'mode=%s\n' "$1"
  printf 'reason=%s\n' "$2"
  printf 'apiDigest=%s\n' "${3:-}"
  printf 'webDigest=%s\n' "${4:-}"
  printf 'runtimeDigest=%s\n' "${5:-}"
}

if [ "$#" -ne 2 ]; then
  fail 'Expected exact main SHA and repository source URL.'
fi

readonly COMMIT_SHA="$1"
readonly SOURCE_URL="$2"
readonly TEST_MODE="${FORMDOCK_ARTIFACT_TEST_MODE:-false}"

is_sha "$COMMIT_SHA" || fail 'Main SHA must be a non-zero lowercase 40-character SHA.'
[ "$SOURCE_URL" = "$EXPECTED_SOURCE" ] || fail 'Repository source URL is outside the FormDock allowlist.'

if [ "$TEST_MODE" = fixture ]; then
  DOCKER_BIN="${FORMDOCK_ARTIFACT_DOCKER_BIN:-}"
  [[ "$DOCKER_BIN" =~ ^/ ]] && [ -f "$DOCKER_BIN" ] && [ -x "$DOCKER_BIN" ] && [ ! -L "$DOCKER_BIN" ] \
    || fail 'Fixture Docker executable is missing or unsafe.'
else
  [ "$TEST_MODE" = false ] || fail 'Unsupported artifact publication test mode.'
  [ -z "${FORMDOCK_ARTIFACT_DOCKER_BIN:-}" ] \
    || fail 'Production Docker executable cannot be overridden.'
  DOCKER_BIN="$(command -v docker)" || fail 'Docker is unavailable.'
fi
readonly DOCKER_BIN

TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/formdock-artifact-probe.XXXXXX")"
cleanup() {
  find "$TEMP_ROOT" -type f -exec unlink {} \; 2>/dev/null || true
  rmdir "$TEMP_ROOT" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

probe_tag() {
  local component="$1"
  local tag="$2"
  local error_file="$TEMP_ROOT/$component.pull-error"

  if "$DOCKER_BIN" pull "$tag" >/dev/null 2>"$error_file"; then
    printf '%s\n' PRESENT
    return
  fi

  if grep -Eiq '(unauthorized|denied|forbidden|authentication|timeout|timed out|TLS|connection|network|temporary failure)' "$error_file"; then
    printf '%s\n' ERROR
  elif grep -Eiq '(manifest unknown|manifest.*not found|not found)' "$error_file"; then
    printf '%s\n' ABSENT
  else
    printf '%s\n' ERROR
  fi
}

image_label() {
  local tag="$1"
  local label="$2"
  "$DOCKER_BIN" image inspect --format "{{ index .Config.Labels \"$label\" }}" "$tag"
}

inspect_identity() {
  local image="$1"
  local component="$2"
  local require_project="$3"
  local tag="$image:sha-$COMMIT_SHA"
  local platform source revision version actual_component project repo_digests matching count digest

  platform="$("$DOCKER_BIN" image inspect --format '{{.Architecture}}/{{.Os}}' "$tag")" || return 1
  source="$(image_label "$tag" org.opencontainers.image.source)" || return 1
  revision="$(image_label "$tag" org.opencontainers.image.revision)" || return 1
  version="$(image_label "$tag" org.opencontainers.image.version)" || return 1
  actual_component="$(image_label "$tag" io.formdock.image.component)" || return 1

  [ "$platform" = arm64/linux ] \
    && [ "$source" = "$SOURCE_URL" ] \
    && [ "$revision" = "$COMMIT_SHA" ] \
    && [ "$version" = "sha-$COMMIT_SHA" ] \
    && [ "$actual_component" = "$component" ] \
    || return 1

  if [ "$require_project" = true ]; then
    project="$(image_label "$tag" io.formdock.runtime-config.project)" || return 1
    [ "$project" = form-dock ] || return 1
  fi

  repo_digests="$("$DOCKER_BIN" image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$tag")" \
    || return 1
  matching="$(printf '%s\n' "$repo_digests" | awk -v prefix="$image@sha256:" 'index($0, prefix) == 1')"
  count="$(printf '%s\n' "$matching" | awk 'NF { count += 1 } END { print count + 0 }')"
  [ "$count" -eq 1 ] || return 1
  digest="${matching#*@}"
  is_digest "$digest" || return 1
  printf '%s\n' "$digest"
}

api_tag="$API_IMAGE:sha-$COMMIT_SHA"
web_tag="$WEB_IMAGE:sha-$COMMIT_SHA"
runtime_tag="$RUNTIME_IMAGE:sha-$COMMIT_SHA"
api_state="$(probe_tag api "$api_tag")"
web_state="$(probe_tag web "$web_tag")"
runtime_state="$(probe_tag runtime-config "$runtime_tag")"

if [ "$api_state" = ERROR ] || [ "$web_state" = ERROR ] || [ "$runtime_state" = ERROR ]; then
  emit HOLD REGISTRY_STATE_UNAVAILABLE
  exit 0
fi

if [ "$api_state" = ABSENT ] && [ "$web_state" = ABSENT ] && [ "$runtime_state" = ABSENT ]; then
  emit PUBLISH ALL_ARTIFACTS_ABSENT
  exit 0
fi

if [ "$api_state" != PRESENT ] || [ "$web_state" != PRESENT ] || [ "$runtime_state" != PRESENT ]; then
  emit HOLD PARTIAL_ARTIFACT_SET
  exit 0
fi

if ! api_digest="$(inspect_identity "$API_IMAGE" api false)" \
  || ! web_digest="$(inspect_identity "$WEB_IMAGE" web false)" \
  || ! runtime_digest="$(inspect_identity "$RUNTIME_IMAGE" runtime-config true)"; then
  emit HOLD ARTIFACT_IDENTITY_MISMATCH
  exit 0
fi

emit REUSE EXACT_ARTIFACT_SET_PRESENT "$api_digest" "$web_digest" "$runtime_digest"
