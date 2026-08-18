# Flyway Migration Boundary

This scaffold contains no versioned migration.

Production application tables and Spring Session JDBC tables must be introduced through reviewed Flyway migrations in their owning implementation PR. Framework schema auto-initialization remains disabled in local, test, and production profiles.
