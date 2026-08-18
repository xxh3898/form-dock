# FormDock

Self-hosted survey builder and response collection platform for reusable project research.

## Status

Application Scaffold — Baseline

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

The backend, frontend, local runtime, and CI scaffold baseline is established. Business feature implementation has not started.
