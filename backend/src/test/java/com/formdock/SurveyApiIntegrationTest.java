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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
                .get("id")
                .longValue();
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
            fieldWithPath("[].responseCount").description("Zero in Phase 2-A"),
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
            fieldWithPath("responseCount").description("Zero in Phase 2-A"),
            fieldWithPath("structureLocked").description("False in Phase 2-A"),
            fieldWithPath("questions").description("Empty in Phase 2-A")
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
