package com.formdock.auth;

import java.util.Locale;

public final class CreatorEmail {

    static final int MAX_CODE_POINTS = 320;

    private CreatorEmail() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Creator email is required");
        }

        String normalized = value.strip().toLowerCase(Locale.ROOT);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints == 0) {
            throw new IllegalArgumentException("Creator email must not be blank");
        }
        if (codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Creator email must not exceed 320 characters");
        }
        return normalized;
    }
}
