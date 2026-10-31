package com.projectalice.backend.memory.infrastructure.idempotency;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Content-free durable Binding model from database-design §38.4.2. */
public record MemoryIdempotencyBinding(
        Identity identity,
        String requestDigest,
        UUID operationId,
        Status status,
        long leaseEpoch,
        String leaseOwner,
        OffsetDateTime leaseExpiresAt,
        ResultReference resultReference,
        String safeOutcomeCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long expiresAtEpochSeconds) {

    public enum Status { PROCESSING, COMPLETED, FAILED, UNKNOWN }
    public enum ResultType { MEMORY, PLAN, REVIEW, OPERATION }

    /** The raw client UUID is never persisted; the storage key contains only its HMAC digest. */
    public record Identity(String operationType, String keyDigest) {
        public Identity {
            Objects.requireNonNull(operationType, "operationType must not be null");
            Objects.requireNonNull(keyDigest, "keyDigest must not be null");
            if (!operationType.matches("[A-Z][A-Z0-9_]{1,63}"))
                throw new IllegalArgumentException("invalid operation type");
            if (!keyDigest.matches("[A-Za-z0-9_-]{43}"))
                throw new IllegalArgumentException("keyDigest must be a full SHA-256 base64url digest");
        }
        public String partitionKey() { return "MEMORY_OPERATION#IDEMPOTENCY#" + operationType + "#" + keyDigest; }
        public String sortKey() { return "META"; }
    }

    /** Safe replay locator only; never contains content or response bodies. */
    public record ResultReference(ResultType type, String id, Long version) {
        public ResultReference {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(id, "id must not be null");
            try {
                if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("result id must be a canonical UUID");
            }
            if (version != null && version < 1) throw new IllegalArgumentException("version must be positive");
        }
    }

    public MemoryIdempotencyBinding {
        Objects.requireNonNull(identity, "identity must not be null");
        requireDigest(requestDigest, "requestDigest");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (leaseEpoch < 1) throw new IllegalArgumentException("leaseEpoch must be positive");
        Objects.requireNonNull(leaseOwner, "leaseOwner must not be null");
        if (leaseOwner.isBlank()) throw new IllegalArgumentException("leaseOwner must not be blank");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (expiresAtEpochSeconds != null && expiresAtEpochSeconds < 0)
            throw new IllegalArgumentException("expiresAtEpochSeconds must be non-negative");
        if (status == Status.PROCESSING && (resultReference != null || safeOutcomeCode != null))
            throw new IllegalArgumentException("processing Binding cannot contain a result");
        if (status == Status.COMPLETED && (resultReference == null || safeOutcomeCode == null))
            throw new IllegalArgumentException("completed Binding requires a safe result reference and outcome");
        if (status == Status.FAILED && (safeOutcomeCode == null || resultReference != null))
            throw new IllegalArgumentException("failed Binding requires only a stable safe outcome");
        if (safeOutcomeCode != null && !safeOutcomeCode.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new IllegalArgumentException("safeOutcomeCode must be a stable code");
    }

    private static void requireDigest(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (!value.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException(field + " must be a full SHA-256 base64url digest");
    }
}
