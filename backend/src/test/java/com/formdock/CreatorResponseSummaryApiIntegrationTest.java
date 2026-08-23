package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class CreatorResponseSummaryApiIntegrationTest {

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
    void should_returnZeroSummaryWithConfiguredOptionsAndScaleBuckets_when_noResponsesExist()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "zero-summary", "DRAFT", false);
        long choice = insertQuestion(
                surveyId, "SINGLE_CHOICE", "선택", 0, null, null);
        long choiceSecond = insertOption(choice, "둘", 1);
        long choiceFirst = insertOption(choice, "하나", 0);
        long scale = insertQuestion(surveyId, "SCALE", "척도", 1, 2, 4);

        mockMvc.perform(get("/api/surveys/{surveyId}/responses/summary", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(surveyId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalResponses").value(0))
                .andExpect(jsonPath("$.lastSubmittedAt").doesNotExist())
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.questions[0].questionId").value(choice))
                .andExpect(jsonPath("$.questions[0].answeredCount").value(0))
                .andExpect(jsonPath("$.questions[0].options.length()").value(2))
                .andExpect(jsonPath("$.questions[0].options[0].optionId").value(choiceFirst))
                .andExpect(jsonPath("$.questions[0].options[0].count").value(0))
                .andExpect(jsonPath("$.questions[0].options[0].percentage").value("0.00"))
                .andExpect(jsonPath("$.questions[0].options[1].optionId").value(choiceSecond))
                .andExpect(jsonPath("$.questions[1].questionId").value(scale))
                .andExpect(jsonPath("$.questions[1].answeredCount").value(0))
                .andExpect(jsonPath("$.questions[1].average").doesNotExist())
                .andExpect(jsonPath("$.questions[1].distribution.length()").value(3))
                .andExpect(jsonPath("$.questions[1].distribution[0].value").value(2))
                .andExpect(jsonPath("$.questions[1].distribution[1].value").value(3))
                .andExpect(jsonPath("$.questions[1].distribution[2].value").value(4))
                .andExpect(jsonPath("$.questions[1].distribution[*].count")
                        .value(List.of(0, 0, 0)))
                .andExpect(jsonPath("$.questions[1].distribution[*].percentage")
                        .value(List.of("0.00", "0.00", "0.00")))
                .andDo(document(
                        "creator-response-summary-zero",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        responseFields(summaryResponseFields())));
    }

    @Test
    void should_returnGroupedChoiceScaleAndBoundedTextNumberSummary_when_responsesExist()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "representative-summary", "CLOSED", false);

        long number = insertQuestion(surveyId, "NUMBER", "숫자", 7, null, null);
        long scaleWhole = insertQuestion(surveyId, "SCALE", "정수 평균", 6, 1, 5);
        long multiple = insertQuestion(
                surveyId, "MULTIPLE_CHOICE", "여러 선택", 3, null, null);
        long shortText = insertQuestion(surveyId, "SHORT_TEXT", "짧은 답변", 0, null, null);
        long scaleRounded = insertQuestion(surveyId, "SCALE", "반올림 평균", 5, 1, 5);
        long longText = insertQuestion(surveyId, "LONG_TEXT", "긴 답변", 1, null, null);
        long scaleHalf = insertQuestion(surveyId, "SCALE", "소수 평균", 4, 1, 5);
        long single = insertQuestion(
                surveyId, "SINGLE_CHOICE", "하나 선택", 2, null, null);

        long singleThird = insertOption(single, "다", 2);
        long singleFirst = insertOption(single, "가", 0);
        long singleSecond = insertOption(single, "나", 1);
        long multipleSecond = insertOption(multiple, "둘", 1);
        long multipleThird = insertOption(multiple, "셋", 2);
        long multipleFirst = insertOption(multiple, "하나", 0);

        Instant firstSubmittedAt = Instant.parse("2026-08-23T00:00:00Z");
        long[] responses = new long[8];
        for (int index = 0; index < responses.length; index++) {
            responses[index] = insertResponse(
                    surveyId,
                    Integer.toHexString(index + 1),
                    firstSubmittedAt.plusSeconds(index * 60L));
        }

        insertTextAnswer(responses[0], shortText, "raw-short-value-not-for-summary");
        insertTextAnswer(responses[1], shortText, "두 번째 원문");
        insertTextAnswer(responses[0], longText, "raw-long-value-not-for-summary");

        insertOptionSelection(responses[0], single, singleFirst);
        insertOptionSelection(responses[1], single, singleFirst);
        insertOptionSelection(responses[2], single, singleSecond);

        insertOptionSelection(responses[0], multiple, multipleFirst, multipleSecond);
        insertOptionSelection(responses[1], multiple, multipleThird, multipleFirst);

        insertNumericAnswer(responses[0], scaleHalf, new BigDecimal("2.0000"));
        insertNumericAnswer(responses[1], scaleHalf, new BigDecimal("3.0000"));

        int[] roundedValues = {1, 2, 2, 2, 3, 3, 3, 3};
        for (int index = 0; index < roundedValues.length; index++) {
            insertNumericAnswer(
                    responses[index],
                    scaleRounded,
                    BigDecimal.valueOf(roundedValues[index]));
        }
        insertNumericAnswer(responses[0], scaleWhole, new BigDecimal("3.0000"));
        insertNumericAnswer(responses[0], number, new BigDecimal("1234.5678"));

        long responseCountBefore = count("survey_responses");
        long answerCountBefore = count("answers");
        long answerOptionCountBefore = count("answer_options");

        MvcResult result = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/summary", surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(surveyId))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.totalResponses").value(8))
                .andExpect(jsonPath("$.lastSubmittedAt").value("2026-08-23T00:07:00Z"))
                .andExpect(jsonPath("$.questionCount").value(8))
                .andExpect(jsonPath("$.questions[*].questionId").value(List.of(
                        (int) shortText,
                        (int) longText,
                        (int) single,
                        (int) multiple,
                        (int) scaleHalf,
                        (int) scaleRounded,
                        (int) scaleWhole,
                        (int) number)))
                .andExpect(jsonPath("$.questions[0].answeredCount").value(2))
                .andExpect(jsonPath("$.questions[1].answeredCount").value(1))
                .andExpect(jsonPath("$.questions[2].answeredCount").value(3))
                .andExpect(jsonPath("$.questions[2].options[0].optionId").value(singleFirst))
                .andExpect(jsonPath("$.questions[2].options[0].count").value(2))
                .andExpect(jsonPath("$.questions[2].options[0].percentage").value("66.67"))
                .andExpect(jsonPath("$.questions[2].options[1].optionId").value(singleSecond))
                .andExpect(jsonPath("$.questions[2].options[1].count").value(1))
                .andExpect(jsonPath("$.questions[2].options[1].percentage").value("33.33"))
                .andExpect(jsonPath("$.questions[2].options[2].optionId").value(singleThird))
                .andExpect(jsonPath("$.questions[2].options[2].count").value(0))
                .andExpect(jsonPath("$.questions[2].options[2].percentage").value("0.00"))
                .andExpect(jsonPath("$.questions[3].answeredCount").value(2))
                .andExpect(jsonPath("$.questions[3].options[0].optionId").value(multipleFirst))
                .andExpect(jsonPath("$.questions[3].options[0].percentage").value("100.00"))
                .andExpect(jsonPath("$.questions[3].options[1].optionId").value(multipleSecond))
                .andExpect(jsonPath("$.questions[3].options[1].percentage").value("50.00"))
                .andExpect(jsonPath("$.questions[3].options[2].optionId").value(multipleThird))
                .andExpect(jsonPath("$.questions[3].options[2].percentage").value("50.00"))
                .andExpect(jsonPath("$.questions[4].average").value("2.50"))
                .andExpect(jsonPath("$.questions[4].distribution[*].count")
                        .value(List.of(0, 1, 1, 0, 0)))
                .andExpect(jsonPath("$.questions[5].average").value("2.38"))
                .andExpect(jsonPath("$.questions[5].distribution[*].count")
                        .value(List.of(1, 3, 4, 0, 0)))
                .andExpect(jsonPath("$.questions[5].distribution[*].percentage")
                        .value(List.of("12.50", "37.50", "50.00", "0.00", "0.00")))
                .andExpect(jsonPath("$.questions[6].average").value("3.00"))
                .andExpect(jsonPath("$.questions[7].answeredCount").value(1))
                .andDo(document(
                        "creator-response-summary",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        responseFields(summaryResponseFields())))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(
                "raw-short-value-not-for-summary",
                "raw-long-value-not-for-summary",
                "1234.5678",
                "textValue",
                "numericValue",
                "clientSubmissionId",
                "payloadHash",
                "responseId",
                "answerId",
                "ownerId",
                "session");
        assertThat(count("survey_responses")).isEqualTo(responseCountBefore);
        assertThat(count("answers")).isEqualTo(answerCountBefore);
        assertThat(count("answer_options")).isEqualTo(answerOptionCountBefore);
    }

    @Test
    void should_concealUnknownUnownedAndDeletedSurveys_beforeSummaryAggregation()
            throws Exception {
        AuthenticatedSession owner = authenticateCreator("owner@example.test");
        AuthenticatedSession other = authenticateCreator("other@example.test");
        long foreignSurvey = insertSurvey(other.userId(), "foreign-summary", "OPEN", false);
        long deletedSurvey = insertSurvey(owner.userId(), "deleted-summary", "CLOSED", true);
        long foreignResponse = insertResponse(foreignSurvey, "a", Instant.now());
        long foreignQuestion = insertQuestion(
                foreignSurvey, "SHORT_TEXT", "비공개 질문", 0, null, null);
        insertTextAnswer(foreignResponse, foreignQuestion, "비공개 응답");

        MvcResult foreign = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/summary", foreignSurvey)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andDo(document(
                        "creator-response-summary-survey-not-found",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        responseFields(errorResponseFields())))
                .andReturn();
        MvcResult unknown = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/summary", 999_999)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andReturn();
        MvcResult deleted = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/summary", deletedSurvey)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andReturn();

        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(foreign.getResponse().getContentAsString())
                .isEqualTo(deleted.getResponse().getContentAsString())
                .doesNotContain("비공개 질문", "비공개 응답");
    }

    @Test
    void should_readSummaryForEverySurveyLifecycle_when_creatorOwnsSurvey() throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        for (String lifecycle : List.of("DRAFT", "OPEN", "CLOSED")) {
            long surveyId = insertSurvey(
                    creator.userId(),
                    "summary-" + lifecycle.toLowerCase(),
                    lifecycle,
                    false);

            mockMvc.perform(get("/api/surveys/{surveyId}/responses/summary", surveyId)
                            .cookie(creator.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(lifecycle));
        }
    }

    @Test
    void should_requireCreatorAuthentication_forSummary() throws Exception {
        mockMvc.perform(get("/api/surveys/{surveyId}/responses/summary", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private AuthenticatedSession authenticateCreator(String email) throws Exception {
        User user = userRepository.saveAndFlush(User.createAdmin(
                email,
                passwordEncoder.encode(CREATOR_PASSWORD),
                "Summary Creator"));
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

    private long insertSurvey(long ownerId, String slug, String status, boolean deleted) {
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
                "Response Summary",
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
                title,
                false,
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

    private void insertTextAnswer(long responseId, long questionId, String value) {
        insertAnswer(responseId, questionId, value, null);
    }

    private void insertNumericAnswer(long responseId, long questionId, BigDecimal value) {
        insertAnswer(responseId, questionId, null, value);
    }

    private void insertOptionSelection(long responseId, long questionId, long... optionIds) {
        long answerId = insertAnswer(responseId, questionId, null, null);
        for (long optionId : optionIds) {
            jdbcTemplate.update(
                    "INSERT INTO answer_options (answer_id, option_id) VALUES (?, ?)",
                    answerId,
                    optionId);
        }
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

    private static FieldDescriptor[] summaryResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("surveyId").description("소유권이 확인된 Survey 식별자"),
            fieldWithPath("status").description("현재 DRAFT, OPEN 또는 CLOSED 상태"),
            fieldWithPath("totalResponses").description("canonical Response 전체 수"),
            fieldWithPath("lastSubmittedAt")
                    .type(JsonFieldType.STRING)
                    .description("가장 최근 canonical 제출 시각; Response가 없으면 null")
                    .optional(),
            fieldWithPath("questionCount").description("현재 canonical Question 수"),
            fieldWithPath("questions").description("position 순 Question summary"),
            fieldWithPath("questions[].questionId").description("Question 식별자"),
            fieldWithPath("questions[].type").description("여섯 Question type 중 하나"),
            fieldWithPath("questions[].title").description("Question 제목"),
            fieldWithPath("questions[].position").description("0부터 시작하는 Question 순서"),
            fieldWithPath("questions[].answeredCount")
                    .description("persisted Answer가 존재하는 Response 수"),
            fieldWithPath("questions[].options")
                    .type(JsonFieldType.ARRAY)
                    .description("Choice Question의 position 순 전체 Option summary")
                    .optional(),
            fieldWithPath("questions[].options[].optionId")
                    .type(JsonFieldType.NUMBER)
                    .description("Choice Option 식별자")
                    .optional(),
            fieldWithPath("questions[].options[].label")
                    .type(JsonFieldType.STRING)
                    .description("Choice Option label")
                    .optional(),
            fieldWithPath("questions[].options[].position")
                    .type(JsonFieldType.NUMBER)
                    .description("Choice Option의 canonical position")
                    .optional(),
            fieldWithPath("questions[].options[].count")
                    .type(JsonFieldType.NUMBER)
                    .description("Option 선택 count")
                    .optional(),
            fieldWithPath("questions[].options[].percentage")
                    .type(JsonFieldType.STRING)
                    .description("answeredCount denominator의 scale 2 HALF_UP percentage")
                    .optional(),
            fieldWithPath("questions[].average")
                    .type(JsonFieldType.STRING)
                    .description("SCALE Answer의 scale 2 HALF_UP average; 없으면 null")
                    .optional(),
            fieldWithPath("questions[].distribution")
                    .type(JsonFieldType.ARRAY)
                    .description("configured Scale 범위 전체의 오름차순 bucket")
                    .optional(),
            fieldWithPath("questions[].distribution[].value")
                    .type(JsonFieldType.NUMBER)
                    .description("Scale integer bucket")
                    .optional(),
            fieldWithPath("questions[].distribution[].count")
                    .type(JsonFieldType.NUMBER)
                    .description("Scale bucket Answer count")
                    .optional(),
            fieldWithPath("questions[].distribution[].percentage")
                    .type(JsonFieldType.STRING)
                    .description("answeredCount denominator의 bucket percentage")
                    .optional()
        };
    }

    private static FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("기계적으로 처리할 수 있는 안정적인 오류 코드"),
            fieldWithPath("message").description("내부 정보를 노출하지 않는 안전한 오류 요약"),
            fieldWithPath("fieldErrors").description("항상 존재하는 필드 단위 오류 목록"),
            fieldWithPath("fieldErrors[].path")
                    .type(JsonFieldType.STRING)
                    .description("오류 field path")
                    .optional(),
            fieldWithPath("fieldErrors[].code")
                    .type(JsonFieldType.STRING)
                    .description("필드 단위 오류 code")
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
