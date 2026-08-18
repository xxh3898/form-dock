package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FormDockApplicationIntegrationTest {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    void should_loadApplicationContext_when_postgresqlIsAvailable() {
        assertThat(applicationContext.isActive()).isTrue();
    }

    @Test
    void should_reportDatabaseUp_when_healthEndpointIsRequested() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void should_connectWithoutBusinessMigrations_when_postgresqlIsAvailable() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        }

        assertThat(flyway.info().all()).isEmpty();
    }

    @Test
    void should_disableSessionCleanup_when_sessionMigrationIsNotPresent() {
        assertThat(environment.getProperty("spring.session.jdbc.cleanup-cron")).isEqualTo("-");
    }

    @Test
    void should_denyRequest_when_endpointIsNotScaffoldHealth() throws Exception {
        mockMvc.perform(get("/api/scaffold-probe"))
                .andExpect(status().isForbidden());
    }
}
