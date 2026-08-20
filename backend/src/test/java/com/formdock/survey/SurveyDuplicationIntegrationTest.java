package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import com.formdock.PostgreSQLTestConfiguration;
import com.formdock.auth.User;
import com.formdock.auth.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
class SurveyDuplicationIntegrationTest {

    private static final String TEST_PASSWORD_HASH = "{bcrypt}test-only-hash";

    @Autowired
    private SurveyService surveyService;

    @Autowired
    private SurveyRequestParser requestParser;

    @Autowired
    private UserRepository userRepository;

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
    void should_rollBackEntireDuplicate_when_questionCopyFailsAfterSurveyInsert() {
        Long ownerId = userRepository.saveAndFlush(User.createAdmin(
                "duplicate-owner@example.test",
                TEST_PASSWORD_HASH,
                "Duplicate Owner")).getId();
        SurveyDetailResponse source = surveyService.create(
                ownerId,
                requestParser.parseCreate(Map.of(
                        "title", "Atomic Duplicate",
                        "slug", "atomic-duplicate")));
        Instant now = Instant.now();
        Long questionId = jdbcTemplate.queryForObject("""
                INSERT INTO questions (
                    survey_id, type, title, required, position, created_at, updated_at
                ) VALUES (?, 'SINGLE_CHOICE', 'Invalid fixture', true, 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                source.id(),
                Timestamp.from(now),
                Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO question_options (question_id, label, position)
                VALUES (?, 'Only option', 0)
                """,
                questionId);

        assertThatThrownBy(() -> surveyService.duplicate(ownerId, source.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two options");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surveys WHERE owner_id = ?",
                Integer.class,
                ownerId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questions WHERE survey_id = ?",
                Integer.class,
                source.id())).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM surveys WHERE slug LIKE 'atomic-duplicate-%'",
                Integer.class)).isZero();
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM survey_responses");
        jdbcTemplate.update("DELETE FROM question_options");
        jdbcTemplate.update("DELETE FROM questions");
        jdbcTemplate.update("DELETE FROM surveys");
        jdbcTemplate.update("DELETE FROM spring_session");
        userRepository.deleteAllInBatch();
    }
}
