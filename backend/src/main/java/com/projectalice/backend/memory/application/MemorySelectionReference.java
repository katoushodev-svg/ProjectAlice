package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Opaque request-scope reference; it carries identities and versions, never Memory content. */
public record MemorySelectionReference(
        String referenceId,
        long resetGeneration,
        SearchCompleteness searchCompleteness,
        List<Entry> entries) {
    public MemorySelectionReference {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        if (referenceId.isBlank() || resetGeneration < 0) throw new IllegalArgumentException("invalid selection reference");
        Objects.requireNonNull(searchCompleteness, "searchCompleteness must not be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        if (entries.size() > MemoryContextBudget.DEFAULT_MAX_FINAL_ITEMS)
            throw new IllegalArgumentException("selection reference exceeds the final item limit");
        if (searchCompleteness == SearchCompleteness.UNAVAILABLE && !entries.isEmpty())
            throw new IllegalArgumentException("unavailable search cannot have selection entries");
        if (entries.stream().map(Entry::memoryId).distinct().count() != entries.size())
            throw new IllegalArgumentException("selection reference contains duplicate memory IDs");
    }

    public static MemorySelectionReference capture(long resetGeneration, SearchCompleteness completeness,
            List<MemoryContextItem> items) {
        return new MemorySelectionReference(UUID.randomUUID().toString(), resetGeneration, completeness,
                items.stream().map(item -> new Entry(item.memoryId(), item.memoryVersion())).toList());
    }

    public record Entry(MemoryId memoryId, long version) {
        public Entry {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            if (version < 1) throw new IllegalArgumentException("version must be positive");
        }
    }
}
