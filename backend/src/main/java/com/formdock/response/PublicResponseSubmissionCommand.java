package com.formdock.response;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

record PublicResponseSubmissionCommand(
        UUID clientSubmissionId,
        List<SubmittedAnswer> answers) {

    PublicResponseSubmissionCommand {
        Objects.requireNonNull(clientSubmissionId, "Client submission ID is required");
        answers = List.copyOf(Objects.requireNonNull(answers, "Submitted answers are required"));
    }
}

record SubmittedAnswer(
        long questionId,
        String textValue,
        List<Long> optionIds,
        String numericValue) {

    SubmittedAnswer {
        if (questionId <= 0) {
            throw new IllegalArgumentException("Question ID must be positive");
        }
        optionIds = optionIds == null ? null : List.copyOf(optionIds);
    }
}
