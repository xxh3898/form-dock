# FormDock

Self-hosted survey builder and response collection platform for reusable project research.

## Status

Phase 4 — Results / Export (구현 승인, serial slice 착수 대기)

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

Application scaffold, Phase 1 Creator Foundation과 Phase 2 Survey Builder는 완료되어 `main`에 release됐다. Phase 3-A~D Public Survey/Response backend와 `/s/:slug` respondent frontend도 [Phase 3 완료 근거](docs/06-quality/phase-3-completion-evidence.md) 및 [Phase 3 main release 근거](docs/06-quality/phase-3-main-release-evidence.md)의 Gate 3 검증 뒤 PR #60으로 `main`에 release됐고 repository Release identity는 annotated tag `v0.3.0`이다. Phase 4 Creator Results/Export 구현은 승인됐지만 아직 시작되지 않았으며 Production은 승인되지 않았다. Repository Release와 tag는 Production 배포 또는 activation을 뜻하지 않는다.
