package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest(properties = {
    "formdock.public-response.rate-limit.max-requests=1",
    "formdock.public-response.rate-limit.window=10m",
    "formdock.public-response.rate-limit.max-identities=16"
})
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class PublicResponseRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_ignoreForwardedHeadersAndReturn429_when_sameObservedPeerExceedsLimit()
            throws Exception {
        String body = "{\"clientSubmissionId\":\"%s\",\"answers\":[]}".formatted(
                UUID.randomUUID());

        mockMvc.perform(post("/api/public/surveys/{slug}/responses", "unknown-rate-survey")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.40");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.1")
                        .header("Forwarded", "for=203.0.113.1")
                        .header("CF-Connecting-IP", "203.0.113.1")
                        .header("True-Client-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));

        mockMvc.perform(post("/api/public/surveys/{slug}/responses", "unknown-rate-survey")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.40");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.2")
                        .header("Forwarded", "for=203.0.113.2")
                        .header("CF-Connecting-IP", "203.0.113.2")
                        .header("True-Client-IP", "203.0.113.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andDo(document(
                        "public-responses-rate-limited",
                        pathParameters(parameterWithName("slug")
                                .description("응답을 제출할 Public Survey slug")),
                        responseFields(
                                fieldWithPath("code").description("RATE_LIMITED 오류 코드"),
                                fieldWithPath("message").description("안전한 재시도 안내"),
                                fieldWithPath("fieldErrors").description("빈 필드 오류 목록"))));

        mockMvc.perform(post("/api/public/surveys/{slug}/responses", "unknown-rate-survey")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.41");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM survey_responses",
                Long.class)).isZero();
    }
}
