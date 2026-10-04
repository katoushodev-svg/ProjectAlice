package com.projectalice.backend.ai.application.model;

public record TextGenerationResult(
        String content,
        TextGenerationFinishReason finishReason
) {
}
