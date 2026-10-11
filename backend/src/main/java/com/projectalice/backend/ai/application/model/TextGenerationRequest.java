package com.projectalice.backend.ai.application.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TextGenerationRequest(
        AiWorkload workload,
        String instructions,
        List<AiMessage> messages,
        List<AiContextBlock> contextBlocks
) {
    public TextGenerationRequest(AiWorkload workload, String instructions, List<AiMessage> messages) {
        this(workload, instructions, messages, List.of());
    }

    public TextGenerationRequest {
        Objects.requireNonNull(workload, "workload must not be null");
        Objects.requireNonNull(instructions, "instructions must not be null");
        if (instructions.isBlank()) {
            throw new IllegalArgumentException("instructions must not be blank");
        }

        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }

        List<AiMessage> validatedMessages = new ArrayList<>(messages.size());
        for (AiMessage message : messages) {
            Objects.requireNonNull(message, "message must not be null");
            Objects.requireNonNull(message.role(), "message role must not be null");
            Objects.requireNonNull(message.content(), "message content must not be null");
            if (message.content().isEmpty()) {
                throw new IllegalArgumentException("message content must not be empty");
            }
            validatedMessages.add(message);
        }

        if (validatedMessages.getLast().role() != AiMessageRole.USER) {
            throw new IllegalArgumentException("last message must have USER role");
        }

        messages = List.copyOf(validatedMessages);

        Objects.requireNonNull(contextBlocks, "contextBlocks must not be null");
        List<AiContextBlock> validatedContextBlocks = new ArrayList<>(contextBlocks.size());
        for (AiContextBlock contextBlock : contextBlocks) {
            validatedContextBlocks.add(Objects.requireNonNull(contextBlock, "contextBlock must not be null"));
        }
        contextBlocks = List.copyOf(validatedContextBlocks);
    }
}
