package com.projectalice.backend.memory.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * User-readable content of a PersonalMemory.
 *
 * <p>Detailed maximum character/UTF-8 byte limits are intentionally deferred
 * to the API/database design. Domain-level validation here only enforces the
 * invariants that are already fixed by the memory design.</p>
 */
public record MemoryContent(String value) {

    private static final Pattern API_KEY_LIKE = Pattern.compile(
            "(?i)(api[_ -]?key|access[_ -]?token|secret[_ -]?key)\\s*[:=]\\s*\\S+");
    private static final Pattern BEARER_TOKEN_LIKE = Pattern.compile(
            "(?i)bearer\\s+[A-Za-z0-9._~+/=-]{12,}");
    private static final Pattern CREDENTIAL_LIKE = Pattern.compile(
            "(?i)(password|passwd|credential|authentication[_ -]?token)\\s*[:=]\\s*\\S+");

    public MemoryContent {
        Objects.requireNonNull(value, "content must not be null");
        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (looksLikeSecret(value)) {
            throw new IllegalArgumentException("secrets and credentials must not be stored as memory");
        }
    }

    private static boolean looksLikeSecret(String value) {
        return API_KEY_LIKE.matcher(value).find()
                || BEARER_TOKEN_LIKE.matcher(value).find()
                || CREDENTIAL_LIKE.matcher(value).find();
    }
}
