# Flyway Migration Ownership

- `V1__create_users.sql`: Creator/User business persistence schema.
- `V2__create_spring_session.sql`: Spring Session JDBC 4.1.0 PostgreSQL infrastructure schema.

Flyway is the schema authority in local, test, and production profiles. Framework schema auto-initialization remains disabled.
