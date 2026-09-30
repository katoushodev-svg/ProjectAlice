package com.projectalice.backend.memory.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MemoryContentTest {

    @Test
    void trimsOuterWhitespace() {
        MemoryContent content = new MemoryContent("  Javaが好き  ");

        assertEquals("Javaが好き", content.value());
    }

    @Test
    void rejectsBlankContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryContent("   "));
    }

    @Test
    void rejectsObviousSecretLikeContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryContent("api_key=very-secret-value"));
    }
}
