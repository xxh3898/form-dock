package com.formdock.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.formdock.PostgreSQLTestConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
class ResponsePersistenceIntegrationTest {

    private static final String FIRST_HASH = "a".repeat(64);
    private static final String SECOND_HASH = "b".repeat(64);

    @Autowired
    private SurveyResponseRepository responseRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void should_resolveCreatedSameAndDifferentPayload_when_identityIsReplayed() {
        Long surveyId = createSurvey();
        UUID submissionId = UUID.randomUUID();
        Instant submittedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        SurveyResponseResolution created = inTransaction(() -> responseRepository.createOrResolve(
                surveyId,
                submissionId,
                FIRST_HASH,
                submittedAt));
        SurveyResponseResolution same = inTransaction(() -> responseRepository.createOrResolve(
                surveyId,
                submissionId,
                FIRST_HASH,
                submittedAt.plusSeconds(1)));
        SurveyResponseResolution different = inTransaction(() -> responseRepository.createOrResolve(
                surveyId,
                submissionId,
                SECOND_HASH,
                submittedAt.plusSeconds(2)));

        assertThat(created.outcome()).isEqualTo(SurveyResponseResolution.Outcome.CREATED);
        assertThat(same.outcome()).isEqualTo(SurveyResponseResolution.Outcome.EXISTING_SAME_PAYLOAD);
        assertThat(different.outcome())
                .isEqualTo(SurveyResponseResolution.Outcome.EXISTING_DIFFERENT_PAYLOAD);
        assertThat(List.of(created.response().id(), same.response().id(), different.response().id()))
                .containsOnly(created.response().id());
        assertThat(same.response().payloadHash()).isEqualTo(FIRST_HASH);
        assertThat(different.response().payloadHash()).isEqualTo(FIRST_HASH);
        assertThat(responseCount(surveyId, submissionId)).isOne();
    }

    @Test
    void should_convergeToCreatedAndSamePayload_when_concurrentIdentityAndHashMatch() throws Exception {
        Long surveyId = createSurvey();
        UUID submissionId = UUID.randomUUID();

        List<SurveyResponseResolution> resolutions = runConcurrentCreates(
                surveyId,
                submissionId,
                FIRST_HASH,
                FIRST_HASH);

        assertThat(resolutions)
                .extracting(SurveyResponseResolution::outcome)
                .containsExactlyInAnyOrder(
                        SurveyResponseResolution.Outcome.CREATED,
                        SurveyResponseResolution.Outcome.EXISTING_SAME_PAYLOAD);
        assertThat(resolutions)
                .extracting(resolution -> resolution.response().id())
                .containsOnly(resolutions.getFirst().response().id());
        assertThat(responseCount(surveyId, submissionId)).isOne();
    }

    @Test
    void should_convergeToCreatedAndConflict_when_concurrentIdentityHashesDiffer() throws Exception {
        Long surveyId = createSurvey();
        UUID submissionId = UUID.randomUUID();

        List<SurveyResponseResolution> resolutions = runConcurrentCreates(
                surveyId,
                submissionId,
                FIRST_HASH,
                SECOND_HASH);

        assertThat(resolutions)
                .extracting(SurveyResponseResolution::outcome)
                .containsExactlyInAnyOrder(
                        SurveyResponseResolution.Outcome.CREATED,
                        SurveyResponseResolution.Outcome.EXISTING_DIFFERENT_PAYLOAD);
        assertThat(resolutions)
                .extracting(resolution -> resolution.response().id())
                .containsOnly(resolutions.getFirst().response().id());
        assertThat(responseCount(surveyId, submissionId)).isOne();
    }

