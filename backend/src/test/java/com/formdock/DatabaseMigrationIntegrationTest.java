package com.formdock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@Transactional
class DatabaseMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_createRequiredTables_when_cleanDatabaseIsMigrated() {
        assertThat(tableNames())
                .contains("flyway_schema_history", "users", "spring_session",
                        "spring_session_attributes");
    }

    @Test
    void should_createSpringSessionIndexesAndCascadeForeignKey_when_sessionMigrationRuns() {
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'spring_session'
                """, String.class);

        assertThat(indexes)
                .contains("spring_session_pk", "spring_session_ix1", "spring_session_ix2",
                        "spring_session_ix3");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name = 'spring_session_attributes_fk'
                """, String.class))
                .isEqualTo("CASCADE");
    }

    @Test
    void should_enforceAdminRole_when_usersRowIsInserted() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                "invalid-role@example.test",
                "{bcrypt}test-only-hash",
                "Invalid Role",
                "EDITOR",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);
    }
}
