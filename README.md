# FormDock

Self-hosted survey builder and response collection platform for reusable project research.

## Status

Phase 3 — Public Survey/Response (Phase 3-A 구현 완료, dev 통합 필요)

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

Application scaffold와 Phase 1 Creator Foundation, Phase 2 Survey Builder는 완료되어 `main`에 release됐다. [Phase 2 완료 근거](docs/06-quality/phase-2-completion-evidence.md)와 [main release 근거](docs/06-quality/phase-2-main-release-evidence.md)는 exact 통합 및 Gate 3 release tree를 기록한다. 현재 tree에는 Phase 3-A 익명 OPEN Public Survey GET만 구현되어 있으며, Phase 3-B를 시작하려면 사용자 merge와 latest `dev` validation이 필요하다. V6 Answer schema, public respondent route, response submission, Phase 4 results/export와 Production은 roadmap에 따라 아직 구현되지 않았거나 승인되지 않았다.
