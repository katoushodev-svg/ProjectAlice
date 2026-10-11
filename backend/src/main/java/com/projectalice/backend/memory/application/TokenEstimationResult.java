package com.projectalice.backend.memory.application;

/** The outcome of estimating a Memory Context payload. */
public sealed interface TokenEstimationResult
        permits TokenEstimationResult.Estimated, TokenEstimationResult.Unavailable {

    record Estimated(long tokenCount) implements TokenEstimationResult {
        public Estimated {
            if (tokenCount < 0) {
                throw new IllegalArgumentException("tokenCount must be >= 0");
            }
        }
    }

    record Unavailable() implements TokenEstimationResult {}
}
