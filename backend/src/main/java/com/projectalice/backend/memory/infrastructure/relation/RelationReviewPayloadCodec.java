package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Versioned canonical binary encoding for plaintext that is immediately AEAD-encrypted. */
public final class RelationReviewPayloadCodec {
    private static final int VERSION = 1;

    public byte[] encodeCandidate(RelationReviewDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        return encode(out -> {
            out.writeInt(VERSION);
            field(out, "relationType", draft.relationType().name());
            field(out, "content", draft.proposedContent().value());
            field(out, "category", draft.proposedCategory().name());
            field(out, "sensitivityLevel", draft.proposedSensitivityLevel().name());
        });
    }

    public byte[] encodeTargetPreview(RelationReviewDraft draft, RelationReviewDraft.Target target) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(target, "target must not be null");
        return encode(out -> {
            out.writeInt(VERSION);
            field(out, "operation", target.preview().operation().name());
            field(out, "relationType", target.preview().relationType().name());
            field(out, "targetMemoryId", target.preview().targetMemoryId().value());
            out.writeLong(target.preview().expectedTargetVersion());
            field(out, "proposedContent", target.preview().proposedContent().value());
            field(out, "proposedCategory", target.preview().proposedCategory().name());
            field(out, "proposedSensitivityLevel", target.preview().proposedSensitivityLevel().name());
        });
    }

    private static byte[] encode(Encoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { encoder.write(out); }
            byte[] result = bytes.toByteArray();
            if (result.length > RelationCiphertext.MAX_CIPHERTEXT_BYTES - RelationCiphertext.TAG_BYTES)
                throw new IllegalArgumentException("Review payload exceeds the encrypted payload limit");
            return result;
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode Review payload");
        }
    }

    private static void field(DataOutputStream out, String name, String value) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(nameBytes.length);
        out.write(nameBytes);
        out.writeInt(valueBytes.length);
        out.write(valueBytes);
    }

    @FunctionalInterface private interface Encoder { void write(DataOutputStream out) throws IOException; }
}
