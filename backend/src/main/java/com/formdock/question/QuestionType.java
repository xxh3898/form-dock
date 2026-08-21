package com.formdock.question;

public enum QuestionType {
    SHORT_TEXT,
    LONG_TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    SCALE,
    NUMBER;

    boolean isChoice() {
        return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE;
    }
}
