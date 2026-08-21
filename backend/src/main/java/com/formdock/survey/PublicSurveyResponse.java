package com.formdock.survey;

import java.math.BigDecimal;
import java.util.List;

import com.formdock.question.Question;
import com.formdock.question.QuestionOption;
import com.formdock.question.QuestionType;

public record PublicSurveyResponse(
        String slug,
        String title,
        String description,
        String privacyNotice,
        List<PublicQuestionResponse> questions) {

    static PublicSurveyResponse from(Survey survey, List<Question> questions) {
        return new PublicSurveyResponse(
                survey.getSlug(),
                survey.getTitle(),
                survey.getDescription(),
                survey.getPrivacyNotice(),
                questions.stream()
                        .map(PublicQuestionResponse::from)
                        .toList());
    }

    public record PublicQuestionResponse(
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
            List<PublicQuestionOptionResponse> options) {

        static PublicQuestionResponse from(Question question) {
            return new PublicQuestionResponse(
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
                            .map(PublicQuestionOptionResponse::from)
                            .toList());
        }
    }

    public record PublicQuestionOptionResponse(Long id, String label, int position) {

        static PublicQuestionOptionResponse from(QuestionOption option) {
            return new PublicQuestionOptionResponse(
                    option.getId(),
                    option.getLabel(),
                    option.getPosition());
        }
    }

    private static String decimalString(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }
}
