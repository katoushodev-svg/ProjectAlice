package com.projectalice.backend.ai.application.model;

import java.util.Objects;

/** A provider-independent context value with an opaque, application-internal reference. */
public record AiContextBlock(
        String referenceId,
        AiContextSource source,
        String content
) {
    public AiContextBlock {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        if (referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId must not be blank");
        }
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be empty");
        }
    }
}
