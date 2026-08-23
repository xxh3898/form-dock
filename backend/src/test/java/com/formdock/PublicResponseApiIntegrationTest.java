package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
@SpringBootTest(properties = "formdock.public-response.rate-limit.max-requests=10000")
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class PublicResponseApiIntegrationTest {

    private static final String TEST_PASSWORD = "test-only-public-response-passphrase";

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
    void should_createCanonicalAggregateAndReplaySameIdentity_when_allSixTypesAreValid()
            throws Exception {
        SixTypeFixture fixture = createSixTypeSurvey("OPEN", null);
        UUID submissionId = UUID.randomUUID();
        String request = allSixTypeRequest(fixture, submissionId, "  정확한 텍스트  ");

        MvcResult created = mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        fixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.responseId").isNumber())
                .andExpect(jsonPath("$.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.replayed").value(false))
                .andDo(document(
                        "public-responses-create",
                        pathParameters(parameterWithName("slug")
                                .description("응답을 제출할 Public Survey slug")),
                        requestFields(submissionRequestFields()),
                        responseFields(submissionResponseFields())))
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
        long responseId = createdBody.get("responseId").longValue();
        String submittedAt = createdBody.get("submittedAt").stringValue();

        mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        fixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equivalentSixTypeRequest(
                                fixture,
                                submissionId,
                                "  정확한 텍스트  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseId").value(responseId))
                .andExpect(jsonPath("$.submittedAt").value(submittedAt))
                .andExpect(jsonPath("$.replayed").value(true))
                .andDo(document(
                        "public-responses-replay",
                        pathParameters(parameterWithName("slug")
                                .description("기존 canonical Response가 속한 Public Survey slug")),
                        requestFields(submissionRequestFields()),
                        responseFields(submissionResponseFields())));

        assertThat(count("survey_responses")).isOne();
        assertThat(count("answers")).isEqualTo(5);
        assertThat(count("answer_options")).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT text_value FROM answers WHERE response_id = ? AND question_id = ?",
                String.class,
                responseId,
                fixture.shortTextId())).isEqualTo("  정확한 텍스트  ");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT numeric_value FROM answers WHERE response_id = ? AND question_id = ?",
                BigDecimal.class,
                responseId,
                fixture.numberId())).isEqualByComparingTo("7.5000");
    }

    @Test
    void should_returnDuplicateConflictWithoutChangingAggregate_when_sameIdentityPayloadDiffers()
            throws Exception {
        SixTypeFixture fixture = createSixTypeSurvey("OPEN", null);
        UUID submissionId = UUID.randomUUID();
        submitCreated(fixture.slug(), allSixTypeRequest(fixture, submissionId, "first"));

        mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        fixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allSixTypeRequest(fixture, submissionId, "different")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSE_DUPLICATE_CONFLICT"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andDo(document(
                        "public-responses-duplicate-conflict",
                        pathParameters(parameterWithName("slug")
                                .description("응답을 제출할 Public Survey slug")),
                        responseFields(errorResponseFields())));

        assertThat(count("survey_responses")).isOne();
        assertThat(count("answers")).isEqualTo(5);
    }

    @Test
    void should_applyConcealmentAndClosedReplayOrder_when_surveyIsUnavailableOrClosed()
            throws Exception {
        SixTypeFixture openFixture = createSixTypeSurvey("OPEN", null);
        UUID existingId = UUID.randomUUID();
        String existingPayload = allSixTypeRequest(openFixture, existingId, "closed replay");
        MvcResult created = submitCreated(openFixture.slug(), existingPayload);
        long responseId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("responseId")
                .longValue();
        jdbcTemplate.update(
                "UPDATE surveys SET status = 'CLOSED', closed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                openFixture.surveyId());

        mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        openFixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseId").value(responseId))
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        openFixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allSixTypeRequest(openFixture, existingId, "conflict")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSE_DUPLICATE_CONFLICT"));

        mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        openFixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allSixTypeRequest(openFixture, UUID.randomUUID(), "new")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_OPEN"))
                .andDo(document(
                        "public-responses-closed",
                        pathParameters(parameterWithName("slug")
                                .description("마감된 Public Survey slug")),
                        responseFields(errorResponseFields())));

        SixTypeFixture draft = createSixTypeSurvey("DRAFT", null);
        SixTypeFixture deleted = createSixTypeSurvey("CLOSED", Instant.now());
        String safeBody = unavailableBody(draft.slug(), allSixTypeRequest(
                draft, UUID.randomUUID(), "draft"), true);
        assertThat(unavailableBody(deleted.slug(), allSixTypeRequest(
                deleted, UUID.randomUUID(), "deleted"), false)).isEqualTo(safeBody);
        assertThat(unavailableBody("unknown-slug", emptyRequest(UUID.randomUUID()), false))
                .isEqualTo(safeBody);
    }

    @Test
    void should_rejectClosedNewIdentityBeforeSemanticValidation_when_questionIsUnknown()
            throws Exception {
        SixTypeFixture fixture = createSixTypeSurvey("CLOSED", null);

        mockMvc.perform(post(
                        "/api/public/surveys/{slug}/responses",
                        fixture.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                UUID.randomUUID(),
                                "{\"questionId\":999999,\"textValue\":\"x\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_OPEN"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertThat(count("survey_responses")).isZero();
        assertThat(count("answers")).isZero();
        assertThat(count("answer_options")).isZero();
    }

    @Test
    void should_rejectSemanticViolationsWithoutPartialAggregate_when_payloadIsInvalid()
            throws Exception {
        SixTypeFixture fixture = createSixTypeSurvey("OPEN", null);
        long foreignSurvey = insertSurvey(createOwner(), "foreign-options", "OPEN", null);
        long foreignQuestion = insertQuestion(
                foreignSurvey, "SINGLE_CHOICE", true, 0, null, null, null, null);
        long foreignOption = insertOption(foreignQuestion, "Foreign", 0);

        List<String> invalidRequests = List.of(
                emptyRequest(UUID.randomUUID()),
                request(UUID.randomUUID(), "{\"questionId\":999999,\"textValue\":\"x\"}"),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"optionIds\":[%d]}"
                        .formatted(fixture.singleChoiceId(), foreignOption)),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"textValue\":\"   \"}"
                        .formatted(fixture.shortTextId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"textValue\":\"%s\"}"
                        .formatted(fixture.shortTextId(), "a".repeat(501))),
                request(UUID.randomUUID(), "{\"questionId\":"
                        + fixture.shortTextId()
                        + ",\"textValue\":\""
                        + '\\'
                        + "uD800\"}"),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"optionIds\":[%d,%d]}"
                        .formatted(
                                fixture.singleChoiceId(),
                                fixture.singleFirstOptionId(),
                                fixture.singleSecondOptionId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"optionIds\":[]}"
                        .formatted(fixture.multipleChoiceId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"numericValue\":\"2.0\"}"
                        .formatted(fixture.scaleId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"numericValue\":\"11\"}"
                        .formatted(fixture.scaleId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"numericValue\":\"1e2\"}"
                        .formatted(fixture.numberId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"numericValue\":\"7.00000\"}"
                        .formatted(fixture.numberId())),
                request(UUID.randomUUID(), "{\"questionId\":%d,\"numericValue\":\"99\"}"
                        .formatted(fixture.numberId())));

        for (int index = 0; index < invalidRequests.size(); index++) {
            var actions = mockMvc.perform(post(
                            "/api/public/surveys/{slug}/responses",
                            fixture.slug())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequests.get(index)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RESPONSE_INVALID"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
            if (index == 0) {
                actions.andDo(document(
                        "public-responses-invalid",
                        pathParameters(parameterWithName("slug")
                                .description("응답을 제출할 Public Survey slug")),
                        responseFields(errorResponseFields())));
            }
        }

        assertThat(count("survey_responses")).isZero();
        assertThat(count("answers")).isZero();
        assertThat(count("answer_options")).isZero();
    }

    @Test
    void should_enforceTextAndNumericStorageBoundaries_when_valuesReachExactLimits()
            throws Exception {
        long ownerId = createOwner();
        String slug = "response-boundaries-" + UUID.randomUUID();
        long surveyId = insertSurvey(ownerId, slug, "OPEN", null);
        long shortTextId = insertQuestion(
                surveyId, "SHORT_TEXT", true, 0, null, null, null, null);
        long longTextId = insertQuestion(
                surveyId, "LONG_TEXT", true, 1, null, null, null, null);
        long numberId = insertQuestion(
                surveyId, "NUMBER", true, 2, null, null, null, null);

        mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(boundaryRequest(
                                UUID.randomUUID(),
                                shortTextId,
                                "a".repeat(500),
                                longTextId,
                                "나".repeat(5000),
                                numberId,
                                "999999999999999.9999")))
                .andExpect(status().isCreated());

        List<String> invalidRequests = List.of(
                boundaryRequest(
                        UUID.randomUUID(),
                        shortTextId,
                        "a".repeat(501),
                        longTextId,
                        "나".repeat(5000),
                        numberId,
                        "999999999999999.9999"),
                boundaryRequest(
                        UUID.randomUUID(),
                        shortTextId,
                        "a".repeat(500),
                        longTextId,
                        "나".repeat(5001),
                        numberId,
                        "999999999999999.9999"),
                boundaryRequest(
                        UUID.randomUUID(),
                        shortTextId,
                        "a".repeat(500),
                        longTextId,
                        "나".repeat(5000),
                        numberId,
                        "1000000000000000.0000"));

        for (String request : invalidRequests) {
            mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RESPONSE_INVALID"));
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM survey_responses WHERE survey_id = ?",
                Long.class,
                surveyId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM answers WHERE response_id IN "
                        + "(SELECT id FROM survey_responses WHERE survey_id = ?)",
                Long.class,
                surveyId)).isEqualTo(3);
    }

    @Test
    void should_enforceActualRawByteBoundaryAndMediaType_beforeProductWrite()
            throws Exception {
        long ownerId = createOwner();
        String slug = "transport-" + UUID.randomUUID();
        long surveyId = insertSurvey(ownerId, slug, "OPEN", null);
        insertQuestion(surveyId, "SHORT_TEXT", false, 0, null, null, null, null);

        byte[] exact = paddedJson(emptyRequest(UUID.randomUUID()), 1_048_576);
        mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                        .contentType("application/json;charset=UTF-8")
                        .content(exact))
                .andExpect(status().isCreated());

        byte[] oversized = paddedJson(emptyRequest(UUID.randomUUID()), 1_048_577);
        mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_LENGTH, "1")
                        .content(oversized))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("RESPONSE_PAYLOAD_TOO_LARGE"))
                .andDo(document(
                        "public-responses-payload-too-large",
                        pathParameters(parameterWithName("slug")
                                .description("응답을 제출할 Public Survey slug")),
                        responseFields(errorResponseFields())));

        mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(emptyRequest(UUID.randomUUID())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$").doesNotExist())
                .andDo(document(
                        "public-responses-unsupported-media-type",
                        pathParameters(parameterWithName("slug")
                                .description("응답을 제출할 Public Survey slug"))));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM survey_responses WHERE survey_id = ?",
                Long.class,
                surveyId)).isOne();
    }

    @Test
    void should_permitAndExemptCsrfOnlyForExactPublicResponsePost_when_anonymous()
            throws Exception {
        String body = emptyRequest(UUID.randomUUID());

        mockMvc.perform(post("/api/public/surveys/{slug}/responses", "unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));

        mockMvc.perform(post("/api/public/surveys/{slug}/responses/extra", "unknown")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(post("/api/public/surveys/{slug}/responses/extra", "unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private MvcResult submitCreated(String slug, String request) throws Exception {
        return mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String unavailableBody(String slug, String request, boolean documentResponse)
            throws Exception {
        var actions = mockMvc.perform(post("/api/public/surveys/{slug}/responses", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        if (documentResponse) {
            actions.andDo(document(
                    "public-responses-not-found",
                    pathParameters(parameterWithName("slug")
                            .description("제출할 수 없거나 존재하지 않는 Public Survey slug")),
                    responseFields(errorResponseFields())));
        }
        return actions.andReturn().getResponse().getContentAsString();
    }

    private SixTypeFixture createSixTypeSurvey(String status, Instant deletedAt) {
        long ownerId = createOwner();
        String slug = "response-" + UUID.randomUUID();
        long surveyId = insertSurvey(ownerId, slug, status, deletedAt);
        long shortTextId = insertQuestion(
                surveyId, "SHORT_TEXT", true, 0, null, null, null, null);
        long longTextId = insertQuestion(
                surveyId, "LONG_TEXT", false, 1, null, null, null, null);
        long singleChoiceId = insertQuestion(
                surveyId, "SINGLE_CHOICE", true, 2, null, null, null, null);
        long multiChoiceId = insertQuestion(
                surveyId, "MULTIPLE_CHOICE", true, 3, null, null, null, null);
        long scaleId = insertQuestion(
                surveyId, "SCALE", true, 4, 1, 10, null, null);
        long numberId = insertQuestion(
                surveyId, "NUMBER", true, 5, null, null, new BigDecimal("-10.0000"), new BigDecimal("10.0000"));
        long singleFirst = insertOption(singleChoiceId, "Single first", 0);
        long singleSecond = insertOption(singleChoiceId, "Single second", 1);
        long multiFirst = insertOption(multiChoiceId, "Multiple first", 0);
        long multiSecond = insertOption(multiChoiceId, "Multiple second", 1);
        return new SixTypeFixture(
                surveyId,
                slug,
                shortTextId,
                longTextId,
                singleChoiceId,
                multiChoiceId,
                scaleId,
                numberId,
                singleFirst,
                singleSecond,
                multiFirst,
                multiSecond);
    }

    private long createOwner() {
        String identity = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.createAdmin(
                        identity + "@public-response.test",
                        passwordEncoder.encode(TEST_PASSWORD),
                        "Public Response Owner"))
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
                    owner_id, title, slug, status, opened_at, closed_at,
                    deleted_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Public Response Survey",
                slug,
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
            boolean required,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            BigDecimal numberMin,
            BigDecimal numberMax) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, required, position,
                    scale_min, scale_max, number_min, number_max, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                surveyId,
                type,
                type + " response",
                required,
                position,
                scaleMin,
                scaleMax,
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

    private String allSixTypeRequest(
            SixTypeFixture fixture,
            UUID submissionId,
            String shortText) {
        return """
                {
                  "clientSubmissionId": "%s",
                  "answers": [
                    {"questionId": %d, "numericValue": "7.5000"},
                    {"questionId": %d, "optionIds": [%d, %d]},
                    {"questionId": %d, "textValue": "%s"},
                    {"questionId": %d, "numericValue": "7"},
                    {"questionId": %d, "optionIds": [%d]}
                  ]
                }
                """.formatted(
                submissionId,
                fixture.numberId(),
                fixture.multipleChoiceId(),
                fixture.multipleSecondOptionId(),
                fixture.multipleFirstOptionId(),
                fixture.shortTextId(),
                shortText,
                fixture.scaleId(),
                fixture.singleChoiceId(),
                fixture.singleFirstOptionId());
    }

    private String equivalentSixTypeRequest(
            SixTypeFixture fixture,
            UUID submissionId,
            String shortText) {
        return """
                {
                  "answers": [
                    {"questionId": %d, "optionIds": [%d]},
                    {"questionId": %d, "numericValue": "07"},
                    {"questionId": %d, "textValue": "%s"},
                    {"questionId": %d, "optionIds": [%d, %d]},
                    {"questionId": %d, "numericValue": "7.5"}
                  ],
                  "clientSubmissionId": "%s"
                }
                """.formatted(
                fixture.singleChoiceId(),
                fixture.singleFirstOptionId(),
                fixture.scaleId(),
                fixture.shortTextId(),
                shortText,
                fixture.multipleChoiceId(),
                fixture.multipleFirstOptionId(),
                fixture.multipleSecondOptionId(),
                fixture.numberId(),
                submissionId);
    }

    private String emptyRequest(UUID submissionId) {
        return request(submissionId, null);
    }

    private String request(UUID submissionId, String answer) {
        return "{\"clientSubmissionId\":\"%s\",\"answers\":[%s]}".formatted(
                submissionId,
                answer == null ? "" : answer);
    }

    private String boundaryRequest(
            UUID submissionId,
            long shortTextId,
            String shortText,
            long longTextId,
            String longText,
            long numberId,
            String number) {
        return """
                {
                  "clientSubmissionId": "%s",
                  "answers": [
                    {"questionId": %d, "textValue": "%s"},
                    {"questionId": %d, "textValue": "%s"},
                    {"questionId": %d, "numericValue": "%s"}
                  ]
                }
                """.formatted(
                submissionId,
                shortTextId,
                shortText,
                longTextId,
                longText,
                numberId,
                number);
    }

    private byte[] paddedJson(String json, int targetBytes) {
        byte[] source = json.getBytes(StandardCharsets.UTF_8);
        if (source.length > targetBytes) {
            throw new IllegalArgumentException("JSON source exceeds target size");
        }
        byte[] result = java.util.Arrays.copyOf(source, targetBytes);
        java.util.Arrays.fill(result, source.length, result.length, (byte) ' ');
        return result;
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
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

    private static FieldDescriptor[] submissionRequestFields() {
        return new FieldDescriptor[] {
            fieldWithPath("clientSubmissionId")
                    .description("재시도에도 유지하는 client-owned UUID"),
            fieldWithPath("answers").description("제출하는 semantic Answer 목록"),
            fieldWithPath("answers[].questionId")
                    .description("Public Survey Question 식별자"),
            fieldWithPath("answers[].textValue")
                    .description("Text Question의 exact 문자열 값")
                    .optional(),
            fieldWithPath("answers[].optionIds")
                    .description("Choice Question에서 선택한 Option 식별자 목록")
                    .optional(),
            fieldWithPath("answers[].numericValue")
                    .description("SCALE 또는 NUMBER의 10진수 문자열 값")
                    .optional()
        };
    }

    private static FieldDescriptor[] submissionResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("responseId").description("canonical SurveyResponse 식별자"),
            fieldWithPath("submittedAt").description("canonical 최초 제출 시각"),
            fieldWithPath("replayed").description("기존 canonical Response 재사용 여부")
        };
    }

    private static FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[] {
            fieldWithPath("code").description("기계적으로 처리할 수 있는 안정적인 오류 코드"),
            fieldWithPath("message").description("내부 정보를 노출하지 않는 안전한 오류 요약"),
            fieldWithPath("fieldErrors").description("필드 단위 오류 목록"),
            fieldWithPath("fieldErrors[].path")
                    .type(JsonFieldType.STRING)
                    .description("오류가 발생한 request field 경로")
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

    private record SixTypeFixture(
            long surveyId,
            String slug,
            long shortTextId,
            long longTextId,
            long singleChoiceId,
            long multipleChoiceId,
            long scaleId,
            long numberId,
            long singleFirstOptionId,
            long singleSecondOptionId,
            long multipleFirstOptionId,
            long multipleSecondOptionId) {
    }
}
