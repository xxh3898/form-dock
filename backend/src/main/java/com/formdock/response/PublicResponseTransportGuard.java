package com.formdock.response;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class PublicResponseTransportGuard {

    static final int MAX_BODY_BYTES = 1_048_576;

    private final PublicResponseRateLimiter rateLimiter;

    public PublicResponseTransportGuard(PublicResponseRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    byte[] admit(HttpServletRequest request) {
        requireJson(request.getContentType());
        byte[] body = readBoundedBody(request);
        rateLimiter.check(request.getRemoteAddr());
        return body;
    }

    private void requireJson(String contentType) {
        if (contentType == null) {
            throw PublicResponseException.unsupportedMediaType();
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (!"application".equalsIgnoreCase(mediaType.getType())
                    || !"json".equalsIgnoreCase(mediaType.getSubtype())) {
                throw PublicResponseException.unsupportedMediaType();
            }
        } catch (InvalidMediaTypeException exception) {
            throw PublicResponseException.unsupportedMediaType();
        }
    }

    private byte[] readBoundedBody(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw PublicResponseException.payloadTooLarge();
            }
            return body;
        } catch (IOException exception) {
            throw PublicResponseException.invalid(java.util.List.of(
                    new PublicResponseException.Violation(
                            "body", "MALFORMED", "요청 본문을 읽을 수 없습니다.")));
        }
    }
}
