package com.projectalice.backend.memory.application.port;

import com.projectalice.backend.memory.application.TokenEstimationResult;

/** Estimates tokens for the complete serialized Memory Context payload. */
@FunctionalInterface
public interface TokenEstimator {

    TokenEstimationResult estimate(String payload);
}
