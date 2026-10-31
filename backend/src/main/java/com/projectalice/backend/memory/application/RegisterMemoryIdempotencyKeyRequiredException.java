package com.projectalice.backend.memory.application;

/** Safe validation signal for a missing required registration idempotency key. */
public final class RegisterMemoryIdempotencyKeyRequiredException extends IllegalArgumentException {
    public RegisterMemoryIdempotencyKeyRequiredException() {
        super("Idempotency-Key is required");
    }
}
