package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.formdock.PostgreSQLTestConfiguration;
import com.formdock.auth.User;
import com.formdock.auth.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest(properties = "formdock.survey.structure-lock-timeout=2s")
class SurveyStructureGuardIntegrationTest {

    private static final String TEST_PASSWORD_HASH = "{bcrypt}test-only-hash";

    @Autowired
    private SurveyStructureGuard structureGuard;

    @Autowired
    private SurveyService surveyService;

    @Autowired
    private SurveyRequestParser requestParser;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void should_returnCurrentStatus_when_ownerIsVisibleAndNoResponseExists() {
        Long ownerId = createOwner("owner@example.test");
        SurveyDetailResponse survey = createSurvey(ownerId, "mutable-survey");
        jdbcTemplate.update(
                "UPDATE surveys SET status = 'OPEN', opened_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                survey.id());

        Survey locked = transactionTemplate.execute(
                status -> structureGuard.requireMutable(ownerId, survey.id()));

        assertThat(locked).isNotNull();
        assertThat(locked.getId()).isEqualTo(survey.id());
        assertThat(locked.getOwnerId()).isEqualTo(ownerId);
        assertThat(locked.getStatus()).isEqualTo(SurveyStatus.OPEN);
    }

    @Test
    void should_requireCallerOwnedTransaction_when_structureGuardIsInvoked() {
        assertThatThrownBy(() -> structureGuard.requireMutable(1L, 1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void should_rejectStructureMutation_when_canonicalResponseExists() {
        Long ownerId = createOwner("owner@example.test");
        SurveyDetailResponse survey = createSurvey(ownerId, "locked-survey");
        insertResponse(survey.id());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> structureGuard.requireMutable(ownerId, survey.id())))
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.STRUCTURE_LOCKED);
    }

    @Test
    void should_concealSurvey_when_ownerDiffersOrSurveyIsDeleted() {
        Long ownerId = createOwner("owner@example.test");
        Long otherOwnerId = createOwner("other@example.test");
        SurveyDetailResponse survey = createSurvey(ownerId, "concealed-survey");

        assertNotFound(() -> transactionTemplate.executeWithoutResult(
                status -> structureGuard.requireMutable(otherOwnerId, survey.id())));

        surveyService.delete(ownerId, survey.id());

        assertNotFound(() -> transactionTemplate.executeWithoutResult(
                status -> structureGuard.requireMutable(ownerId, survey.id())));
    }

    @Test
    void should_failWithinBoundedTimeoutAndRollBackCallerWork_when_surveyRowIsLocked()
            throws Exception {
        Long ownerId = createOwner("owner@example.test");
        SurveyDetailResponse survey = createSurvey(ownerId, "contended-survey");
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicLong holderPid = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                holderPid.set(jdbcTemplate.queryForObject(
                        "SELECT pg_backend_pid()",
                        Long.class));
                jdbcTemplate.queryForObject(
                        "SELECT id FROM surveys WHERE id = ? FOR UPDATE",
                        Long.class,
                        survey.id());
                holderLocked.countDown();
                try {
                    assertThat(releaseHolder.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Survey lock holder was interrupted", exception);
                }
                return null;
            }));

            assertThat(holderLocked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<SurveyException.Kind> waiter = executor.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        structureGuard.requireMutable(ownerId, survey.id());
                        insertTestQuestion(survey.id());
                    });
                    return null;
                } catch (SurveyException exception) {
                    return exception.kind();
                }
            });

            awaitBlockedBy(holderPid.get());
            assertThat(waiter.get(10, TimeUnit.SECONDS))
                    .isEqualTo(SurveyException.Kind.TEMPORARILY_UNAVAILABLE);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM questions WHERE survey_id = ?",
                    Integer.class,
                    survey.id())).isZero();

            releaseHolder.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isNull();
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedBy(long holderPid) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Boolean blocked = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_stat_activity activity
                        WHERE activity.datname = current_database()
                          AND ? = ANY(pg_blocking_pids(activity.pid))
                          AND lower(activity.query) LIKE '%surveys%'
                    )
                    """, Boolean.class, holderPid);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Structure guard did not block on the expected Survey row");
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.NOT_FOUND);
    }

    private Long createOwner(String email) {
        return userRepository.saveAndFlush(User.createAdmin(
                email,
                TEST_PASSWORD_HASH,
                "Survey Owner")).getId();
    }

    private SurveyDetailResponse createSurvey(Long ownerId, String slug) {
        return surveyService.create(
                ownerId,
                requestParser.parseCreate(Map.of(
                        "title", "Structure Survey",
                        "slug", slug)));
    }

    private void insertResponse(Long surveyId) {
        jdbcTemplate.update("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                """,
                surveyId,
                UUID.randomUUID(),
                "a".repeat(64),
                Timestamp.from(Instant.now()));
    }

    private void insertTestQuestion(Long surveyId) {
        jdbcTemplate.update("""
                INSERT INTO questions (
                    survey_id, type, title, required, position, created_at, updated_at
                ) VALUES (?, 'SHORT_TEXT', 'Should roll back', false, 0, ?, ?)
                """,
                surveyId,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }
}
