package com.formdock.survey;

import java.time.Instant;
import java.util.List;

import com.formdock.question.QuestionResponse;

public record SurveyDetailResponse(
        Long id,
        String title,
        String description,
        String slug,
        String privacyNotice,
        SurveyStatus status,
        Instant openedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        long responseCount,
        boolean structureLocked,
        List<QuestionResponse> questions) {

    static SurveyDetailResponse from(
            Survey survey,
            List<QuestionResponse> questions,
            long responseCount,
            boolean structureLocked) {
        return new SurveyDetailResponse(
                survey.getId(),
                survey.getTitle(),
                survey.getDescription(),
                survey.getSlug(),
                survey.getPrivacyNotice(),
                survey.getStatus(),
                survey.getOpenedAt(),
                survey.getClosedAt(),
                survey.getCreatedAt(),
                survey.getUpdatedAt(),
                responseCount,
                structureLocked,
                List.copyOf(questions));
    }
}
