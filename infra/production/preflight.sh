#!/usr/bin/env bash
set -euo pipefail

CANONICAL_PROJECT='form-dock'
CANONICAL_WEB_PORT='18082'
CANONICAL_RELEASE_SHA='1648047645720e67d5e928345c875dc53a93ff0e'
CANONICAL_API_IMAGE='ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b'
CANONICAL_WEB_IMAGE='ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551'

preflight_invalid() {
  printf 'FORMDOCK_PREFLIGHT_CONFIG_INVALID\n' >&2
  exit 64
}

preflight_blocked() {
  printf 'FORMDOCK_PREFLIGHT_BLOCKED\n' >&2
  exit 2
}

require_env() {
  local name="$1"
  [ -n "${!name:-}" ] || preflight_invalid
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || preflight_invalid
}

is_uint() {
  [[ "$1" =~ ^[0-9]+$ ]] && [ "${#1}" -le 15 ]
}

require_safe_absolute_path() {
  local value="$1"
  [[ "$value" =~ ^/[A-Za-z0-9._/-]+$ ]] || preflight_invalid
  case "/$value/" in
    *'/../'*|*'/./'*) preflight_invalid ;;
  esac
  [ "$value" != / ] || preflight_invalid
}

file_owner_uid() {
  local target="$1"
  local owner
  if owner="$(stat -f '%u' "$target" 2>/dev/null)"; then
    printf '%s\n' "$owner"
    return
  fi
  if owner="$(stat -c '%u' "$target" 2>/dev/null)"; then
    printf '%s\n' "$owner"
    return
  fi
  return 1
}

fixture_validate() {
  awk -F= '
    BEGIN {
      allowed["hostArchitecture"] = 1
      allowed["macosVersion"] = 1
      allowed["dockerArchitecture"] = 1
      allowed["dockerEngineVersion"] = 1
      allowed["dockerComposeVersion"] = 1
      allowed["diskAvailableKiB"] = 1
      allowed["diskAvailablePercent"] = 1
      allowed["webPortListenerCount"] = 1
      allowed["projectContainerCount"] = 1
      allowed["projectNetworkCount"] = 1
      allowed["projectVolumeCount"] = 1
      allowed["exactNameConflictCount"] = 1
      allowed["privateConfigPresent"] = 1
      allowed["deploymentStatePresent"] = 1
      allowed["operationLockPresent"] = 1
      allowed["localBackupRootStatus"] = 1
      allowed["edgeNetworkStatus"] = 1
      allowed["edgeAliasConflictCount"] = 1
      allowed["cloudflaredEdgeStatus"] = 1
      allowed["cloudflareDnsStatus"] = 1
      allowed["homeOpsWebStatus"] = 1
      allowed["homeOpsApiStatus"] = 1
      allowed["homeOpsDbStatus"] = 1
      allowed["apiRemoteDigest"] = 1
      allowed["apiRemotePlatform"] = 1
      allowed["webRemoteDigest"] = 1
      allowed["webRemotePlatform"] = 1
    }
    NF != 2 || !($1 in allowed) || seen[$1]++ { exit 1 }
    { count += 1 }
    END { if (count != 27) exit 1 }
  ' "$1" || preflight_invalid
}

fixture_value() {
  local file="$1"
  local key="$2"
  awk -F= -v wanted="$key" '$1 == wanted { print $2 }' "$file"
}

inspect_remote_image() {
  local reference="$1"
  local manifest_json digest architecture operating_system
  manifest_json="$(docker manifest inspect --verbose "$reference" 2>/dev/null)" || preflight_blocked
  digest="$(jq -r '.Descriptor.digest // empty' <<< "$manifest_json")"
  architecture="$(jq -r '.Descriptor.platform.architecture // empty' <<< "$manifest_json")"
  operating_system="$(jq -r '.Descriptor.platform.os // empty' <<< "$manifest_json")"
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || preflight_blocked
  [ -n "$architecture" ] && [ -n "$operating_system" ] || preflight_blocked
  printf '%s|%s/%s\n' "$digest" "$operating_system" "$architecture"
}

