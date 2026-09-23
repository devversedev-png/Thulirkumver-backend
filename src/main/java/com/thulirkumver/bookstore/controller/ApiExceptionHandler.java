package com.thulirkumver.bookstore.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Please check the entered details");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    // Spring Security's own 403 response has no JSON body ("message"), which is
    // what made the admin dashboard show a bare, unhelpful "Something went wrong"
    // for expired/invalid tokens. Give it a real, actionable JSON message instead.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Your admin session has expired or does not have access. Please log in again."));
    }

    // Catch-all: any unexpected server error (bad data, null pointers, etc.)
    // used to fall through to Spring Boot's default error page, which the
    // frontend's api() helper cannot parse as JSON, so it silently collapsed
    // into the generic "Something went wrong" toast with no clue why. Now
    // every failure is logged server-side AND returns a real JSON message.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Something went wrong on the server. Please try again, and check the server logs if it persists."));
    }
}
