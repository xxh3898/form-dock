package com.formdock.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

class SessionDependencyFailureFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionDependencyFailureFilter filter =
            new SessionDependencyFailureFilter(new ApiErrorWriter(objectMapper));

    @Test
    void should_returnTemporarilyUnavailable_when_sessionDependencyFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            ((MockHttpServletResponse) servletResponse)
                    .addHeader("Set-Cookie", "SESSION=unusable-session-id");
            throw new DataAccessResourceFailureException("internal database detail");
        });

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"TEMPORARILY_UNAVAILABLE\"")
                .contains("\"fieldErrors\":[]")
                .doesNotContain("internal database detail");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