homeops_service_health() {
  local service="$1"
  local ids count container_id
  ids="$(docker ps -aq \
    --filter 'label=com.docker.compose.project=homeops' \
    --filter "label=com.docker.compose.service=$service")"
  count="$(printf '%s\n' "$ids" | awk 'NF { count += 1 } END { print count + 0 }')"
  [ "$count" = 1 ] || {
    printf 'ambiguous\n'
    return
  }
  container_id="$(printf '%s\n' "$ids" | awk 'NF { print; exit }')"
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "$container_id" 2>/dev/null || printf 'ambiguous\n'
}

for expected in \
  FORMDOCK_PREFLIGHT_EXPECTED_PROJECT \
  FORMDOCK_PREFLIGHT_EXPECTED_WEB_PORT \
  FORMDOCK_PREFLIGHT_EXPECTED_RELEASE_SHA \
  FORMDOCK_PREFLIGHT_EXPECTED_API_IMAGE \
  FORMDOCK_PREFLIGHT_EXPECTED_WEB_IMAGE; do
  require_env "$expected"
done

[ "$FORMDOCK_PREFLIGHT_EXPECTED_PROJECT" = "$CANONICAL_PROJECT" ] || preflight_invalid
[ "$FORMDOCK_PREFLIGHT_EXPECTED_WEB_PORT" = "$CANONICAL_WEB_PORT" ] || preflight_invalid
[ "$FORMDOCK_PREFLIGHT_EXPECTED_RELEASE_SHA" = "$CANONICAL_RELEASE_SHA" ] || preflight_invalid
[ "$FORMDOCK_PREFLIGHT_EXPECTED_API_IMAGE" = "$CANONICAL_API_IMAGE" ] || preflight_invalid
[ "$FORMDOCK_PREFLIGHT_EXPECTED_WEB_IMAGE" = "$CANONICAL_WEB_IMAGE" ] || preflight_invalid

