package com.formdock.response;

import java.util.Objects;

public record SurveyResponseResolution(Outcome outcome, SurveyResponse response) {

    public SurveyResponseResolution {
        Objects.requireNonNull(outcome, "Response resolution outcome is required");
        Objects.requireNonNull(response, "Canonical SurveyResponse is required");
    }

    public enum Outcome {
        CREATED,
        EXISTING_SAME_PAYLOAD,
        EXISTING_DIFFERENT_PAYLOAD
    }
}
