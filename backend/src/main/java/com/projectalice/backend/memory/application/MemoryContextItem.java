package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.Objects;
import java.util.Set;

public record MemoryContextItem(
        MemoryId memoryId,
        long memoryVersion,
        String content,
        MemoryCategory category,
        MemoryState state,
        SensitivityLevel sensitivityLevel,
        MemoryTemporalRole temporalRole,
        Set<MemoryRelevanceReasonCode> relevanceReasonCodes) {

    public MemoryContextItem {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        if (memoryVersion < 1) {
            throw new IllegalArgumentException("memoryVersion must be >= 1");
        }
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(sensitivityLevel, "sensitivityLevel must not be null");
        Objects.requireNonNull(temporalRole, "temporalRole must not be null");
        relevanceReasonCodes = relevanceReasonCodes == null ? Set.of() : Set.copyOf(relevanceReasonCodes);
    }
}
