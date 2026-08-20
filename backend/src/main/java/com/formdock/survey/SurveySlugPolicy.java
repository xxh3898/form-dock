package com.formdock.survey;

import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SurveySlugPolicy {

    static final int MIN_LENGTH = 3;
    static final int MAX_LENGTH = 64;
    private static final int SUFFIX_LENGTH = 10;
    private static final Pattern VALID_SLUG =
            Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final Supplier<String> suffixSupplier;

    public SurveySlugPolicy() {
        this(() -> UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, SUFFIX_LENGTH));
    }

    SurveySlugPolicy(Supplier<String> suffixSupplier) {
        this.suffixSupplier = suffixSupplier;
    }

    String generatedCandidate(String title, int attempt) {
        String base = normalizeTitle(title);
        if (attempt == 0 && base.length() >= MIN_LENGTH) {
            return base;
        }
        return appendSuffix(base.length() >= MIN_LENGTH ? base : "survey", suffixSupplier.get());
    }

    String validateExplicit(String slug) {
        if (slug.length() < MIN_LENGTH
                || slug.length() > MAX_LENGTH
                || !VALID_SLUG.matcher(slug).matches()) {
            throw SurveyException.validation(List.of(new SurveyException.Violation(
                    "slug",
                    "INVALID_FORMAT",
                    "Slug must be 3 to 64 lowercase letters, numbers, or single hyphens.")));
        }
        return slug;
    }

    boolean isCanonical(String slug) {
        return slug != null
                && slug.length() >= MIN_LENGTH
                && slug.length() <= MAX_LENGTH
                && VALID_SLUG.matcher(slug).matches();
    }

    private String normalizeTitle(String title) {
        String normalized = title
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.length() <= MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LENGTH).replaceAll("-+$", "");
    }

    private String appendSuffix(String base, String suffix) {
        int prefixLength = Math.min(base.length(), MAX_LENGTH - suffix.length() - 1);
        String prefix = base.substring(0, prefixLength).replaceAll("-+$", "");
        return prefix + "-" + suffix;
    }
}
