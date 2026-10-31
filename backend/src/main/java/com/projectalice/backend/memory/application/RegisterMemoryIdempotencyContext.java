package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Transient, canonical input for MEMORY_REGISTER idempotency. The raw key and canonical
 * request bytes must be converted to the design's domain-separated HMACs before persistence.
 */
public final class RegisterMemoryIdempotencyContext {
    public static final String OPERATION_TYPE = "MEMORY_REGISTER";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final int CANONICAL_SCHEMA_VERSION = 1;

    private final String canonicalKey;
    private final byte[] canonicalRequestBytes;

    private RegisterMemoryIdempotencyContext(String canonicalKey, byte[] canonicalRequestBytes) {
        this.canonicalKey = canonicalKey;
        this.canonicalRequestBytes = canonicalRequestBytes.clone();
    }

    /** Builds canonical identity from a raw API content value and validated, resolved fields. */
    public static RegisterMemoryIdempotencyContext forRequest(String key, String rawContent,
            MemoryCategory category, SensitivityLevel sensitivityLevel) {
        if (key == null) throw new RegisterMemoryIdempotencyKeyRequiredException();
        if (!UUID_PATTERN.matcher(key).matches()) throw new InvalidRegisterIdempotencyKeyException();
        final String canonicalKey;
        try {
            canonicalKey = UUID.fromString(key).toString();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRegisterIdempotencyKeyException();
        }
        Objects.requireNonNull(rawContent, "content must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(sensitivityLevel, "sensitivityLevel must not be null");
        String canonicalContent = canonicalizeContent(rawContent);
        return new RegisterMemoryIdempotencyContext(canonicalKey,
                canonicalBytes(canonicalContent, category, sensitivityLevel));
    }

    /** Applies API Design §32.7-32.8 before request identity is constructed. */
    public static String canonicalizeContent(String rawContent) {
        Objects.requireNonNull(rawContent, "content must not be null");
        String normalized = Normalizer.normalize(rawContent.replace("\r\n", "\n").replace('\r', '\n'),
                Normalizer.Form.NFC);
        int start = 0;
        int end = normalized.length();
        while (start < end) {
            int cp = normalized.codePointAt(start);
            if (!isUnicodeWhitespace(cp)) break;
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = normalized.codePointBefore(end);
            if (!isUnicodeWhitespace(cp)) break;
            end -= Character.charCount(cp);
        }
        String canonical = normalized.substring(start, end);
        int codePoints = canonical.codePointCount(0, canonical.length());
        int utf8Bytes = canonical.getBytes(StandardCharsets.UTF_8).length;
        if (codePoints < 1 || codePoints > 2_000 || utf8Bytes > 8_192
                || canonical.codePoints().anyMatch(cp -> cp != '\n'
                        && (cp <= 0x1f || cp >= 0x7f && cp <= 0x9f))) {
            throw new InvalidRegisterMemoryRequestException();
        }
        return canonical;
    }

    private static boolean isUnicodeWhitespace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    public String operationType() { return OPERATION_TYPE; }

    /** Transient input only; persistence must store a purpose-separated HMAC digest instead. */
    public String canonicalKeyForBinding() { return canonicalKey; }

    /** Canonical request bytes are sensitive and must never be logged or persisted directly. */
    public byte[] canonicalRequestBytesForDigest() { return canonicalRequestBytes.clone(); }

    /** Pure identity comparison for callers reconciling a previously read Binding. */
    public boolean identifiesSameRequest(RegisterMemoryIdempotencyContext other) {
        return other != null && canonicalKey.equals(other.canonicalKey)
                && MessageDigest.isEqual(canonicalRequestBytes, other.canonicalRequestBytes);
    }

    private static byte[] canonicalBytes(String content, MemoryCategory category,
            SensitivityLevel sensitivityLevel) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(contentBytes.length + 64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(CANONICAL_SCHEMA_VERSION);
            writeField(out, "content", contentBytes);
            writeField(out, "category", category.name().getBytes(StandardCharsets.US_ASCII));
            writeField(out, "sensitivityLevel", sensitivityLevel.name().getBytes(StandardCharsets.US_ASCII));
            writeField(out, "state", MemoryState.ACTIVE.name().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not canonicalize register request");
        }
    }

    private static void writeField(DataOutputStream out, String name, byte[] value) throws IOException {
        byte[] nameBytes = name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        out.writeInt(nameBytes.length);
        out.write(nameBytes);
        out.writeInt(value.length);
        out.write(value);
    }

    @Override public String toString() {
        return "RegisterMemoryIdempotencyContext[operationType=" + OPERATION_TYPE + ", redacted=true]";
    }
}
