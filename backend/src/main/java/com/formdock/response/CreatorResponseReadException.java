package com.formdock.response;

import java.util.List;

public final class CreatorResponseReadException extends RuntimeException {

    public enum Kind {
        VALIDATION,
        SURVEY_NOT_FOUND,
        RESPONSE_NOT_FOUND
    }

    public record Violation(String path, String code, String message) {
    }

    private final Kind kind;
    private final List<Violation> violations;

    private CreatorResponseReadException(
            Kind kind,
            String message,
            List<Violation> violations) {
        super(message);
        this.kind = kind;
        this.violations = List.copyOf(violations);
    }

    static CreatorResponseReadException validation(List<Violation> violations) {
        return new CreatorResponseReadException(
                Kind.VALIDATION,
                "Response list pagination validation failed.",
                violations);
    }

    public static CreatorResponseReadException surveyNotFound() {
        return new CreatorResponseReadException(
                Kind.SURVEY_NOT_FOUND,
                "Survey was not found.",
                List.of());
    }

    static CreatorResponseReadException responseNotFound() {
        return new CreatorResponseReadException(
                Kind.RESPONSE_NOT_FOUND,
                "Response was not found.",
                List.of());
    }

    public Kind kind() {
        return kind;
    }

    public List<Violation> violations() {
        return violations;
    }
}
