package org.example.migration.exceptions;

public class OpenSearchToPostgresException extends Exception {

    public OpenSearchToPostgresException(String message) {
        super(message);
    }
    public OpenSearchToPostgresException(String message, Throwable cause) {
        super(message, cause);
    }
}
