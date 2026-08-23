package com.formdock.response;

import java.time.Instant;
import java.util.List;

import com.formdock.question.QuestionType;

public record CreatorResponseDetailResponse(
        Long responseId,
        Instant submittedAt,
        List<Question> questions) {

    public CreatorResponseDetailResponse {
        questions = List.copyOf(questions);
    }

    public record Question(
            Long questionId,
            QuestionType type,
            String title,
            String description,
            boolean required,
            int position,
            Answer answer) {
    }

    public record Answer(
            String textValue,
            String numericValue,
            List<Option> options) {

        public Answer {
            options = List.copyOf(options);
        }
    }

    public record Option(Long id, String label, int position) {
    }
}
