## Related Issue

Closes #

## Phase

<!-- Current Product Phase from the latest dev Source of Truth. -->

## Authorization

<!-- Cite AGENTS.md Current Gate, Roadmap, relevant accepted ADR, and merged prerequisites. Roadmap scheduling alone is not authorization. -->

## Summary

<!-- Explain the observable outcome of this PR. -->

## Included

-

## Explicitly Excluded

-

## Implementation

<!-- Describe the smallest coherent implementation and the invariants it preserves. -->

## Validation

Record only checks that actually ran. Use `NOT RUN — reason` or `N/A — reason` instead of implying evidence exists.

### Backend

<!-- Canonical command: cd backend && ./gradlew --no-daemon clean check -->

- Result:
- Evidence:

### Frontend

<!-- Canonical commands: cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build -->

- Result:
- Evidence:

### PostgreSQL / Testcontainers

- Result:
- Evidence:

### Flyway

- Result:
- Evidence:

### Compose

<!-- Canonical commands: docker compose --env-file .env.example -f infra/compose.yaml config --quiet && docker compose --env-file .env.example -f infra/compose.yaml build -->

- Result:
- Evidence:

### Hosted CI

- Head SHA:
- Backend:
- Frontend:
- Infrastructure:
- Run:

### Documentation

- `git diff --check`:
- UTF-8 / LF / final newline:
- Markdown fences / local links:
- Contract contradiction scan:

## Contract Impact

<!-- Product, domain, API, architecture, quality, or operations contract changes; use N/A — reason when absent. -->

## Security / Session / CSRF

<!-- Include privacy, credential, origin, and authorization impact; use N/A — reason when absent. -->

## Data / Migration

<!-- Include schema compatibility, Flyway, backup, rollback, and data-loss impact; use N/A — reason when absent. -->

## Production Impact

<!-- State deployment, Secret, live-data, or public URL impact. Production work requires a separate Gate. -->

## Risk

<!-- List residual risks and what is not yet proven. -->

## Rollback / Recovery

<!-- Give a safe code/config/data recovery boundary without destructive shortcuts. -->

## Follow-up

<!-- Link deferred work or write NONE — reason. -->

## Next Authorized Slice

<!-- Name the next slice only when the current Roadmap and Phase gate authorize it; otherwise write NOT AUTHORIZED. -->
