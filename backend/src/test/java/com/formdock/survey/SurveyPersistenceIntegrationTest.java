package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
class SurveyPersistenceIntegrationTest {

    private static final String TEST_PASSWORD_HASH = "{bcrypt}test-only-hash";

    @Autowired
    private SurveyService surveyService;

    @Autowired
    private SurveyRequestParser requestParser;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void should_persistCanonicalDraft_when_validSurveyIsCreated() {
        Long ownerId = createOwner("owner@example.test");

        SurveyDetailResponse response = surveyService.create(
                ownerId,
                requestParser.parseCreate(Map.of(
                        "title", "  Project Research  ",
                        "description", "  Description whitespace  ",
                        "privacyNotice", "Privacy notice")));

        Survey persisted = surveyRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getOwnerId()).isEqualTo(ownerId);
        assertThat(persisted.getTitle()).isEqualTo("Project Research");
        assertThat(persisted.getDescription()).isEqualTo("  Description whitespace  ");
        assertThat(persisted.getSlug()).isEqualTo("project-research");
        assertThat(persisted.getPrivacyNotice()).isEqualTo("Privacy notice");
        assertThat(persisted.getStatus()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(persisted.getOpenedAt()).isNull();
        assertThat(persisted.getClosedAt()).isNull();
        assertThat(persisted.getDeletedAt()).isNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.responseCount()).isZero();
        assertThat(response.structureLocked()).isFalse();
    }

    @Test
    void should_filterByOwnerAndOrderDeterministically_when_activeSurveysAreListed() {
        Long ownerId = createOwner("owner@example.test");
        Long otherOwnerId = createOwner("other@example.test");
        SurveyDetailResponse older = createSurvey(ownerId, "Older Survey", "older-survey");
        SurveyDetailResponse newer = createSurvey(ownerId, "Newer Survey", "newer-survey");
        createSurvey(otherOwnerId, "Foreign Survey", "foreign-survey");

        jdbcTemplate.update(
                "UPDATE surveys SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-08-20T00:00:00Z")),
                older.id());
        jdbcTemplate.update(
                "UPDATE surveys SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-08-20T00:01:00Z")),
                newer.id());

        List<SurveyListItemResponse> result = surveyService.list(ownerId);

        assertThat(result)
                .extracting(SurveyListItemResponse::id)
                .containsExactly(newer.id(), older.id());
        assertThat(result)
                .allSatisfy(item -> assertThat(item.responseCount()).isZero());
    }

    @Test
    void should_allocateFreshSuffix_when_generatedBaseIsAlreadyReserved() {
        Long ownerId = createOwner("owner@example.test");

        SurveyDetailResponse first = surveyService.create(
                ownerId,
                requestParser.parseCreate(Map.of("title", "Project Research")));
        SurveyDetailResponse second = surveyService.create(
                ownerId,
                requestParser.parseCreate(Map.of("title", "Project Research")));

        assertThat(first.slug()).isEqualTo("project-research");
        assertThat(second.slug())
                .isNotEqualTo(first.slug())
                .matches("^project-research-[a-z0-9]+$")
                .hasSizeLessThanOrEqualTo(SurveySlugPolicy.MAX_LENGTH);
    }

    @Test
    void should_createDistinctSurveys_when_generatedSlugRequestsRace() throws Exception {
        Long ownerId = createOwner("owner@example.test");
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SurveyDetailResponse> first = executor.submit(() -> {
                start.await();
                return surveyService.create(
                        ownerId,
                        requestParser.parseCreate(Map.of("title", "Concurrent Survey")));
            });
            Future<SurveyDetailResponse> second = executor.submit(() -> {
                start.await();
                return surveyService.create(
                        ownerId,
                        requestParser.parseCreate(Map.of("title", "Concurrent Survey")));
            });
            start.countDown();

            List<SurveyDetailResponse> results = List.of(
                    getResult(first),
                    getResult(second));
            assertThat(results)
                    .extracting(SurveyDetailResponse::slug)
                    .doesNotHaveDuplicates()
                    .allMatch(slug -> slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"));
            assertThat(surveyRepository.count()).isEqualTo(2);
        }
    }

    @Test
    void should_preserveDeletedSlugAndConcealSurvey_when_draftIsSoftDeleted() {
        Long ownerId = createOwner("owner@example.test");
        SurveyDetailResponse survey = createSurvey(ownerId, "Reserved Survey", "reserved-survey");

        surveyService.delete(ownerId, survey.id());

        assertThat(surveyService.list(ownerId)).isEmpty();
        assertThatThrownBy(() -> surveyService.detail(ownerId, survey.id()))
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surveys WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                survey.id()))
                .isOne();
        assertThatThrownBy(() -> createSurvey(
                ownerId,
                "Replacement Survey",
                "reserved-survey"))
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.SLUG_CONFLICT);
    }

    @Test
    void should_enforceFutureLifecycleGuards_when_seededStatusIsNotDraft() {
        Long ownerId = createOwner("owner@example.test");
        SurveyDetailResponse openSurvey = createSurvey(ownerId, "Open Survey", "open-survey");
        SurveyDetailResponse closedSurvey = createSurvey(ownerId, "Closed Survey", "closed-survey");
        jdbcTemplate.update(
                "UPDATE surveys SET status = 'OPEN', opened_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                openSurvey.id());
        jdbcTemplate.update(
                "UPDATE surveys SET status = 'CLOSED', opened_at = ?, closed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                closedSurvey.id());

        assertThatThrownBy(() -> surveyService.delete(ownerId, openSurvey.id()))
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.DELETE_REQUIRES_CLOSED);
        assertThatThrownBy(() -> surveyService.update(
                ownerId,
                closedSurvey.id(),
                requestParser.parsePatch(Map.of("slug", "new-closed-slug"))))
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.SLUG_IMMUTABLE);

        surveyService.delete(ownerId, closedSurvey.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surveys WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                closedSurvey.id()))
                .isOne();
    }

    @Test
    void should_concealSurvey_when_ownerDoesNotMatch() {
        Long ownerId = createOwner("owner@example.test");
        Long otherOwnerId = createOwner("other@example.test");
        SurveyDetailResponse survey = createSurvey(ownerId, "Private Survey", "private-survey");

        assertThatThrownBy(() -> surveyService.detail(otherOwnerId, survey.id()))
                .isInstanceOf(SurveyException.class)
                .extracting(failure -> ((SurveyException) failure).kind())
                .isEqualTo(SurveyException.Kind.NOT_FOUND);
    }

    private SurveyDetailResponse getResult(Future<SurveyDetailResponse> future)
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        return future.get(10, TimeUnit.SECONDS);
    }

    private Long createOwner(String email) {
        return userRepository.saveAndFlush(User.createAdmin(
                email,
                TEST_PASSWORD_HASH,
                "Survey Owner")).getId();
    }

    private SurveyDetailResponse createSurvey(Long ownerId, String title, String slug) {
        return surveyService.create(
                ownerId,
                requestParser.parseCreate(Map.of(
                        "title", title,
                        "slug", slug)));
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
