
package com.projectalice.backend.memory.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Initial provider-neutral ranking policy.
 *
 * Weights are provisional implementation defaults, not learned model scores.
 * This policy orders candidates only; it never filters them.
 */
public final class WeightedMemoryRelevanceRankingPolicy
        implements MemoryRelevanceRankingPolicy {

    private static final Map<MemoryRelevanceSignal, Integer> WEIGHTS = Map.of(
            MemoryRelevanceSignal.DIRECT_SUBJECT_ENTITY_MATCH, 1000,
            MemoryRelevanceSignal.ATTRIBUTE_INTENT_MATCH, 800,
            MemoryRelevanceSignal.SCOPE_MATCH, 600,
            MemoryRelevanceSignal.SEMANTIC_RELEVANCE, 400,
            MemoryRelevanceSignal.KEYWORD_RELEVANCE, 300,
            MemoryRelevanceSignal.CURRENTNESS, 250,
            MemoryRelevanceSignal.EXPLICIT_CONFIRMATION, 200,
            MemoryRelevanceSignal.SPECIFICITY, 150,
            MemoryRelevanceSignal.RECENCY, 100,
            MemoryRelevanceSignal.REDUNDANCY_PENALTY, -500
    );

    @Override
    public List<MemoryRankingCandidate> rank(
            MemoryRetrievalQuery query,
            List<MemoryRankingCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator
                        // 1. Primary ordering: relevance signal score.
                        .comparingInt(
                                WeightedMemoryRelevanceRankingPolicy::signalScore)
                        .reversed()

                        // 2. Tie-breaker: search evidence score.
                        .thenComparing(
                                Comparator.comparingInt(
                                        WeightedMemoryRelevanceRankingPolicy::evidenceScore)
                                        .reversed())

                        // 3. Deterministic tie-breakers.
                        .thenComparing(
                                c -> c.item().memoryId().value())
                        .thenComparing(
                                (MemoryRankingCandidate c) -> c.item().memoryVersion(),
                                Comparator.reverseOrder())
                        .thenComparing(
                                WeightedMemoryRelevanceRankingPolicy::signalKey)
                        .thenComparing(
                                c -> c.item().content()))
                .toList();
    }

    /**
     * Calculates relevance from ranking signals only.
     * Search evidence must not override a stronger relevance signal.
     */
    private static int signalScore(MemoryRankingCandidate candidate) {
        return candidate.signals().stream()
                .mapToInt(signal -> WEIGHTS.getOrDefault(signal, 0))
                .sum();
    }

    /**
     * Calculates search evidence for use only when signal scores are equal.
     */
    private static int evidenceScore(MemoryRankingCandidate candidate) {
        return candidate.searchEvidence().distinctTermCoverage() * 5
                + candidate.searchEvidence().termWeightSum() * 3;
    }

    /**
     * Creates a stable representation of the candidate's ranking signals.
     */
    private static String signalKey(MemoryRankingCandidate candidate) {
        return candidate.signals().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
