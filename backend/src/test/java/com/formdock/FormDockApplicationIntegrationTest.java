package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.formdock.auth.CreatorBootstrapProperties;
import com.formdock.web.SessionDependencyFailureFilter;

import java.sql.Connection;
import java.util.Arrays;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.testcontainers.postgresql.PostgreSQLContainer;

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

    @Autowired
    private CreatorBootstrapProperties bootstrapProperties;

    @Autowired
    private ObjectProvider<PostgreSQLContainer> postgresContainerProvider;

    @Autowired
    @Qualifier("sessionDependencyFailureFilter")
    private FilterRegistrationBean<SessionDependencyFailureFilter> sessionFailureFilterRegistration;

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
    void should_connectAndApplyRequiredMigrations_when_postgresqlIsAvailable() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        }

        assertThat(Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList())
                .containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void should_enableSessionCleanupWithoutAutoInitialization_when_sessionMigrationIsPresent() {
        assertThat(environment.getProperty("spring.session.jdbc.cleanup-cron"))
                .isEqualTo("0 * * * * *");
        assertThat(environment.getProperty("spring.session.jdbc.initialize-schema"))
                .isEqualTo("never");
    }

    @Test
    void should_keepCreatorBootstrapDisabled_when_environmentDoesNotEnableIt() {
        assertThat(bootstrapProperties.enabled()).isFalse();
    }

    @Test
    void should_requireAuthentication_when_endpointIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/scaffold-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void should_runPostgres18_6Alpine3_23Testcontainer_when_testcontainersAreEnabled() throws Exception {
        boolean testcontainersEnabled = environment.getProperty(
                "formdock.testcontainers.enabled",
                Boolean.class,
                true);
        PostgreSQLContainer postgresContainer = postgresContainerProvider.getIfAvailable();

        if (testcontainersEnabled) {
            assertThat(postgresContainer).isNotNull();
            assertThat(postgresContainer.isRunning()).isTrue();
            assertThat(postgresContainer.getDockerImageName())
                    .isEqualTo("postgres:18.6-alpine3.23");
        } else {
            assertThat(postgresContainer).isNull();
        }

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
        }
    }

    @Test
    void should_mapDependencyFailureBeforeSessionRepositoryFilter_when_applicationStarts() {
        assertThat(sessionFailureFilterRegistration.getOrder())
                .isLessThan(SessionRepositoryFilter.DEFAULT_ORDER);
    }
}
