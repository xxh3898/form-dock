package com.formdock.web;

import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<ApiFieldError> fieldErrors) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, List.of());
    }

    public record ApiFieldError(String path, String code, String message) {
    }
}
