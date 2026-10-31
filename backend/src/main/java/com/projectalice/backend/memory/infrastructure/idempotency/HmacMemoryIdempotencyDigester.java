package com.projectalice.backend.memory.infrastructure.idempotency;

import com.projectalice.backend.memory.application.RegisterIdempotencyUnavailableException;
import com.projectalice.backend.memory.application.RegisterMemoryIdempotencyContext;
import com.projectalice.backend.memory.application.port.out.MemoryIdempotencyDigester;
import com.projectalice.backend.memory.application.port.out.MemoryIdempotencySecretProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** Implements database-design §38.4.1. This class does not load or persist key material. */
public final class HmacMemoryIdempotencyDigester implements MemoryIdempotencyDigester {
    private static final String HMAC = "HmacSHA256";
    private static final byte[] KEY_PREFIX = "alice-memory-idempotency-key\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REQUEST_PREFIX = "alice-memory-idempotency-request\0".getBytes(StandardCharsets.UTF_8);
    private final MemoryIdempotencySecretProvider secrets;

    public HmacMemoryIdempotencyDigester(MemoryIdempotencySecretProvider secrets) {
        this.secrets = Objects.requireNonNull(secrets, "secrets must not be null");
    }

    @Override public Digests digest(RegisterMemoryIdempotencyContext context) {
        Objects.requireNonNull(context, "context must not be null");
        try {
            byte[] operation = context.operationType().getBytes(StandardCharsets.US_ASCII);
            byte[] key = context.canonicalKeyForBinding().getBytes(StandardCharsets.US_ASCII);
            byte[] request = context.canonicalRequestBytesForDigest();
            SecretKey keySecret = secrets.idempotencyKeyDigestSecret();
            SecretKey requestSecret = secrets.idempotencyRequestDigestSecret();
            requireSeparateExportableSecrets(keySecret, requestSecret);
            return new Digests(encode(hmac(keySecret, KEY_PREFIX, operation, key)),
                    encode(hmac(requestSecret, REQUEST_PREFIX, operation, request)));
        } catch (RegisterIdempotencyUnavailableException failure) {
            throw failure;
        } catch (RuntimeException | java.security.GeneralSecurityException failure) {
            throw new RegisterIdempotencyUnavailableException();
        }
    }

    private static void requireSeparateExportableSecrets(SecretKey keySecret, SecretKey requestSecret)
            throws java.security.GeneralSecurityException {
        if (keySecret == null || requestSecret == null)
            throw new java.security.GeneralSecurityException("missing idempotency secret");
        byte[] keyBytes = keySecret.getEncoded();
        byte[] requestBytes = requestSecret.getEncoded();
        try {
            if (keyBytes != null && requestBytes != null
                    && java.security.MessageDigest.isEqual(keyBytes, requestBytes))
                throw new java.security.GeneralSecurityException("idempotency secrets must be distinct");
        } finally {
            if (keyBytes != null) java.util.Arrays.fill(keyBytes, (byte) 0);
            if (requestBytes != null) java.util.Arrays.fill(requestBytes, (byte) 0);
        }
    }

    private static byte[] hmac(SecretKey key, byte[] prefix, byte[] operation, byte[] value)
            throws java.security.GeneralSecurityException {
        if (key == null || !HMAC.equalsIgnoreCase(key.getAlgorithm()))
            throw new java.security.GeneralSecurityException("unsupported HMAC key");
        Mac mac = Mac.getInstance(HMAC);
        mac.init(key);
        mac.update(prefix);
        mac.update(operation);
        mac.update((byte) 0);
        mac.update(value);
        return mac.doFinal();
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
