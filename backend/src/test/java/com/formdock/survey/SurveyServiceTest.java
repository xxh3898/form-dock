package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private SurveyCreationAttempt creationAttempt;

    @Test
    void should_failSafely_when_generatedSlugRetriesAreExhausted() {
        SurveySlugPolicy slugPolicy = new SurveySlugPolicy(() -> "abc1234567");
        SurveyService surveyService = new SurveyService(
                surveyRepository,
                creationAttempt,
                slugPolicy);
        SurveyCreateCommand command = new SurveyCreateCommand(
                "Project Research",
                null,
                null,
                null);
        when(creationAttempt.create(eq(1L), same(command), anyString()))
                .thenThrow(new SurveySlugCollisionException());

        assertThatThrownBy(() -> surveyService.create(1L, command))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> {
                    SurveyException exception = (SurveyException) failure;
                    assertThat(exception.kind())
                            .isEqualTo(SurveyException.Kind.TEMPORARILY_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain(
                            SurveyDatabaseConstraints.UNIQUE_SLUG,
                            "constraint",
                            "SQL");
                });
        verify(creationAttempt, times(SurveyService.MAX_GENERATED_SLUG_ATTEMPTS))
                .create(eq(1L), same(command), anyString());
    }
}