    @Test
    void should_propagateUnrelatedIntegrityViolation_when_surveyDoesNotExist() {
        assertThatThrownBy(() -> inTransaction(() -> responseRepository.createOrResolve(
                Long.MAX_VALUE,
                UUID.randomUUID(),
                FIRST_HASH,
                Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_requireCallerOwnedTransaction_when_responseWriteIsAttempted() {
        assertThatThrownBy(() -> responseRepository.createOrResolve(
                1L,
                UUID.randomUUID(),
                FIRST_HASH,
                Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void should_roundTripAllAnswerRepresentations_when_aggregateIsPersisted() {
        Long surveyId = createSurvey();
        Long textQuestionId = createQuestion(surveyId, "LONG_TEXT", 0, null, null);
        Long singleQuestionId = createQuestion(surveyId, "SINGLE_CHOICE", 1, null, null);
        Long multiQuestionId = createQuestion(surveyId, "MULTIPLE_CHOICE", 2, null, null);
        Long scaleQuestionId = createQuestion(surveyId, "SCALE", 3, 1, 10);
        Long numberQuestionId = createQuestion(surveyId, "NUMBER", 4, null, null);
        createQuestion(surveyId, "SHORT_TEXT", 5, null, null);
        Long singleOptionId = createOption(singleQuestionId, "Only", 0);
        Long firstMultiOptionId = createOption(multiQuestionId, "First", 0);
        Long secondMultiOptionId = createOption(multiQuestionId, "Second", 1);
        UUID submissionId = UUID.randomUUID();

        SurveyResponse persistedResponse = inTransaction(() -> {
            SurveyResponse response = responseRepository.createOrResolve(
                    surveyId,
                    submissionId,
                    FIRST_HASH,
                    Instant.now()).response();
            answerRepository.insertAll(response.id(), List.of(
                    new CanonicalAnswer.NumberValue(numberQuestionId, new BigDecimal("7.5000")),
                    new CanonicalAnswer.OptionValues(
                            multiQuestionId,
                            List.of(secondMultiOptionId, firstMultiOptionId)),
                    new CanonicalAnswer.TextValue(textQuestionId, "  한글\nexact  "),
                    new CanonicalAnswer.ScaleValue(scaleQuestionId, 7),
                    new CanonicalAnswer.OptionValues(singleQuestionId, List.of(singleOptionId))),
                    Instant.now());
            return response;
        });

        List<PersistedAnswer> answers = answerRepository.findAllByResponseId(persistedResponse.id());

        assertThat(answers).extracting(PersistedAnswer::questionId).containsExactly(
                textQuestionId,
                singleQuestionId,
                multiQuestionId,
                scaleQuestionId,
                numberQuestionId);
        assertThat(answers.get(0).textValue()).isEqualTo("  한글\nexact  ");
        assertThat(answers.get(1).optionIds()).containsExactly(singleOptionId);
        assertThat(answers.get(2).optionIds()).containsExactly(
                firstMultiOptionId,
                secondMultiOptionId);
        assertThat(answers.get(3).numericValue()).isEqualByComparingTo("7.0000");
        assertThat(answers.get(4).numericValue()).isEqualByComparingTo("7.5000");
        assertThat(answers).allSatisfy(answer -> assertThat(answer.createdAt()).isNotNull());
    }

    @Test
    void should_rollBackResponseAndAnswers_when_answerOptionPersistenceFails() {
        Long surveyId = createSurvey();
        Long questionId = createQuestion(surveyId, "SINGLE_CHOICE", 0, null, null);
        UUID submissionId = UUID.randomUUID();

        assertThatThrownBy(() -> inTransaction(() -> {
            SurveyResponse response = responseRepository.createOrResolve(
                    surveyId,
                    submissionId,
                    FIRST_HASH,
                    Instant.now()).response();
            answerRepository.insertAll(
                    response.id(),
                    List.of(new CanonicalAnswer.OptionValues(questionId, List.of(Long.MAX_VALUE))),
                    Instant.now());
            return response;
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(responseCount(surveyId, submissionId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM answers answer
                JOIN survey_responses response ON response.id = answer.response_id
                WHERE response.survey_id = ?
                  AND response.client_submission_id = ?
                """,
                Long.class,
                surveyId,
                submissionId)).isZero();
    }

    @Test
    void should_requireCallerOwnedTransaction_when_answerWriteIsAttempted() {
        assertThatThrownBy(() -> answerRepository.insertAll(
                1L,
                List.of(new CanonicalAnswer.TextValue(1L, "value")),
                Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private List<SurveyResponseResolution> runConcurrentCreates(
            Long surveyId,
            UUID submissionId,
            String firstHash,
            String secondHash) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch transactionsReady = new CountDownLatch(2);
        try {
            Future<SurveyResponseResolution> first = executor.submit(() -> concurrentCreate(
                    surveyId,
                    submissionId,
                    firstHash,
                    transactionsReady));
            Future<SurveyResponseResolution> second = executor.submit(() -> concurrentCreate(
                    surveyId,
                    submissionId,
                    secondHash,
                    transactionsReady));
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private SurveyResponseResolution concurrentCreate(
            Long surveyId,
            UUID submissionId,
            String payloadHash,
            CountDownLatch transactionsReady) {
        return inTransaction(() -> {
            transactionsReady.countDown();
            if (!transactionsReady.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent Response transactions did not become ready");
            }
            return responseRepository.createOrResolve(
                    surveyId,
                    submissionId,
                    payloadHash,
                    Instant.now());
        });
    }

    private <T> T inTransaction(TransactionCallback<T> callback) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return callback.execute();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private Long createSurvey() {
        Instant now = Instant.now();
        String identity = UUID.randomUUID().toString();
        Long ownerId = jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                identity + "@response.test",
                "{bcrypt}test-only-hash",
                "Response Owner",
                "ADMIN",
                Timestamp.from(now),
                Timestamp.from(now));
        return jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Response Survey",
                "response-" + identity,
                "OPEN",
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private Long createQuestion(
            Long surveyId,
            String type,
            int position,
            Integer scaleMin,
            Integer scaleMax) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, required, position,
                    scale_min, scale_max, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                type,
                type + " answer",
                false,
                position,
                scaleMin,
                scaleMax,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private Long createOption(Long questionId, String label, int position) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO question_options (question_id, label, position)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                questionId,
                label,
                position);
    }

    private long responseCount(Long surveyId, UUID submissionId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM survey_responses
                WHERE survey_id = ? AND client_submission_id = ?
                """,
                Long.class,
                surveyId,
                submissionId);
    }

    @FunctionalInterface
    private interface TransactionCallback<T> {

        T execute() throws Exception;
    }
}
