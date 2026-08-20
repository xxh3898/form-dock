package com.formdock.survey;

import java.time.Instant;
import java.util.List;

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
        List<Object> questions) {

    static SurveyDetailResponse from(Survey survey) {
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
                0,
                false,
                List.of());
    }
}
