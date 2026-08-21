package com.formdock.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResponsePayloadCanonicalizerTest {

    private final ResponsePayloadCanonicalizer canonicalizer = new ResponsePayloadCanonicalizer();

    @Test
    void should_matchLiteralCanonicalJsonAndHash_when_semanticAnswersAreUnordered() {
        CanonicalResponsePayload payload = canonicalizer.canonicalize(List.of(
                new CanonicalAnswer.NumberValue(3L, new BigDecimal("7.5000")),
                new CanonicalAnswer.OptionValues(2L, List.of(11L, 10L)),
                new CanonicalAnswer.TextValue(1L, "exact decoded text")));

        assertThat(payload.json()).isEqualTo(
                "{\"answers\":[{\"questionId\":1,\"textValue\":\"exact decoded text\"},"
                        + "{\"questionId\":2,\"optionIds\":[10,11]},"
                        + "{\"questionId\":3,\"numericValue\":\"7.5\"}]}");
        assertThat(payload.sha256()).isEqualTo(
                "d176d20c21b3653a64ee9902e306114740c9e6cae3e9e58380358228b261f61e");
        assertThat(payload.utf8Bytes()).containsExactly(payload.json().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_matchLiteralEmptyPayloadVector_when_noOptionalAnswersExist() {
        CanonicalResponsePayload payload = canonicalizer.canonicalize(List.of());

        assertThat(payload.json()).isEqualTo("{\"answers\":[]}");
        assertThat(payload.sha256()).isEqualTo(
                "5a52da6f8f1a906d99b253b3f5dcfa83cfffe519b9e2f9cd4b875e71eaaafaf5");
    }

    @Test
    void should_preserveDecodedTextAndNormalizeNumericRepresentations_when_valuesAreCanonicalized() {
        CanonicalResponsePayload payload = canonicalizer.canonicalize(List.of(
                new CanonicalAnswer.NumberValue(4L, new BigDecimal("0.0000")),
                new CanonicalAnswer.ScaleValue(3L, 7),
                new CanonicalAnswer.TextValue(1L, " exact \n한글 e\u0301 \uD83D\uDE00 \"\\ ")));

        assertThat(payload.json()).isEqualTo(
                "{\"answers\":[{\"questionId\":1,\"textValue\":\" exact \\n한글 e\u0301 \uD83D\uDE00 \\\"\\\\ \"},"
                        + "{\"questionId\":3,\"numericValue\":\"7\"},"
                        + "{\"questionId\":4,\"numericValue\":\"0\"}]}");
    }

    @Test
    void should_rejectDuplicateQuestionIdentity_when_payloadIsCanonicalized() {
        assertThatThrownBy(() -> canonicalizer.canonicalize(List.of(
                new CanonicalAnswer.TextValue(1L, "first"),
                new CanonicalAnswer.ScaleValue(1L, 3))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate Question ID in canonical payload: 1");
    }

    @Test
    void should_rejectDuplicateOptionIdentity_when_choiceAnswerIsCreated() {
        assertThatThrownBy(() -> new CanonicalAnswer.OptionValues(1L, List.of(10L, 10L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Option IDs must be distinct");
    }

    @Test
    void should_excludeTransportMetadataByConstruction_when_payloadIsCanonicalized() {
        CanonicalResponsePayload payload = canonicalizer.canonicalize(List.of(
                new CanonicalAnswer.TextValue(1L, "answer")));

        assertThat(payload.json())
                .doesNotContain("clientSubmissionId")
                .doesNotContain("payloadHash")
                .doesNotContain("submittedAt");
    }

    @Test
    void should_rejectUnpairedSurrogate_when_textCannotBeEncodedAsExactUnicode() {
        assertThatThrownBy(() -> canonicalizer.canonicalize(List.of(
                new CanonicalAnswer.TextValue(1L, "broken-\uD800"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Text value contains an unpaired surrogate");
    }
}
