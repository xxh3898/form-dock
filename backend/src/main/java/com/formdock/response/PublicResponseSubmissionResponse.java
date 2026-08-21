package com.formdock.response;

import java.time.Instant;

public record PublicResponseSubmissionResponse(
        Long responseId,
        Instant submittedAt,
        boolean replayed) {

    static PublicResponseSubmissionResponse created(SurveyResponse response) {
        return from(response, false);
    }

    static PublicResponseSubmissionResponse replayed(SurveyResponse response) {
        return from(response, true);
    }

    private static PublicResponseSubmissionResponse from(
            SurveyResponse response,
            boolean replayed) {
        return new PublicResponseSubmissionResponse(
                response.id(),
                response.submittedAt(),
                replayed);
    }
}
