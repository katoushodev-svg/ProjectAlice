package com.projectalice.backend.memory.application.port.out;

/**
 * Thrown when Memory preferences cannot be updated because the persisted
 * version no longer matches the expected version.
 */
public final class MemoryPreferencesUpdateConflictException extends RuntimeException {

    public MemoryPreferencesUpdateConflictException(String message) {
        super(message);
    }

    public MemoryPreferencesUpdateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}