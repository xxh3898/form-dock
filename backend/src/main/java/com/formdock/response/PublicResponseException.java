package com.formdock.response;

import java.util.List;

public final class PublicResponseException extends RuntimeException {

    public enum Kind {
        INVALID,
        NOT_FOUND,
        NOT_OPEN,
        DUPLICATE_CONFLICT,
        PAYLOAD_TOO_LARGE,
        UNSUPPORTED_MEDIA_TYPE,
        RATE_LIMITED,
        TEMPORARILY_UNAVAILABLE
    }

    public record Violation(String path, String code, String message) {
    }

    private final Kind kind;
    private final List<Violation> violations;

    private PublicResponseException(Kind kind, String message, List<Violation> violations) {
        super(message);
        this.kind = kind;
        this.violations = List.copyOf(violations);
    }

    static PublicResponseException invalid(List<Violation> violations) {
        return new PublicResponseException(
                Kind.INVALID,
                "응답 요청이 올바르지 않습니다.",
                violations);
    }

    static PublicResponseException notFound() {
        return new PublicResponseException(
                Kind.NOT_FOUND,
                "설문을 찾을 수 없습니다.",
                List.of());
    }

    static PublicResponseException notOpen() {
        return new PublicResponseException(
                Kind.NOT_OPEN,
                "이 설문은 새 응답을 받지 않습니다.",
                List.of());
    }

    static PublicResponseException duplicateConflict() {
        return new PublicResponseException(
                Kind.DUPLICATE_CONFLICT,
                "동일한 제출 식별자가 다른 응답 내용에 사용되었습니다.",
                List.of());
    }

    static PublicResponseException payloadTooLarge() {
        return new PublicResponseException(
                Kind.PAYLOAD_TOO_LARGE,
                "응답 요청 본문이 허용된 크기를 초과했습니다.",
                List.of());
    }

    static PublicResponseException unsupportedMediaType() {
        return new PublicResponseException(
                Kind.UNSUPPORTED_MEDIA_TYPE,
                "지원하지 않는 요청 형식입니다.",
                List.of());
    }

    static PublicResponseException rateLimited() {
        return new PublicResponseException(
                Kind.RATE_LIMITED,
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                List.of());
    }

    static PublicResponseException temporarilyUnavailable() {
        return new PublicResponseException(
                Kind.TEMPORARILY_UNAVAILABLE,
                "응답 제출을 일시적으로 처리할 수 없습니다.",
                List.of());
    }

    public Kind kind() {
        return kind;
    }

    public List<Violation> violations() {
        return violations;
    }
}
