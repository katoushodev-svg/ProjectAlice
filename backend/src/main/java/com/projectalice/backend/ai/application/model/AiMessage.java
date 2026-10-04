package com.projectalice.backend.ai.application.model;

public record AiMessage(
        AiMessageRole role,
        String content
) {
}
