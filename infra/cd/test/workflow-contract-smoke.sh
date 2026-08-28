#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
WORKFLOW="$ROOT/.github/workflows/publish-and-deploy.yml"
VALIDATE="$ROOT/.github/workflows/validate.yml"
RUNTIME_DOCKERFILE="$ROOT/runtime-config.Dockerfile"

test -f "$WORKFLOW"
test "$(grep -c '^  workflow_dispatch:$' "$WORKFLOW")" = 1
test "$(grep -c '^      - main$' "$WORKFLOW")" = 1
test "$(grep -c "vars.MAC_MINI_DEPLOY_ENABLED" "$WORKFLOW")" -ge 1
test "$(grep -c "infra/cd/resolve-production-baseline.sh" "$WORKFLOW")" = 1
test "$(grep -c "infra/cd/classify-change.sh" "$WORKFLOW")" = 1
test "$(grep -c "classification == 'APPLICATION_ONLY'" "$WORKFLOW")" = 2
test "$(grep -c '^      name: Production$' "$WORKFLOW")" = 1
test "$(grep -c 'ubuntu-24.04-arm' "$WORKFLOW")" = 1
test "$(grep -c 'runtime-config.Dockerfile' "$WORKFLOW")" = 1
test "$(grep -c 'deploy-formdock-v1 form-dock' "$WORKFLOW")" = 1
test "$(grep -c 'StrictHostKeyChecking=yes' "$WORKFLOW")" = 1
test "$(grep -c 'password-stdin' "$WORKFLOW")" = 0
test "$(grep -c '^  workflow_call:$' "$VALIDATE")" = 1
test "$(grep -c '^COPY infra/compose.production.yaml ./compose.yaml$' "$RUNTIME_DOCKERFILE")" = 1
test "$(grep -c '^COPY infra/production/deploy-release.sh ./scripts/deploy-release.sh$' "$RUNTIME_DOCKERFILE")" = 1
test "$(grep -c '^COPY infra/backup/verify.sh ./scripts/verify-backup.sh$' "$RUNTIME_DOCKERFILE")" = 1
test "$(grep -c '^COPY infra/delivery/common.sh ./scripts/delivery-common.sh$' "$RUNTIME_DOCKERFILE")" = 1

if grep -Eq '(COPY .*\.env|ENV .*PASSWORD|ENV .*TOKEN|ENV .*SECRET)' "$RUNTIME_DOCKERFILE"; then
  printf '%s\n' 'Runtime-config artifact would include secret-bearing configuration.' >&2
  exit 1
fi

if grep -Eq '(:latest|docker compose down|--volumes|cloudflare|discord|HOMEOPS_NOTIFICATIONS_ENABLED)' "$WORKFLOW"; then
  printf '%s\n' 'Production workflow contains a forbidden mutable/destructive contract.' >&2
  exit 1
fi

printf 'FORMDOCK_CD_WORKFLOW_CONTRACT_TEST=PASS\n'
