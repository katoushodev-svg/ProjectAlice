package com.projectalice.backend.memory.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryIdTest {

    @Test
    void generateCreatesCanonicalUuidV4() {
        MemoryId id = MemoryId.generate();

        assertDoesNotThrow(() -> UUID.fromString(id.value()));
        assertTrue(id.value().matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"));
    }

    @Test
    void rejectsNonCanonicalValue() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryId("550E8400-E29B-41D4-A716-446655440000"));
    }
}
