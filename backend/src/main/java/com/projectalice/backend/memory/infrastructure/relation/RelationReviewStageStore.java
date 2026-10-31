package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.time.OffsetDateTime;
import java.util.List;

/** Durable staging boundary for ciphertext rows; plaintext payloads never cross this interface. */
public interface RelationReviewStageStore {
    void createPreparingMetadata(String reviewId, long reviewVersion, int keyVersion,
            RelationReviewDraft draft, String targetSetDigest, String stageOwner,
            OffsetDateTime createdAt, OffsetDateTime expiresAt);

    void stageAndVerify(String reviewId, long reviewVersion, int keyVersion, String stageOwner,
            RelationCiphertext candidate, List<EncryptedTarget> targets, String expectedTargetSetDigest);

    void cleanupOwnedCiphertext(String reviewId, String stageOwner);
    void markCleanupPending(String reviewId, String stageOwner, OffsetDateTime updatedAt);

    record EncryptedTarget(int ordinal, String memoryId, long expectedVersion, String allowedMutation,
            RelationCiphertext ciphertext) {
        public EncryptedTarget {
            if (ordinal < 0 || ordinal >= 100) throw new IllegalArgumentException("invalid ordinal");
            if (memoryId == null || memoryId.isBlank()) throw new IllegalArgumentException("memoryId required");
            if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
            if (!"UPDATE_TARGET".equals(allowedMutation)) throw new IllegalArgumentException("unsupported mutation");
            java.util.Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        }
    }
}
