package com.projectalice.backend.memory.application;

import java.util.List;
import java.util.Objects;

public record FindRelevantMemoriesResult(Status status, List<MemoryContextItem> items) {

    public enum Status {
        /** Every candidate was either hydrated or confirmed absent. */
        COMPLETE,
        /** At least one candidate could not be hydrated and was skipped. */
        PARTIAL,
        /** Search failed; callers must continue without memory. */
        UNAVAILABLE
    }

    public FindRelevantMemoriesResult {
        Objects.requireNonNull(status, "status must not be null");
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }

    public static FindRelevantMemoriesResult unavailable() {
        return new FindRelevantMemoriesResult(Status.UNAVAILABLE, List.of());
    }
}
