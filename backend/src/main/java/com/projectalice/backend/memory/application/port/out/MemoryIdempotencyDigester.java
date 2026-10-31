package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.application.RegisterMemoryIdempotencyContext;

/** Produces non-reversible, operation-separated fingerprints; callers persist only these values. */
public interface MemoryIdempotencyDigester {
    Digests digest(RegisterMemoryIdempotencyContext context);

    record Digests(String keyDigest, String requestDigest) {
        public Digests {
            if (keyDigest == null || requestDigest == null) throw new NullPointerException("digests must not be null");
        }
    }
}
