package com.formdock.response;

import java.time.Instant;
import java.util.List;

import com.formdock.question.QuestionType;
import com.formdock.survey.SurveyStatus;

public record CreatorResponseSummaryResponse(
        Long surveyId,
        SurveyStatus status,
        long totalResponses,
        Instant lastSubmittedAt,
        int questionCount,
        List<QuestionSummary> questions) {

    public CreatorResponseSummaryResponse {
        questions = List.copyOf(questions);
    }

    public sealed interface QuestionSummary
            permits CountQuestion, ChoiceQuestion, ScaleQuestion {

        Long questionId();

        QuestionType type();

        String title();

        int position();

        long answeredCount();
    }

    public record CountQuestion(
            Long questionId,
            QuestionType type,
            String title,
            int position,
            long answeredCount) implements QuestionSummary {
    }

    public record ChoiceQuestion(
            Long questionId,
            QuestionType type,
            String title,
            int position,
            long answeredCount,
            List<Option> options) implements QuestionSummary {

        public ChoiceQuestion {
            options = List.copyOf(options);
        }
    }

    public record ScaleQuestion(
            Long questionId,
            QuestionType type,
            String title,
            int position,
            long answeredCount,
            String average,
            List<ScaleBucket> distribution) implements QuestionSummary {

        public ScaleQuestion {
            distribution = List.copyOf(distribution);
        }
    }

    public record Option(
            Long optionId,
            String label,
            int position,
            long count,
            String percentage) {
    }

    public record ScaleBucket(int value, long count, String percentage) {
    }
}
