package com.formdock.question;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_questions_survey_position",
                columnNames = {"survey_id", "position"}))
public class Question {

    private static final int TITLE_MAX_CODE_POINTS = 500;
    private static final int DESCRIPTION_MAX_CODE_POINTS = 2000;
    private static final int SCALE_LABEL_MAX_CODE_POINTS = 100;
    private static final int NUMBER_MAX_INTEGER_DIGITS = 15;
    private static final int NUMBER_MAX_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false, updatable = false)
    private Long surveyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionType type;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private int position;

    @Column(name = "scale_min")
    private Integer scaleMin;

    @Column(name = "scale_max")
    private Integer scaleMax;

    @Column(name = "scale_min_label", length = 100)
    private String scaleMinLabel;

    @Column(name = "scale_max_label", length = 100)
    private String scaleMaxLabel;

    @Column(name = "number_min", precision = 19, scale = 4)
    private BigDecimal numberMin;

    @Column(name = "number_max", precision = 19, scale = 4)
    private BigDecimal numberMax;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuestionOption> options = new ArrayList<>();

    protected Question() {
    }

    private Question(
            Long surveyId,
            QuestionType type,
            String title,
            String description,
            boolean required,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            BigDecimal numberMin,
            BigDecimal numberMax,
            List<String> optionLabels) {
        this.surveyId = requirePositiveId(surveyId);
        this.type = Objects.requireNonNull(type, "Question type is required");
        this.title = requireTrimmedText(title, TITLE_MAX_CODE_POINTS, "Question title");
        this.description = requireOptionalText(
                description,
                DESCRIPTION_MAX_CODE_POINTS,
                "Question description");
        this.required = required;
        this.position = requirePosition(position);
        this.scaleMin = scaleMin;
        this.scaleMax = scaleMax;
        this.scaleMinLabel = requireOptionalText(
                scaleMinLabel,
                SCALE_LABEL_MAX_CODE_POINTS,
                "Scale minimum label");
        this.scaleMaxLabel = requireOptionalText(
                scaleMaxLabel,
                SCALE_LABEL_MAX_CODE_POINTS,
                "Scale maximum label");
        this.numberMin = requireNumber(numberMin, "Number minimum");
        this.numberMax = requireNumber(numberMax, "Number maximum");

        List<String> labels = List.copyOf(Objects.requireNonNull(
                optionLabels,
                "Question options are required"));
        validateTypeConfiguration(labels);
        for (int optionPosition = 0; optionPosition < labels.size(); optionPosition++) {
            options.add(new QuestionOption(this, labels.get(optionPosition), optionPosition));
        }
    }

    static Question create(
            Long surveyId,
            QuestionType type,
            String title,
            String description,
            boolean required,
            int position,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            BigDecimal numberMin,
            BigDecimal numberMax,
            List<String> optionLabels) {
        return new Question(
                surveyId,
                type,
                title,
                description,
                required,
                position,
                scaleMin,
                scaleMax,
                scaleMinLabel,
                scaleMaxLabel,
                numberMin,
                numberMax,
                optionLabels);
    }

    static Question create(Long surveyId, int position, QuestionCommand command) {
        return new Question(
                surveyId,
                command.type(),
                command.title(),
                command.description(),
                command.required(),
                position,
                command.scaleMin(),
                command.scaleMax(),
                command.scaleMinLabel(),
                command.scaleMaxLabel(),
                command.numberMin(),
                command.numberMax(),
                command.optionLabels());
    }

    public Question deepCopyTo(Long targetSurveyId) {
        return new Question(
                targetSurveyId,
                type,
                title,
                description,
                required,
                position,
                scaleMin,
                scaleMax,
                scaleMinLabel,
                scaleMaxLabel,
                numberMin,
                numberMax,
                options.stream().map(QuestionOption::getLabel).toList());
    }

    void temporarilyOffsetOptionPositions(int offset) {
        if (offset <= options.size()) {
            throw new IllegalArgumentException("Option position offset must exceed option count");
        }
        options.forEach(option -> option.moveTo(option.getPosition() + offset));
    }

    void replaceSemanticState(QuestionCommand command) {
        Question normalized = create(surveyId, position, command);
        java.util.Map<Long, QuestionOption> existingById = options.stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestionOption::getId,
                        option -> option));
        List<QuestionOption> replacement = new ArrayList<>();
        for (int optionPosition = 0;
                optionPosition < command.options().size();
                optionPosition++) {
            QuestionOptionCommand optionCommand = command.options().get(optionPosition);
            QuestionOption option = optionCommand.id() == null
                    ? new QuestionOption(this, optionCommand.label(), optionPosition)
                    : existingById.get(optionCommand.id());
            if (option == null) {
                throw new IllegalArgumentException("Submitted Option does not belong to Question");
            }
            option.update(optionCommand.label(), optionPosition);
            replacement.add(option);
        }

        type = normalized.type;
        title = normalized.title;
        description = normalized.description;
        required = normalized.required;
        scaleMin = normalized.scaleMin;
        scaleMax = normalized.scaleMax;
        scaleMinLabel = normalized.scaleMinLabel;
        scaleMaxLabel = normalized.scaleMaxLabel;
        numberMin = normalized.numberMin;
        numberMax = normalized.numberMax;
        options.clear();
        options.addAll(replacement);
    }

    void moveToPosition(int targetPosition) {
        position = requirePosition(targetPosition);
    }

    public boolean hasCanonicalConfiguration() {
        try {
            Question normalized = new Question(
                    surveyId,
                    type,
                    title,
                    description,
                    required,
                    position,
                    scaleMin,
                    scaleMax,
                    scaleMinLabel,
                    scaleMaxLabel,
                    numberMin,
                    numberMax,
                    options.stream().map(QuestionOption::getLabel).toList());
            if (!Objects.equals(title, normalized.title)) {
                return false;
            }
            for (int optionPosition = 0; optionPosition < options.size(); optionPosition++) {
                QuestionOption option = options.get(optionPosition);
                if (option.getPosition() != optionPosition
                        || !option.getLabel().equals(option.getLabel().strip())) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateTypeConfiguration(List<String> optionLabels) {
        if (type.isChoice()) {
            requireUnusedScaleAndNumberFields();
            if (optionLabels.size() < 2) {
                throw new IllegalArgumentException("Choice Question requires at least two options");
            }
            return;
        }
        if (!optionLabels.isEmpty()) {
            throw new IllegalArgumentException("Only Choice Question may contain options");
        }

        switch (type) {
            case SHORT_TEXT, LONG_TEXT -> requireUnusedScaleAndNumberFields();
            case SCALE -> validateScaleConfiguration();
            case NUMBER -> validateNumberConfiguration();
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> throw new IllegalStateException(
                    "Choice configuration was not handled");
        }
    }

    private void requireUnusedScaleAndNumberFields() {
        if (scaleMin != null
                || scaleMax != null
                || scaleMinLabel != null
                || scaleMaxLabel != null
                || numberMin != null
                || numberMax != null) {
            throw new IllegalArgumentException("Question contains fields unused by its type");
        }
    }

    private void validateScaleConfiguration() {
        if (scaleMin == null || scaleMax == null) {
            throw new IllegalArgumentException("Scale Question requires minimum and maximum");
        }
        if (scaleMin < 1 || scaleMin >= scaleMax || scaleMax > 10) {
            throw new IllegalArgumentException("Scale range must satisfy 1 <= min < max <= 10");
        }
        if (numberMin != null || numberMax != null) {
            throw new IllegalArgumentException("Scale Question cannot contain number bounds");
        }
    }

    private void validateNumberConfiguration() {
        if (scaleMin != null
                || scaleMax != null
                || scaleMinLabel != null
                || scaleMaxLabel != null) {
            throw new IllegalArgumentException("Number Question cannot contain scale fields");
        }
        if (numberMin != null && numberMax != null && numberMin.compareTo(numberMax) > 0) {
            throw new IllegalArgumentException("Number minimum cannot exceed maximum");
        }
    }

    private static Long requirePositiveId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Survey identifier must be positive");
        }
        return value;
    }

    private static int requirePosition(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Question position cannot be negative");
        }
        return value;
    }

    private static String requireTrimmedText(String value, int maxCodePoints, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (codePointLength(normalized) > maxCodePoints) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static String requireOptionalText(String value, int maxCodePoints, String field) {
        if (value != null && codePointLength(value) > maxCodePoints) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return value;
    }

    private static BigDecimal requireNumber(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        int integerDigits = Math.max(0, value.precision() - value.scale());
        if (value.scale() > NUMBER_MAX_SCALE || integerDigits > NUMBER_MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(field + " must fit NUMERIC(19,4)");
        }
        return value;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    @PrePersist
    private void populateTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public QuestionType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public int getPosition() {
        return position;
    }

    public Integer getScaleMin() {
        return scaleMin;
    }

    public Integer getScaleMax() {
        return scaleMax;
    }

    public String getScaleMinLabel() {
        return scaleMinLabel;
    }

    public String getScaleMaxLabel() {
        return scaleMaxLabel;
    }

    public BigDecimal getNumberMin() {
        return numberMin;
    }

    public BigDecimal getNumberMax() {
        return numberMax;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<QuestionOption> getOptions() {
        return List.copyOf(options);
    }
}
