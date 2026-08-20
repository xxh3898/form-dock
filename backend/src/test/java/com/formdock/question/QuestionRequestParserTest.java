package com.formdock.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class QuestionRequestParserTest {

    private final QuestionRequestParser parser = new QuestionRequestParser();

    @Test
    void should_parseAllSixCanonicalTypes_when_completeSemanticPayloadIsValid() {
        for (QuestionType type : QuestionType.values()) {
            QuestionCommand command = parser.parseCreate(validBody(type));

            assertThat(command.type()).isEqualTo(type);
            assertThat(command.title()).isEqualTo(type + " title");
            assertThat(command.required()).isTrue();
            assertThat(command.options()).hasSize(type.isChoice() ? 2 : 0);
        }
    }

    @Test
    void should_preserveExistingOptionIdentity_when_updatePayloadContainsOwnedIds() {
        Map<String, Object> body = validBody(QuestionType.SINGLE_CHOICE);
        body.put("options", List.of(
                Map.of("id", 10L, "label", " Renamed "),
                Map.of("label", "New")));

        QuestionCommand command = parser.parseUpdate(body);

        assertThat(command.options())
                .extracting(QuestionOptionCommand::id, QuestionOptionCommand::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, "Renamed"),
                        org.assertj.core.groups.Tuple.tuple(null, "New"));
    }

    @Test
    void should_returnConfigurationError_when_typeSpecificFieldsAreInvalid() {
        Map<String, Object> choice = validBody(QuestionType.SINGLE_CHOICE);
        choice.put("options", List.of(Map.of("label", "Only")));

        assertThatThrownBy(() -> parser.parseCreate(choice))
                .isInstanceOf(QuestionException.class)
                .extracting(failure -> ((QuestionException) failure).kind())
                .isEqualTo(QuestionException.Kind.INVALID_CONFIGURATION);

        Map<String, Object> number = validBody(QuestionType.NUMBER);
        number.put("numberMin", "1e3");
        assertThatThrownBy(() -> parser.parseCreate(number))
                .isInstanceOf(QuestionException.class)
                .extracting(failure -> ((QuestionException) failure).kind())
                .isEqualTo(QuestionException.Kind.INVALID_CONFIGURATION);
    }

    @Test
    void should_returnGeneralValidation_when_fieldsOrIdentitiesAreMalformed() {
        Map<String, Object> missing = validBody(QuestionType.SHORT_TEXT);
        missing.remove("scaleMin");
        assertValidation(() -> parser.parseCreate(missing));

        Map<String, Object> unknown = validBody(QuestionType.SHORT_TEXT);
        unknown.put("position", 1);
        assertValidation(() -> parser.parseCreate(unknown));

        Map<String, Object> floatingNumber = validBody(QuestionType.NUMBER);
        floatingNumber.put("numberMin", 1.25);
        assertValidation(() -> parser.parseCreate(floatingNumber));

        Map<String, Object> duplicateOptionIds = validBody(QuestionType.SINGLE_CHOICE);
        duplicateOptionIds.put("options", List.of(
                Map.of("id", 5, "label", "First"),
                Map.of("id", 5, "label", "Second")));
        assertValidation(() -> parser.parseUpdate(duplicateOptionIds));

        assertValidation(() -> parser.parseReorder(Map.of(
                "questionIds", List.of(1, 1))));
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(QuestionException.class)
                .extracting(failure -> ((QuestionException) failure).kind())
                .isEqualTo(QuestionException.Kind.VALIDATION);
    }

    private Map<String, Object> validBody(QuestionType type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type.name());
        body.put("title", "  " + type + " title  ");
        body.put("description", null);
        body.put("required", true);
        body.put("scaleMin", type == QuestionType.SCALE ? 1 : null);
        body.put("scaleMax", type == QuestionType.SCALE ? 5 : null);
        body.put("scaleMinLabel", type == QuestionType.SCALE ? "Low" : null);
        body.put("scaleMaxLabel", type == QuestionType.SCALE ? "High" : null);
        body.put("numberMin", type == QuestionType.NUMBER ? "-1.2500" : null);
        body.put("numberMax", type == QuestionType.NUMBER ? "10" : null);
        List<Map<String, Object>> options = new ArrayList<>();
        if (type.isChoice()) {
            options.add(Map.of("label", "First"));
            options.add(Map.of("label", "Second"));
        }
        body.put("options", options);
        return body;
    }
}
