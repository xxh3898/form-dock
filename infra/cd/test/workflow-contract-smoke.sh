#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd -P)"
WORKFLOW="$ROOT/.github/workflows/publish-and-deploy.yml"
VALIDATE="$ROOT/.github/workflows/validate.yml"
PUBLICATION_WORKFLOW="$ROOT/.github/workflows/ghcr-publication-evidence.yml"
RUNTIME_DOCKERFILE="$ROOT/runtime-config.Dockerfile"

test -f "$WORKFLOW"
test -f "$VALIDATE"
test -f "$PUBLICATION_WORKFLOW"
test "$(grep -c '^  workflow_dispatch:$' "$WORKFLOW")" = 1
test "$(grep -c '^      - main$' "$WORKFLOW")" = 1
test "$(grep -c "vars.MAC_MINI_DEPLOY_ENABLED" "$WORKFLOW")" -ge 1
test "$(grep -c "infra/cd/resolve-production-baseline.sh" "$WORKFLOW")" = 1
test "$(grep -c "infra/cd/classify-change.sh" "$WORKFLOW")" = 1
test "$(grep -c "infra/cd/resolve-artifact-publication.sh" "$WORKFLOW")" = 2
test "$(grep -c "classification == 'APPLICATION_ONLY'" "$WORKFLOW")" = 2
test "$(grep -c '^      name: Production$' "$WORKFLOW")" = 1
test "$(grep -c 'ubuntu-24.04-arm' "$WORKFLOW")" = 1
test "$(grep -c 'runtime-config.Dockerfile' "$WORKFLOW")" = 1
test "$(grep -c "steps.probe.outputs.mode == 'PUBLISH'" "$WORKFLOW")" = 4
test "$(grep -c '^      publication_mode:' "$WORKFLOW")" = 1
test "$(grep -c '^        id: artifacts$' "$WORKFLOW")" = 1
test "$(grep -c 'deploy-formdock-v1 form-dock' "$WORKFLOW")" = 1
test "$(grep -c 'StrictHostKeyChecking=yes' "$WORKFLOW")" = 1
test "$(grep -c 'password-stdin' "$WORKFLOW")" = 0
test "$(grep -c '^  workflow_call:$' "$VALIDATE")" = 1
test "$(grep -Ec '^[[:space:]]+[[:alnum:]_-]+: write$' "$VALIDATE")" = 0
test "$(grep -c '^      packages: write$' "$VALIDATE")" = 0
test "$(grep -c '^  arm64-publication:$' "$VALIDATE")" = 0
test "$(grep -c "github.head_ref == 'fix/105-reusable-validate-startup-failure'" "$VALIDATE")" = 1

validate_caller="$(sed -n '/^  validate:$/,/^  classify:$/p' "$WORKFLOW")"
test "$(grep -c '^    uses: ./.github/workflows/validate.yml$' <<< "$validate_caller")" = 1
test "$(grep -c '^      contents: read$' <<< "$validate_caller")" = 1
test "$(grep -c '^      packages: write$' <<< "$validate_caller")" = 0
test "$(grep -Ec '^      [[:alnum:]_-]+: write$' <<< "$validate_caller")" = 0

test "$(grep -c '^  pull_request:$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c '^      - dev$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c '^  push:$' "$PUBLICATION_WORKFLOW")" = 0
test "$(grep -c '^  workflow_call:$' "$PUBLICATION_WORKFLOW")" = 0
test "$(grep -c '^  workflow_dispatch:$' "$PUBLICATION_WORKFLOW")" = 0
test "$(grep -c '^  arm64-publication:$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c '^      packages: write$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -Ec '^[[:space:]]+[[:alnum:]_-]+: write$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c "github.event_name == 'pull_request'" "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c "github.base_ref == 'dev'" "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c "github.head_ref == 'release-evidence/89-phase-5-c2-artifact-publication'" "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c 'github.event.pull_request.head.repo.full_name == github.repository' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c 'ubuntu-24.04-arm' "$PUBLICATION_WORKFLOW")" = 2
test "$(grep -c '^      RELEASE_TAG: v0.4.0$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c '^      RELEASE_SHA: 1648047645720e67d5e928345c875dc53a93ff0e$' "$PUBLICATION_WORKFLOW")" = 1
test "$(grep -c '^      PUBLICATION_AUTHORITY: issuecomment-5432560272$' "$PUBLICATION_WORKFLOW")" = 1
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
