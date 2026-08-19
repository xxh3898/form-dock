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
- PostgreSQL and Flyway wiring
- Actuator health
- Spring REST Docs and Testcontainers test baseline

Login/Logout/Me controllers, frontend authentication, and Survey domain implementation are intentionally absent.
