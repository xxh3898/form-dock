# Flyway Migration Ownership

- `V1__create_users.sql`: Creator/User business persistence schema.
- `V2__create_spring_session.sql`: Spring Session JDBC 4.1.0 PostgreSQL infrastructure schema.
- `V3__create_surveys.sql`: Phase 2-A Survey business persistence schema.
- `V4__create_questions_and_options.sql`: Question and QuestionOption schema.
- `V5__create_survey_responses.sql`: schema-only canonical Response authority.

Flyway is the schema authority in local, test, and production profiles. Framework schema auto-initialization remains disabled.

V5 intentionally adds no Product writer, Answer schema, or Response API. Phase 2 reads
canonical Response counts/existence only; Phase 3 owns submission runtime.
