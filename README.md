# FormDock

Self-hosted survey builder and response collection platform for reusable project research.

## Status

Phase 5 — Production Readiness (5-A Runtime Foundation 구현 완료, `dev` 통합 필요)

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
infra/     Local/Production Docker Compose 계약 경계
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

Application scaffold와 Phase 1~4 capability는 `main`에 release됐다. Phase 3 repository Release identity는 annotated `v0.3.0`이고, Phase 4 Creator Results/CSV capability는 [Phase 4 완료 근거](docs/06-quality/phase-4-completion-evidence.md) 및 [Phase 4 main release 근거](docs/06-quality/phase-4-main-release-evidence.md)의 검증 뒤 PR #79로 release돼 annotated `v0.4.0`으로 식별된다. GitHub Release는 필요하지 않아 생성하지 않았다. Phase 5-A Production Runtime Foundation은 canonical Compose/config와 isolated validation까지 구현됐고 `dev` 통합을 기다린다. Image publish, Secret, live database, Cloudflare와 Production deployment는 별도 Gate 전까지 승인되지 않는다.
