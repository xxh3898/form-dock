package com.formdock.response;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AnswerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnswerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertAll(
            Long responseId,
            List<CanonicalAnswer> semanticAnswers,
            Instant createdAt) {
        requirePositive(responseId, "SurveyResponse ID");
        List<CanonicalAnswer> answers = List.copyOf(Objects.requireNonNull(
                semanticAnswers,
                "Semantic answers are required"));
        Objects.requireNonNull(createdAt, "Answer creation timestamp is required");

        for (CanonicalAnswer answer : answers) {
            Long answerId = insertAnswer(responseId, answer, createdAt);
            if (answer instanceof CanonicalAnswer.OptionValues optionValues) {
                for (Long optionId : optionValues.optionIds().stream().sorted().toList()) {
                    jdbcTemplate.update(
                            """
                            INSERT INTO answer_options (answer_id, option_id)
                            VALUES (:answerId, :optionId)
                            """,
                            Map.of("answerId", answerId, "optionId", optionId));
                }
            }
        }
    }

    public List<PersistedAnswer> findAllByResponseId(Long responseId) {
        requirePositive(responseId, "SurveyResponse ID");
        List<AnswerRow> rows = jdbcTemplate.query(
                """
                SELECT id, response_id, question_id, text_value, numeric_value, created_at
                FROM answers
                WHERE response_id = :responseId
                ORDER BY question_id ASC
                """,
                Map.of("responseId", responseId),
                this::mapAnswerRow);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Long>> optionIdsByAnswer = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT answer_id, option_id
                FROM answer_options
                WHERE answer_id IN (:answerIds)
                ORDER BY answer_id ASC, option_id ASC
                """,
                new MapSqlParameterSource(
                        "answerIds",
                        rows.stream().map(AnswerRow::id).toList()),
                (resultSet, rowNumber) -> new AnswerOptionRow(
                        resultSet.getLong("answer_id"),
                        resultSet.getLong("option_id")))
                .forEach(row -> optionIdsByAnswer
                        .computeIfAbsent(row.answerId(), ignored -> new ArrayList<>())
                        .add(row.optionId()));

        return rows.stream()
                .map(row -> new PersistedAnswer(
                        row.id(),
                        row.responseId(),
                        row.questionId(),
                        row.textValue(),
                        row.numericValue(),
                        row.createdAt(),
                        optionIdsByAnswer.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private Long insertAnswer(Long responseId, CanonicalAnswer answer, Instant createdAt) {
        String textValue = null;
        BigDecimal numericValue = null;
        if (answer instanceof CanonicalAnswer.TextValue value) {
            textValue = value.value();
        } else if (answer instanceof CanonicalAnswer.ScaleValue value) {
            numericValue = BigDecimal.valueOf(value.value());
        } else if (answer instanceof CanonicalAnswer.NumberValue value) {
            numericValue = value.value();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("responseId", responseId)
                .addValue("questionId", answer.questionId())
                .addValue("textValue", textValue)
                .addValue("numericValue", numericValue)
                .addValue("createdAt", Timestamp.from(createdAt));
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO answers (
                    response_id, question_id, text_value, numeric_value, created_at
                ) VALUES (
                    :responseId, :questionId, :textValue, :numericValue, :createdAt
                )
                RETURNING id
                """,
                parameters,
                Long.class);
    }

    private AnswerRow mapAnswerRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AnswerRow(
                resultSet.getLong("id"),
                resultSet.getLong("response_id"),
                resultSet.getLong("question_id"),
                resultSet.getString("text_value"),
                resultSet.getBigDecimal("numeric_value"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private record AnswerRow(
            Long id,
            Long responseId,
            Long questionId,
            String textValue,
            BigDecimal numericValue,
            Instant createdAt) {
    }

    private record AnswerOptionRow(Long answerId, Long optionId) {
    }
}
