package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JceRelationAeadTest {
    private static final String REVIEW_ID = "8a1f8e2a-4b3c-4d5e-8f60-123456789abc";
    private static final byte[] PLAINTEXT = "private candidate and preview".getBytes(StandardCharsets.UTF_8);
    private static final AtomicLong NONCE_COUNTER = new AtomicLong();

    @Test
    void encryptsAndDecryptsWithVersionBoundRelationKey() {
        var provider = new TestKeyProvider(4, key(7));
        var aead = aead(provider);
        var binding = candidateBinding(4);

        var envelope = aead.encrypt(PLAINTEXT, binding);

        assertNotEquals(Arrays.toString(PLAINTEXT), Arrays.toString(envelope.ciphertext()));
        assertArrayEquals(PLAINTEXT, aead.decrypt(envelope, binding));
        assertTrue(envelope.toString().contains("protected payload"));
        assertTrue(!envelope.toString().contains(new String(PLAINTEXT, StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsCiphertextNonceAndAadTampering() {
        var aead = aead(new TestKeyProvider(4, key(7)));
        var binding = candidateBinding(4);
        var envelope = aead.encrypt(PLAINTEXT, binding);

        byte[] changedCiphertext = envelope.ciphertext();
        changedCiphertext[0] ^= 1;
        assertThrows(RelationCryptoException.class, () -> aead.decrypt(new RelationCiphertext(
                envelope.aadSchemaVersion(), envelope.keyVersion(), envelope.nonce(), changedCiphertext), binding));
        byte[] changedNonce = envelope.nonce();
        changedNonce[0] ^= 1;
        assertThrows(RelationCryptoException.class, () -> aead.decrypt(new RelationCiphertext(
                envelope.aadSchemaVersion(), envelope.keyVersion(), changedNonce, envelope.ciphertext()), binding));
        assertThrows(RelationCryptoException.class, () -> aead.decrypt(envelope,
                new RelationStateBinding(REVIEW_ID, 2, RelationStateBinding.SourceOperation.REGISTER,
                        RelationStateBinding.PayloadKind.CANDIDATE, -1)));
        assertThrows(RelationCryptoException.class, () -> aead.decrypt(envelope,
                new RelationStateBinding(REVIEW_ID, 1, RelationStateBinding.SourceOperation.UPDATE,
                        RelationStateBinding.PayloadKind.CANDIDATE, -1)));
        assertThrows(RelationCryptoException.class, () -> aead.decrypt(envelope,
                new RelationStateBinding(REVIEW_ID, 1, RelationStateBinding.SourceOperation.REGISTER,
                        RelationStateBinding.PayloadKind.TARGET_PREVIEW, 0)));
    }

    @Test
    void rejectsUnknownOrMismatchedKeyVersionWithoutFallback() {
        var aead = aead(new TestKeyProvider(4, key(7)));
        var envelope = aead.encrypt(PLAINTEXT, candidateBinding(4));
        var mismatchedVersionEnvelope = new RelationCiphertext(envelope.aadSchemaVersion(), 5,
                envelope.nonce(), envelope.ciphertext());
        assertThrows(RelationCryptoException.class, () -> aead.decrypt(mismatchedVersionEnvelope,
                candidateBinding(4)));
        assertThrows(RelationCryptoException.class, () -> aead(new TestKeyProvider(5, key(7)))
                .decrypt(envelope, candidateBinding(4)));
        assertThrows(RelationCryptoException.class, () -> aead(new TestKeyProvider(4, key(8)))
                .decrypt(envelope, candidateBinding(4)));
    }

    @Test
    void providerFailureIsSanitizedAndNeverReturnsPlaintext() {
        RelationStateKeyProvider failing = new RelationStateKeyProvider() {
            @Override public void probeRelationPurpose() { throw new IllegalStateException("secret detail"); }
            @Override public KeyMaterial activeEncryptionKey() { throw new IllegalStateException("secret detail"); }
            @Override public SecretKey decryptionKey(int version) { throw new IllegalStateException("secret detail"); }
        };
        var error = assertThrows(RelationCryptoException.class,
                () -> aead(failing).encrypt(PLAINTEXT, candidateBinding(4)));
        assertTrue(!error.getMessage().contains("secret detail"));
        assertTrue(error.getCause() == null);
        assertThrows(RelationCryptoException.class, () -> aead(failing)
                .decrypt(new RelationCiphertext(1, 4, new byte[12], new byte[16]), candidateBinding(4)));
    }

    @Test
    void rejectsMalformedEnvelopeAndOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> new RelationCiphertext(1, 4,
                new byte[11], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new RelationCiphertext(2, 4,
                new byte[12], new byte[16]));
        var aead = aead(new TestKeyProvider(4, key(7)));
        assertThrows(IllegalArgumentException.class, () -> aead.encrypt(
                new byte[RelationCiphertext.MAX_CIPHERTEXT_BYTES], candidateBinding(4)));
    }

    private static RelationStateBinding candidateBinding(int keyVersion) {
        // keyVersion is bound by JceRelationAead from the exact provider material version.
        if (keyVersion < 1) throw new IllegalArgumentException();
        return new RelationStateBinding(REVIEW_ID, 1, RelationStateBinding.SourceOperation.REGISTER,
                RelationStateBinding.PayloadKind.CANDIDATE, -1);
    }

    private static SecretKey key(int seed) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (seed + i);
        return new SecretKeySpec(bytes, "AES");
    }

    private static JceRelationAead aead(RelationStateKeyProvider keyProvider) {
        return new JceRelationAead(keyProvider, version -> {
            long value = NONCE_COUNTER.incrementAndGet();
            return java.nio.ByteBuffer.allocate(12).putInt(version).putLong(value).array();
        });
    }

    private static final class TestKeyProvider implements RelationStateKeyProvider {
        private final int version;
        private final SecretKey key;
        private TestKeyProvider(int version, SecretKey key) { this.version = version; this.key = key; }
        @Override public void probeRelationPurpose() { }
        @Override public KeyMaterial activeEncryptionKey() { return new KeyMaterial(version, key); }
        @Override public SecretKey decryptionKey(int requestedVersion) {
            if (requestedVersion != version) throw new IllegalStateException("not available");
            return key;
        }
    }
}
