package com.formdock.response;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SurveyResponseRepository {

    private static final Pattern SHA_256_LOWERCASE_HEX = Pattern.compile("[0-9a-f]{64}");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SurveyResponseRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SurveyResponseResolution createOrResolve(
            Long surveyId,
            UUID clientSubmissionId,
            String payloadHash,
            Instant submittedAt) {
        requirePositive(surveyId, "Survey ID");
        Objects.requireNonNull(clientSubmissionId, "Client submission ID is required");
        requirePayloadHash(payloadHash);
        Objects.requireNonNull(submittedAt, "Submission timestamp is required");

        Map<String, Object> parameters = Map.of(
                "surveyId", surveyId,
                "clientSubmissionId", clientSubmissionId,
                "payloadHash", payloadHash,
                "submittedAt", Timestamp.from(submittedAt));
        List<SurveyResponse> inserted = jdbcTemplate.query(
                """
                INSERT INTO survey_responses (
                    survey_id, client_submission_id, payload_hash, submitted_at
                ) VALUES (
                    :surveyId, :clientSubmissionId, :payloadHash, :submittedAt
                )
                ON CONFLICT ON CONSTRAINT uk_survey_responses_survey_submission DO NOTHING
                RETURNING id, survey_id, client_submission_id, payload_hash, submitted_at
                """,
                parameters,
                this::mapResponse);
        if (!inserted.isEmpty()) {
            return new SurveyResponseResolution(
                    SurveyResponseResolution.Outcome.CREATED,
                    inserted.getFirst());
        }

        SurveyResponse existing = findBySurveyIdAndClientSubmissionId(surveyId, clientSubmissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical SurveyResponse was not visible after identity conflict"));
        SurveyResponseResolution.Outcome outcome = existing.payloadHash().equals(payloadHash)
                ? SurveyResponseResolution.Outcome.EXISTING_SAME_PAYLOAD
                : SurveyResponseResolution.Outcome.EXISTING_DIFFERENT_PAYLOAD;
        return new SurveyResponseResolution(outcome, existing);
    }

    public Optional<SurveyResponse> findBySurveyIdAndClientSubmissionId(
            Long surveyId,
            UUID clientSubmissionId) {
        requirePositive(surveyId, "Survey ID");
        Objects.requireNonNull(clientSubmissionId, "Client submission ID is required");
        return jdbcTemplate.query(
                        """
                        SELECT id, survey_id, client_submission_id, payload_hash, submitted_at
                        FROM survey_responses
                        WHERE survey_id = :surveyId
                          AND client_submission_id = :clientSubmissionId
                        """,
                        Map.of(
                                "surveyId", surveyId,
                                "clientSubmissionId", clientSubmissionId),
                        this::mapResponse)
                .stream()
                .findFirst();
    }

    private SurveyResponse mapResponse(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SurveyResponse(
                resultSet.getLong("id"),
                resultSet.getLong("survey_id"),
                resultSet.getObject("client_submission_id", UUID.class),
                resultSet.getString("payload_hash"),
                resultSet.getTimestamp("submitted_at").toInstant());
    }

    private void requirePayloadHash(String payloadHash) {
        Objects.requireNonNull(payloadHash, "Payload hash is required");
        if (!SHA_256_LOWERCASE_HEX.matcher(payloadHash).matches()) {
            throw new IllegalArgumentException("Payload hash must be SHA-256 lowercase hex");
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
