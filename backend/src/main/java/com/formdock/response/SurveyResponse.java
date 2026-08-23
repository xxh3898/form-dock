package com.formdock.response;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record SurveyResponse(
        Long id,
        Long surveyId,
        UUID clientSubmissionId,
        String payloadHash,
        Instant submittedAt) {

    private static final Pattern SHA_256_LOWERCASE_HEX = Pattern.compile("[0-9a-f]{64}");

    public SurveyResponse {
        requirePositive(id, "SurveyResponse ID");
        requirePositive(surveyId, "Survey ID");
        Objects.requireNonNull(clientSubmissionId, "Client submission ID is required");
        Objects.requireNonNull(payloadHash, "Payload hash is required");
        Objects.requireNonNull(submittedAt, "Submission timestamp is required");
        if (!SHA_256_LOWERCASE_HEX.matcher(payloadHash).matches()) {
            throw new IllegalArgumentException("Payload hash must be SHA-256 lowercase hex");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
