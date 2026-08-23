package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class CreatorResponseCsvExportApiIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String CREATOR_PASSWORD = "test-only-creator-passphrase";
    private static final byte[] UTF_8_BOM = {
        (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };

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
    void should_exportCanonicalHeaderOnlyCsv_when_ownedSurveyHasNoResponsesAcrossLifecycle()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        String[] statuses = {"DRAFT", "OPEN", "CLOSED"};

        for (int index = 0; index < statuses.length; index++) {
            String lifecycle = statuses[index];
            long surveyId = insertSurvey(
                    creator.userId(),
                    "empty-export-" + lifecycle.toLowerCase(),
                    lifecycle,
                    false);

            ResultActions actions = mockMvc.perform(get(
                            "/api/surveys/{surveyId}/responses/export.csv",
                            surveyId)
                            .cookie(creator.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            HttpHeaders.CONTENT_TYPE,
                            "text/csv; charset=UTF-8"))
                    .andExpect(header().string(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"formdock-survey-"
                                    + surveyId
                                    + "-responses.csv\""));
            if (index == 0) {
                actions.andDo(document(
                        "creator-response-csv-export-empty",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        csvResponseHeaders()));
            }

            byte[] body = actions.andReturn().getResponse().getContentAsByteArray();
            assertThat(body).isEqualTo(csvBytes("response_id,submitted_at\r\n"));
            assertThat(countBom(body)).isEqualTo(1);
        }
    }

    @Test
    void should_exportDeterministicSixTypeRows_when_answersRequireCanonicalCsvEncoding()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "canonical-export", "CLOSED", false);

        long number = insertQuestion(surveyId, "NUMBER", "숫자", 5, null, null);
        long multiple = insertQuestion(
                surveyId,
                "MULTIPLE_CHOICE",
                "복수, 선택",
                3,
                null,
                null);
        long shortText = insertQuestion(
                surveyId,
                "SHORT_TEXT",
                "=제목, \"인용\"",
                0,
                null,
                null);
        long scale = insertQuestion(surveyId, "SCALE", "척도", 4, 1, 10);
        long longText = insertQuestion(
                surveyId,
                "LONG_TEXT",
                "=제목, \"인용\"",
                1,
                null,
                null);
        long single = insertQuestion(
                surveyId,
                "SINGLE_CHOICE",
                "하나 선택",
                2,
                null,
                null);

        long singleSecond = insertOption(single, "두, \"번째\"", 1);
        long singleFirst = insertOption(single, "첫 번째", 0);
        long multipleThird = insertOption(multiple, "@다", 2);
        long multipleFirst = insertOption(multiple, "=가", 0);
        long multipleSecond = insertOption(multiple, "+나", 1);

        Instant firstSubmittedAt = Instant.parse("2026-08-23T00:00:00Z");
        Instant tiedSubmittedAt = Instant.parse("2026-08-23T00:00:01Z");
        long firstResponse = insertResponse(surveyId, "a", firstSubmittedAt);
        long tiedFirstResponse = insertResponse(surveyId, "b", tiedSubmittedAt);
        long tiedSecondResponse = insertResponse(surveyId, "c", tiedSubmittedAt);
        long lastResponse = insertResponse(
                surveyId,
                "d",
                Instant.parse("2026-08-23T00:00:02Z"));

        String exactText = "  한글,\"인용\"\r\n다음\n줄  ";
        insertTextAnswer(firstResponse, shortText, "=SUM(1,2)");
        insertTextAnswer(firstResponse, longText, exactText);
        long firstSingleAnswer = insertOptionAnswer(firstResponse, single);
        insertAnswerOption(firstSingleAnswer, singleSecond);
        long firstMultipleAnswer = insertOptionAnswer(firstResponse, multiple);
        insertAnswerOption(firstMultipleAnswer, multipleThird);
        insertAnswerOption(firstMultipleAnswer, multipleFirst);
        insertNumericAnswer(firstResponse, scale, new BigDecimal("7.0000"));
        insertNumericAnswer(firstResponse, number, new BigDecimal("-12.3400"));

        insertTextAnswer(tiedFirstResponse, shortText, " +명령");
        insertTextAnswer(tiedFirstResponse, longText, "@위험");
        insertNumericAnswer(tiedFirstResponse, number, new BigDecimal("0.0000"));

        insertTextAnswer(tiedSecondResponse, shortText, "\u2003-명령");
        insertTextAnswer(tiedSecondResponse, longText, "안전");
        long tiedSingleAnswer = insertOptionAnswer(tiedSecondResponse, single);
        insertAnswerOption(tiedSingleAnswer, singleFirst);
        long tiedMultipleAnswer = insertOptionAnswer(tiedSecondResponse, multiple);
        insertAnswerOption(tiedMultipleAnswer, multipleSecond);
        insertNumericAnswer(tiedSecondResponse, scale, new BigDecimal("1.0000"));
        insertNumericAnswer(tiedSecondResponse, number, new BigDecimal("1.2300"));

        insertTextAnswer(lastResponse, shortText, "+위험");
        insertTextAnswer(lastResponse, longText, "-위험");

        long responseCountBefore = count("survey_responses");
        long answerCountBefore = count("answers");
        long answerOptionCountBefore = count("answer_options");

        MvcResult result = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/export.csv",
                        surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "text/csv; charset=UTF-8"))
                .andDo(document(
                        "creator-response-csv-export",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        csvResponseHeaders()))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        List<List<String>> rows = parseCsv(body);
        assertThat(countBom(body)).isEqualTo(1);
        assertThat(rows).hasSize(5);
        assertThat(rows.getFirst()).containsExactly(
                "response_id",
                "submitted_at",
                "q_" + shortText + ": =제목, \"인용\"",
                "q_" + longText + ": =제목, \"인용\"",
                "q_" + single + ": 하나 선택",
                "q_" + multiple + "_option_" + multipleFirst + ": 복수, 선택 / =가",
                "q_" + multiple + "_option_" + multipleSecond + ": 복수, 선택 / +나",
                "q_" + multiple + "_option_" + multipleThird + ": 복수, 선택 / @다",
                "q_" + scale + ": 척도",
                "q_" + number + ": 숫자");
        assertThat(rows.get(1)).containsExactly(
                Long.toString(firstResponse),
                "2026-08-23T00:00:00Z",
                "'=SUM(1,2)",
                exactText,
                singleSecond + ": 두, \"번째\"",
                "true",
                "false",
                "true",
                "7",
                "-12.34");
        assertThat(rows.get(2)).containsExactly(
                Long.toString(tiedFirstResponse),
                "2026-08-23T00:00:01Z",
                "' +명령",
                "'@위험",
                "",
                "false",
                "false",
                "false",
                "",
                "0");
        assertThat(rows.get(3)).containsExactly(
                Long.toString(tiedSecondResponse),
                "2026-08-23T00:00:01Z",
                "'\u2003-명령",
                "안전",
                singleFirst + ": 첫 번째",
                "false",
                "true",
                "false",
                "1",
                "1.23");
        assertThat(rows.get(4)).containsExactly(
                Long.toString(lastResponse),
                "2026-08-23T00:00:02Z",
                "'+위험",
                "'-위험",
                "",
                "false",
                "false",
                "false",
                "",
                "");
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain(
                "client_submission_id",
                "payload_hash",
                "answer_id",
                "owner_id",
                "session");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT text_value FROM answers WHERE response_id = ? AND question_id = ?",
                String.class,
                firstResponse,
                shortText)).isEqualTo("=SUM(1,2)");
        assertThat(count("survey_responses")).isEqualTo(responseCountBefore);
        assertThat(count("answers")).isEqualTo(answerCountBefore);
        assertThat(count("answer_options")).isEqualTo(answerOptionCountBefore);
    }

    @Test
    void should_streamEveryResponseInCanonicalOrder_when_resultExceedsFetchSize()
            throws Exception {
        AuthenticatedSession creator = authenticateCreator("creator@example.test");
        long surveyId = insertSurvey(creator.userId(), "cursor-boundary", "OPEN", false);
        Instant submittedAt = Instant.parse("2026-08-23T01:00:00Z");
        List<Long> responseIds = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            responseIds.add(insertResponse(surveyId, hashPrefix(index), submittedAt));
        }

        MvcResult result = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/export.csv",
                        surveyId)
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        List<List<String>> rows = parseCsv(result.getResponse().getContentAsByteArray());
        assertThat(rows).hasSize(258);
        assertThat(rows.getFirst()).containsExactly("response_id", "submitted_at");
        assertThat(rows.subList(1, rows.size()))
                .extracting(row -> Long.valueOf(row.getFirst()))
                .containsExactlyElementsOf(responseIds);
        assertThat(rows.subList(1, rows.size()))
                .allSatisfy(row -> assertThat(row).containsExactly(
                        row.getFirst(),
                        "2026-08-23T01:00:00Z"));
    }

    @Test
    void should_concealUnavailableSurveysBeforeCsvBodyCommit_when_locationIsNotOwnedAndActive()
            throws Exception {
        AuthenticatedSession owner = authenticateCreator("owner@example.test");
        AuthenticatedSession other = authenticateCreator("other@example.test");
        long foreignSurveyId = insertSurvey(owner.userId(), "foreign-export", "OPEN", false);
        long deletedSurveyId = insertSurvey(owner.userId(), "deleted-export", "CLOSED", true);

        ResultActions foreignActions = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/export.csv",
                        foreignSurveyId)
                        .cookie(other.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        MvcResult foreign = foreignActions
                .andDo(document(
                        "creator-response-csv-export-survey-not-found",
                        pathParameters(parameterWithName("surveyId")
                                .description("소유권을 확인할 내부 Survey 식별자")),
                        responseFields(errorResponseFields())))
                .andReturn();
        MvcResult unknown = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/export.csv",
                        999_999)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn();
        MvcResult deleted = mockMvc.perform(get(
                        "/api/surveys/{surveyId}/responses/export.csv",
                        deletedSurveyId)
                        .cookie(owner.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andReturn();

        assertThat(foreign.getResponse().getContentType()).startsWith("application/json");
        assertThat(countBom(foreign.getResponse().getContentAsByteArray())).isZero();
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(foreign.getResponse().getContentAsString())
                .isEqualTo(deleted.getResponse().getContentAsString());
    }

    @Test
    void should_requireCreatorAuthentication_when_csvExportIsRequested() throws Exception {
        mockMvc.perform(get("/api/surveys/{surveyId}/responses/export.csv", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    private AuthenticatedSession authenticateCreator(String email) throws Exception {
        User user = userRepository.saveAndFlush(User.createAdmin(
                email,
                passwordEncoder.encode(CREATOR_PASSWORD),
                "CSV Creator"));
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
        return new AuthenticatedSession(user.getId(), requireResponseCookie(loginResult));
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
                "CSV Export Survey",
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
                ) VALUES (?, ?, ?, false, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                type,
                title,
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

    private String hashPrefix(int index) {
        return Integer.toHexString(index % 16);
    }

    private byte[] csvBytes(String csvBody) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(UTF_8_BOM);
        output.writeBytes(csvBody.getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private int countBom(byte[] body) {
        int count = 0;
        for (int index = 0; index <= body.length - UTF_8_BOM.length; index++) {
            if (body[index] == UTF_8_BOM[0]
                    && body[index + 1] == UTF_8_BOM[1]
                    && body[index + 2] == UTF_8_BOM[2]) {
                count++;
            }
        }
        return count;
    }

    private List<List<String>> parseCsv(byte[] body) {
        assertThat(body).startsWith(UTF_8_BOM);
        String csv = new String(
                body,
                UTF_8_BOM.length,
                body.length - UTF_8_BOM.length,
                StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }
            if (current == '"' && field.isEmpty()) {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\r') {
                assertThat(index + 1).isLessThan(csv.length());
                assertThat(csv.charAt(index + 1)).isEqualTo('\n');
                row.add(field.toString());
                rows.add(List.copyOf(row));
                row.clear();
                field.setLength(0);
                index++;
            } else {
                assertThat(current).as("record separator must be CRLF").isNotEqualTo('\n');
                field.append(current);
            }
        }

        assertThat(quoted).isFalse();
        assertThat(field).hasToString("");
        assertThat(row).isEmpty();
        return List.copyOf(rows);
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

    private static org.springframework.restdocs.snippet.Snippet csvResponseHeaders() {
        return responseHeaders(
                headerWithName(HttpHeaders.CONTENT_TYPE)
                        .description("UTF-8로 인코딩한 CSV media type"),
                headerWithName(HttpHeaders.CONTENT_DISPOSITION)
                        .description("Survey ID를 포함하는 다운로드 filename"));
    }

    private static FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("기계적으로 처리할 수 있는 안정적인 오류 코드"),
            fieldWithPath("message").description("내부 정보를 노출하지 않는 안전한 오류 요약"),
            fieldWithPath("fieldErrors").description("필드 단위 오류 목록"),
            fieldWithPath("fieldErrors[].path")
                    .type(JsonFieldType.STRING)
                    .description("오류 request 경로")
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
