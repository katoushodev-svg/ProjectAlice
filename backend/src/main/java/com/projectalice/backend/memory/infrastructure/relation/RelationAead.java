package com.projectalice.backend.memory.infrastructure.relation;

public interface RelationAead {
    RelationCiphertext encrypt(byte[] plaintext, RelationStateBinding binding);
    byte[] decrypt(RelationCiphertext envelope, RelationStateBinding expectedBinding);
}
