package com.projectalice.backend.memory.application;

/** Safe validation signal; deliberately excludes the supplied key from its message. */
public final class InvalidRegisterIdempotencyKeyException extends IllegalArgumentException {
    public InvalidRegisterIdempotencyKeyException() {
        super("Idempotency-Key must be a canonical UUID");
    }
}
