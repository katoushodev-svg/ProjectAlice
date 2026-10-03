package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryCategory;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MemoryRetrievalQuery(
        MemoryRetrievalPurpose purpose,
        String currentUserContent,
        List<String> minimalConversationContext,
        List<String> intentHints,
        List<String> entityHints,
        Set<MemoryCategory> categoryHints,
        MemoryTemporalIntent temporalIntent,
        MemoryContextBudget contextBudget,
        String requestId) {

    public MemoryRetrievalQuery {
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(currentUserContent, "currentUserContent must not be null");
        if (currentUserContent.isBlank()) {
            throw new IllegalArgumentException("currentUserContent must not be blank");
        }
        minimalConversationContext = copyOf(minimalConversationContext);
        intentHints = copyOf(intentHints);
        entityHints = copyOf(entityHints);
        categoryHints = categoryHints == null ? Set.of() : Set.copyOf(categoryHints);
        temporalIntent = temporalIntent == null ? MemoryTemporalIntent.UNSPECIFIED : temporalIntent;
        Objects.requireNonNull(contextBudget, "contextBudget must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    private static List<String> copyOf(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
