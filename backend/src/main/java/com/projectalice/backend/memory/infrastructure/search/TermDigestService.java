package com.projectalice.backend.memory.infrastructure.search;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Creates versioned, domain-separated HMAC-SHA-256 digests for KW_V1 search terms.
 *
 * <p>Callers must keep normalized terms and key bytes transient. This class never logs them and
 * does not persist or access DynamoDB. Key material is supplied by an infrastructure boundary.</p>
 */
public final class TermDigestService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN_PREFIX =
            "alice-memory-search-term\0".getBytes(StandardCharsets.UTF_8);

    @FunctionalInterface
    public interface SearchTermKeyProvider {
        /** Returns key material for the requested version; callers should return at least 32 bytes. */
        byte[] keyForVersion(int keyVersion);
    }

    public record TermDigest(int keyVersion, String value) {
        public TermDigest {
            if (keyVersion < 1) {
                throw new IllegalArgumentException("keyVersion must be >= 1");
            }
            Objects.requireNonNull(value, "value must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
        }
    }

    private final SearchTermKeyProvider keyProvider;

    public TermDigestService(SearchTermKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
    }

    /**
     * Digest input is UTF-8(domain || analyzerVersion || NUL || normalizedTerm), where domain
     * includes the trailing NUL. The key version is returned alongside the digest and is part
     * of the DynamoDB partition-key format; it is not appended to the HMAC input.
     */
    public TermDigest digest(String analyzerVersion, String normalizedTerm, int keyVersion) {
        if (analyzerVersion == null || analyzerVersion.isBlank()) {
            throw new IllegalArgumentException("analyzerVersion must not be blank");
        }
        if (normalizedTerm == null || normalizedTerm.isBlank()) {
            throw new IllegalArgumentException("normalizedTerm must not be blank");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be >= 1");
        }

        byte[] key = keyProvider.keyForVersion(keyVersion);
        if (key == null || key.length < 32) {
            throw new IllegalStateException("Search term key is unavailable or too short");
        }

        byte[] analyzer = analyzerVersion.getBytes(StandardCharsets.UTF_8);
        byte[] term = normalizedTerm.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[DOMAIN_PREFIX.length + analyzer.length + 1 + term.length];
        int offset = 0;
        System.arraycopy(DOMAIN_PREFIX, 0, input, offset, DOMAIN_PREFIX.length);
        offset += DOMAIN_PREFIX.length;
        System.arraycopy(analyzer, 0, input, offset, analyzer.length);
        offset += analyzer.length;
        input[offset++] = 0;
        System.arraycopy(term, 0, input, offset, term.length);

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.clone(), HMAC_ALGORITHM));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input));
            return new TermDigest(keyVersion, encoded);
        } catch (GeneralSecurityException failure) {
            // HmacSHA256 is required by the Java platform. Treat absence as a configuration/runtime failure.
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", failure);
        }
    }
}
