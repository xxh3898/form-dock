package com.formdock.response;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreatorResponseSummaryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CreatorResponseSummaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Overview findOverviewBySurveyId(Long surveyId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*) AS total_responses,
                       max(submitted_at) AS last_submitted_at
                FROM survey_responses
                WHERE survey_id = :surveyId
                """,
                Map.of("surveyId", surveyId),
                (resultSet, rowNumber) -> {
                    Timestamp lastSubmittedAt = resultSet.getTimestamp("last_submitted_at");
                    return new Overview(
                            resultSet.getLong("total_responses"),
                            lastSubmittedAt == null ? null : lastSubmittedAt.toInstant());
                });
    }

    public Map<Long, Long> findAnsweredCountsBySurveyId(Long surveyId) {
        Map<Long, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT answer.question_id,
                       count(*) AS answered_count
                FROM survey_responses response
                JOIN answers answer ON answer.response_id = response.id
                JOIN questions question ON question.id = answer.question_id
                WHERE response.survey_id = :surveyId
                  AND question.survey_id = :surveyId
                GROUP BY answer.question_id
                """,
                Map.of("surveyId", surveyId),
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getLong("question_id"),
                        resultSet.getLong("answered_count")))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(counts);
    }

    public Map<ChoiceCountKey, Long> findChoiceCountsBySurveyId(Long surveyId) {
        Map<ChoiceCountKey, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT answer.question_id,
                       answer_option.option_id,
                       count(*) AS selected_count
                FROM survey_responses response
                JOIN answers answer ON answer.response_id = response.id
                JOIN questions question
                  ON question.id = answer.question_id
                 AND question.survey_id = response.survey_id
                JOIN answer_options answer_option ON answer_option.answer_id = answer.id
                JOIN question_options question_option
                  ON question_option.id = answer_option.option_id
                 AND question_option.question_id = question.id
                WHERE response.survey_id = :surveyId
                  AND question.type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE')
                GROUP BY answer.question_id, answer_option.option_id
                """,
                Map.of("surveyId", surveyId),
                (resultSet, rowNumber) -> Map.entry(
                        new ChoiceCountKey(
                                resultSet.getLong("question_id"),
                                resultSet.getLong("option_id")),
                        resultSet.getLong("selected_count")))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(counts);
    }

    public Map<Long, BigDecimal> findScaleAveragesBySurveyId(Long surveyId) {
        Map<Long, BigDecimal> averages = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT answer.question_id,
                       avg(answer.numeric_value) AS average_value
                FROM survey_responses response
                JOIN answers answer ON answer.response_id = response.id
                JOIN questions question
                  ON question.id = answer.question_id
                 AND question.survey_id = response.survey_id
                WHERE response.survey_id = :surveyId
                  AND question.type = 'SCALE'
                  AND answer.numeric_value IS NOT NULL
                GROUP BY answer.question_id
                """,
                Map.of("surveyId", surveyId),
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getLong("question_id"),
                        resultSet.getBigDecimal("average_value")))
                .forEach(entry -> averages.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(averages);
    }

    public Map<ScaleBucketKey, Long> findScaleDistributionBySurveyId(Long surveyId) {
        Map<ScaleBucketKey, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT answer.question_id,
                       answer.numeric_value,
                       count(*) AS bucket_count
                FROM survey_responses response
                JOIN answers answer ON answer.response_id = response.id
                JOIN questions question
                  ON question.id = answer.question_id
                 AND question.survey_id = response.survey_id
                WHERE response.survey_id = :surveyId
                  AND question.type = 'SCALE'
                  AND answer.numeric_value IS NOT NULL
                GROUP BY answer.question_id, answer.numeric_value
                """,
                Map.of("surveyId", surveyId),
                (resultSet, rowNumber) -> Map.entry(
                        new ScaleBucketKey(
                                resultSet.getLong("question_id"),
                                resultSet.getBigDecimal("numeric_value").intValueExact()),
                        resultSet.getLong("bucket_count")))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(counts);
    }

    public record Overview(long totalResponses, Instant lastSubmittedAt) {
    }

    public record ChoiceCountKey(Long questionId, Long optionId) {
    }

    public record ScaleBucketKey(Long questionId, int value) {
    }
}
