package com.formdock.survey;

import java.time.Instant;

public record SurveyListItemResponse(
        Long id,
        String title,
        SurveyStatus status,
        String slug,
        long responseCount,
        Instant updatedAt) {

    static SurveyListItemResponse from(Survey survey) {
        return new SurveyListItemResponse(
                survey.getId(),
                survey.getTitle(),
                survey.getStatus(),
                survey.getSlug(),
                0,
                survey.getUpdatedAt());
    }
}
