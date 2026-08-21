package com.formdock.response;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.formdock.question.Question;
import com.formdock.question.QuestionOption;

import org.springframework.stereotype.Component;

@Component
public class PublicResponseValidator {

    private static final Pattern INTEGER_STRING = Pattern.compile("^-?[0-9]+$");
    private static final Pattern DECIMAL_STRING = Pattern.compile("^-?[0-9]+(?:\\.[0-9]+)?$");
    private static final int SHORT_TEXT_MAX_CODE_POINTS = 500;
    private static final int LONG_TEXT_MAX_CODE_POINTS = 5000;
    private static final int NUMBER_MAX_INTEGER_DIGITS = 15;
    private static final int NUMBER_MAX_SCALE = 4;

    List<CanonicalAnswer> validateAnswers(
            List<Question> questions,
            List<SubmittedAnswer> submittedAnswers) {
        Map<Long, Question> questionsById = new HashMap<>();
        questions.forEach(question -> questionsById.put(question.getId(), question));

        List<CanonicalAnswer> semanticAnswers = new ArrayList<>();
        List<PublicResponseException.Violation> violations = new ArrayList<>();
        Set<Long> submittedQuestionIds = new HashSet<>();
        for (int index = 0; index < submittedAnswers.size(); index++) {
            SubmittedAnswer submitted = submittedAnswers.get(index);
            String path = "answers[" + index + "]";
            if (!submittedQuestionIds.add(submitted.questionId())) {
                violations.add(violation(
                        path + ".questionId", "DUPLICATE", "Question은 한 번만 제출할 수 있습니다."));
                continue;
            }
            Question question = questionsById.get(submitted.questionId());
            if (question == null) {
                violations.add(violation(
                        path + ".questionId", "UNKNOWN_QUESTION", "제출할 수 없는 Question입니다."));
                continue;
            }
            CanonicalAnswer answer = validateAnswer(question, submitted, path, violations);
            if (answer != null) {
                semanticAnswers.add(answer);
            }
        }
        throwIfInvalid(violations);
        return List.copyOf(semanticAnswers);
    }

    void requireComplete(List<Question> questions, List<CanonicalAnswer> answers) {
        Set<Long> answeredQuestionIds = answers.stream()
                .map(CanonicalAnswer::questionId)
                .collect(java.util.stream.Collectors.toSet());
        boolean missingRequired = questions.stream()
                .anyMatch(question -> question.isRequired()
                        && !answeredQuestionIds.contains(question.getId()));
        if (missingRequired) {
            throw PublicResponseException.invalid(List.of(violation(
                    "answers",
                    "REQUIRED",
                    "필수 Question에 응답해 주세요.")));
        }
    }

    private CanonicalAnswer validateAnswer(
            Question question,
            SubmittedAnswer submitted,
            String path,
            List<PublicResponseException.Violation> violations) {
        return switch (question.getType()) {
            case SHORT_TEXT -> textAnswer(
                    question,
                    submitted,
                    path,
                    SHORT_TEXT_MAX_CODE_POINTS,
                    violations);
            case LONG_TEXT -> textAnswer(
                    question,
                    submitted,
                    path,
                    LONG_TEXT_MAX_CODE_POINTS,
                    violations);
            case SINGLE_CHOICE -> choiceAnswer(question, submitted, path, true, violations);
            case MULTIPLE_CHOICE -> choiceAnswer(question, submitted, path, false, violations);
            case SCALE -> scaleAnswer(question, submitted, path, violations);
            case NUMBER -> numberAnswer(question, submitted, path, violations);
        };
    }

    private CanonicalAnswer textAnswer(
            Question question,
            SubmittedAnswer submitted,
            String path,
            int maxCodePoints,
            List<PublicResponseException.Violation> violations) {
        if (submitted.textValue() == null) {
            violations.add(wrongRepresentation(path, "textValue"));
            return null;
        }
        String value = submitted.textValue();
        if (containsUnpairedSurrogate(value)) {
            violations.add(violation(
                    path + ".textValue",
                    "INVALID_TEXT",
                    "텍스트 응답에 올바르지 않은 Unicode 문자가 있습니다."));
            return null;
        }
        if (value.isBlank()) {
            violations.add(violation(
                    path + ".textValue", "REQUIRED", "텍스트 응답은 공백일 수 없습니다."));
            return null;
        }
        if (value.codePointCount(0, value.length()) > maxCodePoints) {
            violations.add(violation(
                    path + ".textValue",
                    "TOO_LONG",
                    "텍스트 응답이 허용된 길이를 초과했습니다."));
            return null;
        }
        return new CanonicalAnswer.TextValue(question.getId(), value);
    }

