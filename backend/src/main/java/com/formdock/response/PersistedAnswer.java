package com.formdock.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PersistedAnswer(
        Long id,
        Long responseId,
        Long questionId,
        String textValue,
        BigDecimal numericValue,
        Instant createdAt,
        List<Long> optionIds) {

    public PersistedAnswer {
        requirePositive(id, "Answer ID");
        requirePositive(responseId, "SurveyResponse ID");
        requirePositive(questionId, "Question ID");
        Objects.requireNonNull(createdAt, "Answer creation timestamp is required");
        optionIds = List.copyOf(Objects.requireNonNull(optionIds, "Answer Option IDs are required"));
        if (textValue != null && numericValue != null) {
            throw new IllegalArgumentException("Answer cannot contain both text and numeric values");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
