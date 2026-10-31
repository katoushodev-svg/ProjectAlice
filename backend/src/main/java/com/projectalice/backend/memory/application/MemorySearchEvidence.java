package com.projectalice.backend.memory.application;

/**
 * Provider-neutral evidence collected by keyword search.
 * Scores describe observed matching evidence, not eligibility.
 */
public record MemorySearchEvidence(
        int distinctTermCoverage,
        int termWeightSum) {

    public static final MemorySearchEvidence EMPTY =
            new MemorySearchEvidence(0, 0);

    public MemorySearchEvidence {
        if (distinctTermCoverage < 0 || termWeightSum < 0) {
            throw new IllegalArgumentException(
                    "search evidence values must be non-negative");
        }
        if (termWeightSum < distinctTermCoverage) {
            throw new IllegalArgumentException(
                    "termWeightSum must be >= distinctTermCoverage");
        }
        if ((distinctTermCoverage == 0) != (termWeightSum == 0)) {
            throw new IllegalArgumentException(
                    "empty coverage and empty weight must agree");
        }
    }
}
