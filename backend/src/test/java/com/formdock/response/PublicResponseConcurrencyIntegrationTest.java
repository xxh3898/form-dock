package com.formdock.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.formdock.PostgreSQLTestConfiguration;
import com.formdock.question.QuestionService;
import com.formdock.question.QuestionType;
import com.formdock.survey.SurveyException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest(properties = {
    "formdock.survey.structure-lock-timeout=500ms",
    "formdock.public-response.rate-limit.max-requests=10000"
})
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class PublicResponseConcurrencyIntegrationTest {

    @Autowired
    private PublicResponseSubmissionService submissionService;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void should_waitAndValidateLatestStructure_when_mutationOwnsSurveyLockFirst()
            throws Exception {
        Fixture fixture = createFixture();
        PublicResponseSubmissionCommand command = command(
                fixture.questionId(), UUID.randomUUID(), "stale text");
        CountDownLatch mutationReady = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        AtomicLong mutationPid = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> mutation = executor.submit(() -> transactionTemplate.execute(status -> {
                questionService.update(
                        fixture.ownerId(),
                        fixture.surveyId(),
                        fixture.questionId(),
                        questionBody(QuestionType.NUMBER));
                mutationPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class));
                mutationReady.countDown();
                awaitLatch(releaseMutation, "mutation release");
                return null;
            }));
            assertThat(mutationReady.await(10, TimeUnit.SECONDS)).isTrue();

            Future<PublicResponseException.Kind> submit = executor.submit(() -> {
                try {
                    submissionService.submit(fixture.slug(), command);
                    return null;
                } catch (PublicResponseException exception) {
                    return exception.kind();
                }
            });
            awaitBlockedBy(mutationPid.get());
            releaseMutation.countDown();

            assertThat(mutation.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(submit.get(10, TimeUnit.SECONDS))
                    .isEqualTo(PublicResponseException.Kind.INVALID);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT type FROM questions WHERE id = ?",
                    String.class,
                    fixture.questionId())).isEqualTo("NUMBER");
            assertThat(responseCount(fixture.surveyId())).isZero();
        } finally {
            releaseMutation.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_commitResponseThenRejectStructureMutation_when_submitOwnsSurveyLockFirst()
            throws Exception {
        Fixture fixture = createFixture();
        PublicResponseSubmissionCommand command = command(
                fixture.questionId(), UUID.randomUUID(), "canonical text");
        CountDownLatch submitReady = new CountDownLatch(1);
        CountDownLatch releaseSubmit = new CountDownLatch(1);
        AtomicLong submitPid = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PublicResponseSubmissionResponse> submit = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        PublicResponseSubmissionResponse response = submissionService.submit(
                                fixture.slug(), command);
                        submitPid.set(jdbcTemplate.queryForObject(
                                "SELECT pg_backend_pid()", Long.class));
                        submitReady.countDown();
                        awaitLatch(releaseSubmit, "submit release");
                        return response;
                    }));
            assertThat(submitReady.await(10, TimeUnit.SECONDS)).isTrue();

            Future<SurveyException.Kind> mutation = executor.submit(() -> {
                try {
                    questionService.update(
                            fixture.ownerId(),
                            fixture.surveyId(),
                            fixture.questionId(),
                            questionBody(QuestionType.LONG_TEXT));
                    return null;
                } catch (SurveyException exception) {
                    return exception.kind();
                }
            });
            awaitBlockedBy(submitPid.get());
            releaseSubmit.countDown();

            assertThat(submit.get(10, TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(mutation.get(10, TimeUnit.SECONDS))
                    .isEqualTo(SurveyException.Kind.STRUCTURE_LOCKED);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT type FROM questions WHERE id = ?",
                    String.class,
                    fixture.questionId())).isEqualTo("SHORT_TEXT");
            assertThat(responseCount(fixture.surveyId())).isOne();
            assertThat(answerCount(fixture.surveyId())).isOne();
        } finally {
            releaseSubmit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_convergeConcurrentSameAndDifferentPayloads_toOneCanonicalAggregate()
            throws Exception {
        Fixture sameFixture = createFixture();
        UUID sameIdentity = UUID.randomUUID();
        List<SubmissionOutcome> same = runConcurrent(
                sameFixture,
                command(sameFixture.questionId(), sameIdentity, "same"),
                command(sameFixture.questionId(), sameIdentity, "same"));

        assertThat(same).extracting(SubmissionOutcome::result)
                .containsExactlyInAnyOrder("CREATED", "REPLAYED");
        assertThat(responseCount(sameFixture.surveyId())).isOne();
        assertThat(answerCount(sameFixture.surveyId())).isOne();

        Fixture differentFixture = createFixture();
        UUID differentIdentity = UUID.randomUUID();
        List<SubmissionOutcome> different = runConcurrent(
                differentFixture,
                command(differentFixture.questionId(), differentIdentity, "first"),
                command(differentFixture.questionId(), differentIdentity, "second"));

        assertThat(different).extracting(SubmissionOutcome::result)
                .containsExactlyInAnyOrder("CREATED", "DUPLICATE_CONFLICT");
        assertThat(responseCount(differentFixture.surveyId())).isOne();
        assertThat(answerCount(differentFixture.surveyId())).isOne();
    }

    @Test
    void should_return503WithoutWritesAndRecoverWithSameIdentity_when_lockTimesOut()
            throws Exception {
        Fixture fixture = createFixture();
        UUID submissionId = UUID.randomUUID();
        CountDownLatch holderReady = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Void> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM surveys WHERE id = ? FOR UPDATE",
                        Long.class,
                        fixture.surveyId());
                holderReady.countDown();
                awaitLatch(releaseHolder, "lock holder release");
                return null;
            }));
            assertThat(holderReady.await(10, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(post(
                            "/api/public/surveys/{slug}/responses",
                            fixture.slug())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request(fixture.questionId(), submissionId, "retry text")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.fieldErrors").isEmpty())
                    .andDo(document(
                            "public-responses-temporarily-unavailable",
                            pathParameters(parameterWithName("slug")
                                    .description("잠금 대기 중인 Public Survey slug")),
                            responseFields(
                                    fieldWithPath("code")
                                            .description("TEMPORARILY_UNAVAILABLE 오류 코드"),
                                    fieldWithPath("message")
                                            .description("내부 잠금 정보를 숨긴 안전한 재시도 안내"),
                                    fieldWithPath("fieldErrors")
                                            .description("빈 필드 오류 목록"))));
            assertThat(responseCount(fixture.surveyId())).isZero();
            assertThat(answerCount(fixture.surveyId())).isZero();

            releaseHolder.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isNull();

            PublicResponseSubmissionResponse created = submissionService.submit(
                    fixture.slug(),
                    command(fixture.questionId(), submissionId, "retry text"));
            PublicResponseSubmissionResponse replayed = submissionService.submit(
                    fixture.slug(),
                    command(fixture.questionId(), submissionId, "retry text"));

            assertThat(created.replayed()).isFalse();
            assertThat(replayed.replayed()).isTrue();
            assertThat(replayed.responseId()).isEqualTo(created.responseId());
            assertThat(responseCount(fixture.surveyId())).isOne();
            assertThat(answerCount(fixture.surveyId())).isOne();
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<SubmissionOutcome> runConcurrent(
            Fixture fixture,
            PublicResponseSubmissionCommand firstCommand,
            PublicResponseSubmissionCommand secondCommand) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<SubmissionOutcome> first = executor.submit(() -> concurrentSubmit(
                    fixture.slug(), firstCommand, ready));
            Future<SubmissionOutcome> second = executor.submit(() -> concurrentSubmit(
                    fixture.slug(), secondCommand, ready));
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private SubmissionOutcome concurrentSubmit(
            String slug,
            PublicResponseSubmissionCommand command,
            CountDownLatch ready) {
        ready.countDown();
        awaitLatch(ready, "concurrent submissions");
        try {
            PublicResponseSubmissionResponse response = submissionService.submit(slug, command);
            return new SubmissionOutcome(response.replayed() ? "REPLAYED" : "CREATED");
        } catch (PublicResponseException exception) {
            return new SubmissionOutcome(exception.kind().name());
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
        throw new AssertionError("Expected transaction did not block on the Survey row");
    }

    private Fixture createFixture() {
        Instant now = Instant.now();
        String identity = UUID.randomUUID().toString();
        long ownerId = jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, 'ADMIN', ?, ?)
                RETURNING id
                """,
                Long.class,
                identity + "@concurrency.test",
                "{bcrypt}test-only-hash",
                "Concurrency Owner",
                Timestamp.from(now),
                Timestamp.from(now));
        String slug = "concurrency-" + identity;
        long surveyId = jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, opened_at, created_at, updated_at
                ) VALUES (?, 'Concurrency Survey', ?, 'OPEN', ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                slug,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        long questionId = jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, required, position, created_at, updated_at
                ) VALUES (?, 'SHORT_TEXT', 'Concurrency Question', true, 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                Timestamp.from(now),
                Timestamp.from(now));
        return new Fixture(ownerId, surveyId, slug, questionId);
    }

    private PublicResponseSubmissionCommand command(
            long questionId,
            UUID submissionId,
            String value) {
        return new PublicResponseSubmissionCommand(
                submissionId,
                List.of(new SubmittedAnswer(questionId, value, null, null)));
    }

    private String request(long questionId, UUID submissionId, String value) {
        return """
                {
                  "clientSubmissionId": "%s",
                  "answers": [{"questionId": %d, "textValue": "%s"}]
                }
                """.formatted(submissionId, questionId, value);
    }

    private Map<String, Object> questionBody(QuestionType type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type.name());
        body.put("title", "Changed Question");
        body.put("description", null);
        body.put("required", true);
        body.put("scaleMin", null);
        body.put("scaleMax", null);
        body.put("scaleMinLabel", null);
        body.put("scaleMaxLabel", null);
        body.put("numberMin", null);
        body.put("numberMax", null);
        body.put("options", new ArrayList<>());
        return body;
    }

    private long responseCount(long surveyId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM survey_responses WHERE survey_id = ?",
                Long.class,
                surveyId);
    }

    private long answerCount(long surveyId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM answers answer
                JOIN survey_responses response ON response.id = answer.response_id
                WHERE response.survey_id = ?
                """,
                Long.class,
                surveyId);
    }

    private void awaitLatch(CountDownLatch latch, String description) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(description + " timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " was interrupted", exception);
        }
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM answer_options");
        jdbcTemplate.update("DELETE FROM answers");
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.update("DELETE FROM users");
    }

    private record Fixture(long ownerId, long surveyId, String slug, long questionId) {
    }

    private record SubmissionOutcome(String result) {
    }
}
