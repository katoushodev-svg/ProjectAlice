package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** Canonical HMAC-SHA-256 digest for staged target identity/version sets. */
public final class HmacRelationTargetSetDigester implements RelationTargetSetDigester {
    private static final byte[] DOMAIN = "project-alice/relation-review/target-set/v1\0".getBytes(StandardCharsets.US_ASCII);
    private final RelationTargetSetDigestKeyProvider keyProvider;

    public HmacRelationTargetSetDigester(RelationTargetSetDigestKeyProvider keyProvider) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
    }

    /** Fails closed if an operator accidentally configures the AEAD key as the HMAC key. */
    public void assertSeparateFrom(SecretKey relationAeadKey) {
        Objects.requireNonNull(relationAeadKey, "relationAeadKey must not be null");
        SecretKey digestKey = keyProvider.targetSetDigestKey();
        byte[] relationBytes = relationAeadKey.getEncoded();
        byte[] digestBytes = digestKey == null ? null : digestKey.getEncoded();
        try {
            if (relationBytes != null && digestBytes != null
                    && java.security.MessageDigest.isEqual(relationBytes, digestBytes))
                throw new RelationControlUnavailableException();
        } finally {
            if (relationBytes != null) java.util.Arrays.fill(relationBytes, (byte) 0);
            if (digestBytes != null) java.util.Arrays.fill(digestBytes, (byte) 0);
        }
    }

    @Override public String digestDraft(RelationReviewDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        List<TargetIdentity> identities = new ArrayList<>(draft.targets().size());
        for (int i = 0; i < draft.targets().size(); i++) {
            var target = draft.targets().get(i);
            identities.add(new TargetIdentity(i, target.snapshot().memoryId().value(), target.preview().expectedTargetVersion()));
        }
        return digestTargets(identities);
    }

    @Override public String digestTargets(List<TargetIdentity> targets) {
        Objects.requireNonNull(targets, "targets must not be null");
        if (targets.isEmpty() || targets.size() > 100) throw new IllegalArgumentException("target count must be 1..100");
        List<TargetIdentity> ordered = targets.stream().sorted(Comparator.comparingInt(TargetIdentity::ordinal)).toList();
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).ordinal() != i)
            throw new IllegalArgumentException("target ordinals must be contiguous from zero");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                out.writeInt(ordered.size());
                for (TargetIdentity target : ordered) {
                    byte[] id = target.memoryId().getBytes(StandardCharsets.US_ASCII);
                    out.writeInt(target.ordinal());
                    out.writeInt(id.length);
                    out.write(id);
                    out.writeLong(target.expectedVersion());
                }
            }
            SecretKey key = keyProvider.targetSetDigestKey();
            if (key == null || !"HmacSHA256".equalsIgnoreCase(key.getAlgorithm()))
                throw new java.security.GeneralSecurityException("missing target digest key");
            byte[] encodedKey = key.getEncoded();
            try {
                if (encodedKey != null && encodedKey.length < 32)
                    throw new java.security.GeneralSecurityException("target digest key is too short");
            } finally {
                if (encodedKey != null) java.util.Arrays.fill(encodedKey, (byte) 0);
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(DOMAIN);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(bytes.toByteArray()));
        } catch (Exception failure) {
            throw new RelationControlUnavailableException();
        }
    }
}
