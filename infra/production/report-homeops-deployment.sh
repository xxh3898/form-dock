#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'FORMDOCK_HOMEOPS_EVENT_INVALID: %s\n' "$1" >&2
  exit 64
}

if [ "$#" -ne 6 ]; then
  fail 'Expected status, commit SHA, previous SHA, workflow run ID, startedAt and finishedAt.'
fi

status="$1"
commit_sha="$2"
previous_sha="$3"
workflow_run_id="$4"
started_at="$5"
finished_at="$6"
reporter="${FORMDOCK_HOMEOPS_REPORTER:-}"
api_digest="${FORMDOCK_API_IMAGE_DIGEST:-}"
web_digest="${FORMDOCK_WEB_IMAGE_DIGEST:-}"
actor="${FORMDOCK_DEPLOY_ACTOR:-formdock-cd}"

case "$status" in
  REQUESTED|RUNNING|SUCCESS|FAILED|ROLLED_BACK) ;;
  *) fail 'Deployment status is outside the HomeOps allowlist.' ;;
esac
[[ "$commit_sha" =~ ^[0-9a-f]{40}$ ]] || fail 'Commit SHA is invalid.'
[[ "$previous_sha" =~ ^[0-9a-f]{40}$ ]] || fail 'Previous SHA is invalid.'
[[ "$workflow_run_id" =~ ^[0-9]{1,20}$ ]] || fail 'Workflow run ID is invalid.'
[[ "$api_digest" =~ ^sha256:[0-9a-f]{64}$ ]] || fail 'API digest is invalid.'
[[ "$web_digest" =~ ^sha256:[0-9a-f]{64}$ ]] || fail 'Web digest is invalid.'
[[ "$actor" =~ ^[A-Za-z0-9][A-Za-z0-9-]{0,38}(\[bot\])?$ ]] || fail 'Actor is invalid.'
[[ "$started_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
  || fail 'startedAt is invalid.'
if [ -n "$finished_at" ]; then
  [[ "$finished_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
    || fail 'finishedAt is invalid.'
fi
[ -x "$reporter" ] && [ -f "$reporter" ] && [ ! -L "$reporter" ] \
  || fail 'Installed HomeOps reporter is unavailable or unsafe.'

FORMDOCK_EVENT_STATUS="$status" \
FORMDOCK_EVENT_COMMIT_SHA="$commit_sha" \
FORMDOCK_EVENT_PREVIOUS_SHA="$previous_sha" \
FORMDOCK_EVENT_RUN_ID="$workflow_run_id" \
FORMDOCK_EVENT_STARTED_AT="$started_at" \
FORMDOCK_EVENT_FINISHED_AT="$finished_at" \
FORMDOCK_EVENT_API_DIGEST="$api_digest" \
FORMDOCK_EVENT_WEB_DIGEST="$web_digest" \
FORMDOCK_EVENT_ACTOR="$actor" \
python3 - <<'PY' | "$reporter" deployments
import json
import os

status = os.environ["FORMDOCK_EVENT_STATUS"]
finished_at = os.environ["FORMDOCK_EVENT_FINISHED_AT"] or None
failure_stage = "candidate-verification" if status == "FAILED" else None
failure_summary = "Bounded FormDock deployment verification failed." if status == "FAILED" else None

payload = {
    "eventKey": f"formdock-production-{os.environ['FORMDOCK_EVENT_RUN_ID']}",
    "project": "form-dock",
    "environment": "Production",
    "branch": "main",
    "commitSha": os.environ["FORMDOCK_EVENT_COMMIT_SHA"],
    "imageTag": (
        f"api@{os.environ['FORMDOCK_EVENT_API_DIGEST']},"
        f"web@{os.environ['FORMDOCK_EVENT_WEB_DIGEST']}"
    ),
    "previousCommitSha": os.environ["FORMDOCK_EVENT_PREVIOUS_SHA"],
    "status": status,
    "startedAt": os.environ["FORMDOCK_EVENT_STARTED_AT"],
    "finishedAt": finished_at,
    "failureStage": failure_stage,
    "failureSummary": failure_summary,
    "actor": os.environ["FORMDOCK_EVENT_ACTOR"],
    "workflowRunId": os.environ["FORMDOCK_EVENT_RUN_ID"],
    "workflowRunUrl": (
        "https://github.com/xxh3898/form-dock/actions/runs/"
        + os.environ["FORMDOCK_EVENT_RUN_ID"]
    ),
    "rollback": status == "ROLLED_BACK",
}
print(json.dumps(payload, separators=(",", ":")))
PY
