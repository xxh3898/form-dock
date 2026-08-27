# FormDock

Self-hosted survey builder and response collection platform for reusable project research.

## Status

Phase 5 — Production Readiness (5-A~5-C1 `dev` 통합 완료, 5-C2 remote artifact publish 완료·evidence 통합 필요)

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
- CI/CD: GitHub Actions와 GHCR exact release artifacts

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

Application scaffold와 Phase 1~4 capability는 `main`에 release됐다. Phase 3 repository Release identity는 annotated `v0.3.0`이고, Phase 4 Creator Results/CSV capability는 [Phase 4 완료 근거](docs/06-quality/phase-4-completion-evidence.md) 및 [Phase 4 main release 근거](docs/06-quality/phase-4-main-release-evidence.md)의 검증 뒤 PR #79로 release돼 annotated `v0.4.0`으로 식별된다. GitHub Release는 필요하지 않아 생성하지 않았다. Phase 5-A~5-C1은 `dev`에 통합됐다. Issue #89의 GitHub-hosted native ARM64 job이 exact `v0.4.0` API/Web images를 GHCR에 publish하고 remote digest pull acceptance를 통과했으며, [Phase 5-C2 publication evidence](docs/06-quality/phase-5-c2-remote-artifact-publication-evidence.md)는 `dev` 통합을 기다린다. Secret, live database, Cloudflare와 Production deployment는 별도 Phase 5-D Gate 전까지 승인되지 않는다.
