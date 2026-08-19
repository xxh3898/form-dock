package com.formdock.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SessionDependencyFailureFilter extends OncePerRequestFilter {

    private final ApiErrorWriter apiErrorWriter;

    public SessionDependencyFailureFilter(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (DataAccessException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            response.reset();
            apiErrorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TEMPORARILY_UNAVAILABLE",
                    "Service is temporarily unavailable.");
        }
    }
}
