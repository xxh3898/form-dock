package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@Transactional
class DatabaseMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_createRequiredTables_when_cleanDatabaseIsMigrated() {
        assertThat(tableNames())
                .contains("flyway_schema_history", "users", "spring_session",
                        "spring_session_attributes", "surveys", "questions",
                        "question_options", "survey_responses")
                .doesNotContain(
                        "answers",
                        "answer_options");
    }

    @Test
    void should_applyExpectedVersionedHistory_when_cleanDatabaseIsMigrated() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT script
                FROM flyway_schema_history
                WHERE type = 'SQL'
                ORDER BY installed_rank
                """, String.class))
                .containsExactly(
                        "V1__create_users.sql",
                        "V2__create_spring_session.sql",
                        "V3__create_surveys.sql",
                        "V4__create_questions_and_options.sql",
                        "V5__create_survey_responses.sql");
    }

    @Test
    void should_createSpringSessionIndexesAndCascadeForeignKey_when_sessionMigrationRuns() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'spring_session'
                """, String.class);

        assertThat(indexes)
                .contains("spring_session_pk", "spring_session_ix1", "spring_session_ix2",
                        "spring_session_ix3");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name = 'spring_session_attributes_fk'
                """, String.class))
                .isEqualTo("CASCADE");
    }

    @Test
    void should_enforceAdminRole_when_usersRowIsInserted() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                "invalid-role@example.test",
                "{bcrypt}test-only-hash",
                "Invalid Role",
                "EDITOR",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_createSurveyConstraintsAndOwnerListIndex_when_surveyMigrationRuns() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name = 'fk_surveys_owner'
                """, String.class))
                .isEqualTo("NO ACTION");

        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = 'surveys'
                """, String.class);
        assertThat(constraints)
                .contains("pk_surveys", "fk_surveys_owner", "uk_surveys_slug", "ck_surveys_status");

        String indexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'surveys'
                  AND indexname = 'ix_surveys_owner_active_updated'
                """, String.class);
        assertThat(indexDefinition)
                .contains("owner_id", "updated_at DESC", "id DESC", "deleted_at IS NULL");
    }

    @Test
    void should_rejectSurveyStatus_when_valueIsOutsideCanonicalLifecycle() {
        Long ownerId = createOwner("survey-owner@example.test");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                ownerId,
                "Invalid status",
                "invalid-status",
                "ARCHIVED",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_enforceQuestionTypePositionAndConfiguration_when_v4IsApplied() {
        Long surveyId = createSurvey(createOwner("question-owner@example.test"));

        assertThat(constraintNames("questions"))
                .contains(
                        "pk_questions",
                        "fk_questions_survey",
                        "uk_questions_survey_position",
                        "ck_questions_type",
                        "ck_questions_position",
                        "ck_questions_type_configuration");
        assertThat(constraintNames("question_options"))
                .contains(
                        "pk_question_options",
                        "fk_question_options_question",
                        "uk_question_options_question_position",
                        "ck_question_options_position");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name = 'fk_question_options_question'
                """, String.class))
                .isEqualTo("CASCADE");

        assertConstraintViolation(() -> insertQuestion(
                surveyId,
                "UNKNOWN",
                0,
                null,
                null,
                null,
                null));
        assertConstraintViolation(() -> insertQuestion(
                surveyId,
                "SHORT_TEXT",
                -1,
                null,
                null,
                null,
                null));
        assertConstraintViolation(() -> insertQuestion(
                surveyId,
                "SCALE",
                0,
                5,
                5,
                null,
                null));
        assertConstraintViolation(() -> insertQuestion(
                surveyId,
                "NUMBER",
                0,
                null,
                null,
                new java.math.BigDecimal("10"),
                new java.math.BigDecimal("1")));
    }

    @Test
    void should_enforceResponseIdentityAndPayloadHash_when_v5IsApplied() {
        Long surveyId = createSurvey(createOwner("response-owner@example.test"));
        UUID submissionId = UUID.randomUUID();
        String payloadHash = "a".repeat(64);

        assertThat(constraintNames("survey_responses"))
                .contains(
                        "pk_survey_responses",
                        "fk_survey_responses_survey",
                        "uk_survey_responses_survey_submission",
                        "ck_survey_responses_payload_hash");

        jdbcTemplate.update("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                """,
                surveyId,
                submissionId,
                payloadHash,
                Timestamp.from(Instant.now()));

        assertConstraintViolation(() -> jdbcTemplate.update("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                """,
                surveyId,
                submissionId,
                "b".repeat(64),
                Timestamp.from(Instant.now())));
        assertConstraintViolation(() -> jdbcTemplate.update("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                """,
                surveyId,
                UUID.randomUUID(),
                "NOT-A-SHA-256",
                Timestamp.from(Instant.now())));
    }

    private Long createOwner(String email) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                email,
                "{bcrypt}test-only-hash",
                "Survey Owner",
                "ADMIN",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private Long createSurvey(Long ownerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Migration Survey",
                "migration-survey-" + ownerId,
                "DRAFT",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private void insertQuestion(
            Long surveyId,
            String type,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            java.math.BigDecimal numberMin,
            java.math.BigDecimal numberMax) {
        jdbcTemplate.update("""
                INSERT INTO questions (
                    survey_id, type, title, required, position,
                    scale_min, scale_max, number_min, number_max,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                surveyId,
                type,
                "Question",
                false,
                position,
                scaleMin,
                scaleMax,
                numberMin,
                numberMax,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private void assertConstraintViolation(Runnable statement) {
        jdbcTemplate.execute("SAVEPOINT expected_constraint_violation");
        try {
            assertThatThrownBy(statement::run)
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT expected_constraint_violation");
            jdbcTemplate.execute("RELEASE SAVEPOINT expected_constraint_violation");
        }
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);
    }

    private List<String> constraintNames(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = ?
                """, String.class, tableName);
    }
}
