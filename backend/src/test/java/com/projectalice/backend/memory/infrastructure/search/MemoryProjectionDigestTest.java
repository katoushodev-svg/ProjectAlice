package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.domain.MemoryId;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryProjectionDigestTest {

    private static final byte[] KEY_V1 = "synthetic-projection-key-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_V2 = "synthetic-projection-key-v2".getBytes(StandardCharsets.UTF_8);
    private final MemoryProjectionDigestCanonicalEncoder encoder = new MemoryProjectionDigestCanonicalEncoder();

    @Test
    void generatesFixedPurposeSeparatedVersionedHmacAndVerifiesIt() {
        MemoryProjectionDigest digest = new MemoryProjectionDigest(encoder, Map.of(1, KEY_V1), 1);
        var input = emptyInput(1);

        MemoryProjectionDigest.VersionedDigest generated = digest.digestForWrite(input);

        assertEquals(1, generated.projectionDigestKeyVersion());
        assertEquals("xTyffK8qO6DD-LyPdG7x3h_-BIXBpgqLEv0rcX1m8n8", generated.value());
        assertEquals(43, generated.value().length());
        assertTrue(generated.value().matches("[A-Za-z0-9_-]{43}"));
        assertTrue(digest.verify(1, input, generated.value()));
        assertFalse(digest.verify(1, emptyInput(2), generated.value()));
        assertFalse(digest.verify(1, input, "!"));
        assertFalse(digest.verify(1, input, "A".repeat(42)));
        assertFalse(digest.verify(1, input, "A".repeat(43)));
    }

    @Test
    void bindsTermKeyVersionAndKeepsProjectionDigestKeyVersionIndependent() {
        MemoryProjectionDigest digest = new MemoryProjectionDigest(encoder, Map.of(1, KEY_V1, 2, KEY_V2), 1);
        var termKeyVersion1 = emptyInput(1);
        var termKeyVersion2 = emptyInput(2);
        String value = digest.digestForWrite(termKeyVersion1).value();

        assertNotEquals(value, digest.digestForWrite(termKeyVersion2).value());
        assertTrue(digest.verify(1, termKeyVersion1, value));
        assertFalse(digest.verify(2, termKeyVersion1, value));
        assertThrows(IllegalArgumentException.class, () -> digest.verify(3, termKeyVersion1, value));
        assertEquals(1, digest.digestForWrite(termKeyVersion2).projectionDigestKeyVersion());
    }

    @Test
    void separatesProjectionPurposeFromTermDigestAndDoesNotExposeKeyMaterial() {
        MemoryProjectionDigest digest = new MemoryProjectionDigest(encoder, Map.of(1, KEY_V1), 1);
        MemorySearchTermDigest termDigest = new MemorySearchTermDigest(Map.of(1, KEY_V1), 1);
        String projectionValue = digest.digestForWrite(emptyInput(1)).value();

        assertNotEquals(projectionValue, termDigest.digestForWrite("KW_V1", "term"));
        assertTrue(Arrays.stream(MemoryProjectionDigest.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase(java.util.Locale.ROOT).contains("keymaterial")));
    }

    @Test
    void copiesKeyBytesAndValidatesVersionedKeyConfiguration() {
        byte[] mutableKey = KEY_V1.clone();
        MemoryProjectionDigest digest = new MemoryProjectionDigest(encoder, Map.of(1, mutableKey), 1);
        String expected = digest.digestForWrite(emptyInput(1)).value();
        Arrays.fill(mutableKey, (byte) 0);

        assertEquals(expected, digest.digestForWrite(emptyInput(1)).value());
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryProjectionDigest(encoder, Map.of(1, KEY_V1), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryProjectionDigest(encoder, Map.of(0, KEY_V1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryProjectionDigest(encoder, Map.of(1, new byte[0]), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryProjectionDigest(encoder, Map.of(1, KEY_V1, 2, KEY_V2, 3, KEY_V1), 1));
        assertEquals(java.util.List.of(1), digest.readableKeyVersions());
    }

    private MemoryProjectionDigestCanonicalEncoder.Input emptyInput(int tokenKeyVersion) {
        return new MemoryProjectionDigestCanonicalEncoder.Input(
                new MemoryId("00000000-0000-4000-8000-000000000001"),
                1,
                "KW_V1",
                tokenKeyVersion,
                0,
                0,
                java.util.List.of());
    }
}
