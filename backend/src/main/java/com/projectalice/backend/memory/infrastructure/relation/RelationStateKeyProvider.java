package com.projectalice.backend.memory.infrastructure.relation;

import java.util.Objects;
import javax.crypto.SecretKey;

/** Purpose-separated boundary to an external Relation State secret provider. */
public interface RelationStateKeyProvider {
    /** Performs a Relation-purpose encrypt/decrypt known-answer capability probe without exposing key bytes. */
    void probeRelationPurpose();
    /** Returns a fresh or provider-managed AES-256 key for the sole WRITE_ACTIVE Relation version. */
    KeyMaterial activeEncryptionKey();
    /** Returns only the exact requested Relation AES-256 version; never falls back to another version. */
    SecretKey decryptionKey(int keyVersion);

    record KeyMaterial(int version, SecretKey key) {
        public KeyMaterial {
            if (version < 1) throw new IllegalArgumentException("version must be positive");
            Objects.requireNonNull(key, "key must not be null");
            if (!"AES".equalsIgnoreCase(key.getAlgorithm()))
                throw new IllegalArgumentException("Relation key must use AES");
            byte[] encoded = key.getEncoded();
            if (encoded != null) {
                try {
                    if (encoded.length != 32) throw new IllegalArgumentException("Relation key must be AES-256");
                } finally {
                    java.util.Arrays.fill(encoded, (byte) 0);
                }
            }
        }
    }
}
