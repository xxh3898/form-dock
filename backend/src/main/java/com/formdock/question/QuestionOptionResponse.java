package com.formdock.question;

public record QuestionOptionResponse(Long id, String label, int position) {

    static QuestionOptionResponse from(QuestionOption option) {
        return new QuestionOptionResponse(
                option.getId(),
                option.getLabel(),
                option.getPosition());
    }
}
