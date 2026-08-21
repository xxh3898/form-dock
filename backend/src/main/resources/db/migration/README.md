# Flyway Migration Ownership

- `V1__create_users.sql`: Creator/User business persistence schema.
- `V2__create_spring_session.sql`: Spring Session JDBC 4.1.0 PostgreSQL infrastructure schema.
- `V3__create_surveys.sql`: Phase 2-A Survey business persistence schema.
- `V4__create_questions_and_options.sql`: Question and QuestionOption schema.
- `V5__create_survey_responses.sql`: schema-only canonical Response authority.
- `V6__create_answers_and_answer_options.sql`: Phase 3-B Answer/AnswerOption schema.

Flyway is the schema authority in local, test, and production profiles. Framework schema auto-initialization remains disabled.

V5에는 의도적으로 Product writer, Answer schema와 Response API를 추가하지 않았다.
Phase 2는 canonical Response count/existence만 읽는다. Phase 3-B는 V6와 caller-owned
persistence primitive를 추가하고, Phase 3-C가 Public submission transaction과 HTTP runtime을 소유한다.