    private CanonicalAnswer choiceAnswer(
            Question question,
            SubmittedAnswer submitted,
            String path,
            boolean single,
            List<PublicResponseException.Violation> violations) {
        if (submitted.optionIds() == null) {
            violations.add(wrongRepresentation(path, "optionIds"));
            return null;
        }
        List<Long> optionIds = submitted.optionIds();
        if ((single && optionIds.size() != 1) || (!single && optionIds.isEmpty())) {
            violations.add(violation(
                    path + ".optionIds",
                    "INVALID_COUNT",
                    single
                            ? "단일 선택 Question은 Option 하나를 선택해야 합니다."
                            : "다중 선택 Question은 Option을 하나 이상 선택해야 합니다."));
            return null;
        }
        if (new HashSet<>(optionIds).size() != optionIds.size()) {
            violations.add(violation(
                    path + ".optionIds", "DUPLICATE", "Option은 한 번만 선택할 수 있습니다."));
            return null;
        }
        Set<Long> ownedOptionIds = question.getOptions().stream()
                .map(QuestionOption::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!ownedOptionIds.containsAll(optionIds)) {
            violations.add(violation(
                    path + ".optionIds", "UNKNOWN_OPTION", "제출할 수 없는 Option입니다."));
            return null;
        }
        return new CanonicalAnswer.OptionValues(question.getId(), optionIds);
    }

    private CanonicalAnswer scaleAnswer(
            Question question,
            SubmittedAnswer submitted,
            String path,
            List<PublicResponseException.Violation> violations) {
        if (submitted.numericValue() == null) {
            violations.add(wrongRepresentation(path, "numericValue"));
            return null;
        }
        String value = submitted.numericValue();
        if (!INTEGER_STRING.matcher(value).matches()) {
            violations.add(violation(
                    path + ".numericValue", "INVALID_NUMBER", "SCALE 응답은 정수 문자열이어야 합니다."));
            return null;
        }
        BigInteger number = new BigInteger(value);
        if (number.compareTo(BigInteger.valueOf(question.getScaleMin())) < 0
                || number.compareTo(BigInteger.valueOf(question.getScaleMax())) > 0) {
            violations.add(violation(
                    path + ".numericValue", "OUT_OF_RANGE", "SCALE 응답이 허용 범위를 벗어났습니다."));
            return null;
        }
        return new CanonicalAnswer.ScaleValue(question.getId(), number.intValueExact());
    }

    private CanonicalAnswer numberAnswer(
            Question question,
            SubmittedAnswer submitted,
            String path,
            List<PublicResponseException.Violation> violations) {
        if (submitted.numericValue() == null) {
            violations.add(wrongRepresentation(path, "numericValue"));
            return null;
        }
        String value = submitted.numericValue();
        if (!DECIMAL_STRING.matcher(value).matches()) {
            violations.add(violation(
                    path + ".numericValue",
                    "INVALID_NUMBER",
                    "NUMBER 응답은 지수 없는 10진수 문자열이어야 합니다."));
            return null;
        }
        BigDecimal number = new BigDecimal(value);
        int integerDigits = Math.max(0, number.precision() - number.scale());
        if (number.scale() > NUMBER_MAX_SCALE || integerDigits > NUMBER_MAX_INTEGER_DIGITS) {
            violations.add(violation(
                    path + ".numericValue",
                    "OUT_OF_RANGE",
                    "NUMBER 응답은 NUMERIC(19,4) 범위여야 합니다."));
            return null;
        }
        if ((question.getNumberMin() != null && number.compareTo(question.getNumberMin()) < 0)
                || (question.getNumberMax() != null && number.compareTo(question.getNumberMax()) > 0)) {
            violations.add(violation(
                    path + ".numericValue", "OUT_OF_RANGE", "NUMBER 응답이 허용 범위를 벗어났습니다."));
            return null;
        }
        return new CanonicalAnswer.NumberValue(question.getId(), number);
    }

    private boolean containsUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }

    private PublicResponseException.Violation wrongRepresentation(String path, String field) {
        return violation(
                path,
                "INVALID_REPRESENTATION",
                "Question type에는 " + field + " 응답이 필요합니다.");
    }

    private PublicResponseException.Violation violation(String path, String code, String message) {
        return new PublicResponseException.Violation(path, code, message);
    }

    private void throwIfInvalid(List<PublicResponseException.Violation> violations) {
        if (!violations.isEmpty()) {
            throw PublicResponseException.invalid(violations);
        }
    }
}
