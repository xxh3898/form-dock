package com.formdock.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class PublicResponseRequestParserTest {

    private final PublicResponseRequestParser parser =
            new PublicResponseRequestParser(new ObjectMapper());

    @Test
    void should_parsePresenceAndWireTypes_when_requestIsValid() {
        UUID submissionId = UUID.randomUUID();

        PublicResponseSubmissionCommand command = parse("""
                {
                  "clientSubmissionId": "%s",
                  "answers": [
                    {"questionId": 3, "textValue": "  exact text  "},
                    {"questionId": 4, "optionIds": [12, 11]},
                    {"questionId": 5, "numericValue": "7.5000"}
                  ]
                }
                """.formatted(submissionId));

        assertThat(command.clientSubmissionId()).isEqualTo(submissionId);
        assertThat(command.answers()).containsExactly(
                new SubmittedAnswer(3L, "  exact text  ", null, null),
                new SubmittedAnswer(4L, null, List.of(12L, 11L), null),
                new SubmittedAnswer(5L, null, null, "7.5000"));
    }

    @Test
    void should_rejectUnknownAndClientHashFields_when_requestContainsUnsupportedFields() {
        assertInvalid("""
                {
                  "clientSubmissionId": "550e8400-e29b-41d4-a716-446655440000",
                  "payloadHash": "client-owned",
                  "answers": [{"questionId": 1, "textValue": "answer", "extra": true}]
                }
                """, "payloadHash", "answers[0].extra");
    }

    @Test
    void should_rejectWrongScalarTypesAndMultipleRepresentations_when_shapeIsInvalid() {
        assertInvalid("""
                {
                  "clientSubmissionId": 42,
                  "answers": [
                    {"questionId": "1", "textValue": 7},
                    {"questionId": 2, "textValue": "answer", "numericValue": "2"},
                    {"questionId": 3, "optionIds": [1, "2"]}
                  ]
                }
                """,
                "clientSubmissionId",
                "answers[0].questionId",
                "answers[0].textValue",
                "answers[1]",
                "answers[2].optionIds[1]");
    }

    @Test
    void should_rejectDuplicateQuestionAndOptionIdentities_when_requestRepeatsThem() {
        assertInvalid("""
                {
                  "clientSubmissionId": "550e8400-e29b-41d4-a716-446655440000",
                  "answers": [
                    {"questionId": 1, "textValue": "first"},
                    {"questionId": 1, "optionIds": [10, 10]}
                  ]
                }
                """, "answers[1].questionId", "answers[1].optionIds[1]");
    }

    @Test
    void should_rejectMalformedJsonAndMissingRequiredFields_when_transportShapeIsInvalid() {
        assertInvalid("{", "body");
        assertInvalid("{}", "clientSubmissionId", "answers");
        assertInvalid("[]", "body");
    }

    private PublicResponseSubmissionCommand parse(String json) {
        return parser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String json, String... paths) {
        assertThatThrownBy(() -> parse(json))
                .isInstanceOf(PublicResponseException.class)
                .satisfies(failure -> {
                    PublicResponseException exception = (PublicResponseException) failure;
                    assertThat(exception.kind()).isEqualTo(PublicResponseException.Kind.INVALID);
                    assertThat(exception.violations())
                            .extracting(PublicResponseException.Violation::path)
                            .contains(paths);
                });
    }
}
