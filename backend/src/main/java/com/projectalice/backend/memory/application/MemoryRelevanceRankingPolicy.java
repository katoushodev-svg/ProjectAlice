package com.projectalice.backend.memory.application;

import java.util.List;

/** Orders eligible candidates; must not re-run eligibility, drop by threshold or add candidates. */
public interface MemoryRelevanceRankingPolicy {

    List<MemoryRankingCandidate> rank(MemoryRetrievalQuery query, List<MemoryRankingCandidate> candidates);

    /** Weights, threshold and tie-breaker are undecided, so the default keeps input order. */
    static MemoryRelevanceRankingPolicy preservingInputOrder() {
        return (query, candidates) -> List.copyOf(candidates);
    }
}
