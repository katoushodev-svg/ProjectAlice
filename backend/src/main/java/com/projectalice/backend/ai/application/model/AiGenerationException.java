package com.projectalice.backend.ai.application.model;

import java.util.Objects;

/** Carries only the Provider-independent failure; no cause is ever exposed. */
public final class AiGenerationException extends RuntimeException {

    private final transient AiGenerationFailure failure;

    public AiGenerationException(AiGenerationFailure failure) {
        super(Objects.requireNonNull(failure, "failure must not be null").safeMessage(), null);
        this.failure = failure;
    }

    public AiGenerationFailure failure() {
        return failure;
    }
}
