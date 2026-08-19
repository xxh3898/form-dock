package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formdock.auth.User;
import com.formdock.auth.UserRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class CreatorAuthenticationIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";
    private static final String CREATOR_EMAIL = "creator@example.com";
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
    private HikariDataSource dataSource;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void restoreDatabaseAndSessionTimeout() {
        sessionRepository.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }

    @Test
    void should_issueAnonymousCsrfToken_when_csrfEndpointIsRequested() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(SESSION_COOKIE))
                .andExpect(cookie().httpOnly(SESSION_COOKIE, true))
                .andExpect(cookie().secure(SESSION_COOKIE, true))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andDo(document(
                        "auth-csrf",
                        responseFields(
                                fieldWithPath("token").description("Request-specific CSRF token"),
                                fieldWithPath("headerName").description("Header used to submit the token"))));
    }

    @Test
    void should_authenticateRestoreAndLogoutCreator_when_credentialsAndCsrfAreValid() throws Exception {
        createCreator();
        CsrfSession anonymous = issueCsrf(null);

        MvcResult loginResult = login(
                        anonymous,
                        " Creator@Example.com ",
                        CREATOR_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(CREATOR_EMAIL))
                .andExpect(jsonPath("$.displayName").value("Creator"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andDo(document(
                        "auth-login",
                        requestFields(
                                fieldWithPath("email").description("Creator email; canonicalized server-side"),
                                fieldWithPath("password").description("Creator plaintext password")),
                        creatorResponseFields()))
                .andReturn();

        Cookie authenticatedCookie = requireResponseCookie(loginResult);
        assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymous.cookie().getValue());

        mockMvc.perform(get("/api/auth/me").cookie(authenticatedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CREATOR_EMAIL))
                .andDo(document("auth-me", creatorResponseFields()));

        assertThat(sessionRepository.findByPrincipalName(CREATOR_EMAIL)).hasSize(1);
        Integer securityContextAttributes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session_attributes WHERE attribute_name = ?",
                Integer.class,
                "SPRING_SECURITY_CONTEXT");
        assertThat(securityContextAttributes).isEqualTo(1);
        byte[] serializedSecurityContext = jdbcTemplate.queryForObject(
                "SELECT attribute_bytes FROM spring_session_attributes WHERE attribute_name = ?",
                byte[].class,
                "SPRING_SECURITY_CONTEXT");
        String storedPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?",
                String.class,
                CREATOR_EMAIL);
        assertThat(new String(serializedSecurityContext, StandardCharsets.ISO_8859_1))
                .doesNotContain(CREATOR_PASSWORD, storedPasswordHash);

        CsrfSession authenticated = issueCsrf(authenticatedCookie);
        assertThat(authenticated.token()).isNotEqualTo(anonymous.token());

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(authenticated.cookie())
                        .header(authenticated.headerName(), authenticated.token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andDo(document("auth-logout"))
                .andReturn();

        Cookie clearedCookie = logoutResult.getResponse().getCookie(SESSION_COOKIE);
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isZero();
        assertThat(sessionRepository.findByPrincipalName(CREATOR_EMAIL)).isEmpty();

        mockMvc.perform(get("/api/auth/me").cookie(authenticated.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void should_returnIdenticalCredentialError_when_emailIsUnknownOrPasswordIsWrong() throws Exception {
        createCreator();

        CsrfSession unknownSession = issueCsrf(null);
        MvcResult unknownEmail = login(
                        unknownSession,
                        "unknown@example.com",
                        "wrong-passphrase")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andDo(document("auth-login-invalid-credentials", errorResponseFields()))
                .andReturn();

        CsrfSession wrongPasswordSession = issueCsrf(null);
        MvcResult wrongPassword = login(
                        wrongPasswordSession,
                        CREATOR_EMAIL,
                        "wrong-passphrase")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    @Test
    void should_rejectLogin_when_csrfTokenIsMissingOrInvalid() throws Exception {
        createCreator();
        String requestBody = loginRequest(CREATOR_EMAIL, CREATOR_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andDo(document("auth-login-csrf-invalid", errorResponseFields()));

        CsrfSession csrfSession = issueCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfSession.cookie())
                        .header(csrfSession.headerName(), "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void should_rejectLogoutAndPreserveSession_when_csrfTokenIsMissingOrInvalid() throws Exception {
        createCreator();
        Cookie authenticatedCookie = authenticateCreator();

        mockMvc.perform(post("/api/auth/logout").cookie(authenticatedCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        CsrfSession csrfSession = issueCsrf(authenticatedCookie);
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrfSession.cookie())
                        .header(csrfSession.headerName(), "invalid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mockMvc.perform(get("/api/auth/me").cookie(authenticatedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CREATOR_EMAIL));
    }

    @Test
    void should_requireAuthentication_when_meOrLogoutIsAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andDo(document("auth-required", errorResponseFields()));

        CsrfSession csrfSession = issueCsrf(null);
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrfSession.cookie())
                        .header(csrfSession.headerName(), csrfSession.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void should_restoreJdbcBackedAuthentication_when_applicationContextRestarts() throws Exception {
        createCreator();
        Cookie authenticatedCookie = authenticateCreator();

        try (ConfigurableWebServerApplicationContext restarted =
                (ConfigurableWebServerApplicationContext) new SpringApplicationBuilder(
                        FormDockApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(
                                "server.port=0",
                                "spring.datasource.url=" + dataSource.getJdbcUrl(),
                                "spring.datasource.username=" + dataSource.getUsername(),
                                "spring.datasource.password=" + dataSource.getPassword(),
                                "spring.datasource.hikari.maximum-pool-size=2",
                                "spring.session.jdbc.cleanup-cron=-",
                                "formdock.bootstrap.enabled=false")
                        .run()) {
            int port = restarted.getWebServer().getPort();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                    .header("Cookie", SESSION_COOKIE + "=" + authenticatedCookie.getValue())
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"email\":\"" + CREATOR_EMAIL + "\"");
            assertThat(response.body()).doesNotContain("password", "session");
        }
    }

    @Test
    void should_rejectExpiredSession_when_configuredTimeoutHasElapsed() throws Exception {
        sessionRepository.setDefaultMaxInactiveInterval(Duration.ofSeconds(1));
        createCreator();
        Cookie authenticatedCookie = authenticateCreator();

        Map<?, ?> sessions = sessionRepository.findByPrincipalName(CREATOR_EMAIL);
        assertThat(sessions).hasSize(1);
        Session session = (Session) sessions.values().iterator().next();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(1));

        Long expiryTime = jdbcTemplate.queryForObject(
                "SELECT expiry_time FROM spring_session WHERE session_id = ?",
                Long.class,
                session.getId());
        long waitMillis = Math.max(0L, expiryTime - System.currentTimeMillis() + 250L);
        assertThat(waitMillis).isLessThan(3_000L);
        Thread.sleep(waitMillis);

        mockMvc.perform(get("/api/auth/me").cookie(authenticatedCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void should_notAllowCredentialedCrossOriginAccess_when_originIsArbitrary() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void should_protectFutureAdminMutationWithCsrf_when_endpointIsRequested() throws Exception {
        createCreator();
        Cookie authenticatedCookie = authenticateCreator();

        mockMvc.perform(post("/api/surveys").cookie(authenticatedCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private void createCreator() {
        userRepository.saveAndFlush(User.createAdmin(
                CREATOR_EMAIL,
                passwordEncoder.encode(CREATOR_PASSWORD),
                "Creator"));
    }

    private Cookie authenticateCreator() throws Exception {
        CsrfSession csrfSession = issueCsrf(null);
        return requireResponseCookie(login(
                        csrfSession,
                        CREATOR_EMAIL,
                        CREATOR_PASSWORD)
                .andExpect(status().isOk())
                .andReturn());
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

    private org.springframework.test.web.servlet.ResultActions login(
            CsrfSession csrfSession,
            String email,
            String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .cookie(csrfSession.cookie())
                .header(csrfSession.headerName(), csrfSession.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest(email, password)));
    }

    private String loginRequest(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password));
    }

    private Cookie requireResponseCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static org.springframework.restdocs.payload.ResponseFieldsSnippet creatorResponseFields() {
        return responseFields(
                fieldWithPath("id").description("Internal Creator identifier"),
                fieldWithPath("email").description("Canonical Creator email"),
                fieldWithPath("displayName").description("Creator display name"),
                fieldWithPath("role").description("Creator role; ADMIN in V1"));
    }

    private static org.springframework.restdocs.payload.ResponseFieldsSnippet errorResponseFields() {
        return responseFields(
                fieldWithPath("code").description("Stable machine-readable error code"),
                fieldWithPath("message").description("Safe error summary"),
                fieldWithPath("fieldErrors").description("Field errors; empty for authentication errors"));
    }

    private record CsrfSession(Cookie cookie, String headerName, String token) {
    }
}
