package com.projectalice.backend.memory.application;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record FindRelevantMemoriesResult(
        SearchCompleteness searchCompleteness,
        HydrationStatus hydrationStatus,
        List<MemoryContextItem> items,
        String payload,
        EstimationStatus estimationStatus,
        OptionalLong estimatedTokenCount,
        java.util.Optional<MemorySelectionReference> selectionReference) {

    public enum HydrationStatus {
        COMPLETE,
        PARTIAL,
        NOT_ATTEMPTED
    }

    public enum EstimationStatus {
        ESTIMATED,
        UNAVAILABLE,
        NOT_ATTEMPTED
    }

    public FindRelevantMemoriesResult {
        Objects.requireNonNull(searchCompleteness, "searchCompleteness must not be null");
        Objects.requireNonNull(hydrationStatus, "hydrationStatus must not be null");
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(estimationStatus, "estimationStatus must not be null");
        Objects.requireNonNull(estimatedTokenCount, "estimatedTokenCount must not be null");
        selectionReference = selectionReference == null ? java.util.Optional.empty() : selectionReference;
        if ((estimationStatus == EstimationStatus.ESTIMATED) != estimatedTokenCount.isPresent()) {
            throw new IllegalArgumentException("estimatedTokenCount must match estimationStatus");
        }
        if (searchCompleteness == SearchCompleteness.UNAVAILABLE
                && (hydrationStatus != HydrationStatus.NOT_ATTEMPTED || !items.isEmpty())) {
            throw new IllegalArgumentException("unavailable search must not be hydrated or return items");
        }
    }

    public FindRelevantMemoriesResult(SearchCompleteness searchCompleteness, HydrationStatus hydrationStatus,
            List<MemoryContextItem> items, String payload, EstimationStatus estimationStatus,
            OptionalLong estimatedTokenCount) {
        this(searchCompleteness, hydrationStatus, items, payload, estimationStatus, estimatedTokenCount,
                java.util.Optional.empty());
    }

    public static FindRelevantMemoriesResult unavailable() {
        return new FindRelevantMemoriesResult(
                SearchCompleteness.UNAVAILABLE,
                HydrationStatus.NOT_ATTEMPTED,
                List.of(),
                "",
                EstimationStatus.NOT_ATTEMPTED,
                OptionalLong.empty(),
                java.util.Optional.empty());
    }
}
