package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TermDigestServiceTest {

    private static final byte[] KEY_V1 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_V2 = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    @Test
    void isDeterministicForSameVersionKeyAnalyzerAndTerm() {
        var service = new TermDigestService(version -> version == 1 ? KEY_V1.clone() : KEY_V2.clone());

        var first = service.digest("KW_V1", "東京", 1);
        var second = service.digest("KW_V1", "東京", 1);

        assertEquals(first, second);
        assertEquals(1, first.keyVersion());
        assertEquals(43, first.value().length());
        assertTrue(Base64.getUrlDecoder().decode(first.value()).length == 32);
        assertTrue(!first.value().contains("="));
    }

    @Test
    void usesDistinctDigestForDifferentAnalyzerVersions() {
        var service = new TermDigestService(version -> KEY_V1.clone());

        assertNotEquals(service.digest("KW_V1", "alice", 1).value(),
                service.digest("KW_V2", "alice", 1).value());
    }

    @Test
    void usesDistinctDigestForDifferentNormalizedTerms() {
        var service = new TermDigestService(version -> KEY_V1.clone());

        assertNotEquals(service.digest("KW_V1", "alice", 1).value(),
                service.digest("KW_V1", "alicia", 1).value());
    }

    @Test
    void usesRequestedKeyVersionForRotation() {
        var service = new TermDigestService(version -> version == 1 ? KEY_V1.clone() : KEY_V2.clone());

        var oldDigest = service.digest("KW_V1", "alice", 1);
        var newDigest = service.digest("KW_V1", "alice", 2);

        assertEquals(1, oldDigest.keyVersion());
        assertEquals(2, newDigest.keyVersion());
        assertNotEquals(oldDigest.value(), newDigest.value());
    }

    @Test
    void rejectsInvalidInputAndMissingOrShortKeys() {
        var service = new TermDigestService(version -> KEY_V1.clone());
        assertThrows(IllegalArgumentException.class, () -> service.digest(" ", "alice", 1));
        assertThrows(IllegalArgumentException.class, () -> service.digest("KW_V1", " ", 1));
        assertThrows(IllegalArgumentException.class, () -> service.digest("KW_V1", "alice", 0));
        assertThrows(IllegalStateException.class, () -> new TermDigestService(version -> null)
                .digest("KW_V1", "alice", 1));
        assertThrows(IllegalStateException.class, () -> new TermDigestService(version -> new byte[16])
                .digest("KW_V1", "alice", 1));
    }
}
