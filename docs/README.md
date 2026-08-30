---
title: FormDock Documentation Index
status: draft
version: 1.5
last_updated: 2026-08-30
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
Phase 5 Production Readiness     COMPLETE — PRODUCTION ACTIVE + ACCEPTED
Phase 5-A Runtime Foundation     COMPLETE + DEV INTEGRATED
Phase 5-B Backup/Restore         COMPLETE + DEV INTEGRATED
Phase 5-C1 Delivery/Monitoring   COMPLETE + DEV INTEGRATED
Phase 5-C2 Remote Artifact       COMPLETE + DEV INTEGRATED
Phase 5-D1 Activation Preflight  COMPLETE + DEV INTEGRATED
Phase 5-D2A Local Bootstrap      COMPLETE + DEV INTEGRATED
Phase 5-D2B Public/HomeOps       LIVE ACTIVE + ACCEPTED
Production Activation           ACTIVE + ACCEPTED
Production CD Foundation        COMPLETE + RELEASED
Production CD Control Plane     ACTIVE + ACCEPTED
Production CD Automation        ARMED — FIRST APPLICATION AUTO DEPLOY PENDING
Phase 6 Dogfooding               IN PROGRESS — 6-A COMPLETE / COLLECTION ACTIVE
Phase 6-B Analysis               NOT AUTHORIZED
GitHub Release                   NOT REQUIRED / NOT CREATED
```

Application scaffold와 Phase 1 Creator Foundation은 [Phase 1 Completion Evidence](06-quality/phase-1-completion-evidence.md)와 [Phase 1 Main Release Evidence](06-quality/phase-1-main-release-evidence.md)를 거쳐 `main`에 release됐다. Phase 2-A→D도 owner-scoped Survey/Question Builder, Admin Preview와 [ADR-0006](08-decisions/adr-0006-response-schema-sequencing-for-structure-lock.md)의 schema-only Response lock authority를 [Phase 2 Completion Evidence](06-quality/phase-2-completion-evidence.md) 및 [Phase 2 Main Release Evidence](06-quality/phase-2-main-release-evidence.md)의 exact Gate 3 tree로 `main`에 release했다. Release는 Production activation을 포함하지 않는다.

Phase 3-A anonymous OPEN Public Survey GET, Phase 3-B V6/data/canonicalization foundation, Phase 3-C atomic Public Response POST와 Phase 3-D `/s/:slug` respondent frontend는 [Phase 3 Completion Evidence](06-quality/phase-3-completion-evidence.md)와 [Phase 3 Main Release Evidence](06-quality/phase-3-main-release-evidence.md)의 검증을 거쳐 PR #60으로 `main`에 release됐다. Annotated tag `v0.3.0`은 Phase 3 repository Release identity이며 Production 증거가 아니다.

Phase 4-A~D Creator-owned Response list/detail, bounded summary, CSV export와 Admin Results UI는 [Phase 4 Completion Evidence](06-quality/phase-4-completion-evidence.md)의 exact `dev` 통합·application smoke와 [Phase 4 Main Release Evidence](06-quality/phase-4-main-release-evidence.md)의 full diff, native ARM64, released-main same V6 compatibility 및 previous-main rollback 검증을 통과했다. PR #79가 exact tree를 `main`에 release했고 annotated `v0.4.0`이 repository identity다. GitHub Release와 Production activation은 수행하지 않았다.

Phase 5는 repository-only 5-A, isolated recovery evidence 5-B, delivery/monitoring foundation 5-C1, exact remote artifact publication evidence 5-C2, read-only activation preflight 5-D1, local bootstrap 5-D2A와 public/HomeOps final activation 5-D2B 순서로 완료했다. 5-A~5-D2A는 `dev`에 통합됐다. Issue #95는 exact public route, secure same-origin session, bounded Product canary와 HomeOps service/reporter를 [Phase 5-D2B Public/HomeOps Final Activation Evidence](06-quality/phase-5-d2b-public-homeops-activation-evidence.md)로 검증해 Production을 `ACTIVE + ACCEPTED`로 판정했다. Off-host durability는 accepted deferred risk다. Issue #97의 bounded authorization은 [Phase 6-A Dogfooding Launch Evidence](06-quality/phase-6a-dogfooding-launch-evidence.md)에 따라 real Survey launch와 collection handoff를 완료했다. Phase 6-B와 Phase 6 전체는 계속 별도 Gate다.

Issue #103은 [ADR-0007](08-decisions/adr-0007-production-cd-change-gate.md) foundation을 `main`에 release한 뒤 protected `Production` Environment, restricted SSH, stable runtime, initial baseline과 kill switch를 수립해 [Production CD Control-plane Activation Evidence](06-quality/phase-5-production-cd-control-plane-activation-evidence.md)로 `ACTIVE + ACCEPTED`를 확정했다. Exact current-main safe no-op에서 publication/deploy는 0이었으며, 첫 eligible application 자동 배포는 pending이다.
