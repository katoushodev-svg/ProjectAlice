package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MemorySearchTermDigestTest {

    private static final byte[] KEY_V1 = "synthetic-memory-search-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void usesPurposeSeparatedVersionedHmacAndBase64UrlWithoutPadding() {
        MemorySearchTermDigest digest = new MemorySearchTermDigest(Map.of(1, KEY_V1), 1);

        String value = digest.digestForWrite("KW_V1", "札幌");

        assertEquals("xByOEBw-3aY0WaUv7Lo4rcHxEp6UonFD6Ht-t3RQo-Q", value);
        assertEquals(value, digest.digestForRead(1, "KW_V1", "札幌"));
        assertEquals(43, value.length());
        assertTrue(value.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(value, digest.digestForWrite("KW_V1", "札幌市"));
        assertNotEquals(value, digest.digestForWrite("KW_V2", "札幌"));
    }

    @Test
    void supportsAtMostTwoReadableVersionsAndOneWriteVersion() {
        MemorySearchTermDigest digest = new MemorySearchTermDigest(Map.of(1, KEY_V1, 2, "synthetic-v2".getBytes()), 2);

        assertEquals(2, digest.writeKeyVersion());
        assertThrows(IllegalArgumentException.class, () -> digest.digestForRead(3, "KW_V1", "term"));
        assertThrows(IllegalArgumentException.class, () -> new MemorySearchTermDigest(
                Map.of(1, KEY_V1, 2, new byte[] {1}, 3, new byte[] {2}), 1));
    }

    @Test
    void copiesProvidedKeyBytesAndDoesNotDependOnLaterCallerMutation() {
        byte[] mutableKey = KEY_V1.clone();
        MemorySearchTermDigest digest = new MemorySearchTermDigest(Map.of(1, mutableKey), 1);
        String expected = digest.digestForWrite("KW_V1", "札幌");
        java.util.Arrays.fill(mutableKey, (byte) 0);

        assertEquals(expected, digest.digestForWrite("KW_V1", "札幌"));
    }

    @Test
    void rejectsInvalidKeyConfigurationWithoutExposingKeyMaterial() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemorySearchTermDigest(Map.of(1, KEY_V1), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new MemorySearchTermDigest(Map.of(1, new byte[0]), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MemorySearchTermDigest(Map.of(0, KEY_V1), 0));
        assertTrue(java.util.Arrays.stream(MemorySearchTermDigest.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase(java.util.Locale.ROOT).contains("keymaterial")));
    }
}
