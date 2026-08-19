package com.formdock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdock.auth.BootstrapPasswordPolicy;

import org.junit.jupiter.api.Test;

class BootstrapPasswordPolicyTest {

    private final BootstrapPasswordPolicy passwordPolicy = new BootstrapPasswordPolicy();

    @Test
    void should_rejectPassword_when_itContainsFourteenCharacters() {
        assertThatThrownBy(() -> passwordPolicy.validate("12345678901234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 15 characters");
    }

    @Test
    void should_acceptPassword_when_itContainsFifteenCharacters() {
        assertThatCode(() -> passwordPolicy.validate("123456789012345"))
                .doesNotThrowAnyException();
    }

    @Test
    void should_acceptPassword_when_utf8LengthIsExactlySeventyTwoBytes() {
        assertThatCode(() -> passwordPolicy.validate("가".repeat(24)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectPassword_when_utf8LengthExceedsSeventyTwoBytes() {
        assertThatThrownBy(() -> passwordPolicy.validate("가".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }

    @Test
    void should_acceptPassword_when_itContainsOnlyWhitespace() {
        assertThatCode(() -> passwordPolicy.validate(" ".repeat(15)))
                .doesNotThrowAnyException();
    }
}