scope="${FORMDOCK_PREFLIGHT_SCOPE:-}"
case "$scope" in
  fixture)
    require_env FORMDOCK_PREFLIGHT_FIXTURE_FILE
    fixture_file="$FORMDOCK_PREFLIGHT_FIXTURE_FILE"
    [ -f "$fixture_file" ] && [ ! -L "$fixture_file" ] || preflight_invalid
    fixture_validate "$fixture_file"
    evidence_mode=fixture
    host_architecture="$(fixture_value "$fixture_file" hostArchitecture)"
    macos_version="$(fixture_value "$fixture_file" macosVersion)"
    docker_architecture="$(fixture_value "$fixture_file" dockerArchitecture)"
    docker_engine_version="$(fixture_value "$fixture_file" dockerEngineVersion)"
    docker_compose_version="$(fixture_value "$fixture_file" dockerComposeVersion)"
    disk_available_kib="$(fixture_value "$fixture_file" diskAvailableKiB)"
    disk_available_percent="$(fixture_value "$fixture_file" diskAvailablePercent)"
    web_port_listener_count="$(fixture_value "$fixture_file" webPortListenerCount)"
    project_container_count="$(fixture_value "$fixture_file" projectContainerCount)"
    project_network_count="$(fixture_value "$fixture_file" projectNetworkCount)"
    project_volume_count="$(fixture_value "$fixture_file" projectVolumeCount)"
    exact_name_conflict_count="$(fixture_value "$fixture_file" exactNameConflictCount)"
    private_config_present="$(fixture_value "$fixture_file" privateConfigPresent)"
    deployment_state_present="$(fixture_value "$fixture_file" deploymentStatePresent)"
    operation_lock_present="$(fixture_value "$fixture_file" operationLockPresent)"
    local_backup_root_status="$(fixture_value "$fixture_file" localBackupRootStatus)"
    edge_network_status="$(fixture_value "$fixture_file" edgeNetworkStatus)"
    edge_alias_conflict_count="$(fixture_value "$fixture_file" edgeAliasConflictCount)"
    cloudflared_edge_status="$(fixture_value "$fixture_file" cloudflaredEdgeStatus)"
    cloudflare_dns_status="$(fixture_value "$fixture_file" cloudflareDnsStatus)"
    homeops_web_status="$(fixture_value "$fixture_file" homeOpsWebStatus)"
    homeops_api_status="$(fixture_value "$fixture_file" homeOpsApiStatus)"
    homeops_db_status="$(fixture_value "$fixture_file" homeOpsDbStatus)"
    api_remote_digest="$(fixture_value "$fixture_file" apiRemoteDigest)"
    api_remote_platform="$(fixture_value "$fixture_file" apiRemotePlatform)"
    web_remote_digest="$(fixture_value "$fixture_file" webRemoteDigest)"
    web_remote_platform="$(fixture_value "$fixture_file" webRemotePlatform)"
    ;;
  actual)
    [ -z "${FORMDOCK_PREFLIGHT_FIXTURE_FILE:-}" ] || preflight_invalid
    for required in \
      FORMDOCK_PREFLIGHT_DATA_PATH \
      FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT \
      FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE \
      FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE \
      FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH; do
      require_env "$required"
      require_safe_absolute_path "${!required}"
    done
    for command in uname sw_vers docker jq lsof dig df stat id awk sed grep date; do
      require_command "$command"
    done
    [ -d "$FORMDOCK_PREFLIGHT_DATA_PATH" ] && [ ! -L "$FORMDOCK_PREFLIGHT_DATA_PATH" ] \
      || preflight_invalid

    evidence_mode=actual
    host_architecture="$(uname -m)"
    macos_version="$(sw_vers -productVersion)"
    docker_architecture="$(docker version --format '{{.Server.Arch}}' 2>/dev/null)" || preflight_blocked
    docker_engine_version="$(docker version --format '{{.Server.Version}}' 2>/dev/null)" || preflight_blocked
    docker_compose_version="$(docker compose version --short 2>/dev/null)" || preflight_blocked
    disk_available_kib="$(df -Pk "$FORMDOCK_PREFLIGHT_DATA_PATH" | awk 'NR == 2 { print $4 }')"
    disk_used_percent="$(df -Pk "$FORMDOCK_PREFLIGHT_DATA_PATH" | awk 'NR == 2 { gsub(/%/, "", $5); print $5 }')"
    is_uint "$disk_used_percent" && [ "$disk_used_percent" -le 100 ] || preflight_blocked
    disk_available_percent="$((100 - disk_used_percent))"
    web_port_listener_count="$(
      (lsof -nP -iTCP:"$CANONICAL_WEB_PORT" -sTCP:LISTEN 2>/dev/null || true) \
        | awk 'NR > 1 { count += 1 } END { print count + 0 }'
    )"
    project_container_count="$(docker ps -aq \
      --filter "label=com.docker.compose.project=$CANONICAL_PROJECT" | awk 'NF { count += 1 } END { print count + 0 }')"
    project_network_count="$(docker network ls -q \
      --filter "label=com.docker.compose.project=$CANONICAL_PROJECT" | awk 'NF { count += 1 } END { print count + 0 }')"
    project_volume_count="$(docker volume ls -q \
      --filter "label=com.docker.compose.project=$CANONICAL_PROJECT" | awk 'NF { count += 1 } END { print count + 0 }')"
    exact_name_conflict_count="$(
      {
        docker ps -a --format '{{.Names}}' \
          | awk '$0 == "form-dock-postgres-1" || $0 == "form-dock-api-1" || $0 == "form-dock-web-1"'
        docker network ls --format '{{.Name}}' \
          | awk '$0 == "form-dock_application" || $0 == "form-dock_database"'
        docker volume ls --format '{{.Name}}' | awk '$0 == "form-dock_postgres-data"'
      } | awk 'NF { count += 1 } END { print count + 0 }'
    )"
    if [ -e "$FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE" ] || [ -L "$FORMDOCK_PREFLIGHT_PRIVATE_ENV_FILE" ]; then
      private_config_present=1
    else
      private_config_present=0
    fi
    if [ -e "$FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE" ] || [ -L "$FORMDOCK_PREFLIGHT_DEPLOYMENT_STATE_FILE" ]; then
      deployment_state_present=1
    else
      deployment_state_present=0
    fi
    if [ -e "$FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH" ] || [ -L "$FORMDOCK_PREFLIGHT_OPERATION_LOCK_PATH" ]; then
      operation_lock_present=1
    else
      operation_lock_present=0
    fi
    backup_parent="${FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT%/*}"
    if [ -e "$FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT" ] || [ -L "$FORMDOCK_PREFLIGHT_LOCAL_BACKUP_ROOT" ]; then
      local_backup_root_status=UNEXPECTED_EXISTING
    elif [ -d "$backup_parent" ] && [ ! -L "$backup_parent" ] \
      && [ -w "$backup_parent" ] \
      && [ "$(file_owner_uid "$backup_parent" 2>/dev/null || true)" = "$(id -u)" ]; then
      local_backup_root_status=PARENT_READY_D2_CREATE
    else
      local_backup_root_status=AMBIGUOUS
    fi
    edge_count="$(docker network ls --format '{{.Name}}' \
      | awk '$0 == "edge" { count += 1 } END { print count + 0 }')"
    if [ "$edge_count" = 1 ] \
      && [ "$(docker network inspect edge --format '{{.Name}}' 2>/dev/null || true)" = edge ] \
      && [ "$(docker network inspect edge --format '{{.Internal}}' 2>/dev/null || true)" = false ]; then
      edge_network_status=READY
    else
      edge_network_status=AMBIGUOUS
    fi
    edge_alias_conflict_count="$(
      docker network inspect edge --format '{{range .Containers}}{{println .Name}}{{end}}' 2>/dev/null \
        | while IFS= read -r container_name; do
            [ -n "$container_name" ] || continue
            docker inspect --format '{{with index .NetworkSettings.Networks "edge"}}{{range .Aliases}}{{println .}}{{end}}{{end}}' \
              "$container_name" 2>/dev/null || printf 'ambiguous\n'
          done \
        | awk '$0 == "form-dock-web" { count += 1 } $0 == "ambiguous" { ambiguous = 1 } END { if (ambiguous) print "ambiguous"; else print count + 0 }'
    )"
    cloudflared_count="$(docker ps -a --format '{{.Names}}' \
      | awk '$0 == "cloudflared" { count += 1 } END { print count + 0 }')"
    if [ "$cloudflared_count" = 1 ] \
      && [ "$(docker inspect cloudflared --format '{{.State.Status}}' 2>/dev/null || true)" = running ] \
      && [ "$(docker inspect cloudflared --format '{{if index .NetworkSettings.Networks "edge"}}attached{{else}}absent{{end}}' 2>/dev/null || true)" = attached ]; then
      cloudflared_edge_status=ATTACHED
    else
      cloudflared_edge_status=AMBIGUOUS
    fi
    cloudflare_dns_status="$(dig +noall +comments forms.chochiho.cloud A 2>/dev/null \
      | sed -n 's/.*status: \([A-Z][A-Z]*\),.*/\1/p' | awk 'NR == 1 { print }')"
    [ -n "$cloudflare_dns_status" ] || cloudflare_dns_status=AMBIGUOUS
    homeops_web_status="$(homeops_service_health web)"
    homeops_api_status="$(homeops_service_health api)"
    homeops_db_status="$(homeops_service_health db)"
    IFS='|' read -r api_remote_digest api_remote_platform \
      <<< "$(inspect_remote_image "$CANONICAL_API_IMAGE")"
    IFS='|' read -r web_remote_digest web_remote_platform \
      <<< "$(inspect_remote_image "$CANONICAL_WEB_IMAGE")"
    ;;
  *) preflight_invalid ;;
