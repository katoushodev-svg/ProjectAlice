package com.projectalice.backend.ai.application.model;

import java.util.Objects;

public record AiGenerationFailure(
        AiFailureCategory category,
        String safeMessage,
        boolean retryable
) {
    public AiGenerationFailure {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(safeMessage, "safeMessage must not be null");
        if (safeMessage.isBlank()) {
            throw new IllegalArgumentException("safeMessage must not be blank");
        }
    }
}
