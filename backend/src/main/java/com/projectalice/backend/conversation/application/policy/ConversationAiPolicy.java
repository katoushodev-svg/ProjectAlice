package com.projectalice.backend.conversation.application.policy;

import java.time.Duration;
import java.util.Objects;

public record ConversationAiPolicy(
        long maxInputTokens,
        int maxHistoryMessages,
        Duration useCaseTimeout
) {
    private static final int MAX_HISTORY_MESSAGES_LIMIT = 100;

    public ConversationAiPolicy {
        if (maxInputTokens < 1) {
            throw new IllegalArgumentException("maxInputTokens must be at least 1");
        }
        if (maxHistoryMessages < 1 || maxHistoryMessages > MAX_HISTORY_MESSAGES_LIMIT) {
            throw new IllegalArgumentException("maxHistoryMessages must be between 1 and 100");
        }
        Objects.requireNonNull(useCaseTimeout, "useCaseTimeout must not be null");
    }
}
