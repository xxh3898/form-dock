package com.formdock.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "question_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_options_question_position",
                columnNames = {"question_id", "position"}))
public class QuestionOption {

    private static final int LABEL_MAX_CODE_POINTS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private Question question;

    @Column(nullable = false, length = 500)
    private String label;

    @Column(nullable = false)
    private int position;

    protected QuestionOption() {
    }

    QuestionOption(Question question, String label, int position) {
        if (question == null) {
            throw new IllegalArgumentException("QuestionOption parent is required");
        }
        if (label == null) {
            throw new IllegalArgumentException("QuestionOption label is required");
        }
        String normalized = label.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("QuestionOption label must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > LABEL_MAX_CODE_POINTS) {
            throw new IllegalArgumentException("QuestionOption label is too long");
        }
        if (position < 0) {
            throw new IllegalArgumentException("QuestionOption position cannot be negative");
        }
        this.question = question;
        this.label = normalized;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getPosition() {
        return position;
    }
}
