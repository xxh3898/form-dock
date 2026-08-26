package com.formdock.export;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CreatorResponseCsvRowRepository {

    static final int FETCH_SIZE = 256;

    private static final String EXPORT_QUERY = """
            SELECT
                response.id AS response_id,
                response.submitted_at,
                answer.question_id,
                answer.text_value,
                answer.numeric_value,
                selected_option.option_id
            FROM survey_responses response
            LEFT JOIN answers answer
                ON answer.response_id = response.id
            LEFT JOIN answer_options selected_option
                ON selected_option.answer_id = answer.id
            WHERE response.survey_id = ?
            ORDER BY
                response.submitted_at ASC,
                response.id ASC,
                answer.question_id ASC NULLS LAST,
                selected_option.option_id ASC NULLS LAST
            """;

    private final JdbcTemplate jdbcTemplate;

    CreatorResponseCsvRowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void streamBySurveyId(Long surveyId, Consumer<Row> consumer) {
        Objects.requireNonNull(surveyId, "Survey identifier is required");
        Objects.requireNonNull(consumer, "CSV row consumer is required");
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    EXPORT_QUERY,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY)) {
                statement.setLong(1, surveyId);
                statement.setFetchDirection(ResultSet.FETCH_FORWARD);
                statement.setFetchSize(FETCH_SIZE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        consumer.accept(mapRow(resultSet));
                    }
                }
            }
            return null;
        });
    }

    private Row mapRow(ResultSet resultSet) throws SQLException {
        return new Row(
                resultSet.getLong("response_id"),
                resultSet.getTimestamp("submitted_at").toInstant(),
                nullableLong(resultSet, "question_id"),
                resultSet.getString("text_value"),
                resultSet.getBigDecimal("numeric_value"),
                nullableLong(resultSet, "option_id"));
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    record Row(
            long responseId,
            Instant submittedAt,
            Long questionId,
            String textValue,
            BigDecimal numericValue,
            Long optionId) {
    }
}
