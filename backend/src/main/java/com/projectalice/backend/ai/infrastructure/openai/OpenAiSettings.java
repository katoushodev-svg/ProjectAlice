package com.projectalice.backend.ai.infrastructure.openai;

import java.util.Objects;

record OpenAiSettings(
        String model,
        String reasoningEffort,
        long maxOutputTokens
) {
    OpenAiSettings {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(reasoningEffort, "reasoningEffort must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (reasoningEffort.isBlank()) {
            throw new IllegalArgumentException("reasoningEffort must not be blank");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
