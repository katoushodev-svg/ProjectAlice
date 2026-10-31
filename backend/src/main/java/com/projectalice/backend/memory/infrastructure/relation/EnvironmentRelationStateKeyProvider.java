package com.projectalice.backend.memory.infrastructure.relation;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reads Relation AES-256 keys injected by the deployment secret manager into environment variables.
 * Expected variables: ALICE_RELATION_STATE_WRITE_KEY_VERSION and
 * ALICE_RELATION_STATE_KEY_V{version}_BASE64. This class never supplies a fallback key.
 */
public final class EnvironmentRelationStateKeyProvider implements RelationStateKeyProvider {
    private final int writeVersion;
    private final Map<Integer, SecretKey> keys;

    public EnvironmentRelationStateKeyProvider() {
        this(System.getenv());
    }

    public EnvironmentRelationStateKeyProvider(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment must not be null");
        String versionText = environment.get("ALICE_RELATION_STATE_WRITE_KEY_VERSION");
        if (versionText == null || !versionText.matches("[1-9][0-9]{0,8}"))
            throw new RelationControlUnavailableException();
        try {
            this.writeVersion = Integer.parseInt(versionText);
            if (writeVersion > 1_000_000) throw new RelationControlUnavailableException();
            java.util.HashMap<Integer, SecretKey> loaded = new java.util.HashMap<>();
            java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile("ALICE_RELATION_STATE_KEY_V([1-9][0-9]{0,8})_BASE64");
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                var matcher = namePattern.matcher(entry.getKey());
                if (!matcher.matches()) continue;
                int version = Integer.parseInt(matcher.group(1));
                if (version > writeVersion) throw new RelationControlUnavailableException();
                String encoded = entry.getValue();
                if (encoded == null || encoded.isBlank()) continue;
                byte[] bytes = Base64.getDecoder().decode(encoded);
                try {
                    if (bytes.length != 32) throw new RelationControlUnavailableException();
                    loaded.put(version, new SecretKeySpec(bytes, "AES"));
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
            }
            if (!loaded.containsKey(writeVersion)) throw new RelationControlUnavailableException();
            if (loaded.isEmpty() || loaded.size() > 2) throw new RelationControlUnavailableException();
            this.keys = Map.copyOf(loaded);
        } catch (IllegalArgumentException invalid) {
            throw new RelationControlUnavailableException();
        }
    }

    @Override public void probeRelationPurpose() {
        // Validate local JCE AES-GCM capability with an ephemeral probe key. A production
        // Relation key and its monotonic nonce stream are never used for this probe.
        SecretKey active = activeEncryptionKey().key();
        byte[] probePlaintext = new byte[] { 0x41, 0x4c, 0x49, 0x43, 0x45 };
        byte[] nonce = new byte[12];
        byte[] aad = new byte[] { 0x52, 0x45, 0x4c, 0x41, 0x54, 0x49, 0x4f, 0x4e };
        byte[] probeKeyBytes = null;
        byte[] encrypted = null;
        byte[] recovered = null;
        byte[] activeBytes = null;
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey probeKey = generator.generateKey();
            probeKeyBytes = probeKey.getEncoded();
            new SecureRandom().nextBytes(nonce);
            Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, probeKey, new GCMParameterSpec(128, nonce));
            encrypt.updateAAD(aad);
            encrypted = encrypt.doFinal(probePlaintext);
            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, probeKey, new GCMParameterSpec(128, nonce));
            decrypt.updateAAD(aad);
            recovered = decrypt.doFinal(encrypted);
            activeBytes = active.getEncoded();
            if (!java.security.MessageDigest.isEqual(probePlaintext, recovered)
                    || activeBytes == null || activeBytes.length != 32)
                throw new RelationControlUnavailableException();
        } catch (GeneralSecurityException failure) {
            throw new RelationControlUnavailableException();
        } finally {
            if (activeBytes != null) Arrays.fill(activeBytes, (byte) 0);
            if (probeKeyBytes != null) Arrays.fill(probeKeyBytes, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            if (recovered != null) Arrays.fill(recovered, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(probePlaintext, (byte) 0);
            Arrays.fill(aad, (byte) 0);
        }
    }

    @Override public KeyMaterial activeEncryptionKey() {
        SecretKey key = keys.get(writeVersion);
        if (key == null) throw new RelationControlUnavailableException();
        return new KeyMaterial(writeVersion, key);
    }

    @Override public SecretKey decryptionKey(int keyVersion) {
        SecretKey key = keys.get(keyVersion);
        if (key == null) throw new RelationControlUnavailableException();
        return key;
    }
}
