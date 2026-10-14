package com.projectalice.backend.memory.application;

import java.util.List;
import java.util.Objects;

/** Search completeness and the candidates returned by the search boundary. */
public record AnswerMemorySearchResult(SearchCompleteness completeness, List<MemorySearchCandidate> candidates) {

    public AnswerMemorySearchResult {
        Objects.requireNonNull(completeness, "completeness must not be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (completeness == SearchCompleteness.UNAVAILABLE && !candidates.isEmpty()) {
            throw new IllegalArgumentException("unavailable search must not return candidates");
        }
    }

    public static AnswerMemorySearchResult complete(List<MemorySearchCandidate> candidates) {
        return new AnswerMemorySearchResult(SearchCompleteness.COMPLETE, candidates);
    }

    public static AnswerMemorySearchResult partial(List<MemorySearchCandidate> verifiedCandidates) {
        // A PARTIAL result may contain only candidates verified by the search adapter.
        return new AnswerMemorySearchResult(SearchCompleteness.PARTIAL, verifiedCandidates);
    }

    public static AnswerMemorySearchResult unavailable() {
        return new AnswerMemorySearchResult(SearchCompleteness.UNAVAILABLE, List.of());
    }
}
