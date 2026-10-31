package com.projectalice.backend.memory.infrastructure.relation;

import java.util.Objects;

/** Ciphertext envelope fields; plaintext, key material and serialized domain objects are excluded. */
public final class RelationCiphertext {
    public static final int NONCE_BYTES = 12;
    public static final int TAG_BYTES = 16;
    public static final int MAX_CIPHERTEXT_BYTES = 32 * 1024;
    private final int aadSchemaVersion;
    private final int keyVersion;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public RelationCiphertext(int aadSchemaVersion, int keyVersion, byte[] nonce, byte[] ciphertext) {
        if (aadSchemaVersion != RelationStateBinding.AAD_SCHEMA_VERSION)
            throw new IllegalArgumentException("unsupported AAD schema version");
        if (keyVersion < 1) throw new IllegalArgumentException("keyVersion must be positive");
        Objects.requireNonNull(nonce, "nonce must not be null");
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        if (nonce.length != NONCE_BYTES) throw new IllegalArgumentException("invalid nonce length");
        if (ciphertext.length < TAG_BYTES || ciphertext.length > MAX_CIPHERTEXT_BYTES)
            throw new IllegalArgumentException("invalid ciphertext length");
        this.aadSchemaVersion = aadSchemaVersion;
        this.keyVersion = keyVersion;
        this.nonce = nonce.clone();
        this.ciphertext = ciphertext.clone();
    }

    public int aadSchemaVersion() { return aadSchemaVersion; }
    public int keyVersion() { return keyVersion; }
    public byte[] nonce() { return nonce.clone(); }
    public byte[] ciphertext() { return ciphertext.clone(); }

    @Override public String toString() { return "RelationCiphertext[protected payload]"; }
}
