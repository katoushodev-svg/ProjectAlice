package com.projectalice.backend.memory.infrastructure.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Infrastructure-only MEMORY_PROJECTION_DIGEST capability. Key bytes are never returned. */
public final class MemoryProjectionDigest {

    private static final byte[] PURPOSE_DOMAIN = "alice-memory-projection-digest\0"
            .getBytes(StandardCharsets.UTF_8);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String DIGEST_PATTERN = "[A-Za-z0-9_-]{43}";

    private final MemoryProjectionDigestCanonicalEncoder encoder;
    private final Map<Integer, byte[]> readKeys;
    private final int writeKeyVersion;

    public MemoryProjectionDigest(
            MemoryProjectionDigestCanonicalEncoder encoder,
            Map<Integer, byte[]> readKeys,
            int writeKeyVersion) {
        this.encoder = Objects.requireNonNull(encoder, "encoder must not be null");
        Objects.requireNonNull(readKeys, "readKeys must not be null");
        if (readKeys.isEmpty() || readKeys.size() > 2) {
            throw new IllegalArgumentException("one or two projection digest key versions are required");
        }
        if (!readKeys.containsKey(writeKeyVersion)) {
            throw new IllegalArgumentException("write key version must be readable");
        }
        this.readKeys = readKeys.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> {
                    Integer version = Objects.requireNonNull(entry.getKey(), "key version must not be null");
                    if (version < 1) {
                        throw new IllegalArgumentException("key version must be positive");
                    }
                    return version;
                },
                entry -> {
                    byte[] key = Objects.requireNonNull(entry.getValue(), "key material must not be null");
                    if (key.length == 0) {
                        throw new IllegalArgumentException("key material must not be empty");
                    }
                    return key.clone();
                }));
        this.writeKeyVersion = writeKeyVersion;
    }

    /** Computes a digest using this capability's active write key version. */
    public VersionedDigest digestForWrite(MemoryProjectionDigestCanonicalEncoder.Input input) {
        return new VersionedDigest(writeKeyVersion, digest(writeKeyVersion, input));
    }

    /** Verifies a persisted digest using the explicitly stored projection-digest key version. */
    public boolean verify(
            int projectionDigestKeyVersion,
            MemoryProjectionDigestCanonicalEncoder.Input input,
            String encodedDigest) {
        byte[] supplied = decodeDigest(encodedDigest);
        if (supplied == null) {
            return false;
        }
        byte[] expected = Base64.getUrlDecoder().decode(digest(projectionDigestKeyVersion, input));
        return MessageDigest.isEqual(expected, supplied);
    }

    public int writeKeyVersion() {
        return writeKeyVersion;
    }

    /** Readable versions only; key material remains private to this capability. */
    public List<Integer> readableKeyVersions() {
        return readKeys.keySet().stream().sorted().toList();
    }

    private String digest(int keyVersion, MemoryProjectionDigestCanonicalEncoder.Input input) {
        byte[] key = readKeys.get(keyVersion);
        if (key == null) {
            throw new IllegalArgumentException("projection digest key version is not available");
        }
        byte[] canonicalBytes = encoder.encode(Objects.requireNonNull(input, "input must not be null"));
        byte[] message = new byte[PURPOSE_DOMAIN.length + canonicalBytes.length];
        System.arraycopy(PURPOSE_DOMAIN, 0, message, 0, PURPOSE_DOMAIN.length);
        System.arraycopy(canonicalBytes, 0, message, PURPOSE_DOMAIN.length, canonicalBytes.length);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("projection digest is unavailable", exception);
        }
    }

    private static byte[] decodeDigest(String encodedDigest) {
        if (encodedDigest == null || !encodedDigest.matches(DIGEST_PATTERN)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedDigest);
            if (decoded.length != 32
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(encodedDigest)) {
                return null;
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Digest and its projection-digest key version are returned as one value. */
    public record VersionedDigest(int projectionDigestKeyVersion, String value) {
        public VersionedDigest {
            if (projectionDigestKeyVersion < 1) {
                throw new IllegalArgumentException("projectionDigestKeyVersion must be positive");
            }
            Objects.requireNonNull(value, "digest value must not be null");
            if (decodeDigest(value) == null) {
                throw new IllegalArgumentException("digest value must be canonical Base64url SHA-256");
            }
        }
    }
}
