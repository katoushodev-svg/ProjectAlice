package com.projectalice.backend.memory.infrastructure.relation;

import java.util.List;
import java.util.Objects;

/** Validated content-free Key Control snapshot. Never contains key material or provider handles. */
public record RelationKeyControl(int writeKeyVersion, int maxActiveKeyVersions,
        long controlVersion, List<KeyVersion> versions) {
    public enum KeyState { WRITE_ACTIVE, DECRYPT_ONLY, RETIRE_PENDING }
    public record KeyVersion(int version, KeyState state, String noncePrefix, long nextNonceCounter,
            long activeReviewCount, long preparingReferenceCount, long cleanupPendingCount) {
        public KeyVersion {
            if (version < 1) throw new IllegalArgumentException("version must be positive");
            Objects.requireNonNull(state, "state must not be null");
            if (noncePrefix == null || !noncePrefix.matches("[A-Za-z0-9_-]{6}"))
                throw new IllegalArgumentException("noncePrefix must encode exactly four bytes");
            if (nextNonceCounter < 0 || activeReviewCount < 0 || preparingReferenceCount < 0
                    || cleanupPendingCount < 0)
                throw new IllegalArgumentException("key counters must be non-negative");
        }
    }
    public RelationKeyControl {
        if (writeKeyVersion < 1) throw new IllegalArgumentException("writeKeyVersion must be positive");
        if (maxActiveKeyVersions != 2) throw new IllegalArgumentException("maxActiveKeyVersions must remain 2");
        if (controlVersion < 1) throw new IllegalArgumentException("controlVersion must be positive");
        versions = List.copyOf(Objects.requireNonNull(versions, "versions must not be null"));
        if (versions.isEmpty() || versions.size() > maxActiveKeyVersions)
            throw new IllegalArgumentException("one or two key versions are required");
        if (versions.stream().map(KeyVersion::version).distinct().count() != versions.size())
            throw new IllegalArgumentException("key versions must be unique");
        long writers = versions.stream().filter(v -> v.state() == KeyState.WRITE_ACTIVE).count();
        if (writers != 1 || versions.stream().noneMatch(v -> v.version() == writeKeyVersion
                && v.state() == KeyState.WRITE_ACTIVE))
            throw new IllegalArgumentException("writeKeyVersion must be the only WRITE_ACTIVE version");
    }
}
