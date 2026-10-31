package com.projectalice.backend.memory.infrastructure.relation;

/** Sanitized failure that never retains provider, plaintext, ciphertext, or JCE details. */
public final class RelationCryptoException extends RuntimeException {
    public RelationCryptoException() { super("Relation protected state is unavailable"); }
}
