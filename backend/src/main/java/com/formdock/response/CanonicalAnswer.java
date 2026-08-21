package com.formdock.response;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public sealed interface CanonicalAnswer permits CanonicalAnswer.TextValue,
        CanonicalAnswer.OptionValues,
        CanonicalAnswer.ScaleValue,
        CanonicalAnswer.NumberValue {

    long questionId();

    record TextValue(long questionId, String value) implements CanonicalAnswer {

        public TextValue {
            requirePositiveQuestionId(questionId);
            Objects.requireNonNull(value, "Text value is required");
        }
    }

    record OptionValues(long questionId, List<Long> optionIds) implements CanonicalAnswer {

        public OptionValues {
            requirePositiveQuestionId(questionId);
            optionIds = List.copyOf(Objects.requireNonNull(optionIds, "Option IDs are required"));
            if (optionIds.isEmpty()) {
                throw new IllegalArgumentException("Option IDs are required");
            }
            if (optionIds.stream().anyMatch(optionId -> optionId == null || optionId <= 0)) {
                throw new IllegalArgumentException("Option IDs must be positive");
            }
            if (new HashSet<>(optionIds).size() != optionIds.size()) {
                throw new IllegalArgumentException("Option IDs must be distinct");
            }
        }
    }

    record ScaleValue(long questionId, int value) implements CanonicalAnswer {

        public ScaleValue {
            requirePositiveQuestionId(questionId);
        }
    }

    record NumberValue(long questionId, BigDecimal value) implements CanonicalAnswer {

        public NumberValue {
            requirePositiveQuestionId(questionId);
            Objects.requireNonNull(value, "Number value is required");
        }
    }

    private static void requirePositiveQuestionId(long questionId) {
        if (questionId <= 0) {
            throw new IllegalArgumentException("Question ID must be positive");
        }
    }
}
