package com.formdock.response;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PublicResponseRequestParser {

    private static final Set<String> REQUEST_FIELDS =
            Set.of("clientSubmissionId", "answers");
    private static final Set<String> ANSWER_FIELDS =
            Set.of("questionId", "textValue", "optionIds", "numericValue");

    private final ObjectMapper objectMapper;

    public PublicResponseRequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PublicResponseSubmissionCommand parse(byte[] body) {
        Object decoded;
        try {
            decoded = objectMapper.readValue(body, Object.class);
        } catch (JacksonException exception) {
            throw invalid("body", "MALFORMED", "JSON 요청 본문이 올바르지 않습니다.");
        }
        if (!(decoded instanceof Map<?, ?> rawRequest)) {
            throw invalid("body", "INVALID_TYPE", "요청 본문은 JSON 객체여야 합니다.");
        }

        List<PublicResponseException.Violation> violations = new ArrayList<>();
        Map<String, Object> request = stringKeyMap(rawRequest);
        validateUnknownFields(request, REQUEST_FIELDS, "", violations);

        UUID submissionId = submissionId(request, violations);
        List<SubmittedAnswer> answers = answers(request, violations);
        throwIfInvalid(violations);
        return new PublicResponseSubmissionCommand(submissionId, answers);
    }

    private UUID submissionId(
            Map<String, Object> request,
            List<PublicResponseException.Violation> violations) {
        if (!request.containsKey("clientSubmissionId")) {
            violations.add(violation(
                    "clientSubmissionId", "REQUIRED", "clientSubmissionId는 필수입니다."));
            return null;
        }
        Object value = request.get("clientSubmissionId");
        if (!(value instanceof String text)) {
            violations.add(violation(
                    "clientSubmissionId", "INVALID_TYPE", "clientSubmissionId는 UUID 문자열이어야 합니다."));
            return null;
        }
        try {
            UUID parsed = UUID.fromString(text);
            if (!parsed.toString().equalsIgnoreCase(text)) {
                throw new IllegalArgumentException("UUID is not canonical");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            violations.add(violation(
                    "clientSubmissionId", "INVALID_VALUE", "clientSubmissionId는 올바른 UUID여야 합니다."));
            return null;
        }
    }

    private List<SubmittedAnswer> answers(
            Map<String, Object> request,
            List<PublicResponseException.Violation> violations) {
        if (!request.containsKey("answers")) {
            violations.add(violation("answers", "REQUIRED", "answers는 필수입니다."));
            return List.of();
        }
        Object value = request.get("answers");
        if (!(value instanceof List<?> rawAnswers)) {
            violations.add(violation("answers", "INVALID_TYPE", "answers는 배열이어야 합니다."));
            return List.of();
        }

        List<SubmittedAnswer> parsed = new ArrayList<>();
        Set<Long> questionIds = new HashSet<>();
        for (int index = 0; index < rawAnswers.size(); index++) {
            String path = "answers[" + index + "]";
            Object rawAnswer = rawAnswers.get(index);
            if (!(rawAnswer instanceof Map<?, ?> rawMap)) {
                violations.add(violation(path, "INVALID_TYPE", "각 Answer는 객체여야 합니다."));
                continue;
            }
            Map<String, Object> answer = stringKeyMap(rawMap);
            validateUnknownFields(answer, ANSWER_FIELDS, path + ".", violations);

            Long questionId = positiveLong(answer.get("questionId"), path + ".questionId", violations);
            if (!answer.containsKey("questionId")) {
                violations.add(violation(
                        path + ".questionId", "REQUIRED", "questionId는 필수입니다."));
            } else if (questionId != null && !questionIds.add(questionId)) {
                violations.add(violation(
                        path + ".questionId", "DUPLICATE", "Question은 한 번만 제출할 수 있습니다."));
            }

            int representationCount = 0;
            representationCount += answer.containsKey("textValue") ? 1 : 0;
            representationCount += answer.containsKey("optionIds") ? 1 : 0;
            representationCount += answer.containsKey("numericValue") ? 1 : 0;
            if (representationCount != 1) {
                violations.add(violation(
                        path,
                        "INVALID_REPRESENTATION",
                        "Answer에는 textValue, optionIds, numericValue 중 하나만 있어야 합니다."));
                continue;
            }

            String textValue = null;
            List<Long> optionIds = null;
            String numericValue = null;
            boolean representationValid = true;
            if (answer.containsKey("textValue")) {
                Object rawText = answer.get("textValue");
                if (rawText instanceof String text) {
                    textValue = text;
                } else {
                    representationValid = false;
                    violations.add(violation(
                            path + ".textValue", "INVALID_TYPE", "textValue는 문자열이어야 합니다."));
                }
            } else if (answer.containsKey("optionIds")) {
                optionIds = optionIds(answer.get("optionIds"), path + ".optionIds", violations);
                representationValid = optionIds != null;
            } else {
                Object rawNumeric = answer.get("numericValue");
                if (rawNumeric instanceof String text) {
                    numericValue = text;
                } else {
                    representationValid = false;
                    violations.add(violation(
                            path + ".numericValue", "INVALID_TYPE", "numericValue는 문자열이어야 합니다."));
                }
            }

            if (questionId != null && representationValid) {
                parsed.add(new SubmittedAnswer(questionId, textValue, optionIds, numericValue));
            }
        }
        return List.copyOf(parsed);
    }

    private List<Long> optionIds(
            Object value,
            String path,
            List<PublicResponseException.Violation> violations) {
        if (!(value instanceof List<?> rawIds)) {
            violations.add(violation(path, "INVALID_TYPE", "optionIds는 배열이어야 합니다."));
            return null;
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> unique = new HashSet<>();
        for (int index = 0; index < rawIds.size(); index++) {
            String itemPath = path + "[" + index + "]";
            Long id = positiveLong(rawIds.get(index), itemPath, violations);
            if (id != null && !unique.add(id)) {
                violations.add(violation(
                        itemPath, "DUPLICATE", "Option은 한 번만 선택할 수 있습니다."));
            }
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private Long positiveLong(
            Object value,
            String path,
            List<PublicResponseException.Violation> violations) {
        long candidate;
        if (value instanceof Integer integer) {
            candidate = integer.longValue();
        } else if (value instanceof Long longValue) {
            candidate = longValue;
        } else if (value instanceof BigInteger bigInteger && bigInteger.bitLength() < Long.SIZE) {
            candidate = bigInteger.longValue();
        } else {
            violations.add(violation(path, "INVALID_TYPE", "식별자는 양의 정수여야 합니다."));
            return null;
        }
        if (candidate <= 0) {
            violations.add(violation(path, "INVALID_VALUE", "식별자는 양수여야 합니다."));
            return null;
        }
        return candidate;
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, value);
            }
        });
        return result;
    }

    private void validateUnknownFields(
            Map<String, Object> values,
            Set<String> allowed,
            String prefix,
            List<PublicResponseException.Violation> violations) {
        Set<String> unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        unknown.forEach(field -> violations.add(violation(
                prefix + field, "UNKNOWN_FIELD", "지원하지 않는 필드입니다.")));
    }

    private PublicResponseException invalid(String path, String code, String message) {
        return PublicResponseException.invalid(List.of(violation(path, code, message)));
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
