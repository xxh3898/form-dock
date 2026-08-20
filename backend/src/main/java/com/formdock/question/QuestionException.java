package com.formdock.question;

import java.util.List;

public final class QuestionException extends RuntimeException {

    public enum Kind {
        VALIDATION,
        INVALID_CONFIGURATION,
        NOT_FOUND
    }

    public record Violation(String path, String code, String message) {
    }

    private final Kind kind;
    private final List<Violation> violations;

    private QuestionException(Kind kind, String message, List<Violation> violations) {
        super(message);
        this.kind = kind;
        this.violations = List.copyOf(violations);
    }

    static QuestionException validation(List<Violation> violations) {
        return new QuestionException(
                Kind.VALIDATION,
                "Question request validation failed.",
                violations);
    }

    static QuestionException invalidConfiguration(List<Violation> violations) {
        return new QuestionException(
                Kind.INVALID_CONFIGURATION,
                "Question configuration is invalid.",
                violations);
    }

    static QuestionException notFound() {
        return new QuestionException(
                Kind.NOT_FOUND,
                "Question was not found.",
                List.of());
    }

    public Kind kind() {
        return kind;
    }

    public List<Violation> violations() {
        return violations;
    }
}
