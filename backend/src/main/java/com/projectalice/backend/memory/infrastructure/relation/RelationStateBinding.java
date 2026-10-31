package com.projectalice.backend.memory.infrastructure.relation;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/** Canonical AEAD binding required to prevent ciphertext movement across Review fields. */
public record RelationStateBinding(
        String reviewId,
        long reviewVersion,
        SourceOperation sourceOperation,
        PayloadKind payloadKind,
        int targetOrdinal) {

    public enum SourceOperation {
        REGISTER(1), UPDATE(2);
        private final int aadCode;
        SourceOperation(int aadCode) { this.aadCode = aadCode; }
    }
    public enum PayloadKind {
        CANDIDATE(1), TARGET_PREVIEW(2);
        private final int aadCode;
        PayloadKind(int aadCode) { this.aadCode = aadCode; }
    }
    private static final int PURPOSE_RELATION_REVIEW = 1;
    public static final int AAD_SCHEMA_VERSION = 1;
    private static final int ENCODED_BYTES = 4 + 4 + 16 + 8 + 4 + 1 + 1 + 4;

    public RelationStateBinding {
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        try {
            reviewId = UUID.fromString(reviewId).toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("reviewId must be a UUID");
        }
        if (reviewVersion < 1) throw new IllegalArgumentException("reviewVersion must be positive");
        Objects.requireNonNull(sourceOperation, "sourceOperation must not be null");
        Objects.requireNonNull(payloadKind, "payloadKind must not be null");
        if (payloadKind == PayloadKind.CANDIDATE && targetOrdinal != -1)
            throw new IllegalArgumentException("Candidate binding must use targetOrdinal -1");
        if (payloadKind == PayloadKind.TARGET_PREVIEW && (targetOrdinal < 0 || targetOrdinal >= 100))
            throw new IllegalArgumentException("Target ordinal must be in [0, 99]");
    }

    byte[] canonicalAad(int keyVersion) {
        if (keyVersion < 1) throw new IllegalArgumentException("keyVersion must be positive");
        UUID uuid = UUID.fromString(reviewId);
        ByteBuffer bytes = ByteBuffer.allocate(ENCODED_BYTES);
        bytes.putInt(AAD_SCHEMA_VERSION).putInt(PURPOSE_RELATION_REVIEW)
                .putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
                .putLong(reviewVersion).putInt(keyVersion)
                .put((byte) sourceOperation.aadCode).put((byte) payloadKind.aadCode)
                .putInt(targetOrdinal);
        return bytes.array();
    }
}
