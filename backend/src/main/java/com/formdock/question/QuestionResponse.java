package com.formdock.question;

import java.math.BigDecimal;
import java.util.List;

public record QuestionResponse(
        Long id,
        QuestionType type,
        String title,
        String description,
        boolean required,
        int position,
        Integer scaleMin,
        Integer scaleMax,
        String scaleMinLabel,
        String scaleMaxLabel,
        String numberMin,
        String numberMax,
        List<QuestionOptionResponse> options) {

    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getType(),
                question.getTitle(),
                question.getDescription(),
                question.isRequired(),
                question.getPosition(),
                question.getScaleMin(),
                question.getScaleMax(),
                question.getScaleMinLabel(),
                question.getScaleMaxLabel(),
                decimalString(question.getNumberMin()),
                decimalString(question.getNumberMax()),
                question.getOptions().stream()
                        .map(QuestionOptionResponse::from)
                        .toList());
    }

    private static String decimalString(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }
}
