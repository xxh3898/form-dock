---
title: Application Scaffold Contract
status: active
version: 1.1
last_updated: 2026-08-19
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

JPA production auto-DDL과 Spring Session schema auto-initialization을 local, test, production에서 사용하지 않는다. Application table과 Spring Session JDBC table은 모두 versioned Flyway migration이 소유한다. Scaffold에는 versioned migration이 없으며 Session infrastructure migration은 authentication PR이 소유한다. 해당 migration 전에는 존재하지 않는 Session table을 조회하지 않도록 cleanup scheduler도 비활성화한다.

# 5. Infrastructure Scaffold

Local Compose와 최종 production topology는 다음 세 service boundary를 공유한다.

```text
web
api
postgres
```

Scaffold baseline은 `postgres:18.6-alpine3.23`, `gradle:9.7.0-jdk25-alpine`, `eclipse-temurin:25.0.3_9-jre-alpine-3.23`, `node:24.19.0-alpine3.24`, `nginx:1.30.4-alpine3.24`를 사용한다. Local Compose project는 `dev-form-dock`이며 loopback port와 development-only volume을 사용한다.

GitHub Actions는 backend/frontend/infrastructure validation만 수행한다. GHCR publish, deployment, Cloudflare 변경은 포함하지 않는다. 개발 runtime과 Mac mini production runtime은 분리한다.

# 6. Initial PR Sequence

1. Backend/Frontend project scaffold와 CI baseline
2. Creator authentication, JDBC session, one-time bootstrap
3. Survey CRUD와 lifecycle
4. Question Builder backend와 structure lock
5. Question Builder frontend와 preview
6. Public Survey, atomic Response, idempotency
7. Result dashboard와 CSV export
8. Production infrastructure와 dogfooding readiness

각 PR은 관련 contract test와 문서 동기화를 포함한다. API, schema, infrastructure를 단일 bootstrap PR에 함께 구현하지 않는다.

# 7. Authorization Gate

```text
Phase 0                       COMPLETE
Application Scaffold         COMPLETE
Current Phase                 Phase 1 — Creator Foundation
Creator Foundation            AUTHORIZED
Survey Domain                 NOT AUTHORIZED
```

Creator persistence/authentication/session과 최소 Login/Admin shell만 구현할 수 있다. Survey, Question, Response, Result와 CSV는 별도 승인 전 구현하지 않는다.

# 8. Reference

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
