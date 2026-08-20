package com.formdock.survey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class SurveyRequestParser {

    private static final Set<String> CREATE_FIELDS =
            Set.of("title", "description", "privacyNotice", "slug");
    private static final Set<String> PATCH_FIELDS = CREATE_FIELDS;
    private static final int TITLE_MAX_CODE_POINTS = 200;
    private static final int OPTIONAL_TEXT_MAX_CODE_POINTS = 5000;

    private final SurveySlugPolicy slugPolicy;

    public SurveyRequestParser(SurveySlugPolicy slugPolicy) {
        this.slugPolicy = slugPolicy;
    }

    SurveyCreateCommand parseCreate(Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        List<SurveyException.Violation> violations = new ArrayList<>();
        validateUnknownFields(request, CREATE_FIELDS, violations);

        String title = requiredTitle(request, violations);
        String description = nullableText(
                request,
                "description",
                OPTIONAL_TEXT_MAX_CODE_POINTS,
                violations);
        String privacyNotice = nullableText(
                request,
                "privacyNotice",
                OPTIONAL_TEXT_MAX_CODE_POINTS,
                violations);
        String slug = nullableExplicitSlug(request, violations);

        throwIfInvalid(violations);
        return new SurveyCreateCommand(title, description, privacyNotice, slug);
    }

    SurveyPatchCommand parsePatch(Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        List<SurveyException.Violation> violations = new ArrayList<>();
        validateUnknownFields(request, PATCH_FIELDS, violations);

        boolean titlePresent = request.containsKey("title");
        boolean descriptionPresent = request.containsKey("description");
        boolean privacyNoticePresent = request.containsKey("privacyNotice");
        boolean slugPresent = request.containsKey("slug");

        if (!titlePresent && !descriptionPresent && !privacyNoticePresent && !slugPresent) {
            violations.add(new SurveyException.Violation(
                    "body",
                    "REQUIRED",
                    "At least one supported field is required."));
        }

        String title = titlePresent ? requiredTitle(request, violations) : null;
        String description = descriptionPresent
                ? nullableText(request, "description", OPTIONAL_TEXT_MAX_CODE_POINTS, violations)
                : null;
        String privacyNotice = privacyNoticePresent
                ? nullableText(request, "privacyNotice", OPTIONAL_TEXT_MAX_CODE_POINTS, violations)
                : null;
        String slug = slugPresent ? requiredExplicitSlug(request, violations) : null;

        throwIfInvalid(violations);
        return new SurveyPatchCommand(
                titlePresent,
                title,
                descriptionPresent,
                description,
                privacyNoticePresent,
                privacyNotice,
                slugPresent,
                slug);
    }

    private String requiredTitle(
            Map<String, Object> request,
            List<SurveyException.Violation> violations) {
        if (!request.containsKey("title") || request.get("title") == null) {
            violations.add(new SurveyException.Violation(
                    "title", "REQUIRED", "Title is required."));
            return null;
        }
        Object rawTitle = request.get("title");
        if (!(rawTitle instanceof String title)) {
            violations.add(new SurveyException.Violation(
                    "title", "INVALID_TYPE", "Title must be a string."));
            return null;
        }

        String normalized = title.strip();
        int codePoints = codePointLength(normalized);
        if (codePoints == 0) {
            violations.add(new SurveyException.Violation(
                    "title", "REQUIRED", "Title must not be blank."));
        } else if (codePoints > TITLE_MAX_CODE_POINTS) {
            violations.add(new SurveyException.Violation(
                    "title", "TOO_LONG", "Title must not exceed 200 characters."));
        }
        return normalized;
    }

    private String nullableText(
            Map<String, Object> request,
            String field,
            int maxCodePoints,
            List<SurveyException.Violation> violations) {
        Object value = request.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            violations.add(new SurveyException.Violation(
                    field, "INVALID_TYPE", field + " must be a string or null."));
            return null;
        }
        if (codePointLength(text) > maxCodePoints) {
            violations.add(new SurveyException.Violation(
                    field,
                    "TOO_LONG",
                    field + " must not exceed " + maxCodePoints + " characters."));
        }
        return text;
    }

    private String nullableExplicitSlug(
            Map<String, Object> request,
            List<SurveyException.Violation> violations) {
        if (!request.containsKey("slug") || request.get("slug") == null) {
            return null;
        }
        return explicitSlugValue(request.get("slug"), violations);
    }

    private String requiredExplicitSlug(
            Map<String, Object> request,
            List<SurveyException.Violation> violations) {
        Object value = request.get("slug");
        if (value == null) {
            violations.add(new SurveyException.Violation(
                    "slug", "REQUIRED", "Slug must not be null when supplied."));
            return null;
        }
        return explicitSlugValue(value, violations);
    }

    private String explicitSlugValue(
            Object value,
            List<SurveyException.Violation> violations) {
        if (!(value instanceof String slug)) {
            violations.add(new SurveyException.Violation(
                    "slug", "INVALID_TYPE", "Slug must be a string."));
            return null;
        }
        try {
            return slugPolicy.validateExplicit(slug);
        } catch (SurveyException exception) {
            violations.addAll(exception.violations());
            return null;
        }
    }

    private void validateUnknownFields(
            Map<String, Object> request,
            Set<String> allowed,
            List<SurveyException.Violation> violations) {
        Set<String> unknown = new LinkedHashSet<>(request.keySet());
        unknown.removeAll(allowed);
        unknown.forEach(field -> violations.add(new SurveyException.Violation(
                field, "UNKNOWN_FIELD", "Field is not supported.")));
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private void throwIfInvalid(List<SurveyException.Violation> violations) {
        if (!violations.isEmpty()) {
            throw SurveyException.validation(violations);
        }
    }
}

record SurveyCreateCommand(
        String title,
        String description,
        String privacyNotice,
        String slug) {
}

record SurveyPatchCommand(
        boolean titlePresent,
        String title,
        boolean descriptionPresent,
        String description,
        boolean privacyNoticePresent,
        String privacyNotice,
        boolean slugPresent,
        String slug) {
}
