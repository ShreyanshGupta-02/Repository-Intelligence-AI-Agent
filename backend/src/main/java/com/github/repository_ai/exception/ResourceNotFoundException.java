package com.github.repository_ai.exception;

/**
 * Thrown when a requested resource (entity) does not exist in the database.
 * Handled by {@link GlobalExceptionHandler} to return a clean 404 response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier));
    }
}
