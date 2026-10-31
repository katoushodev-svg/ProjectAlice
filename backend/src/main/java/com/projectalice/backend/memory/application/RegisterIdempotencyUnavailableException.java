package com.projectalice.backend.memory.application;

/** Registration must not mutate until its idempotency Binding can be durably reserved. */
public final class RegisterIdempotencyUnavailableException extends RuntimeException {
    public RegisterIdempotencyUnavailableException() {
        super("Durable registration idempotency is unavailable");
    }
}
