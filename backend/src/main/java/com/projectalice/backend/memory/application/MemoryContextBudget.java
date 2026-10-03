package com.projectalice.backend.memory.application;

/** Carries limits across the application boundary; enforcement is not implemented yet. */
public record MemoryContextBudget(
        int maxCandidateItems,
        int maxFinalItems,
        int maxEstimatedTokens,
        int maxUtf8Bytes) {

    public static final int DEFAULT_MAX_CANDIDATE_ITEMS = 40;
    public static final int DEFAULT_MAX_FINAL_ITEMS = 8;
    public static final int DEFAULT_MAX_ESTIMATED_TOKENS = 1500;
    public static final int DEFAULT_MAX_UTF8_BYTES = 12 * 1024;

    public MemoryContextBudget {
        requirePositive(maxCandidateItems, "maxCandidateItems");
        requirePositive(maxFinalItems, "maxFinalItems");
        requirePositive(maxEstimatedTokens, "maxEstimatedTokens");
        requirePositive(maxUtf8Bytes, "maxUtf8Bytes");
    }

    public static MemoryContextBudget baseline() {
        return new MemoryContextBudget(
                DEFAULT_MAX_CANDIDATE_ITEMS,
                DEFAULT_MAX_FINAL_ITEMS,
                DEFAULT_MAX_ESTIMATED_TOKENS,
                DEFAULT_MAX_UTF8_BYTES);
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1");
        }
    }
}