esac

[[ "$host_architecture" =~ ^(arm64|aarch64)$ ]] || preflight_blocked
[ "$docker_architecture" = arm64 ] || preflight_blocked
[[ "$macos_version" =~ ^[0-9]+([.][0-9]+){1,3}$ ]] || preflight_blocked
[[ "$docker_engine_version" =~ ^[0-9]+([.][0-9]+){1,3}([-+._A-Za-z0-9]*)?$ ]] || preflight_blocked
[[ "$docker_compose_version" =~ ^[0-9]+([.][0-9]+){1,3}([-+._A-Za-z0-9]*)?$ ]] || preflight_blocked
is_uint "$disk_available_kib" && [ "$disk_available_kib" -gt 0 ] || preflight_blocked
is_uint "$disk_available_percent" && [ "$disk_available_percent" -ge 15 ] \
  && [ "$disk_available_percent" -le 100 ] || preflight_blocked
for zero_value in \
  "$web_port_listener_count" \
  "$project_container_count" \
  "$project_network_count" \
  "$project_volume_count" \
  "$exact_name_conflict_count" \
  "$private_config_present" \
  "$deployment_state_present" \
  "$operation_lock_present" \
  "$edge_alias_conflict_count"; do
  [ "$zero_value" = 0 ] || preflight_blocked
