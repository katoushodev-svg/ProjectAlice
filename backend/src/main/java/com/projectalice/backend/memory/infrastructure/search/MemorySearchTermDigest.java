package com.projectalice.backend.memory.infrastructure.search;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Infrastructure-only MEMORY_SEARCH_TERM HMAC capability. Key bytes are never returned. */
public final class MemorySearchTermDigest {

    private static final String PURPOSE_DOMAIN = "alice-memory-search-term\0";
    private static final String ALGORITHM = "HmacSHA256";

    private final Map<Integer, byte[]> readKeys;
    private final int writeKeyVersion;

    public MemorySearchTermDigest(Map<Integer, byte[]> readKeys, int writeKeyVersion) {
        Objects.requireNonNull(readKeys, "readKeys must not be null");
        if (readKeys.isEmpty() || readKeys.size() > 2) {
            throw new IllegalArgumentException("one or two search key versions are required");
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

    public int writeKeyVersion() {
        return writeKeyVersion;
    }

    /** Readable key versions only; key material remains private to this capability. */
    public List<Integer> readableKeyVersions() {
        return readKeys.keySet().stream().sorted().toList();
    }

    public String digestForWrite(String analyzerVersion, String normalizedTerm) {
        return digest(writeKeyVersion, analyzerVersion, normalizedTerm);
    }

    public String digestForRead(int keyVersion, String analyzerVersion, String normalizedTerm) {
        return digest(keyVersion, analyzerVersion, normalizedTerm);
    }

    private String digest(int keyVersion, String analyzerVersion, String normalizedTerm) {
        Objects.requireNonNull(analyzerVersion, "analyzerVersion must not be null");
        Objects.requireNonNull(normalizedTerm, "normalizedTerm must not be null");
        byte[] key = readKeys.get(keyVersion);
        if (key == null) {
            throw new IllegalArgumentException("search key version is not available");
        }
        byte[] message = (PURPOSE_DOMAIN + analyzerVersion + "\0" + normalizedTerm)
                .getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("search term digest is unavailable", exception);
        }
    }
}
