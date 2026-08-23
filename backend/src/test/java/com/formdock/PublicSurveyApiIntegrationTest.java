package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.formdock.auth.User;
import com.formdock.auth.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class PublicSurveyApiIntegrationTest {

    private static final String TEST_PASSWORD = "test-only-public-survey-passphrase";

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
    void should_returnRespondentSafeOrderedSurvey_when_openSurveyIsRequestedAnonymously()
            throws Exception {
        long ownerId = createOwner();
        long surveyId = insertSurvey(ownerId, "public-six-types", "OPEN", null);

        long numberId = insertQuestion(
                surveyId,
                "NUMBER",
                "Number question",
                null,
                true,
                5,
                null,
                null,
                null,
                null,
                new BigDecimal("-1.2500"),
                new BigDecimal("10.0000"));
        long longTextId = insertQuestion(
                surveyId,
                "LONG_TEXT",
                "Long text question",
                "Long description",
                false,
                1,
                null,
                null,
                null,
                null,
                null,
                null);
        long multipleChoiceId = insertQuestion(
                surveyId,
                "MULTIPLE_CHOICE",
                "Multiple choice question",
                null,
                true,
                3,
                null,
                null,
                null,
                null,
                null,
                null);
        long shortTextId = insertQuestion(
                surveyId,
                "SHORT_TEXT",
                "Short text question",
                "Short description",
                true,
                0,
                null,
                null,
                null,
                null,
                null,
                null);
        long scaleId = insertQuestion(
                surveyId,
                "SCALE",
                "Scale question",
                null,
                false,
                4,
                1,
                5,
                "Low",
                "High",
                null,
                null);
        long singleChoiceId = insertQuestion(
                surveyId,
                "SINGLE_CHOICE",
                "Single choice question",
                null,
                true,
                2,
                null,
                null,
                null,
                null,
                null,
                null);

        long singleSecondOptionId = insertOption(singleChoiceId, "Single second", 1);
        long singleFirstOptionId = insertOption(singleChoiceId, "Single first", 0);
        long multipleSecondOptionId = insertOption(multipleChoiceId, "Multiple second", 1);
        long multipleFirstOptionId = insertOption(multipleChoiceId, "Multiple first", 0);

        MvcResult result = mockMvc.perform(get("/api/public/surveys/{slug}", "public-six-types")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .andExpect(jsonPath("$.slug").value("public-six-types"))
                .andExpect(jsonPath("$.title").value("Public Survey"))
                .andExpect(jsonPath("$.description").value("Respondent introduction"))
                .andExpect(jsonPath("$.privacyNotice").value("Respondent privacy notice"))
                .andExpect(jsonPath("$.questions.length()").value(6))
                .andExpect(jsonPath("$.questions[0].id").value(shortTextId))
                .andExpect(jsonPath("$.questions[0].type").value("SHORT_TEXT"))
                .andExpect(jsonPath("$.questions[0].position").value(0))
                .andExpect(jsonPath("$.questions[0].options").isEmpty())
                .andExpect(jsonPath("$.questions[1].id").value(longTextId))
                .andExpect(jsonPath("$.questions[1].type").value("LONG_TEXT"))
                .andExpect(jsonPath("$.questions[2].id").value(singleChoiceId))
                .andExpect(jsonPath("$.questions[2].type").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.questions[2].options[0].id")
                        .value(singleFirstOptionId))
                .andExpect(jsonPath("$.questions[2].options[0].label")
                        .value("Single first"))
                .andExpect(jsonPath("$.questions[2].options[0].position").value(0))
                .andExpect(jsonPath("$.questions[2].options[1].id")
                        .value(singleSecondOptionId))
                .andExpect(jsonPath("$.questions[3].id").value(multipleChoiceId))
                .andExpect(jsonPath("$.questions[3].type").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.questions[3].options[0].id")
                        .value(multipleFirstOptionId))
                .andExpect(jsonPath("$.questions[3].options[1].id")
                        .value(multipleSecondOptionId))
                .andExpect(jsonPath("$.questions[4].id").value(scaleId))
                .andExpect(jsonPath("$.questions[4].type").value("SCALE"))
                .andExpect(jsonPath("$.questions[4].scaleMin").value(1))
                .andExpect(jsonPath("$.questions[4].scaleMax").value(5))
                .andExpect(jsonPath("$.questions[4].scaleMinLabel").value("Low"))
                .andExpect(jsonPath("$.questions[4].scaleMaxLabel").value("High"))
                .andExpect(jsonPath("$.questions[4].options").isEmpty())
                .andExpect(jsonPath("$.questions[5].id").value(numberId))
                .andExpect(jsonPath("$.questions[5].type").value("NUMBER"))
                .andExpect(jsonPath("$.questions[5].numberMin").value("-1.25"))
                .andExpect(jsonPath("$.questions[5].numberMax").value("10"))
                .andExpect(jsonPath("$.questions[5].options").isEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.openedAt").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.responseCount").doesNotExist())
                .andExpect(jsonPath("$.structureLocked").doesNotExist())
                .andDo(document(
                        "public-surveys-get",
                        pathParameters(parameterWithName("slug")
                                .description("Public Survey를 식별하는 slug")),
                        responseFields(publicSurveyResponseFields())))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.has("id")).isFalse();
        assertThat(body.has("ownerId")).isFalse();
        assertThat(body.has("responseCount")).isFalse();
        assertThat(body.has("structureLocked")).isFalse();
        for (JsonNode question : body.get("questions")) {
            assertThat(question.size()).isEqualTo(13);
            assertThat(question.has("surveyId")).isFalse();
            assertThat(question.has("createdAt")).isFalse();
            assertThat(question.has("updatedAt")).isFalse();
            for (JsonNode option : question.get("options")) {
                assertThat(option.size()).isEqualTo(3);
            }
        }
        JsonNode shortText = body.get("questions").get(0);
        assertThat(shortText.has("scaleMin")).isTrue();
        assertThat(shortText.get("scaleMin").isNull()).isTrue();
        assertThat(shortText.has("numberMax")).isTrue();
        assertThat(shortText.get("numberMax").isNull()).isTrue();
    }

    @Test
    void should_returnIdenticalNotFound_when_surveyIsUnavailable() throws Exception {
        long ownerId = createOwner();
        insertSurvey(ownerId, "draft-survey", "DRAFT", null);
        insertSurvey(ownerId, "closed-survey", "CLOSED", null);
        insertSurvey(ownerId, "deleted-survey", "CLOSED", Instant.now());

        MvcResult documented = unavailableSurvey("draft-survey", true);
        String safeBody = documented.getResponse().getContentAsString();

        for (String slug : List.of("closed-survey", "deleted-survey", "unknown-survey")) {
            MvcResult result = unavailableSurvey(slug, false);
            assertThat(result.getResponse().getContentAsString()).isEqualTo(safeBody);
        }
    }

    @Test
    void should_permitOnlyExactPublicSurveyGet_when_requestsAreAnonymous() throws Exception {
        long ownerId = createOwner();
        insertSurvey(ownerId, "exact-public", "OPEN", null);

        mockMvc.perform(get("/api/public/surveys/{slug}", "exact-public"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/surveys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/public/surveys/"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/public/surveys/exact-public/extra"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/surveys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(post("/api/public/surveys/{slug}", "exact-public"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private MvcResult unavailableSurvey(String slug, boolean documentResponse) throws Exception {
        var actions = mockMvc.perform(get("/api/public/surveys/{slug}", slug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Survey was not found."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        if (documentResponse) {
            actions.andDo(document(
                    "public-surveys-not-found",
                    pathParameters(parameterWithName("slug")
                            .description("조회할 수 없거나 존재하지 않는 Public Survey slug")),
                    responseFields(errorResponseFields())));
        }
        return actions.andReturn();
    }

    private long createOwner() {
        return userRepository.saveAndFlush(User.createAdmin(
                        "public-survey-owner@example.test",
                        passwordEncoder.encode(TEST_PASSWORD),
                        "Public Survey Owner"))
                .getId();
    }

    private long insertSurvey(
            long ownerId,
            String slug,
            String status,
            Instant deletedAt) {
        Instant now = Instant.now();
        Instant openedAt = status.equals("DRAFT") ? null : now;
        Instant closedAt = status.equals("CLOSED") ? now : null;
        return jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, description, slug, privacy_notice, status,
                    opened_at, closed_at, deleted_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Public Survey",
                "Respondent introduction",
                slug,
                "Respondent privacy notice",
                status,
                timestamp(openedAt),
                timestamp(closedAt),
                timestamp(deletedAt),
                Timestamp.from(now),
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
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            BigDecimal numberMin,
            BigDecimal numberMax) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, description, required, position,
                    scale_min, scale_max, scale_min_label, scale_max_label,
                    number_min, number_max, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                scaleMinLabel,
                scaleMaxLabel,
                numberMin,
                numberMax,
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

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }

    private static FieldDescriptor[] publicSurveyResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("slug").description("Public Survey를 식별하는 slug"),
            fieldWithPath("title").description("Survey 제목"),
            fieldWithPath("description").description("Respondent에게 표시하는 선택적 안내 문구"),
            fieldWithPath("privacyNotice").description("선택적 개인정보 처리 안내"),
            fieldWithPath("questions")
                    .description("표시 순서대로 정렬한 Respondent 노출용 Question 목록"),
            fieldWithPath("questions[].id").description("Response 제출에 사용하는 Question 식별자"),
            fieldWithPath("questions[].type").description("지원하는 여섯 가지 Question type 중 하나"),
            fieldWithPath("questions[].title").description("Question 제목"),
            fieldWithPath("questions[].description")
                    .description("선택적 Question 설명")
                    .optional(),
            fieldWithPath("questions[].required").description("Answer 필수 여부"),
            fieldWithPath("questions[].position").description("0부터 시작하는 표시 순서"),
            fieldWithPath("questions[].scaleMin")
                    .description("SCALE 최솟값 또는 null")
                    .optional(),
            fieldWithPath("questions[].scaleMax")
                    .description("SCALE 최댓값 또는 null")
                    .optional(),
            fieldWithPath("questions[].scaleMinLabel")
                    .description("SCALE 최솟값 레이블 또는 null")
                    .optional(),
            fieldWithPath("questions[].scaleMaxLabel")
                    .description("SCALE 최댓값 레이블 또는 null")
                    .optional(),
            fieldWithPath("questions[].numberMin")
                    .description("NUMBER 최솟값의 10진수 문자열 또는 null")
                    .optional(),
            fieldWithPath("questions[].numberMax")
                    .description("NUMBER 최댓값의 10진수 문자열 또는 null")
                    .optional(),
            fieldWithPath("questions[].options")
                    .description("표시 순서대로 정렬한 Choice Option 목록이며 Choice가 아니면 빈 배열"),
            fieldWithPath("questions[].options[].id")
                    .description("Response 제출에 사용하는 Option 식별자"),
            fieldWithPath("questions[].options[].label").description("Option 문구"),
            fieldWithPath("questions[].options[].position")
                    .description("0부터 시작하는 Option 순서")
        };
    }

    private static FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("기계적으로 처리할 수 있는 안정적인 오류 코드"),
            fieldWithPath("message").description("안전한 오류 요약"),
            fieldWithPath("fieldErrors")
                    .description("필드 오류 목록이며 조회할 수 없는 Survey에서는 빈 배열")
        };
    }
}
