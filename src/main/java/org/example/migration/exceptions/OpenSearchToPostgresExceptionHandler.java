package org.example.migration.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "org.example.api.migration")
public class OpenSearchToPostgresExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(OpenSearchToPostgresExceptionHandler.class);

    @ExceptionHandler(OpenSearchToPostgresException.class)
    public ResponseEntity<String> handleOpenSearchToPostgresException(OpenSearchToPostgresException ex) {
        logger.error("OpenSearchToPostgresException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error: " + ex.getMessage());
    }
}

