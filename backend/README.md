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
- Phase 3-A anonymous OPEN Public Survey read API and respondent-safe DTO
- PostgreSQL and Flyway wiring
- Actuator health
- Spring REST Docs auth/Survey snippets and PostgreSQL/Testcontainers integration evidence

Product SurveyResponse writes, Answer persistence, Public Response submission, and the Respondent frontend are intentionally absent.
