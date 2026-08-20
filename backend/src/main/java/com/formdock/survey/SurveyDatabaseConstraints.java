package com.formdock.survey;

import org.hibernate.exception.ConstraintViolationException;

final class SurveyDatabaseConstraints {

    static final String UNIQUE_SLUG = "uk_surveys_slug";

    private SurveyDatabaseConstraints() {
    }

    static boolean isUniqueSlugViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && UNIQUE_SLUG.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
