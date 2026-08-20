package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.formdock.auth.User;
import com.formdock.auth.UserRepository;
import com.formdock.survey.SurveyRepository;

import jakarta.servlet.http.Cookie;

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
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class SurveyApiIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String CREATOR_PASSWORD = "test-only-creator-passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SurveyRepository surveyRepository;

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
    void should_createListLoadPatchAndSoftDeleteSurvey_when_creatorSessionIsValid()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("title", "  Project Research  ");
        createRequest.put("description", null);
        createRequest.put("privacyNotice", "Research participants only");
        createRequest.put("slug", null);

        MvcResult createResult = mockMvc.perform(post("/api/surveys")
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Project Research"))
                .andExpect(jsonPath("$.slug").value("project-research"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.openedAt").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.responseCount").value(0))
                .andExpect(jsonPath("$.structureLocked").value(false))
                .andExpect(jsonPath("$.questions").isEmpty())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andDo(document(
                        "surveys-create",
                        requestFields(
                                fieldWithPath("title").description("Survey title"),
                                fieldWithPath("description").description("Optional introduction"),
                                fieldWithPath("privacyNotice").description("Optional privacy notice"),
                                fieldWithPath("slug").description("Optional explicit slug")),
                        responseFields(detailResponseFields())))
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long surveyId = created.get("id").longValue();

        mockMvc.perform(get("/api/surveys").cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(surveyId))
                .andExpect(jsonPath("$[0].responseCount").value(0))
                .andDo(document("surveys-list", responseFields(listResponseFields())));

        mockMvc.perform(get("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(surveyId))
                .andExpect(jsonPath("$.questions").isEmpty())
                .andDo(document(
                        "surveys-detail",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier")),
                        responseFields(detailResponseFields())));

        Map<String, Object> patchRequest = new LinkedHashMap<>();
        patchRequest.put("title", "Updated Research");
        patchRequest.put("description", null);
        MvcResult patchResult = mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Research"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.slug").value("project-research"))
                .andDo(document(
                        "surveys-update",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier")),
                        requestFields(
                                fieldWithPath("title").description("Updated title"),
                                fieldWithPath("description").description("Explicit null clears description")),
                        responseFields(detailResponseFields())))
                .andReturn();
        assertThat(objectMapper.readTree(patchResult.getResponse().getContentAsString())
                .get("slug").stringValue()).isEqualTo("project-research");

        mockMvc.perform(delete("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andDo(document(
                        "surveys-delete",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier"))));

        mockMvc.perform(get("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surveys WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                surveyId)).isOne();
    }

    @Test
    void should_rejectStalePatchAndPreserveSoftDelete_when_deleteCommitsFirst()
            throws Exception {
        AuthenticatedSession deleteSession = authenticateCreator("creator@example.test");
        AuthenticatedSession patchSession = authenticateExistingCreator("creator@example.test");
        long surveyId = createSurvey(deleteSession, "Race Survey", "race-survey");
        CountDownLatch deleteFlushed = new CountDownLatch(1);
        CountDownLatch allowDeleteCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> deleteFuture = executor.submit(() -> transactionTemplate.execute(status -> {
                try {
                    MvcResult result = mockMvc.perform(delete("/api/surveys/{surveyId}", surveyId)
                                    .cookie(deleteSession.cookie())
                                    .header(deleteSession.headerName(), deleteSession.token()))
                            .andReturn();
                    surveyRepository.flush();
                    deleteFlushed.countDown();
                    assertThat(allowDeleteCommit.await(10, TimeUnit.SECONDS)).isTrue();
                    return result;
                } catch (Exception exception) {
                    throw new AssertionError("DELETE transaction failed", exception);
                }
            }));

            assertThat(deleteFlushed.await(10, TimeUnit.SECONDS)).isTrue();
            Future<MvcResult> patchFuture = executor.submit(() -> mockMvc.perform(
                            patch("/api/surveys/{surveyId}", surveyId)
                                    .cookie(patchSession.cookie())
                                    .header(patchSession.headerName(), patchSession.token())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"title\":\"Stale Patch\"}"))
                    .andReturn());

            awaitBlockedSurveyUpdate();
            allowDeleteCommit.countDown();

            assertThat(deleteFuture.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            MvcResult patchResult = patchFuture.get(10, TimeUnit.SECONDS);
            assertThat(patchResult.getResponse().getStatus()).isEqualTo(404);
            assertThat(objectMapper.readTree(patchResult.getResponse().getContentAsString())
                    .get("code").stringValue()).isEqualTo("SURVEY_NOT_FOUND");

            Map<String, Object> persistedSurvey = jdbcTemplate.queryForMap(
                    "SELECT title, slug, deleted_at FROM surveys WHERE id = ?",
                    surveyId);
            assertThat(persistedSurvey.get("title")).isEqualTo("Race Survey");
            assertThat(persistedSurvey.get("slug")).isEqualTo("race-survey");
            assertThat(persistedSurvey.get("deleted_at")).isNotNull();

            mockMvc.perform(get("/api/surveys/{surveyId}", surveyId)
                            .cookie(deleteSession.cookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
            mockMvc.perform(post("/api/surveys")
                            .cookie(deleteSession.cookie())
                            .header(deleteSession.headerName(), deleteSession.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Replacement\",\"slug\":\"race-survey\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SURVEY_SLUG_CONFLICT"));
        } finally {
            allowDeleteCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_concealSurvey_when_creatorDoesNotOwnItOrItIsUnknownOrDeleted()
            throws Exception {
        AuthenticatedSession owner = authenticateCreator("owner@example.test");
        AuthenticatedSession other = authenticateCreator("other@example.test");
        long surveyId = createSurvey(owner, "Private Survey", "private-survey");

        MvcResult foreignResult = mockMvc.perform(get("/api/surveys/{surveyId}", surveyId)
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andDo(document("surveys-not-found", errorResponseFields()))
                .andReturn();

        MvcResult unknownResult = mockMvc.perform(get("/api/surveys/{surveyId}", 999_999)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andReturn();
        assertThat(unknownResult.getResponse().getContentAsString())
                .isEqualTo(foreignResult.getResponse().getContentAsString());

        mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(other.cookie())
                        .header(other.headerName(), other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Foreign update\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
        mockMvc.perform(delete("/api/surveys/{surveyId}", surveyId)
                        .cookie(other.cookie())
                        .header(other.headerName(), other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));

        deleteSurvey(owner, surveyId);
        mockMvc.perform(get("/api/surveys/{surveyId}", surveyId).cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
    }

    @Test
    void should_returnStableValidationError_when_createOrPatchPayloadIsInvalid()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Validation Survey", "validation-survey");

        mockMvc.perform(post("/api/surveys")
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Valid\",\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].path").value("status"))
                .andDo(document("surveys-validation-failed", errorResponseFields()));

        mockMvc.perform(post("/api/surveys")
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].path").value("body"));

        mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].path").value("title"));

        mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\" Invalid Slug \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].path").value("slug"));
    }

    @Test
    void should_returnSlugConflict_when_explicitSlugIsReservedOrPatchConflicts()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long reservedSurveyId = createSurvey(creator, "Reserved Survey", "reserved-survey");
        long otherSurveyId = createSurvey(creator, "Other Survey", "other-survey");
        deleteSurvey(creator, reservedSurveyId);

        mockMvc.perform(post("/api/surveys")
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Replacement\",\"slug\":\"reserved-survey\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_SLUG_CONFLICT"))
                .andDo(document("surveys-slug-conflict", errorResponseFields()));

        mockMvc.perform(patch("/api/surveys/{surveyId}", otherSurveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"reserved-survey\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_SLUG_CONFLICT"));
    }

    @Test
    void should_enforceFutureSlugAndDeleteStateGuards_when_lifecycleFixtureExists()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Lifecycle Survey", "lifecycle-survey");
        jdbcTemplate.update(
                "UPDATE surveys SET status = 'OPEN', opened_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                surveyId);

        mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"changed-slug\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_SLUG_IMMUTABLE"));
        mockMvc.perform(delete("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_DELETE_REQUIRES_CLOSED"));

        jdbcTemplate.update(
                "UPDATE surveys SET status = 'CLOSED', closed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                surveyId);
        deleteSurvey(creator, surveyId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surveys WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                surveyId)).isOne();
    }

    @Test
    void should_returnRealQuestionsAndResponseAuthority_when_phase2bFixturesExist()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long populatedSurveyId = createSurvey(
                creator,
                "Populated Survey",
                "populated-survey");
        long emptySurveyId = createSurvey(creator, "Empty Survey", "empty-survey");

        long choiceQuestionId = insertQuestion(
                populatedSurveyId,
                "SINGLE_CHOICE",
                "Choose one",
                0,
                null,
                null,
                null,
                null);
        jdbcTemplate.update(
                "INSERT INTO question_options (question_id, label, position) VALUES (?, ?, ?)",
                choiceQuestionId,
                "Second",
                1);
        jdbcTemplate.update(
                "INSERT INTO question_options (question_id, label, position) VALUES (?, ?, ?)",
                choiceQuestionId,
                "First",
                0);
        insertQuestion(
                populatedSurveyId,
                "NUMBER",
                "How many?",
                1,
                null,
                null,
                new java.math.BigDecimal("1.2300"),
                new java.math.BigDecimal("1000.0000"));
        insertResponse(populatedSurveyId, "a");
        insertResponse(populatedSurveyId, "b");

        MvcResult listResult = mockMvc.perform(get("/api/surveys").cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(responseCountFor(list, populatedSurveyId)).isEqualTo(2);
        assertThat(responseCountFor(list, emptySurveyId)).isZero();

        mockMvc.perform(get("/api/surveys/{surveyId}", populatedSurveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCount").value(2))
                .andExpect(jsonPath("$.structureLocked").value(true))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.questions[0].options[0].label").value("First"))
                .andExpect(jsonPath("$.questions[0].options[1].label").value("Second"))
                .andExpect(jsonPath("$.questions[1].type").value("NUMBER"))
                .andExpect(jsonPath("$.questions[1].numberMin").value("1.23"))
                .andExpect(jsonPath("$.questions[1].numberMax").value("1000"))
                .andExpect(jsonPath("$.questions[1].options").isEmpty());

        mockMvc.perform(patch("/api/surveys/{surveyId}", populatedSurveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Still Populated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Still Populated"))
                .andExpect(jsonPath("$.responseCount").value(2))
                .andExpect(jsonPath("$.structureLocked").value(true))
                .andExpect(jsonPath("$.questions.length()").value(2));
    }

    @Test
    void should_requireAuthenticationAndCsrf_when_surveyApiIsRequested() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Protected Survey", "protected-survey");

        mockMvc.perform(get("/api/surveys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(post("/api/surveys")
                        .cookie(creator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andDo(document("surveys-csrf-invalid", errorResponseFields()));
        mockMvc.perform(patch("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mockMvc.perform(delete("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private AuthenticatedSession authenticateCreator(String email) throws Exception {
        userRepository.saveAndFlush(User.createAdmin(
                email,
                passwordEncoder.encode(CREATOR_PASSWORD),
                "Survey Creator"));
        return authenticateExistingCreator(email);
    }

    private AuthenticatedSession authenticateExistingCreator(String email) throws Exception {
        CsrfSession anonymous = issueCsrf(null);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(anonymous.cookie())
                        .header(anonymous.headerName(), anonymous.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", CREATOR_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie authenticatedCookie = requireResponseCookie(loginResult);
        CsrfSession authenticatedCsrf = issueCsrf(authenticatedCookie);
        return new AuthenticatedSession(
                authenticatedCsrf.cookie(),
                authenticatedCsrf.headerName(),
                authenticatedCsrf.token());
    }

    private void awaitBlockedSurveyUpdate() throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Boolean blocked = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_stat_activity activity
                        WHERE activity.datname = current_database()
                          AND activity.pid <> pg_backend_pid()
                          AND cardinality(pg_blocking_pids(activity.pid)) > 0
                          AND lower(activity.query) LIKE '%update surveys%'
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("PATCH did not block behind the DELETE row update");
    }

    private long createSurvey(
            AuthenticatedSession creator,
            String title,
            String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/surveys")
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .longValue();
    }

    private long insertQuestion(
            long surveyId,
            String type,
            String title,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            java.math.BigDecimal numberMin,
            java.math.BigDecimal numberMax) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, description, required, position,
                    scale_min, scale_max, number_min, number_max,
                    created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                type,
                title,
                true,
                position,
                scaleMin,
                scaleMax,
                numberMin,
                numberMax,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private void insertResponse(long surveyId, String hashPrefix) {
        jdbcTemplate.update("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                """,
                surveyId,
                UUID.randomUUID(),
                hashPrefix.repeat(64),
                Timestamp.from(Instant.now()));
    }

    private long responseCountFor(JsonNode list, long surveyId) {
        for (JsonNode item : list) {
            if (item.get("id").longValue() == surveyId) {
                return item.get("responseCount").longValue();
            }
        }
        throw new AssertionError("Survey was absent from list response: " + surveyId);
    }

    private void deleteSurvey(AuthenticatedSession creator, long surveyId) throws Exception {
        mockMvc.perform(delete("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isNoContent());
    }

    private CsrfSession issueCsrf(Cookie existingCookie) throws Exception {
        var request = get("/api/auth/csrf");
        if (existingCookie != null) {
            request.cookie(existingCookie);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie responseCookie = result.getResponse().getCookie(SESSION_COOKIE);
        Cookie cookie = responseCookie == null ? existingCookie : responseCookie;
        assertThat(cookie).isNotNull();
        return new CsrfSession(
                cookie,
                body.get("headerName").stringValue(),
                body.get("token").stringValue());
    }

    private Cookie requireResponseCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }

    private static FieldDescriptor[] listResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("[].id").description("Internal Survey identifier"),
            fieldWithPath("[].title").description("Survey title"),
            fieldWithPath("[].status").description("Survey lifecycle status"),
            fieldWithPath("[].slug").description("Reserved public identity"),
            fieldWithPath("[].responseCount").description("Canonical Response count"),
            fieldWithPath("[].updatedAt").description("Last update timestamp")
        };
    }

    private static FieldDescriptor[] detailResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("Internal Survey identifier"),
            fieldWithPath("title").description("Survey title"),
            fieldWithPath("description").description("Optional introduction"),
            fieldWithPath("slug").description("Reserved public identity"),
            fieldWithPath("privacyNotice").description("Optional privacy notice"),
            fieldWithPath("status").description("Survey lifecycle status"),
            fieldWithPath("openedAt").description("First-open timestamp; null in normal Phase 2-A flow"),
            fieldWithPath("closedAt").description("Current closed timestamp; null in normal Phase 2-A flow"),
            fieldWithPath("createdAt").description("Creation timestamp"),
            fieldWithPath("updatedAt").description("Last update timestamp"),
            fieldWithPath("responseCount").description("Canonical Response count"),
            fieldWithPath("structureLocked").description("Canonical Response existence"),
            fieldWithPath("questions").description("Ordered Question and Option structure")
        };
    }

    private static org.springframework.restdocs.payload.ResponseFieldsSnippet errorResponseFields() {
        return responseFields(
                fieldWithPath("code").description("Stable machine-readable error code"),
                fieldWithPath("message").description("Safe error summary"),
                subsectionWithPath("fieldErrors").description("Field-level validation errors"));
    }

    private record CsrfSession(Cookie cookie, String headerName, String token) {
    }

    private record AuthenticatedSession(Cookie cookie, String headerName, String token) {
    }
}
