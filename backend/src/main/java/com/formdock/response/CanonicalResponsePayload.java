package com.formdock.response;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

public record CanonicalResponsePayload(String json, String sha256) {

    private static final Pattern SHA_256_LOWERCASE_HEX = Pattern.compile("[0-9a-f]{64}");

    public CanonicalResponsePayload {
        Objects.requireNonNull(json, "Canonical JSON is required");
        Objects.requireNonNull(sha256, "Payload hash is required");
        if (!SHA_256_LOWERCASE_HEX.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Payload hash must be SHA-256 lowercase hex");
        }
    }

    public byte[] utf8Bytes() {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
