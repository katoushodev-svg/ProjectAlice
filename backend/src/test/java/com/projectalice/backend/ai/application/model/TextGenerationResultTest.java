package com.projectalice.backend.ai.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextGenerationResultTest {

    @Test
    void createsResult() {
        TextGenerationResult result = new TextGenerationResult(
                "Generated text",
                TextGenerationFinishReason.COMPLETED);

        assertEquals("Generated text", result.content());
        assertEquals(TextGenerationFinishReason.COMPLETED, result.finishReason());
    }
}
