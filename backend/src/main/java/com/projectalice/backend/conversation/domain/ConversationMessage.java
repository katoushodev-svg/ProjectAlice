package com.projectalice.backend.conversation.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public record ConversationMessage(
        String messageId,
        long sequence,
        ConversationMessageRole role,
        String content,
        OffsetDateTime createdAt
) {
    private static final ZoneOffset JST = ZoneOffset.ofHours(9);
    private static final int NANOS_PER_MILLI = 1_000_000;

    public ConversationMessage {
        Objects.requireNonNull(messageId, "messageId must not be null");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (!createdAt.getOffset().equals(JST)) {
            throw new IllegalArgumentException("createdAt must use the JST offset");
        }
        if (createdAt.getNano() % NANOS_PER_MILLI != 0) {
            throw new IllegalArgumentException("createdAt must have millisecond precision");
        }
    }
}
