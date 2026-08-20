package com.formdock.survey;

import java.util.List;

public final class SurveyException extends RuntimeException {

    public enum Kind {
        VALIDATION,
        NOT_FOUND,
        SLUG_CONFLICT,
        SLUG_IMMUTABLE,
        DELETE_REQUIRES_CLOSED,
        STRUCTURE_LOCKED,
        TEMPORARILY_UNAVAILABLE
    }

    public record Violation(String path, String code, String message) {
    }

    private final Kind kind;
    private final List<Violation> violations;

    private SurveyException(Kind kind, String message, List<Violation> violations) {
        super(message);
        this.kind = kind;
        this.violations = List.copyOf(violations);
    }

    static SurveyException validation(List<Violation> violations) {
        return new SurveyException(
                Kind.VALIDATION,
                "Survey request validation failed.",
                violations);
    }

    static SurveyException notFound() {
        return new SurveyException(
                Kind.NOT_FOUND,
                "Survey was not found.",
                List.of());
    }

    static SurveyException slugConflict() {
        return new SurveyException(
                Kind.SLUG_CONFLICT,
                "Survey slug is already reserved.",
                List.of());
    }

    static SurveyException slugImmutable() {
        return new SurveyException(
                Kind.SLUG_IMMUTABLE,
                "Survey slug cannot be changed after the first open.",
                List.of());
    }

    static SurveyException deleteRequiresClosed() {
        return new SurveyException(
                Kind.DELETE_REQUIRES_CLOSED,
                "Open Survey must be closed before deletion.",
                List.of());
    }

    static SurveyException structureLocked() {
        return new SurveyException(
                Kind.STRUCTURE_LOCKED,
                "Survey structure cannot change after a response exists.",
                List.of());
    }

    static SurveyException temporarilyUnavailable() {
        return new SurveyException(
                Kind.TEMPORARILY_UNAVAILABLE,
                "Survey service is temporarily unavailable.",
                List.of());
    }

    public Kind kind() {
        return kind;
    }

    public List<Violation> violations() {
        return violations;
    }
}
