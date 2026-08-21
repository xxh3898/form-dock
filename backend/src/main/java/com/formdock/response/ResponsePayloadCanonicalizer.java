package com.formdock.response;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ResponsePayloadCanonicalizer {

    public CanonicalResponsePayload canonicalize(List<CanonicalAnswer> semanticAnswers) {
        List<CanonicalAnswer> answers = new ArrayList<>(List.copyOf(Objects.requireNonNull(
                semanticAnswers,
                "Semantic answers are required")));
        answers.sort(Comparator.comparingLong(CanonicalAnswer::questionId));
        requireDistinctQuestionIds(answers);

        StringBuilder json = new StringBuilder("{\"answers\":[");
        for (int index = 0; index < answers.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendAnswer(json, answers.get(index));
        }
        json.append("]}");

        String canonicalJson = json.toString();
        return new CanonicalResponsePayload(canonicalJson, sha256(canonicalJson));
    }

    private void requireDistinctQuestionIds(List<CanonicalAnswer> answers) {
        Set<Long> questionIds = new HashSet<>();
        for (CanonicalAnswer answer : answers) {
            if (!questionIds.add(answer.questionId())) {
                throw new IllegalArgumentException(
                        "Duplicate Question ID in canonical payload: " + answer.questionId());
            }
        }
    }

    private void appendAnswer(StringBuilder json, CanonicalAnswer answer) {
        json.append("{\"questionId\":").append(answer.questionId());
        if (answer instanceof CanonicalAnswer.TextValue textValue) {
            json.append(",\"textValue\":\"");
            appendEscapedText(json, textValue.value());
            json.append("\"}");
            return;
        }
        if (answer instanceof CanonicalAnswer.OptionValues optionValues) {
            json.append(",\"optionIds\":[");
            List<Long> optionIds = optionValues.optionIds().stream().sorted().toList();
            for (int index = 0; index < optionIds.size(); index++) {
                if (index > 0) {
                    json.append(',');
                }
                json.append(optionIds.get(index));
            }
            json.append("]}");
            return;
        }
        if (answer instanceof CanonicalAnswer.ScaleValue scaleValue) {
            appendNumericValue(json, Integer.toString(scaleValue.value()));
            return;
        }
        if (answer instanceof CanonicalAnswer.NumberValue numberValue) {
            appendNumericValue(json, canonicalNumber(numberValue.value()));
            return;
        }
        throw new IllegalArgumentException("Unsupported canonical Answer representation");
    }

    private void appendNumericValue(StringBuilder json, String value) {
        json.append(",\"numericValue\":\"").append(value).append("\"}");
    }

    private String canonicalNumber(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void appendEscapedText(StringBuilder json, String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("Text value contains an unpaired surrogate");
                }
                json.append(character).append(value.charAt(++index));
                continue;
            }
            if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("Text value contains an unpaired surrogate");
            }
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u00")
                                .append(Character.forDigit((character >>> 4) & 0xF, 16))
                                .append(Character.forDigit(character & 0xF, 16));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
    }

    private String sha256(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
