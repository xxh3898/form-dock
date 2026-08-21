package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
                .containsExactlyInAnyOrder("flyway_schema_history", "users", "spring_session",
                        "spring_session_attributes", "surveys", "questions",
                        "question_options", "survey_responses", "answers",
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
                        "V5__create_survey_responses.sql",
                        "V6__create_answers_and_answer_options.sql");
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

    @Test
    void should_preserveSurveyResponseDefinition_when_v6IsApplied() {
        assertThat(columnNames("survey_responses"))
                .containsExactly(
                        "id",
                        "survey_id",
                        "client_submission_id",
                        "payload_hash",
                        "submitted_at");
        assertThat(constraintNames("survey_responses"))
                .contains(
                        "pk_survey_responses",
                        "fk_survey_responses_survey",
                        "uk_survey_responses_survey_submission",
                        "ck_survey_responses_payload_hash");
    }

    @Test
    void should_createAnswerConstraintsAndDeleteRules_when_v6IsApplied() {
        assertThat(columnNames("answers"))
                .containsExactly(
                        "id",
                        "response_id",
                        "question_id",
                        "text_value",
                        "numeric_value",
                        "created_at");
        assertThat(columnNames("answer_options"))
                .containsExactly("answer_id", "option_id");
        assertThat(constraintNames("answers"))
                .contains(
                        "pk_answers",
                        "fk_answers_response",
                        "fk_answers_question",
                        "uk_answers_response_question",
                        "ck_answers_scalar_value");
        assertThat(constraintNames("answer_options"))
                .contains(
                        "pk_answer_options",
                        "fk_answer_options_answer",
                        "fk_answer_options_option");
        assertThat(deleteRule("fk_answers_response")).isEqualTo("CASCADE");
        assertThat(deleteRule("fk_answers_question")).isEqualTo("NO ACTION");
        assertThat(deleteRule("fk_answer_options_answer")).isEqualTo("CASCADE");
        assertThat(deleteRule("fk_answer_options_option")).isEqualTo("NO ACTION");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT numeric_precision
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'answers'
                  AND column_name = 'numeric_value'
                """, Integer.class)).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT numeric_scale
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'answers'
                  AND column_name = 'numeric_value'
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'answers'
                  AND column_name = 'text_value'
                """, Integer.class)).isEqualTo(5000);
    }

    @Test
    void should_enforceAnswerAndAnswerOptionConstraints_when_v6IsApplied() {
        Long surveyId = createSurvey(createOwner("answer-constraint-owner@example.test"));
        Long textQuestionId = insertQuestion(
                surveyId,
                "SHORT_TEXT",
                0,
                null,
                null,
                null,
                null);
        Long choiceQuestionId = insertQuestion(
                surveyId,
                "SINGLE_CHOICE",
                1,
                null,
                null,
                null,
                null);
        Long optionId = insertOption(choiceQuestionId, "Choice", 0);
        Long responseId = insertResponse(surveyId, UUID.randomUUID(), "c".repeat(64));
        insertAnswer(responseId, textQuestionId, "exact text", null);

        assertConstraintViolation(() -> insertAnswer(
                responseId,
                textQuestionId,
                "duplicate",
                null));
        assertConstraintViolation(() -> insertAnswer(
                responseId,
                choiceQuestionId,
                "text",
                BigDecimal.ONE));
        assertConstraintViolation(() -> insertAnswer(
                Long.MAX_VALUE,
                choiceQuestionId,
                null,
                null));
        assertConstraintViolation(() -> insertAnswer(
                responseId,
                Long.MAX_VALUE,
                null,
                null));

        Long choiceAnswerId = insertAnswer(responseId, choiceQuestionId, null, null);
        insertAnswerOption(choiceAnswerId, optionId);
        assertConstraintViolation(() -> insertAnswerOption(choiceAnswerId, optionId));
        assertConstraintViolation(() -> insertAnswerOption(Long.MAX_VALUE, optionId));
        assertConstraintViolation(() -> insertAnswerOption(choiceAnswerId, Long.MAX_VALUE));
    }

    @Test
    void should_applyAnswerOwnershipDeleteRules_when_v6IsApplied() {
        Long surveyId = createSurvey(createOwner("answer-delete-owner@example.test"));
        Long questionId = insertQuestion(
                surveyId,
                "SINGLE_CHOICE",
                0,
                null,
                null,
                null,
                null);
        Long optionId = insertOption(questionId, "Choice", 0);
        Long firstResponseId = insertResponse(surveyId, UUID.randomUUID(), "d".repeat(64));
        Long firstAnswerId = insertAnswer(firstResponseId, questionId, null, null);
        insertAnswerOption(firstAnswerId, optionId);

        assertConstraintViolation(() -> jdbcTemplate.update(
                "DELETE FROM questions WHERE id = ?",
                questionId));
        assertConstraintViolation(() -> jdbcTemplate.update(
                "DELETE FROM question_options WHERE id = ?",
                optionId));

        jdbcTemplate.update("DELETE FROM survey_responses WHERE id = ?", firstResponseId);
        assertThat(rowCount("answers")).isZero();
        assertThat(rowCount("answer_options")).isZero();

        Long secondResponseId = insertResponse(surveyId, UUID.randomUUID(), "e".repeat(64));
        Long secondAnswerId = insertAnswer(secondResponseId, questionId, null, null);
        insertAnswerOption(secondAnswerId, optionId);

        jdbcTemplate.update("DELETE FROM answers WHERE id = ?", secondAnswerId);
        assertThat(rowCount("answer_options")).isZero();
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

    private Long insertQuestion(
            Long surveyId,
            String type,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            java.math.BigDecimal numberMin,
            java.math.BigDecimal numberMax) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, required, position,
                    scale_min, scale_max, number_min, number_max,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
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

    private Long insertOption(Long questionId, String label, int position) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO question_options (question_id, label, position)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, questionId, label, position);
    }

    private Long insertResponse(
            Long surveyId,
            UUID clientSubmissionId,
            String payloadHash) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                clientSubmissionId,
                payloadHash,
                Timestamp.from(Instant.now()));
    }

    private Long insertAnswer(
            Long responseId,
            Long questionId,
            String textValue,
            BigDecimal numericValue) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO answers (
                    response_id, question_id, text_value, numeric_value, created_at
                ) VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                responseId,
                questionId,
                textValue,
                numericValue,
                Timestamp.from(Instant.now()));
    }

    private void insertAnswerOption(Long answerId, Long optionId) {
        jdbcTemplate.update("""
                INSERT INTO answer_options (answer_id, option_id)
                VALUES (?, ?)
                """, answerId, optionId);
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

    private List<String> columnNames(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, tableName);
    }

    private List<String> constraintNames(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name = ?
                """, String.class, tableName);
    }

    private String deleteRule(String constraintName) {
        return jdbcTemplate.queryForObject("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public' AND constraint_name = ?
                """, String.class, constraintName);
    }

    private int rowCount(String tableName) {
        if (!List.of("answers", "answer_options").contains(tableName)) {
            throw new IllegalArgumentException("Unsupported table name");
        }
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Integer.class);
    }
}
