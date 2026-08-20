---
title: Application Scaffold Contract
status: active
version: 1.6
last_updated: 2026-08-20
---

# 1. Purpose

이 문서는 Phase 0 이후 첫 scaffold PR의 기술 경계와 선택된 baseline을 정의한다. 실제 project, local container와 validation workflow는 승인된 scaffold branch에서 생성하며 business schema와 feature는 후속 PR로 분리한다.

# 2. Backend Scaffold

```text
Java 25
Spring Boot 4.1.0
Gradle Wrapper 9.7.0
```

초기 dependency 범위:

- Spring Web
- Spring Security
- Spring Session JDBC
- Spring Data JPA
- Bean Validation
- PostgreSQL Driver
- Flyway
- Actuator
- Spring REST Docs
- Backend test support
- Testcontainers PostgreSQL

Spring Boot가 관리하는 Spring dependency version은 override하지 않는다. Wrapper와 plugin은 stable release만 사용하며 milestone, release candidate, snapshot은 사용하지 않는다.

# 3. Frontend Scaffold

```text
Node.js 24.19.0 LTS
React 19.2.8
TypeScript 6.0.2
Vite 8.2.1
npm 11.17.0
```

V1 package manager는 별도 global tool을 추가하지 않는 `npm`으로 고정하고 `package-lock.json`을 commit한다. Router는 첫 navigation feature까지 deferred하며 scaffold dependency에 포함하지 않는다.

# 4. Database Scaffold

```text
PostgreSQL 18.6
Flyway-only production schema
```

JPA production auto-DDL과 Spring Session schema auto-initialization을 local, test, production에서 사용하지 않는다. Application table과 Spring Session JDBC table은 모두 versioned Flyway migration이 소유한다. Initial scaffold에는 versioned migration이 없었고 cleanup scheduler도 비활성화했지만, Phase 1 PR A가 `V1__create_users.sql`과 `V2__create_spring_session.sql`을 추가한 뒤 cleanup scheduler를 활성화한다.

# 5. Infrastructure Scaffold

Local Compose와 최종 production topology는 다음 세 service boundary를 공유한다.

```text
web
api
postgres
```

Scaffold baseline은 `postgres:18.6-alpine3.23`, `gradle:9.7.0-jdk25-alpine`, `eclipse-temurin:25.0.3_9-jre-alpine-3.23`, `node:24.19.0-alpine3.24`, `nginx:1.30.4-alpine3.24`를 사용한다. Local Compose project는 `dev-form-dock`이며 loopback port와 development-only volume을 사용한다.

GitHub Actions는 backend/frontend/infrastructure validation만 수행한다. GHCR publish, deployment, Cloudflare 변경은 포함하지 않는다. 개발 runtime과 Mac mini production runtime은 분리한다.

# 6. Implementation Sequence

1. Backend/Frontend project scaffold와 CI baseline
2. Creator authentication, JDBC session, one-time bootstrap
3. Phase 2-A Survey DRAFT Core — complete on `dev`
4. Phase 2-B Question/Lock Data Foundation — complete on `dev`
5. Phase 2-C Survey Builder Backend Completion — complete on `dev`
6. Phase 2-D Survey Builder Frontend + Preview — complete on `dev`
7. Phase 2 Completion / Integration Evidence + Gate 3 main RC — PASS, separate release pending
8. Public Survey, atomic Response, idempotency — Phase 3, not authorized
9. Result dashboard와 CSV export — not authorized
10. Production infrastructure와 dogfooding readiness — not authorized

각 PR은 관련 contract test와 문서 동기화를 포함한다. API, schema, infrastructure를 단일 bootstrap PR에 함께 구현하지 않는다.

Phase 2의 첫 Question structure mutation path보다 먼저 final `survey_responses` table을 schema-only canonical existence authority로 준비한다. Structure mutation은 Survey row lock 안에서 real table을 조회하며 constant-false stub이나 mutable lock flag/count를 사용하지 않는다. [ADR-0006](../08-decisions/adr-0006-response-schema-sequencing-for-structure-lock.md)에 따라 Phase 2는 SurveyResponse row를 생성하지 않고, Phase 3가 Public Response runtime, 최초 canonical insert와 Answer/AnswerOption persistence를 소유한다.

Phase 2-A→B→C→D는 scheduling 순서이며 동시에 여러 slice를 시작하는 권한이 아니다. 각 slice는 직전 PR이 `dev`에 merge되고 exact SHA/Validate가 확인된 뒤 별도 Issue로 시작한다. 세부 scope는 [Roadmap](../00-product/roadmap.md)이 소유한다.

# 7. Authorization Gate

```text
Phase 0                       COMPLETE
Application Scaffold         COMPLETE
Phase 1 Creator Foundation       COMPLETE + RELEASED
Phase 2 Survey Builder           COMPLETE ON DEV — MAIN RC READY TO OPEN
Phase 3 Public Survey/Response   NOT AUTHORIZED
Production                       NOT AUTHORIZED
```

Creator Foundation은 `main`에 release됐다. Phase 2-A/B/C/D는 `dev`에 통합됐고 [Phase 2 Completion Evidence](../06-quality/phase-2-completion-evidence.md)와 [Phase 2 Main Release Evidence](../06-quality/phase-2-main-release-evidence.md)가 integration, full release diff, native ARM64와 Flyway compatibility를 `PASS`로 판정했다. Evidence PR의 user merge/latest dev 검증 뒤 별도 Phase 2 `dev → main` Release Issue/PR만 열 수 있다. Public Survey/Response, Result/CSV와 Production은 별도 승인 전 구현하거나 활성화하지 않는다.

# 8. Reference

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
