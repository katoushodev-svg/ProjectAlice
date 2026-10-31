package com.projectalice.backend.memory.infrastructure.relation;

/** Atomically reserves a never-reused 96-bit nonce for a Relation key version. */
public interface RelationNonceProvider {
    byte[] reserveNonce(int keyVersion);
}
