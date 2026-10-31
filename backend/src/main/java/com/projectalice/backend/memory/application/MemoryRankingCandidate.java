package com.projectalice.backend.memory.application;

import java.util.Objects;
import java.util.Set;

/** Eligible, authoritatively hydrated item plus neutral relevance evidence. */
public record MemoryRankingCandidate(
        MemoryContextItem item,
        Set<MemoryRelevanceSignal> signals,
        MemorySearchEvidence searchEvidence) {

    public MemoryRankingCandidate(
            MemoryContextItem item, Set<MemoryRelevanceSignal> signals) {
        this(item, signals, MemorySearchEvidence.EMPTY);
    }

    public MemoryRankingCandidate {
        Objects.requireNonNull(item, "item must not be null");
        signals = signals == null ? Set.of() : Set.copyOf(signals);
        searchEvidence = searchEvidence == null
                ? MemorySearchEvidence.EMPTY : searchEvidence;
    }
}
