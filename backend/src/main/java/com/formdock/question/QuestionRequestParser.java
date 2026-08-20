package com.formdock.question;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class QuestionRequestParser {

    private static final Set<String> QUESTION_FIELDS = Set.of(
            "type",
            "title",
            "description",
            "required",
            "scaleMin",
            "scaleMax",
            "scaleMinLabel",
            "scaleMaxLabel",
            "numberMin",
            "numberMax",
            "options");
    private static final Set<String> REORDER_FIELDS = Set.of("questionIds");
    private static final Set<String> CREATE_OPTION_FIELDS = Set.of("label");
    private static final Set<String> UPDATE_OPTION_FIELDS = Set.of("id", "label");
    private static final Pattern DECIMAL_STRING = Pattern.compile("^-?[0-9]+(?:\\.[0-9]+)?$");
    private static final int TITLE_MAX_CODE_POINTS = 500;
    private static final int DESCRIPTION_MAX_CODE_POINTS = 2000;
    private static final int SCALE_LABEL_MAX_CODE_POINTS = 100;
    private static final int OPTION_LABEL_MAX_CODE_POINTS = 500;
    private static final int NUMBER_MAX_INTEGER_DIGITS = 15;
    private static final int NUMBER_MAX_SCALE = 4;

    QuestionCommand parseCreate(Map<String, Object> body) {
        return parseQuestion(body, false);
    }

    QuestionCommand parseUpdate(Map<String, Object> body) {
        return parseQuestion(body, true);
    }

    QuestionReorderCommand parseReorder(Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        List<QuestionException.Violation> violations = new ArrayList<>();
        validateUnknownFields(request, REORDER_FIELDS, violations);

        List<Long> questionIds = new ArrayList<>();
        if (!request.containsKey("questionIds")) {
            violations.add(violation(
                    "questionIds", "REQUIRED", "questionIds is required."));
        } else if (!(request.get("questionIds") instanceof List<?> rawIds)) {
            violations.add(violation(
                    "questionIds", "INVALID_TYPE", "questionIds must be an array."));
        } else {
            Set<Long> uniqueIds = new HashSet<>();
            for (int index = 0; index < rawIds.size(); index++) {
                String path = "questionIds[" + index + "]";
                Long id = positiveLong(rawIds.get(index), path, violations);
                if (id != null && !uniqueIds.add(id)) {
                    violations.add(violation(
                            path, "DUPLICATE", "Question identifier must appear once."));
                }
                if (id != null) {
                    questionIds.add(id);
                }
            }
        }

        throwValidationIfAny(violations);
        return new QuestionReorderCommand(questionIds);
    }

    private QuestionCommand parseQuestion(Map<String, Object> body, boolean update) {
        Map<String, Object> request = body == null ? Map.of() : body;
        List<QuestionException.Violation> validation = new ArrayList<>();
        List<QuestionException.Violation> configuration = new ArrayList<>();
        validateUnknownFields(request, QUESTION_FIELDS, validation);
        requireAllQuestionFields(request, validation);

        QuestionType type = questionType(request.get("type"), validation);
        String title = requiredTrimmedText(
                request.get("title"),
                "title",
                TITLE_MAX_CODE_POINTS,
                validation);
        String description = optionalText(
                request.get("description"),
                "description",
                DESCRIPTION_MAX_CODE_POINTS,
                validation);
        Boolean required = requiredBoolean(request.get("required"), validation);
        Integer scaleMin = optionalInteger(request.get("scaleMin"), "scaleMin", validation);
        Integer scaleMax = optionalInteger(request.get("scaleMax"), "scaleMax", validation);
        String scaleMinLabel = optionalText(
                request.get("scaleMinLabel"),
                "scaleMinLabel",
                SCALE_LABEL_MAX_CODE_POINTS,
                validation);
        String scaleMaxLabel = optionalText(
                request.get("scaleMaxLabel"),
                "scaleMaxLabel",
                SCALE_LABEL_MAX_CODE_POINTS,
                validation);
        BigDecimal numberMin = optionalDecimal(
                request.get("numberMin"), "numberMin", validation, configuration);
        BigDecimal numberMax = optionalDecimal(
                request.get("numberMax"), "numberMax", validation, configuration);
        List<QuestionOptionCommand> options = options(
                request.get("options"), update, validation);

        throwValidationIfAny(validation);
        validateTypeConfiguration(
                type,
                scaleMin,
                scaleMax,
                scaleMinLabel,
                scaleMaxLabel,
                numberMin,
                numberMax,
                options,
                configuration);
        if (!configuration.isEmpty()) {
            throw QuestionException.invalidConfiguration(configuration);
        }

        return new QuestionCommand(
                type,
                title,
                description,
                required,
                scaleMin,
                scaleMax,
                scaleMinLabel,
                scaleMaxLabel,
                numberMin,
                numberMax,
                options);
    }

    private void requireAllQuestionFields(
            Map<String, Object> request,
            List<QuestionException.Violation> violations) {
        for (String field : QUESTION_FIELDS) {
            if (!request.containsKey(field)) {
                violations.add(violation(
                        field, "REQUIRED", field + " must be included."));
            }
        }
    }

    private QuestionType questionType(
            Object value,
            List<QuestionException.Violation> violations) {
        if (!(value instanceof String text)) {
            violations.add(violation(
                    "type", "INVALID_TYPE", "type must be a supported string value."));
            return null;
        }
        try {
            return QuestionType.valueOf(text);
        } catch (IllegalArgumentException exception) {
            violations.add(violation(
                    "type", "INVALID_VALUE", "type is not supported."));
            return null;
        }
    }

    private String requiredTrimmedText(
            Object value,
            String path,
            int maxCodePoints,
            List<QuestionException.Violation> violations) {
        if (!(value instanceof String text)) {
            violations.add(violation(
                    path, "INVALID_TYPE", path + " must be a string."));
            return null;
        }
        String normalized = text.strip();
        int length = codePointLength(normalized);
        if (length == 0) {
            violations.add(violation(path, "REQUIRED", path + " must not be blank."));
        } else if (length > maxCodePoints) {
            violations.add(violation(
                    path,
                    "TOO_LONG",
                    path + " must not exceed " + maxCodePoints + " characters."));
        }
        return normalized;
    }

    private String optionalText(
            Object value,
            String path,
            int maxCodePoints,
            List<QuestionException.Violation> violations) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            violations.add(violation(
                    path, "INVALID_TYPE", path + " must be a string or null."));
            return null;
        }
        if (codePointLength(text) > maxCodePoints) {
            violations.add(violation(
                    path,
                    "TOO_LONG",
                    path + " must not exceed " + maxCodePoints + " characters."));
        }
        return text;
    }

    private Boolean requiredBoolean(
            Object value,
            List<QuestionException.Violation> violations) {
        if (!(value instanceof Boolean booleanValue)) {
            violations.add(violation(
                    "required", "INVALID_TYPE", "required must be a boolean."));
            return null;
        }
        return booleanValue;
    }

    private Integer optionalInteger(
            Object value,
            String path,
            List<QuestionException.Violation> violations) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Long longValue
                && longValue >= Integer.MIN_VALUE
                && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }
        if (value instanceof BigInteger bigInteger
                && bigInteger.bitLength() < Integer.SIZE) {
            return bigInteger.intValue();
        }
        violations.add(violation(
                path, "INVALID_TYPE", path + " must be an integer or null."));
        return null;
    }

    private BigDecimal optionalDecimal(
            Object value,
            String path,
            List<QuestionException.Violation> validation,
            List<QuestionException.Violation> configuration) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            validation.add(violation(
                    path,
                    "INVALID_TYPE",
                    path + " must be an exponent-free decimal string or null."));
            return null;
        }
        if (!DECIMAL_STRING.matcher(text).matches()) {
            configuration.add(violation(
                    path,
                    "INVALID_DECIMAL",
                    path + " must be an exponent-free decimal string."));
            return null;
        }

        BigDecimal number = new BigDecimal(text);
        int integerDigits = Math.max(0, number.precision() - number.scale());
        if (number.scale() > NUMBER_MAX_SCALE
                || integerDigits > NUMBER_MAX_INTEGER_DIGITS) {
            configuration.add(violation(
                    path,
                    "OUT_OF_RANGE",
                    path + " must fit NUMERIC(19,4)."));
        }
        return number;
    }

    private List<QuestionOptionCommand> options(
            Object value,
            boolean update,
            List<QuestionException.Violation> violations) {
        if (!(value instanceof List<?> rawOptions)) {
            violations.add(violation(
                    "options", "INVALID_TYPE", "options must be an array."));
            return List.of();
        }

        List<QuestionOptionCommand> options = new ArrayList<>();
        Set<Long> submittedIds = new HashSet<>();
        for (int index = 0; index < rawOptions.size(); index++) {
            String path = "options[" + index + "]";
            Object rawOption = rawOptions.get(index);
            if (!(rawOption instanceof Map<?, ?> rawMap)) {
                violations.add(violation(
                        path, "INVALID_TYPE", "Each option must be an object."));
                continue;
            }

            Map<String, Object> option = stringKeyMap(rawMap, path, violations);
            validateUnknownFields(
                    option,
                    update ? UPDATE_OPTION_FIELDS : CREATE_OPTION_FIELDS,
                    path + ".",
                    violations);

            Long id = null;
            if (update && option.containsKey("id")) {
                id = positiveLong(option.get("id"), path + ".id", violations);
                if (id != null && !submittedIds.add(id)) {
                    violations.add(violation(
                            path + ".id",
                            "DUPLICATE",
                            "Option identifier must appear once."));
                }
            }
            String label = requiredTrimmedText(
                    option.get("label"),
                    path + ".label",
                    OPTION_LABEL_MAX_CODE_POINTS,
                    violations);
            if (label != null) {
                options.add(new QuestionOptionCommand(id, label));
            }
        }
        return List.copyOf(options);
    }

    private Map<String, Object> stringKeyMap(
            Map<?, ?> rawMap,
            String path,
            List<QuestionException.Violation> violations) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, value);
            } else {
                violations.add(violation(
                        path, "INVALID_FIELD", "Option field names must be strings."));
            }
        });
        return result;
    }

    private Long positiveLong(
            Object value,
            String path,
            List<QuestionException.Violation> violations) {
        long candidate;
        if (value instanceof Integer integer) {
            candidate = integer.longValue();
        } else if (value instanceof Long longValue) {
            candidate = longValue;
        } else if (value instanceof BigInteger bigInteger
                && bigInteger.bitLength() < Long.SIZE) {
            candidate = bigInteger.longValue();
        } else {
            violations.add(violation(
                    path, "INVALID_TYPE", path + " must be a positive integer."));
            return null;
        }
        if (candidate <= 0) {
            violations.add(violation(
                    path, "INVALID_VALUE", path + " must be positive."));
            return null;
        }
        return candidate;
    }

    private void validateTypeConfiguration(
            QuestionType type,
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            BigDecimal numberMin,
            BigDecimal numberMax,
            List<QuestionOptionCommand> options,
            List<QuestionException.Violation> violations) {
        if (type == null) {
            return;
        }
        if (type.isChoice()) {
            requireNullScaleAndNumber(
                    scaleMin,
                    scaleMax,
                    scaleMinLabel,
                    scaleMaxLabel,
                    numberMin,
                    numberMax,
                    violations);
            if (options.size() < 2) {
                violations.add(violation(
                        "options",
                        "TOO_FEW",
                        "Choice Question requires at least two options."));
            }
            return;
        }

        if (!options.isEmpty()) {
            violations.add(violation(
                    "options", "UNUSED_FIELD", "Only Choice Question may contain options."));
        }
        switch (type) {
            case SHORT_TEXT, LONG_TEXT -> requireNullScaleAndNumber(
                    scaleMin,
                    scaleMax,
                    scaleMinLabel,
                    scaleMaxLabel,
                    numberMin,
                    numberMax,
                    violations);
            case SCALE -> {
                if (scaleMin == null || scaleMax == null) {
                    violations.add(violation(
                            "scaleMin",
                            "REQUIRED",
                            "Scale Question requires scaleMin and scaleMax."));
                } else if (scaleMin < 1 || scaleMin >= scaleMax || scaleMax > 10) {
                    violations.add(violation(
                            "scaleMin",
                            "INVALID_RANGE",
                            "Scale range must satisfy 1 <= min < max <= 10."));
                }
                if (numberMin != null || numberMax != null) {
                    violations.add(violation(
                            "numberMin",
                            "UNUSED_FIELD",
                            "Scale Question cannot contain number bounds."));
                }
            }
            case NUMBER -> {
                if (scaleMin != null
                        || scaleMax != null
                        || scaleMinLabel != null
                        || scaleMaxLabel != null) {
                    violations.add(violation(
                            "scaleMin",
                            "UNUSED_FIELD",
                            "Number Question cannot contain scale fields."));
                }
                if (numberMin != null
                        && numberMax != null
                        && numberMin.compareTo(numberMax) > 0) {
                    violations.add(violation(
                            "numberMin",
                            "INVALID_RANGE",
                            "numberMin cannot exceed numberMax."));
                }
            }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> throw new IllegalStateException(
                    "Choice configuration was not handled");
        }
    }

    private void requireNullScaleAndNumber(
            Integer scaleMin,
            Integer scaleMax,
            String scaleMinLabel,
            String scaleMaxLabel,
            BigDecimal numberMin,
            BigDecimal numberMax,
            List<QuestionException.Violation> violations) {
        if (scaleMin != null
                || scaleMax != null
                || scaleMinLabel != null
                || scaleMaxLabel != null
                || numberMin != null
                || numberMax != null) {
            violations.add(violation(
                    "type",
                    "UNUSED_FIELD",
                    "Question contains fields unused by its type."));
        }
    }

    private void validateUnknownFields(
            Map<String, Object> request,
            Set<String> allowed,
            List<QuestionException.Violation> violations) {
        validateUnknownFields(request, allowed, "", violations);
    }

    private void validateUnknownFields(
            Map<String, Object> request,
            Set<String> allowed,
            String pathPrefix,
            List<QuestionException.Violation> violations) {
        Set<String> unknown = new LinkedHashSet<>(request.keySet());
        unknown.removeAll(allowed);
        unknown.forEach(field -> violations.add(violation(
                pathPrefix + field, "UNKNOWN_FIELD", "Field is not supported.")));
    }

    private QuestionException.Violation violation(String path, String code, String message) {
        return new QuestionException.Violation(path, code, message);
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private void throwValidationIfAny(List<QuestionException.Violation> violations) {
        if (!violations.isEmpty()) {
            throw QuestionException.validation(violations);
        }
    }
}

record QuestionCommand(
        QuestionType type,
        String title,
        String description,
        boolean required,
        Integer scaleMin,
        Integer scaleMax,
        String scaleMinLabel,
        String scaleMaxLabel,
        BigDecimal numberMin,
        BigDecimal numberMax,
        List<QuestionOptionCommand> options) {

    QuestionCommand {
        options = List.copyOf(options);
    }

    List<String> optionLabels() {
        return options.stream().map(QuestionOptionCommand::label).toList();
    }
}

record QuestionOptionCommand(Long id, String label) {
}

record QuestionReorderCommand(List<Long> questionIds) {

    QuestionReorderCommand {
        questionIds = List.copyOf(questionIds);
    }
}
