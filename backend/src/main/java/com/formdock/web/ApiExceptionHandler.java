package com.formdock.web;

import java.util.List;

import com.formdock.survey.SurveyException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        "AUTH_INVALID_CREDENTIALS",
                        "Invalid email or password."));
    }

    @ExceptionHandler(SurveyException.class)
    ResponseEntity<ApiErrorResponse> handleSurveyException(SurveyException exception) {
        return switch (exception.kind()) {
            case VALIDATION -> response(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    exception.getMessage(),
                    exception.violations().stream()
                            .map(violation -> new ApiErrorResponse.ApiFieldError(
                                    violation.path(),
                                    violation.code(),
                                    violation.message()))
                            .toList());
            case NOT_FOUND -> response(
                    HttpStatus.NOT_FOUND,
                    "SURVEY_NOT_FOUND",
                    exception.getMessage(),
                    List.of());
            case SLUG_CONFLICT -> response(
                    HttpStatus.CONFLICT,
                    "SURVEY_SLUG_CONFLICT",
                    exception.getMessage(),
                    List.of());
            case SLUG_IMMUTABLE -> response(
                    HttpStatus.CONFLICT,
                    "SURVEY_SLUG_IMMUTABLE",
                    exception.getMessage(),
                    List.of());
            case DELETE_REQUIRES_CLOSED -> response(
                    HttpStatus.CONFLICT,
                    "SURVEY_DELETE_REQUIRES_CLOSED",
                    exception.getMessage(),
                    List.of());
            case STRUCTURE_LOCKED -> response(
                    HttpStatus.CONFLICT,
                    "SURVEY_STRUCTURE_LOCKED",
                    exception.getMessage(),
                    List.of());
            case TEMPORARILY_UNAVAILABLE -> response(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TEMPORARILY_UNAVAILABLE",
                    exception.getMessage(),
                    List.of());
        };
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleMalformedRequest() {
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request is malformed or contains an invalid value.",
                List.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.ApiFieldError> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, fieldErrors));
    }
}
