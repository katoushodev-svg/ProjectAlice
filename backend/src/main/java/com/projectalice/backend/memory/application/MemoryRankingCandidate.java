package com.projectalice.backend.memory.application;

import java.util.Objects;
import java.util.Set;

/** Eligible, authoritatively hydrated item plus the neutral signals observed for it. */
public record MemoryRankingCandidate(MemoryContextItem item, Set<MemoryRelevanceSignal> signals) {

    public MemoryRankingCandidate {
        Objects.requireNonNull(item, "item must not be null");
        signals = signals == null ? Set.of() : Set.copyOf(signals);
    }
}
