package com.projectalice.backend.memory.application;

import java.util.List;

/** Transforms deduplicated, authoritatively hydrated candidates without changing relevance ranking. */
public interface MemoryDiversityPolicy {

    List<MemoryRankingCandidate> diversify(
            MemoryRetrievalQuery query, List<MemoryRankingCandidate> candidates);

    /** Diversity rules are undecided, so the default preserves every candidate in input order. */
    static MemoryDiversityPolicy preservingCandidates() {
        return (query, candidates) -> List.copyOf(candidates);
    }
}
