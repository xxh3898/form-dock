# Backend

Java 25 / Spring Boot 4.1 backend.

## Commands

```bash
./gradlew clean check
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile reads PostgreSQL connection values from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`.

PostgreSQL integration tests start the pinned Testcontainers image by default. An
already-isolated PostgreSQL test container can be used by setting
`FORMDOCK_TESTCONTAINERS_ENABLED=false` together with disposable test datasource
environment variables.

## Scope

- Spring Web MVC, Security, Data JPA, Validation
- Creator/User persistence and disabled-by-default one-time bootstrap
- Flyway-owned `users` and Spring Session JDBC schemas
- Spring Session JDBC wiring with schema auto-initialization disabled and cleanup scheduling enabled
- Creator CSRF/Login/Logout/Current Creator APIs with deny-by-default session security
- Phase 2-A owner-scoped Survey DRAFT persistence, metadata API, slug allocation, and soft delete
- Phase 2-B Question/Option persistence, read-only canonical Response count/existence, and bounded Survey structure lock
- Phase 2-C Question mutation, Survey open/close, and atomic deep duplicate backend APIs
- Phase 3-A 익명 OPEN Public Survey 조회 API와 respondent-safe DTO
- Phase 3-B Flyway V6 Answer schema, caller-owned Response/Answer persistence와 deterministic payload canonicalization
- PostgreSQL and Flyway wiring
- Actuator health
- Spring REST Docs auth/Survey snippets and PostgreSQL/Testcontainers integration evidence

Public Response POST, Survey lock 기반 submission service, HTTP replay mapping과 Respondent frontend는 의도적으로 포함하지 않았다.
