package com.projectalice.backend.memory.application.port.out;

/**
 * Thrown when a semantic update cannot be applied because the persisted
 * {@code PersonalMemory} no longer matches the expected version, does not
 * exist, or the atomic update/revision transaction was otherwise rejected.
 */
public final class MemoryUpdateConflictException extends RuntimeException {

    public MemoryUpdateConflictException(String message) {
        super(message);
    }

    public MemoryUpdateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
