package com.projectalice.backend.memory.infrastructure.relation;

import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM Relation payload protection; key material is supplied only through the dedicated provider. */
public final class JceRelationAead implements RelationAead {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final RelationStateKeyProvider keyProvider;
    private final RelationNonceProvider nonceProvider;

    public JceRelationAead(RelationStateKeyProvider keyProvider, RelationNonceProvider nonceProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.nonceProvider = Objects.requireNonNull(nonceProvider, "nonceProvider must not be null");
    }

    @Override public RelationCiphertext encrypt(byte[] plaintext, RelationStateBinding binding) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
        if (plaintext.length > RelationCiphertext.MAX_CIPHERTEXT_BYTES - RelationCiphertext.TAG_BYTES)
            throw new IllegalArgumentException("protected payload exceeds size limit");
        try {
            RelationStateKeyProvider.KeyMaterial material = keyProvider.activeEncryptionKey();
            if (material == null || !isAes(material.key())) throw new RelationCryptoException();
            byte[] nonce = nonceProvider.reserveNonce(material.version());
            if (nonce == null || nonce.length != RelationCiphertext.NONCE_BYTES)
                throw new RelationCryptoException();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, material.key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(binding.canonicalAad(material.version()));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new RelationCiphertext(RelationStateBinding.AAD_SCHEMA_VERSION,
                    material.version(), nonce, ciphertext);
        } catch (RelationCryptoException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new RelationCryptoException();
        }
    }

    @Override public byte[] decrypt(RelationCiphertext envelope, RelationStateBinding expectedBinding) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(expectedBinding, "expectedBinding must not be null");
        if (envelope.aadSchemaVersion() != RelationStateBinding.AAD_SCHEMA_VERSION)
            throw new RelationCryptoException();
        try {
            SecretKey key = keyProvider.decryptionKey(envelope.keyVersion());
            if (!isAes(key)) throw new RelationCryptoException();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, envelope.nonce()));
            cipher.updateAAD(expectedBinding.canonicalAad(envelope.keyVersion()));
            return cipher.doFinal(envelope.ciphertext());
        } catch (RelationCryptoException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new RelationCryptoException();
        }
    }

    private static boolean isAes(SecretKey key) {
        if (key == null || !"AES".equalsIgnoreCase(key.getAlgorithm())) return false;
        byte[] encoded = key.getEncoded();
        if (encoded == null) return true; // Non-exportable provider keys are validated by the provider contract.
        try { return encoded.length == 32; }
        finally { java.util.Arrays.fill(encoded, (byte) 0); }
    }
}
