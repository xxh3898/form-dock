---
title: FormDock Documentation Index
status: draft
version: 1.3
last_updated: 2026-08-27
---

# FormDock Documentation

FormDock은 개인/소규모 프로젝트에서 반복적으로 사용할 수 있는 self-hosted 설문 제작·응답 수집 플랫폼이다.

## Current Product Boundary

```text
Product       FormDock
Repository    form-dock
Domain        forms.chochiho.cloud

Backend       Java 25 + Spring Boot 4 + Gradle
Frontend      React + TypeScript + Vite
Database      PostgreSQL 18
Migration     Flyway

Runtime       Docker Compose
Hosting       Mac mini
External      Cloudflare Tunnel

Creators      승인된 계정만
Respondents   비로그인 공개 참여

V1            Survey Builder
              Public Survey
              Response Collection
              Result Dashboard
              CSV Export
```

## Status Convention

- `draft`: 방향은 잡혔지만 구현 전 변경 가능
- `accepted`: 명시적으로 승인된 결정
- `active`: 현재 운영/개발 기준
- `deprecated`: 더 이상 기준으로 사용하지 않음

## Documentation Map

- `00-product`: 제품 목표와 범위
- `01-domain`: 핵심 도메인 규칙
- `02-requirements`: 사용자/기능 요구사항
- `03-architecture`: 기술 구조와 경계
- `04-data`: ERD, 데이터 사전, 마이그레이션, 보존
- `05-api`: REST API 계약
- `06-quality`: 테스트, 승인 기준, 품질 Gate
- `07-operations`: 로컬/배포/백업/모니터링
- `08-decisions`: ADR

Scaffold 범위와 후속 PR 순서는 [Application Scaffold Contract](03-architecture/scaffold-contract.md)에서 관리한다.

## Source of Truth Hierarchy

같은 decision scope에서 문서가 충돌하면 다음 순서를 적용한다.

1. 해당 범위의 `accepted` ADR
2. Product scope와 PRD
3. Domain invariant와 lifecycle
4. 사용자·기능 requirements
5. Architecture, data, API contract
6. Quality와 operations contract

ADR은 명시된 architecture decision만 소유하며 product scope를 재정의하지 않는다. 각 상세 값은 해당 영역의 authoritative document에 한 번만 정의하고 다른 문서는 링크하거나 관찰 가능한 결과만 요약한다.

## Current Gate

```text
Phase 0                          COMPLETE
Application Scaffold            COMPLETE
Phase 1 Creator Foundation       COMPLETE + RELEASED
Phase 2 Survey Builder           COMPLETE + RELEASED
Phase 3 Public Survey/Response   COMPLETE + RELEASED
Phase 4 Results / Export         COMPLETE + RELEASED — v0.4.0
Phase 4 Gate 3                   PASS + RELEASED
Phase 5 Production Readiness     AUTHORIZED — repository/readiness slices only
Phase 5-A Runtime Foundation     COMPLETE + DEV INTEGRATED
Phase 5-B Backup/Restore         COMPLETE + DEV INTEGRATED
Phase 5-C1 Delivery/Monitoring   COMPLETE + DEV INTEGRATED
Phase 5-C2 Remote Artifact       PUBLISHED — EVIDENCE DEV INTEGRATION PENDING
Phase 5-D Activation Gate        NOT AUTHORIZED
Production Activation           NOT AUTHORIZED
GitHub Release                   NOT REQUIRED / NOT CREATED
```

Application scaffold와 Phase 1 Creator Foundation은 [Phase 1 Completion Evidence](06-quality/phase-1-completion-evidence.md)와 [Phase 1 Main Release Evidence](06-quality/phase-1-main-release-evidence.md)를 거쳐 `main`에 release됐다. Phase 2-A→D도 owner-scoped Survey/Question Builder, Admin Preview와 [ADR-0006](08-decisions/adr-0006-response-schema-sequencing-for-structure-lock.md)의 schema-only Response lock authority를 [Phase 2 Completion Evidence](06-quality/phase-2-completion-evidence.md) 및 [Phase 2 Main Release Evidence](06-quality/phase-2-main-release-evidence.md)의 exact Gate 3 tree로 `main`에 release했다. Release는 Production activation을 포함하지 않는다.

Phase 3-A anonymous OPEN Public Survey GET, Phase 3-B V6/data/canonicalization foundation, Phase 3-C atomic Public Response POST와 Phase 3-D `/s/:slug` respondent frontend는 [Phase 3 Completion Evidence](06-quality/phase-3-completion-evidence.md)와 [Phase 3 Main Release Evidence](06-quality/phase-3-main-release-evidence.md)의 검증을 거쳐 PR #60으로 `main`에 release됐다. Annotated tag `v0.3.0`은 Phase 3 repository Release identity이며 Production 증거가 아니다.

Phase 4-A~D Creator-owned Response list/detail, bounded summary, CSV export와 Admin Results UI는 [Phase 4 Completion Evidence](06-quality/phase-4-completion-evidence.md)의 exact `dev` 통합·application smoke와 [Phase 4 Main Release Evidence](06-quality/phase-4-main-release-evidence.md)의 full diff, native ARM64, released-main same V6 compatibility 및 previous-main rollback 검증을 통과했다. PR #79가 exact tree를 `main`에 release했고 annotated `v0.4.0`이 repository identity다. GitHub Release와 Production activation은 수행하지 않았다.

Phase 5는 repository-only 5-A, isolated recovery evidence 5-B, delivery/monitoring foundation 5-C1, exact remote artifact publication evidence 5-C2와 별도 live-operation 승인 Gate인 5-D 순서로 진행한다. 5-A~5-C1은 `dev`에 통합됐다. Issue #89가 승인한 exact `v0.4.0` API/Web artifacts는 GHCR에 publish됐고 remote digest pull acceptance를 통과했다. [Phase 5-C2 Remote Artifact Publication Evidence](06-quality/phase-5-c2-remote-artifact-publication-evidence.md)는 `dev` 통합을 기다린다. Production Secret, live DB/backup/restore, Cloudflare와 deployment activation은 승인되지 않았다.
