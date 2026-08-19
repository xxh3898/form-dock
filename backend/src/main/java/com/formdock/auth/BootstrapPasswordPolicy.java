package com.formdock.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

@Component
public class BootstrapPasswordPolicy {

    static final int MIN_CODE_POINTS = 15;
    static final int MAX_UTF8_BYTES = 72;

    public void validate(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Creator bootstrap password is required");
        }

        int codePoints = password.codePointCount(0, password.length());
        if (codePoints < MIN_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "Creator bootstrap password must contain at least 15 characters");
        }

        int utf8Bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "Creator bootstrap password must not exceed 72 UTF-8 bytes");
        }
    }
}
