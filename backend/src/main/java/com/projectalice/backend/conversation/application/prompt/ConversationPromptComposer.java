package com.projectalice.backend.conversation.application.prompt;

import java.util.Objects;

public final class ConversationPromptComposer {
    private final String baseIdentity;
    private final String replyPolicy;

    public ConversationPromptComposer(String baseIdentity, String replyPolicy) {
        this.baseIdentity = required(baseIdentity, "baseIdentity");
        this.replyPolicy = required(replyPolicy, "replyPolicy");
    }

    public String compose() { return baseIdentity + "\n\n---\n\n" + replyPolicy; }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
