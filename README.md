# FormDock

Self-hosted survey builder and response collection platform for reusable project research.

## Status

Phase 2 — Survey Builder (`dev` complete; pending Gate 3 main Release Candidate evidence)

## What is FormDock?

FormDock is a self-hosted platform for creating surveys, collecting public responses, reviewing results, and exporting response data for repeated project research.

## V1 Scope

- Survey Builder
- Public Survey
- Response Collection
- Result Dashboard
- CSV Export

## Tech Stack

- Backend: Java 25, Spring Boot 4, Gradle
- Frontend: React, TypeScript, Vite
- Database: PostgreSQL 18, Flyway
- Infrastructure: Docker Compose, Mac mini, Cloudflare Tunnel
- CI/CD: GitHub Actions and GHCR (planned)

## Architecture

Planned deployment path:

```text
Internet
→ Cloudflare Tunnel
→ Web
→ API
→ PostgreSQL
```

## Repository Structure

```text
backend/   Java and Spring Boot backend boundary
frontend/  React and TypeScript frontend boundary
infra/     Local Docker Compose boundary
docs/      Product and engineering contracts
.github/   Validation-only CI workflows
```

## Local Scaffold

```bash
docker compose --env-file .env.example -f infra/compose.yaml up --build --wait
```

The local Web endpoint is `http://127.0.0.1:18082` with API traffic kept behind the same-origin `/api` path. See the [local development guide](docs/07-operations/local-development.md) for validation and shutdown commands.

## Documentation

See the [documentation index](docs/README.md) for the current product, domain, architecture, data, API, quality, operations, and decision records.

## Development Status

The application scaffold and Phase 1 Creator Foundation are complete and released on `main`. Phase 2-A/B/C/D provide the authenticated Creator Survey Builder, Question/Option persistence, canonical Response lock authority, lifecycle/deep duplicate behavior, and Admin-only Preview on `dev`. [Phase 2 completion evidence](docs/06-quality/phase-2-completion-evidence.md) is complete; the main release gate, Phase 3 Public Survey/Response, results/export, and production remain separate pending or unauthorized gates according to the roadmap.
