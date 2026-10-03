package com.projectalice.backend.memory.application;

import java.util.List;

/** Transforms ranked, authoritatively hydrated candidates without provider-specific inputs. */
public interface MemoryDeduplicationPolicy {

    List<MemoryRankingCandidate> deduplicate(
            MemoryRetrievalQuery query, List<MemoryRankingCandidate> candidates);

    /** Duplicate rules are undecided, so the default preserves every candidate in input order. */
    static MemoryDeduplicationPolicy preservingCandidates() {
        return (query, candidates) -> List.copyOf(candidates);
    }
}
