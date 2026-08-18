# Backend

Java 25 / Spring Boot 4.1 backend scaffold.

## Commands

```bash
./gradlew clean check
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile reads PostgreSQL connection values from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`.

## Scope

- Spring Web MVC, Security, Data JPA, Validation
- Spring Session JDBC wiring with schema auto-initialization and cleanup scheduling disabled until the authentication migration
- PostgreSQL and Flyway wiring
- Actuator health
- Spring REST Docs and Testcontainers test baseline

Business controllers, entities, repositories, and versioned migrations are intentionally absent.
