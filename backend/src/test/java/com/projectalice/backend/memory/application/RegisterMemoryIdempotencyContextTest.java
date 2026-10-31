package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.*;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import org.junit.jupiter.api.Test;

class RegisterMemoryIdempotencyContextTest {
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Test void acceptsCanonicalUuidAndNormalizesCaseForIdentity() {
        var lower = request(KEY, "札幌で暮らす", MemoryCategory.PLACE, SensitivityLevel.NORMAL);
        var upper = request(KEY.toUpperCase(), "札幌で暮らす", MemoryCategory.PLACE, SensitivityLevel.NORMAL);
        assertEquals(KEY, upper.canonicalKeyForBinding());
        assertTrue(lower.identifiesSameRequest(upper));
        assertEquals("MEMORY_REGISTER", lower.operationType());
    }

    @Test void rejectsMissingMalformedAndNonUuidKeysWithoutEchoingThem() {
        assertThrows(RegisterMemoryIdempotencyKeyRequiredException.class, () -> request(null, "x", MemoryCategory.OTHER,
                SensitivityLevel.NORMAL));
        for (String key : new String[] { "", "not-a-uuid", "550e8400-e29b-41d4-a716-44665544000z" }) {
            var error = assertThrows(InvalidRegisterIdempotencyKeyException.class,
                    () -> request(key, "x", MemoryCategory.OTHER, SensitivityLevel.NORMAL));
            if (!key.isEmpty()) assertFalse(error.getMessage().contains(key));
        }
    }

    @Test void sameKeyAndCanonicalRequestMatchesButDifferentCanonicalRequestConflicts() {
        var first = request(KEY, "Javaを使う", MemoryCategory.ENGINEERING, SensitivityLevel.NORMAL);
        assertTrue(first.identifiesSameRequest(
                request(KEY, "Javaを使う", MemoryCategory.ENGINEERING, SensitivityLevel.NORMAL)));
        assertFalse(first.identifiesSameRequest(
                request(KEY, "Javaを使う", MemoryCategory.PROFILE, SensitivityLevel.NORMAL)));
        assertFalse(first.identifiesSameRequest(
                request("550e8400-e29b-41d4-a716-446655440001", "Javaを使う",
                        MemoryCategory.ENGINEERING, SensitivityLevel.NORMAL)));
    }

    @Test void requestNormalizationMakesLineEndingsNfcAndOuterUnicodeWhitespaceEquivalent() {
        var composed = request(KEY, "é\n札幌", MemoryCategory.PLACE, SensitivityLevel.NORMAL);
        var decomposed = request(KEY.toUpperCase(), "　e\u0301\r\n札幌\u00a0",
                MemoryCategory.PLACE, SensitivityLevel.NORMAL);
        assertTrue(composed.identifiesSameRequest(decomposed));
        assertEquals("é\n札幌", RegisterMemoryIdempotencyContext.canonicalizeContent(
                "　e\u0301\r\n札幌\u00a0"));
    }

    @Test void rejectsContentOutsideApiCanonicalLimits() {
        assertThrows(IllegalArgumentException.class, () -> RegisterMemoryIdempotencyContext
                .canonicalizeContent("\n\t"));
        assertThrows(IllegalArgumentException.class, () -> RegisterMemoryIdempotencyContext
                .canonicalizeContent("x".repeat(2_001)));
        assertThrows(IllegalArgumentException.class, () -> RegisterMemoryIdempotencyContext
                .canonicalizeContent("x\u0000y"));
    }

    @Test void canonicalBytesAreDefensivelyCopiedAndContextDoesNotRevealRequest() {
        var context = request(KEY, "機微な本文", MemoryCategory.OTHER, SensitivityLevel.SENSITIVE);
        byte[] first = context.canonicalRequestBytesForDigest();
        int last = first.length - 1;
        first[last] = (byte) (first[last] ^ 0x7f);
        assertNotEquals(first[last], context.canonicalRequestBytesForDigest()[last]);
        assertFalse(context.toString().contains("機微な本文"));
        assertFalse(context.toString().contains(KEY));
    }

    private static RegisterMemoryIdempotencyContext request(String key, String content,
            MemoryCategory category, SensitivityLevel sensitivity) {
        return RegisterMemoryIdempotencyContext.forRequest(key, content, category, sensitivity);
    }
}