done
[ "$local_backup_root_status" = PARENT_READY_D2_CREATE ] || preflight_blocked
[ "$edge_network_status" = READY ] || preflight_blocked
[ "$cloudflared_edge_status" = ATTACHED ] || preflight_blocked
[ "$cloudflare_dns_status" = NXDOMAIN ] || preflight_blocked
[ "$homeops_web_status" = healthy ] || preflight_blocked
[ "$homeops_api_status" = healthy ] || preflight_blocked
[ "$homeops_db_status" = healthy ] || preflight_blocked
[ "$api_remote_digest" = "${CANONICAL_API_IMAGE##*@}" ] || preflight_blocked
[ "$api_remote_platform" = linux/arm64 ] || preflight_blocked
[ "$web_remote_digest" = "${CANONICAL_WEB_IMAGE##*@}" ] || preflight_blocked
[ "$web_remote_platform" = linux/arm64 ] || preflight_blocked

disk_available_gib="$((disk_available_kib / 1048576))"
operation_utc="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

printf 'formatVersion=1\n'
printf 'result=PASS\n'
printf 'evidenceMode=%s\n' "$evidence_mode"
printf 'operationUtc=%s\n' "$operation_utc"
printf 'sourceReleaseSha=%s\n' "$CANONICAL_RELEASE_SHA"
printf 'apiArtifact=%s\n' "$CANONICAL_API_IMAGE"
printf 'webArtifact=%s\n' "$CANONICAL_WEB_IMAGE"
printf 'targetHostRole=FORMDOCK_PRODUCTION\n'
printf 'hostArchitecture=%s\n' "$host_architecture"
printf 'macosVersion=%s\n' "$macos_version"
printf 'dockerArchitecture=%s\n' "$docker_architecture"
printf 'dockerEngineVersion=%s\n' "$docker_engine_version"
printf 'dockerComposeVersion=%s\n' "$docker_compose_version"
printf 'diskAvailableGiB=%s\n' "$disk_available_gib"
printf 'diskAvailablePercent=%s\n' "$disk_available_percent"
printf 'canonicalCompose=infra/compose.production.yaml\n'
printf 'intendedProject=%s\n' "$CANONICAL_PROJECT"
printf 'intendedWebPort=%s\n' "$CANONICAL_WEB_PORT"
printf 'resourceConflictCount=0\n'
printf 'activationClass=FIRST_ACTIVATION\n'
printf 'databaseClass=FRESH_PRODUCTION_DB\n'
printf 'privateConfigStatus=NOT_CREATED_D2_REQUIRED\n'
printf 'operationLockStatus=ABSENT_D2_ACQUIRE\n'
printf 'previousState=NONE\n'
printf 'localBackupRootStatus=%s\n' "$local_backup_root_status"
printf 'backupCadence=DAILY\n'
printf 'backupRetentionCompletedSets=7\n'
printf 'offHostDurabilityStatus=DEFERRED_ACCEPTED_RISK\n'
printf 'currentIndependentOffHostTarget=NONE\n'
printf 'firstActivationAllowed=true\n'
printf 'edgeNetworkStatus=%s\n' "$edge_network_status"
printf 'cloudflaredEdgeStatus=%s\n' "$cloudflared_edge_status"
printf 'cloudflareRouteState=ROUTE_ABSENT_DNS_NXDOMAIN\n'
printf 'cloudflareOriginAlias=form-dock-web:8080\n'
printf 'monitoringProvider=HomeOps\n'
printf 'homeOpsRuntimeStatus=READY\n'
printf 'serviceHealthAuthority=HOMEOPS_EXACT_HTTPS_URL\n'
printf 'operationalIncidentHistory=HOMEOPS\n'
printf 'backupDeployEventIngestion=D2_EXPLICIT_HOMEOPS_CONFIGURATION\n'
printf 'outboundNotification=DISABLED_BY_OPERATOR_CHOICE\n'
printf 'monitorDiskMinimumAvailablePercent=15\n'
printf 'monitorBackupMaxAgeSeconds=93600\n'
printf 'monitorHttp5xxThreshold=10\n'
printf 'monitorHttp5xxWindowSeconds=300\n'
printf 'monitorExecutionCadenceSeconds=300\n'
printf 'remainingD1Blockers=0\n'
printf 'productionMutationCount=0\n'
printf 'secretValueReadCount=0\n'
