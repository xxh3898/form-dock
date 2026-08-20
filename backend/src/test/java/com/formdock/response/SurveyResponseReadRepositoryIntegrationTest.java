package com.formdock.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.formdock.PostgreSQLTestConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(PostgreSQLTestConfiguration.class)
@SpringBootTest
@Transactional
class SurveyResponseReadRepositoryIntegrationTest {

    @Autowired
    private SurveyResponseReadRepository responseReadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_readCanonicalCountExistenceAndGroupedCounts_when_responseFixturesExist() {
        Long ownerId = createOwner();
        Long firstSurveyId = createSurvey(ownerId, "first-response-survey");
        Long secondSurveyId = createSurvey(ownerId, "second-response-survey");
        Long emptySurveyId = createSurvey(ownerId, "empty-response-survey");
        insertResponse(firstSurveyId, "a");
        insertResponse(firstSurveyId, "b");
        insertResponse(secondSurveyId, "c");

        assertThat(responseReadRepository.countBySurveyId(firstSurveyId)).isEqualTo(2);
        assertThat(responseReadRepository.existsBySurveyId(firstSurveyId)).isTrue();
        assertThat(responseReadRepository.countBySurveyId(emptySurveyId)).isZero();
        assertThat(responseReadRepository.existsBySurveyId(emptySurveyId)).isFalse();
        assertThat(responseReadRepository.countBySurveyIds(
                List.of(firstSurveyId, secondSurveyId, emptySurveyId)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        firstSurveyId, 2L,
                        secondSurveyId, 1L));
        assertThat(responseReadRepository.countBySurveyIds(List.of())).isEmpty();
    }

    private Long createOwner() {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (
                    email, password_hash, display_name, role, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "response-reader@example.test",
                "{bcrypt}test-only-hash",
                "Response Reader",
                "ADMIN",
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private Long createSurvey(Long ownerId, String slug) {
        Instant now = Instant.now();
        return jdbcTemplate.queryForObject("""
                INSERT INTO surveys (
                    owner_id, title, slug, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerId,
                "Response Survey",
                slug,
                "DRAFT",
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private void insertResponse(Long surveyId, String hashPrefix) {
        jdbcTemplate.update("""
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (?, ?, ?, ?)
                """,
                surveyId,
                UUID.randomUUID(),
                hashPrefix.repeat(64),
                Timestamp.from(Instant.now()));
    }
}
