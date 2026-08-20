package com.formdock.auth;

public final class CreatorDisplayName {

    static final int MAX_CODE_POINTS = 100;

    private CreatorDisplayName() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Creator display name is required");
        }

        String normalized = value.strip();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints == 0) {
            throw new IllegalArgumentException("Creator display name must not be blank");
        }
        if (codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Creator display name must not exceed 100 characters");
        }
        return normalized;
    }
}
