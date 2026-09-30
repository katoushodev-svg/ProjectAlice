package com.projectalice.backend.memory.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Opaque identity of a PersonalMemory.
 *
 * <p>The canonical representation is a lowercase, hyphenated UUID v4.
 * API clients must treat this value as an opaque string.</p>
 */
public record MemoryId(String value) {

    private static final Pattern CANONICAL_UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    public MemoryId {
        Objects.requireNonNull(value, "memoryId must not be null");
        if (!CANONICAL_UUID_V4.matcher(value).matches()) {
            throw new IllegalArgumentException("memoryId must be a canonical lowercase UUID v4");
        }
    }

    public static MemoryId generate() {
        return new MemoryId(UUID.randomUUID().toString());
    }
}
