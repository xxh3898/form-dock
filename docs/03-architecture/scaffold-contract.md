---
title: Application Scaffold Contract
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Purpose

이 문서는 Phase 0 이후 첫 scaffold PR의 기술 경계를 정의한다. 실제 project, schema, container, workflow는 별도 승인된 scaffold 세션에서 생성한다.

# 2. Backend Scaffold

```text
Java 25
Spring Boot 4.x
Gradle Wrapper
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

Spring Boot 4.x exact stable version과 호환 Gradle version은 scaffold 실행 시점의 공식 문서를 다시 확인해 고정한다. 임의의 milestone, release candidate, snapshot은 사용하지 않는다.

# 3. Frontend Scaffold

```text
Node.js Active LTS
React
TypeScript
Vite
npm
```

V1 package manager는 별도 global tool을 추가하지 않는 `npm`으로 고정하고 `package-lock.json`을 commit한다. Node.js exact Active LTS major와 React/Vite/TypeScript stable version은 scaffold 실행 시 호환성을 확인한다.

# 4. Database Scaffold

```text
PostgreSQL 18
Flyway-only production schema
```

JPA production auto-DDL과 Spring Session production schema auto-initialization을 사용하지 않는다. Application table과 Spring Session JDBC table은 모두 versioned Flyway migration이 소유한다. 실제 table/migration은 scaffold PR이 아니라 해당 domain implementation PR에서 작성한다.

# 5. Infrastructure Scaffold

최종 production topology는 다음 세 service boundary를 사용한다.

```text
web
api
postgres
```

Docker Compose 파일, Dockerfile, Cloudflare 설정, GHCR와 GitHub Actions는 project scaffold 이후 별도 infra 범위에서 구현한다. 개발 runtime과 Mac mini production runtime은 분리한다.

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
Current Phase                 Phase 0 — Foundation & Contracts
Application implementation   NOT AUTHORIZED
```

이 contract를 `dev`에 병합한 뒤에도 별도의 scaffold 실행 승인이 있어야 project 파일을 생성할 수 있다.

# 8. Reference

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
