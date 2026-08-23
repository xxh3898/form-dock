package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.formdock.auth.User;
import com.formdock.auth.UserRepository;

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
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class CreatorResponseReadApiIntegrationTest {

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

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void should_returnEmptyDefaultPage_when_ownedSurveyHasNoResponses() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "empty-results", "DRAFT", false);

        mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void should_pageNewestFirstAndUseResponseIdAsTimestampTieBreak_when_responsesExist()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "paged-results", "OPEN", false);
        long oldest = insertResponse(surveyId, "a", Instant.parse("2026-08-22T23:59:59Z"));
        Instant tiedTimestamp = Instant.parse("2026-08-23T00:00:00Z");
        long tiedFirst = insertResponse(surveyId, "b", tiedTimestamp);
        long tiedSecond = insertResponse(surveyId, "c", tiedTimestamp);

        MvcResult firstPage = mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].responseId").value(tiedSecond))
                .andExpect(jsonPath("$.items[1].responseId").value(tiedFirst))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andDo(document(
                        "creator-responses-list",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        queryParameters(
                                parameterWithName("page").description("0부터 시작하는 page 번호"),
                                parameterWithName("size").description("1 이상 100 이하 page 크기")),
                        responseFields(listResponseFields())))
                .andReturn();
        assertThat(firstPage.getResponse().getContentAsString()).doesNotContain(
                "clientSubmissionId",
                "payloadHash",
                "ownerId",
                "session");

        mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].responseId").value(oldest))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .queryParam("page", "99")
                        .queryParam("size", "2")
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(99))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void should_rejectInvalidPagination_withStableValidationError() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "invalid-page", "CLOSED", false);

        mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .queryParam("page", "-1")
                        .cookie(creator.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].path").value("page"))
                .andDo(document(
                        "creator-responses-list-validation",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        queryParameters(parameterWithName("page")
                                .description("0 이상이어야 하는 page 번호")),
                        responseFields(errorResponseFields())));

        assertInvalidPagination(creator, surveyId, "page", "not-an-integer");
        assertInvalidPagination(creator, surveyId, "size", "0");
        assertInvalidPagination(creator, surveyId, "size", "101");
        assertInvalidPagination(creator, surveyId, "size", "not-an-integer");
        mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .queryParam("page", "9223372036854775807")
                        .queryParam("size", "100")
                        .cookie(creator.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_resolveOwnedSurveyBeforePaginationValidation_andConcealUnavailableSurveys()
            throws Exception {
        AuthenticatedSession owner = authenticateCreator("owner@example.test");
        AuthenticatedSession other = authenticateCreator("other@example.test");
        long foreignSurveyId = insertSurvey(owner.userId(), "foreign-results", "OPEN", false);
        long deletedSurveyId = insertSurvey(owner.userId(), "deleted-results", "CLOSED", true);

        MvcResult foreign = mockMvc.perform(get("/api/surveys/{surveyId}/responses", foreignSurveyId)
                        .queryParam("page", "not-an-integer")
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andDo(document(
                        "creator-responses-survey-not-found",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        responseFields(errorResponseFields())))
                .andReturn();

        MvcResult unknown = mockMvc.perform(get("/api/surveys/{surveyId}/responses", 999_999)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andReturn();
        MvcResult deleted = mockMvc.perform(get("/api/surveys/{surveyId}/responses", deletedSurveyId)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andReturn();

        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(foreign.getResponse().getContentAsString())
                .isEqualTo(deleted.getResponse().getContentAsString());
    }

    @Test
    void should_readResponsesForEverySurveyLifecycle_when_creatorOwnsSurvey() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        String[] statuses = {"DRAFT", "OPEN", "CLOSED"};
        String[] hashPrefixes = {"a", "b", "c"};
        for (int index = 0; index < statuses.length; index++) {
            String status = statuses[index];
            long surveyId = insertSurvey(
                    creator.userId(),
                    "lifecycle-" + status.toLowerCase(),
                    status,
                    false);
            long responseId = insertResponse(
                    surveyId,
                    hashPrefixes[index],
                    Instant.now());

            mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                            .cookie(creator.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].responseId").value(responseId));
            mockMvc.perform(get(
                            "/api/surveys/{surveyId}/responses/{responseId}",
                            surveyId,
                            responseId)
                            .cookie(creator.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseId").value(responseId))
                    .andExpect(jsonPath("$.questions").isEmpty());
        }
    }

    @Test
    void should_returnCompleteOrderedSixTypeDetail_withoutTransportOrInternalMetadata()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "six-type-detail", "CLOSED", false);
        long number = insertQuestion(surveyId, "NUMBER", "숫자", null, true, 5, null, null);
        long multi = insertQuestion(surveyId, "MULTIPLE_CHOICE", "여러 선택", null, true, 3, null, null);
        long shortText = insertQuestion(surveyId, "SHORT_TEXT", "짧은 답변", "설명", true, 0, null, null);
        long scale = insertQuestion(surveyId, "SCALE", "척도", null, true, 4, 1, 10);
        long longText = insertQuestion(surveyId, "LONG_TEXT", "긴 답변", null, true, 1, null, null);
        long single = insertQuestion(surveyId, "SINGLE_CHOICE", "하나 선택", null, true, 2, null, null);
        long singleSecond = insertOption(single, "두 번째", 1);
        long singleFirst = insertOption(single, "첫 번째", 0);
        long multiThird = insertOption(multi, "다", 2);
        long multiFirst = insertOption(multi, "가", 0);
        long multiSecond = insertOption(multi, "나", 1);
        Instant submittedAt = Instant.parse("2026-08-23T00:00:00Z");
        long responseId = insertResponse(surveyId, "d", submittedAt);
        insertTextAnswer(responseId, shortText, "  원문\n답변  ");
        insertTextAnswer(responseId, longText, "긴 원문");
        long singleAnswer = insertOptionAnswer(responseId, single);
        insertAnswerOption(singleAnswer, singleSecond);
        long multiAnswer = insertOptionAnswer(responseId, multi);
        insertAnswerOption(multiAnswer, multiThird);
        insertAnswerOption(multiAnswer, multiFirst);
        insertNumericAnswer(responseId, scale, new BigDecimal("7.0000"));
        insertNumericAnswer(responseId, number, new BigDecimal("-12.3400"));

        long responseCountBefore = count("survey_responses");
        long answerCountBefore = count("answers");
        long answerOptionCountBefore = count("answer_options");

        MvcResult result = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}", surveyId, responseId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseId").value(responseId))
                .andExpect(jsonPath("$.submittedAt").value("2026-08-23T00:00:00Z"))
                .andExpect(jsonPath("$.questions.length()").value(6))
                .andExpect(jsonPath("$.questions[0].questionId").value(shortText))
                .andExpect(jsonPath("$.questions[0].type").value("SHORT_TEXT"))
                .andExpect(jsonPath("$.questions[0].description").value("설명"))
                .andExpect(jsonPath("$.questions[0].answer.textValue").value("  원문\n답변  "))
                .andExpect(jsonPath("$.questions[0].answer.numericValue").doesNotExist())
                .andExpect(jsonPath("$.questions[0].answer.options").isEmpty())
                .andExpect(jsonPath("$.questions[1].questionId").value(longText))
                .andExpect(jsonPath("$.questions[1].answer.textValue").value("긴 원문"))
                .andExpect(jsonPath("$.questions[2].answer.options.length()").value(1))
                .andExpect(jsonPath("$.questions[2].answer.options[0].id").value(singleSecond))
                .andExpect(jsonPath("$.questions[2].answer.options[0].label").value("두 번째"))
                .andExpect(jsonPath("$.questions[2].answer.options[0].position").value(1))
                .andExpect(jsonPath("$.questions[3].answer.options.length()").value(2))
                .andExpect(jsonPath("$.questions[3].answer.options[0].id").value(multiFirst))
                .andExpect(jsonPath("$.questions[3].answer.options[1].id").value(multiThird))
                .andExpect(jsonPath("$.questions[4].answer.numericValue").value("7"))
                .andExpect(jsonPath("$.questions[5].answer.numericValue").value("-12.34"))
                .andDo(document(
                        "creator-response-detail",
                        pathParameters(
                                parameterWithName("surveyId")
                                        .description("소유권을 확인할 내부 Survey 식별자"),
                                parameterWithName("responseId")
                                        .description("Survey 안에서 조회할 canonical Response 식별자")),
                        responseFields(detailResponseFields())))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(
                "clientSubmissionId",
                "payloadHash",
                "answerId",
                "ownerId",
                "session");
        assertThat(singleFirst).isPositive();
        assertThat(multiSecond).isPositive();
        assertThat(count("survey_responses")).isEqualTo(responseCountBefore);
        assertThat(count("answers")).isEqualTo(answerCountBefore);
        assertThat(count("answer_options")).isEqualTo(answerOptionCountBefore);
    }

    @Test
    void should_includeCurrentOptionalQuestionWithNullAnswer_when_responseDidNotAnswerIt()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "optional-answer", "OPEN", false);
        long answered = insertQuestion(surveyId, "SHORT_TEXT", "필수", null, true, 0, null, null);
        long zeroNumber = insertQuestion(surveyId, "NUMBER", "영", null, false, 1, null, null);
        long unanswered = insertQuestion(surveyId, "LONG_TEXT", "선택", null, false, 2, null, null);
        long responseId = insertResponse(surveyId, "e", Instant.now());
        insertTextAnswer(responseId, answered, "응답");
        insertNumericAnswer(responseId, zeroNumber, new BigDecimal("0.0000"));

        MvcResult result = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}", surveyId, responseId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].questionId").value(answered))
                .andExpect(jsonPath("$.questions[1].questionId").value(zeroNumber))
                .andExpect(jsonPath("$.questions[1].answer.numericValue").value("0"))
                .andExpect(jsonPath("$.questions[2].questionId").value(unanswered))
                .andExpect(jsonPath("$.questions[2].answer").doesNotExist())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("questions").get(2).has("answer")).isTrue();
        assertThat(body.get("questions").get(2).get("answer").isNull()).isTrue();
    }

    @Test
    void should_concealUnknownWrongSurveyForeignAndDeletedResponseLocations() throws Exception {
        AuthenticatedSession owner = authenticateCreator("owner@example.test");
        AuthenticatedSession other = authenticateCreator("other@example.test");
        long ownedSurvey = insertSurvey(owner.userId(), "owned-detail", "OPEN", false);
        long otherOwnedSurvey = insertSurvey(owner.userId(), "other-owned-detail", "OPEN", false);
        long foreignSurvey = insertSurvey(other.userId(), "foreign-detail", "OPEN", false);
        long deletedSurvey = insertSurvey(owner.userId(), "deleted-detail", "CLOSED", true);
        long responseInOtherSurvey = insertResponse(otherOwnedSurvey, "a", Instant.now());
        long responseInForeignSurvey = insertResponse(foreignSurvey, "b", Instant.now());
        long responseInDeletedSurvey = insertResponse(deletedSurvey, "c", Instant.now());

        MvcResult unknownResponse = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}", ownedSurvey, 999_999)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESPONSE_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andDo(document(
                        "creator-response-not-found",
                        pathParameters(
                                parameterWithName("surveyId")
                                        .description("소유권을 확인할 내부 Survey 식별자"),
                                parameterWithName("responseId")
                                        .description("Survey 안에서 조회할 canonical Response 식별자")),
                        responseFields(errorResponseFields())))
                .andReturn();

        MvcResult wrongSurvey = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}",
                        ownedSurvey,
                        responseInOtherSurvey)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESPONSE_NOT_FOUND"))
                .andReturn();
        assertThat(wrongSurvey.getResponse().getContentAsString())
                .isEqualTo(unknownResponse.getResponse().getContentAsString());

        MvcResult foreignResponse = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}",
                        ownedSurvey,
                        responseInForeignSurvey)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESPONSE_NOT_FOUND"))
                .andReturn();
        assertThat(foreignResponse.getResponse().getContentAsString())
                .isEqualTo(unknownResponse.getResponse().getContentAsString());

        mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}",
                        foreignSurvey,
                        responseInForeignSurvey)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
        mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/{responseId}",
                        deletedSurvey,
                        responseInDeletedSurvey)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
    }

    @Test
    void should_requireCreatorAuthentication_forResponseListAndDetail() throws Exception {
        mockMvc.perform(get("/api/surveys/{surveyId}/responses", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get("/api/surveys/{surveyId}/responses/{responseId}", 1, 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private AuthenticatedSession authenticateCreator(String email) throws Exception {
        User user = userRepository.saveAndFlush(User.createAdmin(
                email,
                passwordEncoder.encode(CREATOR_PASSWORD),
                "Response Creator"));
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
        return new AuthenticatedSession(user.getId(), authenticatedCookie);
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

    private void assertInvalidPagination(
            AuthenticatedSession creator,
            long surveyId,
            String name,
            String value) throws Exception {
        mockMvc.perform(get("/api/surveys/{surveyId}/responses", surveyId)
                        .queryParam(name, value)
                        .cookie(creator.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private long insertSurvey(
            long ownerId,
            String slug,
            String status,
            boolean deleted) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, opened_at, closed_at, deleted_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Response Results",
                slug,
                status,
                "DRAFT".equals(status) ? null : Timestamp.from(now.minusSeconds(60)),
                "CLOSED".equals(status) ? Timestamp.from(now.minusSeconds(30)) : null,
                deleted ? Timestamp.from(now) : null,
                Timestamp.from(now.minusSeconds(120)),
                Timestamp.from(now));
    }

    private long insertQuestion(
            long surveyId,
            String type,
            String title,
            String description,
            boolean required,
            int position,
            Integer scaleMin,
            Integer scaleMax) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, description, required, position,
                    scale_min, scale_max, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                type,
                title,
                description,
                required,
                position,
                scaleMin,
                scaleMax,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private long insertOption(long questionId, String label, int position) {
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

    private long insertResponse(long surveyId, String hashPrefix, Instant submittedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                UUID.randomUUID(),
                hashPrefix.repeat(64),
                Timestamp.from(submittedAt));
    }

    private void insertTextAnswer(long responseId, long questionId, String textValue) {
        insertAnswer(responseId, questionId, textValue, null);
    }

    private void insertNumericAnswer(long responseId, long questionId, BigDecimal numericValue) {
        insertAnswer(responseId, questionId, null, numericValue);
    }

    private long insertOptionAnswer(long responseId, long questionId) {
        return insertAnswer(responseId, questionId, null, null);
    }

    private long insertAnswer(
            long responseId,
            long questionId,
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

    private void insertAnswerOption(long answerId, long optionId) {
        jdbcTemplate.update(
                "INSERT INTO answer_options (answer_id, option_id) VALUES (?, ?)",
                answerId,
                optionId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM answer_options");
        jdbcTemplate.update("DELETE FROM answers");
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }

    private static FieldDescriptor[] listResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("items").description("고정 순서로 정렬된 현재 page의 Response 목록"),
            fieldWithPath("items[].responseId").description("canonical Response 식별자"),
            fieldWithPath("items[].submittedAt").description("canonical 최초 제출 시각"),
            fieldWithPath("page").description("0부터 시작하는 현재 page 번호"),
            fieldWithPath("size").description("요청에 적용된 page 크기"),
            fieldWithPath("totalElements").description("Survey의 canonical Response 전체 수"),
            fieldWithPath("totalPages").description("전체 page 수; Response가 없으면 0")
        };
    }

    private static FieldDescriptor[] detailResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("responseId").description("canonical Response 식별자"),
            fieldWithPath("submittedAt").description("canonical 최초 제출 시각"),
            fieldWithPath("questions").description("현재 Survey의 position 순 Question 전체"),
            fieldWithPath("questions[].questionId").description("Question 식별자"),
            fieldWithPath("questions[].type").description("여섯 Question type 중 하나"),
            fieldWithPath("questions[].title").description("Question 제목"),
            fieldWithPath("questions[].description")
                    .type(JsonFieldType.STRING)
                    .description("선택적 Question 설명")
                    .optional(),
            fieldWithPath("questions[].required").description("필수 Answer 여부"),
            fieldWithPath("questions[].position").description("0부터 시작하는 Question 순서"),
            fieldWithPath("questions[].answer")
                    .description("저장된 Answer; 선택 Question의 무응답은 null"),
            fieldWithPath("questions[].answer.textValue")
                    .type(JsonFieldType.STRING)
                    .description("Text Answer 원문; 다른 type은 null")
                    .optional(),
            fieldWithPath("questions[].answer.numericValue")
                    .type(JsonFieldType.STRING)
                    .description("SCALE/NUMBER canonical decimal; 다른 type은 null")
                    .optional(),
            fieldWithPath("questions[].answer.options")
                    .description("position 순 selected Choice Option; non-Choice는 빈 목록"),
            fieldWithPath("questions[].answer.options[].id")
                    .type(JsonFieldType.NUMBER)
                    .description("선택된 Option 식별자")
                    .optional(),
            fieldWithPath("questions[].answer.options[].label")
                    .type(JsonFieldType.STRING)
                    .description("선택된 Option label")
                    .optional(),
            fieldWithPath("questions[].answer.options[].position")
                    .type(JsonFieldType.NUMBER)
                    .description("선택된 Option의 canonical position")
                    .optional()
        };
    }

    private static FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("기계적으로 처리할 수 있는 안정적인 오류 코드"),
            fieldWithPath("message").description("내부 정보를 노출하지 않는 안전한 오류 요약"),
            fieldWithPath("fieldErrors").description("필드 단위 오류 목록"),
            fieldWithPath("fieldErrors[].path")
                    .type(JsonFieldType.STRING)
                    .description("오류 request parameter 경로")
                    .optional(),
            fieldWithPath("fieldErrors[].code")
                    .type(JsonFieldType.STRING)
                    .description("필드 단위 오류 코드")
                    .optional(),
            fieldWithPath("fieldErrors[].message")
                    .type(JsonFieldType.STRING)
                    .description("안전한 필드 오류 설명")
                    .optional()
        };
    }

    private record CsrfSession(Cookie cookie, String headerName, String token) {
    }

    private record AuthenticatedSession(long userId, Cookie cookie) {
    }
}
