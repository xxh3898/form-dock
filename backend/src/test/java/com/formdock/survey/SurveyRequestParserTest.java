package com.formdock.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SurveyRequestParserTest {

    private final SurveyRequestParser parser =
            new SurveyRequestParser(new SurveySlugPolicy(() -> "abc1234567"));

    @Test
    void should_preserveNullableFieldPresence_when_patchContainsExplicitNull() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("description", null);
        request.put("privacyNotice", "  Preserve surrounding whitespace  ");

        SurveyPatchCommand command = parser.parsePatch(request);

        assertThat(command.descriptionPresent()).isTrue();
        assertThat(command.description()).isNull();
        assertThat(command.privacyNoticePresent()).isTrue();
        assertThat(command.privacyNotice()).isEqualTo("  Preserve surrounding whitespace  ");
        assertThat(command.titlePresent()).isFalse();
        assertThat(command.slugPresent()).isFalse();
    }

    @Test
    void should_validateTitleByUnicodeCodePoint_when_supplementaryCharactersAreUsed() {
        SurveyCreateCommand accepted = parser.parseCreate(Map.of(
                "title", "😀".repeat(200)));

        assertThat(accepted.title().codePointCount(0, accepted.title().length()))
                .isEqualTo(200);

        assertThatThrownBy(() -> parser.parseCreate(Map.of(
                "title", "😀".repeat(201))))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("title"));
    }

    @Test
    void should_trimAndAcceptSingleCodePointTitle_when_titleIsValid() {
        SurveyCreateCommand command = parser.parseCreate(Map.of("title", "  가  "));

        assertThat(command.title()).isEqualTo("가");
    }

    @Test
    void should_rejectTitle_when_trimmedValueIsBlank() {
        assertThatThrownBy(() -> parser.parseCreate(Map.of("title", " \t\n ")))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("title"));
    }

    @Test
    void should_enforceOptionalTextCodePointLimit_when_descriptionAndPrivacyNoticeAreParsed() {
        SurveyCreateCommand accepted = parser.parseCreate(Map.of(
                "title", "Valid title",
                "description", "가".repeat(5000),
                "privacyNotice", "😀".repeat(5000)));

        assertThat(accepted.description().codePointCount(0, accepted.description().length()))
                .isEqualTo(5000);
        assertThat(accepted.privacyNotice().codePointCount(0, accepted.privacyNotice().length()))
                .isEqualTo(5000);

        assertThatThrownBy(() -> parser.parseCreate(Map.of(
                "title", "Valid title",
                "description", "가".repeat(5001),
                "privacyNotice", "😀".repeat(5001))))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("description", "privacyNotice"));
    }

    @Test
    void should_rejectPatch_when_bodyHasNoSupportedFieldOrContainsUnknownField() {
        assertThatThrownBy(() -> parser.parsePatch(Map.of()))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("body"));

        assertThatThrownBy(() -> parser.parsePatch(Map.of("status", "OPEN")))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("status"));
    }

    @Test
    void should_rejectPatch_when_requiredFieldIsExplicitNull() {
        Map<String, Object> titleNull = new LinkedHashMap<>();
        titleNull.put("title", null);
        Map<String, Object> slugNull = new LinkedHashMap<>();
        slugNull.put("slug", null);

        assertThatThrownBy(() -> parser.parsePatch(titleNull))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("title"));
        assertThatThrownBy(() -> parser.parsePatch(slugNull))
                .isInstanceOf(SurveyException.class)
                .satisfies(failure -> assertThat(((SurveyException) failure).violations())
                        .extracting(SurveyException.Violation::path)
                        .contains("slug"));
    }
}
