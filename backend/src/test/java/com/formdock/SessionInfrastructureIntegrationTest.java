package com.formdock;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
class SessionInfrastructureIntegrationTest {

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Test
    void should_executeCleanup_when_sessionSchemaIsMigrated() {
        assertThatCode(sessionRepository::cleanUpExpiredSessions)
                .doesNotThrowAnyException();
    }
}
