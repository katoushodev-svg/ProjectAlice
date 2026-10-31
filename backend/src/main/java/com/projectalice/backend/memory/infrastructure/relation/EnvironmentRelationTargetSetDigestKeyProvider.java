package com.projectalice.backend.memory.infrastructure.relation;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Reads a dedicated HMAC-SHA-256 key injected by the deployment secret manager. */
public final class EnvironmentRelationTargetSetDigestKeyProvider implements RelationTargetSetDigestKeyProvider {
    private final SecretKey key;

    public EnvironmentRelationTargetSetDigestKeyProvider() { this(System.getenv()); }

    public EnvironmentRelationTargetSetDigestKeyProvider(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        String encoded = environment.get("ALICE_RELATION_TARGET_SET_DIGEST_KEY_BASE64");
        if (encoded == null || encoded.isBlank()) throw new RelationControlUnavailableException();
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try {
                if (bytes.length < 32) throw new RelationControlUnavailableException();
                this.key = new SecretKeySpec(bytes, "HmacSHA256");
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (IllegalArgumentException invalid) {
            throw new RelationControlUnavailableException();
        }
    }

    @Override public SecretKey targetSetDigestKey() { return key; }
}
