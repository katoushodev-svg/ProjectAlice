package com.projectalice.backend.memory.infrastructure.idempotency;

import static org.junit.jupiter.api.Assertions.*;

import com.projectalice.backend.memory.application.RegisterIdempotencyUnavailableException;
import com.projectalice.backend.memory.application.RegisterMemoryIdempotencyContext;
import com.projectalice.backend.memory.application.port.out.MemoryIdempotencySecretProvider;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacMemoryIdempotencyDigesterTest {
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final byte[] KEY_SECRET = "separate-idempotency-key-secret-32b".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REQUEST_SECRET = "separate-request-digest-secret-32b".getBytes(StandardCharsets.UTF_8);

    @Test void fingerprintsFollowDistinctOperationSeparatedDigests() {
        var digester = new HmacMemoryIdempotencyDigester(provider(KEY_SECRET, REQUEST_SECRET));
        var first = digester.digest(context(KEY, "Javaが好き"));
        var sameRequest = digester.digest(context(KEY.toUpperCase(), " Javaが好き\r\n"));
        var changedRequest = digester.digest(context(KEY, "札幌で暮らす"));
        var changedKey = digester.digest(context("550e8400-e29b-41d4-a716-446655440001", "Javaが好き"));

        assertEquals(first, sameRequest);
        assertNotEquals(first.keyDigest(), changedKey.keyDigest());
        assertEquals(first.requestDigest(), changedKey.requestDigest());
        assertEquals(first.keyDigest(), changedRequest.keyDigest());
        assertNotEquals(first.requestDigest(), changedRequest.requestDigest());
        assertEquals(43, first.keyDigest().length());
        assertEquals(43, first.requestDigest().length());
    }

    @Test void unavailableSecretFailsClosedWithoutLeakingProviderDetails() {
        var digester = new HmacMemoryIdempotencyDigester(new MemoryIdempotencySecretProvider() {
            @Override public javax.crypto.SecretKey idempotencyKeyDigestSecret() {
                throw new IllegalStateException("secret path must not escape");
            }
            @Override public javax.crypto.SecretKey idempotencyRequestDigestSecret() { return key(REQUEST_SECRET); }
        });
        var error = assertThrows(RegisterIdempotencyUnavailableException.class,
                () -> digester.digest(context(KEY, "private content")));
        assertFalse(error.getMessage().contains("secret path"));
        assertFalse(error.getMessage().contains("private content"));
    }

    @Test void rejectsAccidentalReuseOfTheSameExportableSecretForBothDigestPurposes() {
        byte[] same = "same-secret-must-not-cross-purpose".getBytes(StandardCharsets.UTF_8);
        var error = assertThrows(RegisterIdempotencyUnavailableException.class,
                () -> new HmacMemoryIdempotencyDigester(provider(same, same)).digest(context(KEY, "content")));
        assertFalse(error.getMessage().contains("same-secret"));
    }

    private static RegisterMemoryIdempotencyContext context(String key, String content) {
        return RegisterMemoryIdempotencyContext.forRequest(key, content,
                MemoryCategory.ENGINEERING, SensitivityLevel.NORMAL);
    }
    private static MemoryIdempotencySecretProvider provider(byte[] keySecret, byte[] requestSecret) {
        return new MemoryIdempotencySecretProvider() {
            @Override public javax.crypto.SecretKey idempotencyKeyDigestSecret() { return key(keySecret); }
            @Override public javax.crypto.SecretKey idempotencyRequestDigestSecret() { return key(requestSecret); }
        };
    }
    private static javax.crypto.SecretKey key(byte[] value) { return new SecretKeySpec(value.clone(), "HmacSHA256"); }
}
