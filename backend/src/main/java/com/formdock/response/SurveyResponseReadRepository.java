package com.formdock.response;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyResponseReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SurveyResponseReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countBySurveyId(Long surveyId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM survey_responses WHERE survey_id = :surveyId",
                Map.of("surveyId", surveyId),
                Long.class);
        return count == null ? 0 : count;
    }

    public boolean existsBySurveyId(Long surveyId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM survey_responses WHERE survey_id = :surveyId)",
                Map.of("surveyId", surveyId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Map<Long, Long> countBySurveyIds(Collection<Long> surveyIds) {
        if (surveyIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT survey_id, count(*) AS response_count
                FROM survey_responses
                WHERE survey_id IN (:surveyIds)
                GROUP BY survey_id
                """,
                new MapSqlParameterSource("surveyIds", surveyIds),
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getLong("survey_id"),
                        resultSet.getLong("response_count")))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(counts);
    }
}
