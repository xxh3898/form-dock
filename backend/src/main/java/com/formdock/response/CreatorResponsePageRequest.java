package com.formdock.response;

import java.util.ArrayList;
import java.util.List;

record CreatorResponsePageRequest(long page, int size, long offset) {

    static final String DEFAULT_PAGE = "0";
    static final String DEFAULT_SIZE = "50";
    static final int MAX_SIZE = 100;

    static CreatorResponsePageRequest parse(String rawPage, String rawSize) {
        List<CreatorResponseReadException.Violation> violations = new ArrayList<>();
        Long page = parseLong(rawPage, "page", violations);
        Integer size = parseInteger(rawSize, "size", violations);

        if (page != null && page < 0) {
            violations.add(new CreatorResponseReadException.Violation(
                    "page",
                    "OUT_OF_RANGE",
                    "Page must be zero or greater."));
        }
        if (size != null && (size < 1 || size > MAX_SIZE)) {
            violations.add(new CreatorResponseReadException.Violation(
                    "size",
                    "OUT_OF_RANGE",
                    "Size must be between 1 and 100."));
        }

        long offset = 0;
        if (violations.isEmpty()) {
            try {
                offset = Math.multiplyExact(page, (long) size);
            } catch (ArithmeticException exception) {
                violations.add(new CreatorResponseReadException.Violation(
                        "page",
                        "OUT_OF_RANGE",
                        "Page is too large."));
            }
        }
        if (!violations.isEmpty()) {
            throw CreatorResponseReadException.validation(violations);
        }
        return new CreatorResponsePageRequest(page, size, offset);
    }

    private static Long parseLong(
            String value,
            String path,
            List<CreatorResponseReadException.Violation> violations) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            violations.add(new CreatorResponseReadException.Violation(
                    path,
                    "INVALID_FORMAT",
                    "Page must be an integer."));
            return null;
        }
    }

    private static Integer parseInteger(
            String value,
            String path,
            List<CreatorResponseReadException.Violation> violations) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            violations.add(new CreatorResponseReadException.Violation(
                    path,
                    "INVALID_FORMAT",
                    "Size must be an integer."));
            return null;
        }
    }
}
