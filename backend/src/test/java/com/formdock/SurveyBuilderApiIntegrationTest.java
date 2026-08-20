package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.formdock.auth.User;
import com.formdock.auth.UserRepository;
import com.formdock.question.QuestionType;

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
@SpringBootTest(properties = "formdock.survey.structure-lock-timeout=2s")
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class SurveyBuilderApiIntegrationTest {

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
    void should_createAllSixQuestionTypes_when_completePayloadsAreValid() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Six Types", "six-types");

        int position = 0;
        for (QuestionType type : QuestionType.values()) {
            var request = post("/api/surveys/{surveyId}/questions", surveyId)
                    .cookie(creator.cookie())
                    .header(creator.headerName(), creator.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(questionBody(type, type + " title")));
            var actions = mockMvc.perform(request)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.questions[" + position + "].type")
                            .value(type.name()))
                    .andExpect(jsonPath("$.questions[" + position + "].position")
                            .value(position));
            if (position == 0) {
                actions.andDo(document(
                        "questions-create",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier")),
                        questionRequestFields(),
                        responseFields(detailResponseFields())));
            }
            position++;
        }

        mockMvc.perform(get("/api/surveys/{surveyId}", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(6))
                .andExpect(jsonPath("$.questions[2].options.length()").value(2))
                .andExpect(jsonPath("$.questions[4].scaleMin").value(1))
                .andExpect(jsonPath("$.questions[5].numberMin").value("-1.25"))
                .andExpect(jsonPath("$.questions[5].numberMax").value("10"));
    }

    @Test
    void should_replaceQuestionAndPreserveSubmittedOptionIdentity_when_patchIsComplete()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Option Update", "option-update");
        JsonNode created = createQuestion(
                creator,
                surveyId,
                questionBody(QuestionType.SINGLE_CHOICE, "Original"));
        long questionId = created.get("questions").get(0).get("id").longValue();
        long retainedOptionId = created.get("questions").get(0)
                .get("options").get(0).get("id").longValue();
        long removedOptionId = created.get("questions").get(0)
                .get("options").get(1).get("id").longValue();
        Map<String, Object> update = questionBody(QuestionType.MULTIPLE_CHOICE, "Updated");
        update.put("options", List.of(
                Map.of("id", retainedOptionId, "label", "Renamed first"),
                Map.of("label", "New second")));

        MvcResult result = mockMvc.perform(patch(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        questionId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].id").value(questionId))
                .andExpect(jsonPath("$.questions[0].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.questions[0].position").value(0))
                .andExpect(jsonPath("$.questions[0].options[0].id")
                        .value(retainedOptionId))
                .andExpect(jsonPath("$.questions[0].options[0].label")
                        .value("Renamed first"))
                .andExpect(jsonPath("$.questions[0].options[1].position").value(1))
                .andDo(document(
                        "questions-update",
                        pathParameters(
                                parameterWithName("surveyId")
                                        .description("Internal Survey identifier"),
                                parameterWithName("questionId")
                                        .description("Target Question identifier")),
                        questionRequestFields(),
                        responseFields(detailResponseFields())))
                .andReturn();
        JsonNode updated = objectMapper.readTree(result.getResponse().getContentAsString());
        long newOptionId = updated.get("questions").get(0)
                .get("options").get(1).get("id").longValue();

        assertThat(newOptionId).isNotIn(retainedOptionId, removedOptionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM question_options WHERE id = ?",
                Integer.class,
                removedOptionId)).isZero();
    }

    @Test
    void should_deleteAndReorderQuestions_withoutDisablingUniquePositionConstraint()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Ordering", "ordering");
        long first = questionId(createQuestion(
                creator, surveyId, questionBody(QuestionType.SHORT_TEXT, "First")), 0);
        long second = questionId(createQuestion(
                creator, surveyId, questionBody(QuestionType.LONG_TEXT, "Second")), 1);
        long third = questionId(createQuestion(
                creator, surveyId, questionBody(QuestionType.NUMBER, "Third")), 2);

        mockMvc.perform(delete(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        second)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andDo(document(
                        "questions-delete",
                        pathParameters(
                                parameterWithName("surveyId")
                                        .description("Internal Survey identifier"),
                                parameterWithName("questionId")
                                        .description("Target Question identifier"))));
        assertQuestionOrder(surveyId, List.of(first, third));

        mockMvc.perform(post("/api/surveys/{surveyId}/questions/reorder", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "questionIds", List.of(third, first)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].id").value(third))
                .andExpect(jsonPath("$.questions[0].position").value(0))
                .andExpect(jsonPath("$.questions[1].id").value(first))
                .andExpect(jsonPath("$.questions[1].position").value(1))
                .andDo(document(
                        "questions-reorder",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier")),
                        requestFields(subsectionWithPath("questionIds")
                                .description("Complete ordered Question identifier set")),
                        responseFields(detailResponseFields())));
        assertQuestionOrder(surveyId, List.of(third, first));
    }

    @Test
    void should_returnStableValidationErrors_when_questionPayloadOrIdentityIsInvalid()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Validation", "question-validation");
        long firstQuestionId = questionId(createQuestion(
                creator,
                surveyId,
                questionBody(QuestionType.SINGLE_CHOICE, "First")),
                0);
        JsonNode secondCreated = createQuestion(
                creator,
                surveyId,
                questionBody(QuestionType.SINGLE_CHOICE, "Second"));
        long foreignOptionId = secondCreated.get("questions").get(1)
                .get("options").get(0).get("id").longValue();

        Map<String, Object> invalidChoice = questionBody(
                QuestionType.SINGLE_CHOICE,
                "Invalid Choice");
        invalidChoice.put("options", List.of(Map.of("label", "Only")));
        mockMvc.perform(post("/api/surveys/{surveyId}/questions", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidChoice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_INVALID_CONFIGURATION"))
                .andDo(document("questions-invalid-configuration", errorResponseFields()));

        Map<String, Object> foreignOption = questionBody(
                QuestionType.SINGLE_CHOICE,
                "Foreign Option");
        foreignOption.put("options", List.of(
                Map.of("id", foreignOptionId, "label", "Foreign"),
                Map.of("label", "New")));
        mockMvc.perform(patch(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        firstQuestionId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foreignOption)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/surveys/{surveyId}/questions/reorder", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[" + firstQuestionId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_concealSurveyAndQuestionIdentity_when_ownerOrParentDoesNotMatch()
            throws Exception {
        AuthenticatedSession owner = authenticateCreator("owner@example.test");
        AuthenticatedSession other = authenticateCreator("other@example.test");
        long surveyId = createSurvey(owner, "Private", "private-builder");
        long questionId = questionId(createQuestion(
                owner, surveyId, questionBody(QuestionType.SHORT_TEXT, "Private")), 0);
        long otherSurveyId = createSurvey(owner, "Other", "other-builder");

        mockMvc.perform(patch(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        questionId)
                        .cookie(other.cookie())
                        .header(other.headerName(), other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.SHORT_TEXT, "Hidden"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));

        mockMvc.perform(patch(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        otherSurveyId,
                        questionId)
                        .cookie(owner.cookie())
                        .header(owner.headerName(), owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.SHORT_TEXT, "Wrong Parent"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))
                .andDo(document("questions-not-found", errorResponseFields()));

        mockMvc.perform(delete(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        otherSurveyId,
                        999_999)
                        .cookie(owner.cookie())
                        .header(owner.headerName(), owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        mockMvc.perform(post("/api/surveys/{surveyId}/duplicate", surveyId)
                        .cookie(other.cookie())
                        .header(other.headerName(), other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
    }

    @Test
    void should_rejectEveryQuestionMutation_when_canonicalResponseExists()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Locked", "locked-builder");
        long questionId = questionId(createQuestion(
                creator, surveyId, questionBody(QuestionType.SHORT_TEXT, "Locked")), 0);
        insertResponse(surveyId);

        mockMvc.perform(post("/api/surveys/{surveyId}/questions", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.LONG_TEXT, "No Create"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_STRUCTURE_LOCKED"))
                .andDo(document("questions-structure-locked", errorResponseFields()));
        mockMvc.perform(patch(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        questionId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.SHORT_TEXT, "No Update"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_STRUCTURE_LOCKED"));
        mockMvc.perform(delete(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        questionId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_STRUCTURE_LOCKED"));
        mockMvc.perform(post("/api/surveys/{surveyId}/questions/reorder", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[" + questionId + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_STRUCTURE_LOCKED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM questions WHERE id = ?",
                String.class,
                questionId)).isEqualTo("Locked");
    }

    @Test
    void should_returnSafe503AndWriteNothing_when_productMutationWaitsOnSurveyLock()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Contended", "contended-builder");
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicLong holderPid = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                holderPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class));
                jdbcTemplate.queryForObject(
                        "SELECT id FROM surveys WHERE id = ? FOR UPDATE",
                        Long.class,
                        surveyId);
                holderLocked.countDown();
                awaitLatch(releaseHolder, "Survey lock holder release");
                return null;
            }));
            assertThat(holderLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> waiter = executor.submit(() -> mockMvc.perform(
                            post("/api/surveys/{surveyId}/questions", surveyId)
                                    .cookie(creator.cookie())
                                    .header(creator.headerName(), creator.token())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            questionBody(QuestionType.SHORT_TEXT, "Blocked"))))
                    .andReturn());
            awaitBlockedBy(holderPid.get());

            MvcResult result = waiter.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(503);
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("code").stringValue()).isEqualTo("TEMPORARILY_UNAVAILABLE");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM questions WHERE survey_id = ?",
                    Integer.class,
                    surveyId)).isZero();

            releaseHolder.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isNull();
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_openCloseAndReopenSurvey_whilePreservingFirstOpenTimestamp()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Lifecycle", "builder-lifecycle");
        createQuestion(creator, surveyId, questionBody(QuestionType.SHORT_TEXT, "Question"));

        MvcResult openResult = mockMvc.perform(post("/api/surveys/{surveyId}/open", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andDo(document(
                        "surveys-open",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier")),
                        responseFields(detailResponseFields())))
                .andReturn();
        String openedAt = objectMapper.readTree(openResult.getResponse().getContentAsString())
                .get("openedAt").stringValue();

        mockMvc.perform(post("/api/surveys/{surveyId}/close", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andDo(document(
                        "surveys-close",
                        pathParameters(parameterWithName("surveyId")
                                .description("Internal Survey identifier")),
                        responseFields(detailResponseFields())));

        insertResponse(surveyId);
        mockMvc.perform(post("/api/surveys/{surveyId}/open", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").value(openedAt))
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.responseCount").value(1))
                .andExpect(jsonPath("$.structureLocked").value(true));
    }

    @Test
    void should_rejectInvalidLifecycleStateOrStructure_withStableConflictCode()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Invalid Lifecycle", "invalid-lifecycle");

        mockMvc.perform(post("/api/surveys/{surveyId}/open", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_INVALID_STRUCTURE"))
                .andDo(document("surveys-invalid-structure", errorResponseFields()));

        createQuestion(creator, surveyId, questionBody(QuestionType.SHORT_TEXT, "Question"));
        openSurvey(creator, surveyId);
        mockMvc.perform(post("/api/surveys/{surveyId}/open", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_STATE_CONFLICT"))
                .andDo(document("surveys-state-conflict", errorResponseFields()));
        mockMvc.perform(post("/api/surveys/{surveyId}/close", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/surveys/{surveyId}/close", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_STATE_CONFLICT"));
    }

    @Test
    void should_validateOpenAfterAcquiringSurveyLock_when_structureChangesAheadOfWaiter()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Serialized Open", "serialized-open");
        long questionId = questionId(createQuestion(
                creator, surveyId, questionBody(QuestionType.SHORT_TEXT, "Question")), 0);
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicLong holderPid = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                holderPid.set(jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Long.class));
                jdbcTemplate.queryForObject(
                        "SELECT id FROM surveys WHERE id = ? FOR UPDATE",
                        Long.class,
                        surveyId);
                jdbcTemplate.update("DELETE FROM questions WHERE id = ?", questionId);
                holderLocked.countDown();
                awaitLatch(releaseHolder, "Open serialization holder release");
                return null;
            }));
            assertThat(holderLocked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> open = executor.submit(() -> mockMvc.perform(
                            post("/api/surveys/{surveyId}/open", surveyId)
                                    .cookie(creator.cookie())
                                    .header(creator.headerName(), creator.token()))
                    .andReturn());
            awaitBlockedBy(holderPid.get());
            releaseHolder.countDown();

            MvcResult result = open.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("code").stringValue()).isEqualTo("SURVEY_INVALID_STRUCTURE");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM surveys WHERE id = ?",
                    String.class,
                    surveyId)).isEqualTo("DRAFT");
            assertThat(holder.get(10, TimeUnit.SECONDS)).isNull();
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_deepDuplicateOwnedSurveys_withFreshIdentityAndNoResponses()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        List<Long> sourceIds = new ArrayList<>();
        for (String state : List.of("DRAFT", "OPEN", "CLOSED")) {
            long sourceId = createSurvey(
                    creator,
                    state + " Source",
                    state.toLowerCase() + "-source");
            JsonNode source = createQuestion(
                    creator,
                    sourceId,
                    questionBody(QuestionType.SINGLE_CHOICE, state + " Question"));
            sourceIds.add(sourceId);
            if (!state.equals("DRAFT")) {
                openSurvey(creator, sourceId);
            }
            if (state.equals("CLOSED")) {
                closeSurvey(creator, sourceId);
                insertResponse(sourceId);
            }

            var actions = mockMvc.perform(post(
                            "/api/surveys/{surveyId}/duplicate",
                            sourceId)
                            .cookie(creator.cookie())
                            .header(creator.headerName(), creator.token()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.openedAt").doesNotExist())
                    .andExpect(jsonPath("$.closedAt").doesNotExist())
                    .andExpect(jsonPath("$.responseCount").value(0))
                    .andExpect(jsonPath("$.structureLocked").value(false))
                    .andExpect(jsonPath("$.questions.length()").value(1))
                    .andExpect(jsonPath("$.questions[0].title")
                            .value(state + " Question"));
            if (state.equals("CLOSED")) {
                actions.andDo(document(
                        "surveys-duplicate",
                        pathParameters(parameterWithName("surveyId")
                                .description("Owned source Survey identifier")),
                        responseFields(detailResponseFields())));
            }
            JsonNode duplicate = objectMapper.readTree(
                    actions.andReturn().getResponse().getContentAsString());
            assertThat(duplicate.get("id").longValue()).isNotEqualTo(sourceId);
            assertThat(duplicate.get("slug").stringValue())
                    .isNotEqualTo(state.toLowerCase() + "-source");
            assertThat(duplicate.get("questions").get(0).get("id").longValue())
                    .isNotEqualTo(source.get("questions").get(0).get("id").longValue());
            assertThat(duplicate.get("questions").get(0)
                    .get("options").get(0).get("id").longValue())
                    .isNotEqualTo(source.get("questions").get(0)
                            .get("options").get(0).get("id").longValue());
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM survey_responses WHERE survey_id <> ?",
                Integer.class,
                sourceIds.get(2))).isZero();
    }

    @Test
    void should_requireAuthenticationAndCsrf_forEveryNewUnsafeEndpoint() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = createSurvey(creator, "Protected", "protected-builder");
        long questionId = questionId(createQuestion(
                creator, surveyId, questionBody(QuestionType.SHORT_TEXT, "Question")), 0);

        CsrfSession anonymous = issueCsrf(null);
        mockMvc.perform(post("/api/surveys/{surveyId}/questions", surveyId)
                        .cookie(anonymous.cookie())
                        .header(anonymous.headerName(), anonymous.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.SHORT_TEXT, "Anonymous"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(post("/api/surveys/{surveyId}/questions", surveyId)
                        .cookie(creator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.SHORT_TEXT, "No CSRF"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andDo(document("questions-csrf-invalid", errorResponseFields()));
        mockMvc.perform(patch(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        questionId)
                        .cookie(creator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                questionBody(QuestionType.SHORT_TEXT, "No CSRF"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(
                        "/api/surveys/{surveyId}/questions/{questionId}",
                        surveyId,
                        questionId)
                        .cookie(creator.cookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/surveys/{surveyId}/questions/reorder", surveyId)
                        .cookie(creator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\":[" + questionId + "]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/surveys/{surveyId}/open", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/surveys/{surveyId}/close", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/surveys/{surveyId}/duplicate", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> questionBody(QuestionType type, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type.name());
        body.put("title", title);
        body.put("description", null);
        body.put("required", true);
        body.put("scaleMin", type == QuestionType.SCALE ? 1 : null);
        body.put("scaleMax", type == QuestionType.SCALE ? 5 : null);
        body.put("scaleMinLabel", type == QuestionType.SCALE ? "Low" : null);
        body.put("scaleMaxLabel", type == QuestionType.SCALE ? "High" : null);
        body.put("numberMin", type == QuestionType.NUMBER ? "-1.2500" : null);
        body.put("numberMax", type == QuestionType.NUMBER ? "10" : null);
        body.put("options", (type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE)
                ? List.of(Map.of("label", "First"), Map.of("label", "Second"))
                : List.of());
        return body;
    }

    private JsonNode createQuestion(
            AuthenticatedSession creator,
            long surveyId,
            Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/surveys/{surveyId}/questions", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long questionId(JsonNode survey, int position) {
        return survey.get("questions").get(position).get("id").longValue();
    }

    private void assertQuestionOrder(long surveyId, List<Long> expectedIds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, position FROM questions WHERE survey_id = ? ORDER BY position",
                surveyId);
        assertThat(rows).hasSize(expectedIds.size());
        for (int position = 0; position < rows.size(); position++) {
            assertThat(((Number) rows.get(position).get("id")).longValue())
                    .isEqualTo(expectedIds.get(position));
            assertThat(((Number) rows.get(position).get("position")).intValue())
                    .isEqualTo(position);
        }
    }

    private void openSurvey(AuthenticatedSession creator, long surveyId) throws Exception {
        mockMvc.perform(post("/api/surveys/{surveyId}/open", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isOk());
    }

    private void closeSurvey(AuthenticatedSession creator, long surveyId) throws Exception {
        mockMvc.perform(post("/api/surveys/{surveyId}/close", surveyId)
                        .cookie(creator.cookie())
                        .header(creator.headerName(), creator.token()))
                .andExpect(status().isOk());
    }

    private AuthenticatedSession authenticateCreator(String email) throws Exception {
        userRepository.saveAndFlush(User.createAdmin(
                email,
                passwordEncoder.encode(CREATOR_PASSWORD),
                "Survey Creator"));
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
                .get("id").longValue();
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

    private void insertResponse(long surveyId) {
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
        throw new AssertionError("Expected request did not block on the Survey row");
    }

    private void awaitLatch(CountDownLatch latch, String description) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS))
                    .as(description)
                    .isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " was interrupted", exception);
        }
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }

    private static org.springframework.restdocs.payload.RequestFieldsSnippet
            questionRequestFields() {
        return requestFields(
                fieldWithPath("type").description("One of the six Question types"),
                fieldWithPath("title").description("Trimmed Question title"),
                fieldWithPath("description").description("Optional Question description"),
                fieldWithPath("required").description("Whether an Answer is required"),
                fieldWithPath("scaleMin").description("SCALE minimum or null"),
                fieldWithPath("scaleMax").description("SCALE maximum or null"),
                fieldWithPath("scaleMinLabel").description("Optional SCALE minimum label"),
                fieldWithPath("scaleMaxLabel").description("Optional SCALE maximum label"),
                fieldWithPath("numberMin").description("NUMBER decimal string or null"),
                fieldWithPath("numberMax").description("NUMBER decimal string or null"),
                subsectionWithPath("options").description("Complete ordered Option state"));
    }

    private static FieldDescriptor[] detailResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("id").description("Internal Survey identifier"),
            fieldWithPath("title").description("Survey title"),
            fieldWithPath("description").description("Optional introduction"),
            fieldWithPath("slug").description("Reserved public identity"),
            fieldWithPath("privacyNotice").description("Optional privacy notice"),
            fieldWithPath("status").description("Survey lifecycle status"),
            fieldWithPath("openedAt").description("First-open timestamp"),
            fieldWithPath("closedAt").description("Current close timestamp"),
            fieldWithPath("createdAt").description("Creation timestamp"),
            fieldWithPath("updatedAt").description("Last Survey update timestamp"),
            fieldWithPath("responseCount").description("Canonical Response count"),
            fieldWithPath("structureLocked").description("Canonical Response existence"),
            subsectionWithPath("questions").description("Ordered Question/Option structure")
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
