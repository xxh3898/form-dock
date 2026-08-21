package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SurveySlugPolicyTest {

    private static final String FIXED_SUFFIX = "abc1234567";

    private final SurveySlugPolicy slugPolicy = new SurveySlugPolicy(() -> FIXED_SUFFIX);

    @Test
    void should_normalizeAsciiTitle_when_initialGeneratedCandidateIsRequested() {
        assertThat(slugPolicy.generatedCandidate("  Project   Research!  ", 0))
                .isEqualTo("project-research");
    }

    @Test
    void should_useSurveyFallback_when_titleHasNoAsciiSlugCharacters() {
        assertThat(slugPolicy.generatedCandidate("프로젝트 설문", 0))
                .isEqualTo("survey-" + FIXED_SUFFIX);
    }

    @Test
    void should_appendSuffixWithinLimit_when_generatedBaseConflicts() {
        String slug = slugPolicy.generatedCandidate("a".repeat(200), 1);

        assertThat(slug)
                .hasSize(SurveySlugPolicy.MAX_LENGTH)
                .endsWith("-" + FIXED_SUFFIX)
                .matches("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    }

    @Test
    void should_preserveExplicitSlug_when_valueMatchesCanonicalContract() {
        assertThat(slugPolicy.validateExplicit("project-research-2"))
                .isEqualTo("project-research-2");
    }

    @Test
    void should_rejectExplicitSlug_when_valueWouldRequireNormalization() {
        assertThatThrownBy(() -> slugPolicy.validateExplicit(" Project-Research "))
                .isInstanceOf(SurveyException.class)
                .extracting(exception -> ((SurveyException) exception).kind())
                .isEqualTo(SurveyException.Kind.VALIDATION);
    }
}
