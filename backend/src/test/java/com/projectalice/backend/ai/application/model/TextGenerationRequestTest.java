package com.projectalice.backend.ai.application.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextGenerationRequestTest {

    @Test
    void createsRequestWithOrderedImmutableMessages() {
        List<AiMessage> messages = new ArrayList<>(List.of(
                new AiMessage(AiMessageRole.ASSISTANT, "Earlier reply"),
                new AiMessage(AiMessageRole.USER, "Latest message")));

        TextGenerationRequest request = new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "Follow the conversation rules",
                messages);

        assertEquals(AiWorkload.CONVERSATION_REPLY, request.workload());
        assertEquals("Follow the conversation rules", request.instructions());
        assertEquals(messages, request.messages());

        messages.clear();
        assertEquals(2, request.messages().size());
        assertThrows(UnsupportedOperationException.class,
                () -> request.messages().clear());
    }

    @Test
    void createsRequestWithOrderedImmutableContextBlocks() {
        List<AiContextBlock> blocks = new ArrayList<>(List.of(
                new AiContextBlock("opaque-1", AiContextSource.PERSONAL_MEMORY, "[\"memory\"]"),
                new AiContextBlock("opaque-2", AiContextSource.EXTERNAL_CONTENT, "external")));

        TextGenerationRequest request = new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "Follow the conversation rules",
                List.of(new AiMessage(AiMessageRole.USER, "Current message")),
                blocks);

        assertEquals(blocks, request.contextBlocks());
        blocks.clear();
        assertEquals(2, request.contextBlocks().size());
        assertThrows(UnsupportedOperationException.class, () -> request.contextBlocks().clear());
    }

    @Test
    void legacyConstructorCreatesRequestWithoutContextBlocks() {
        TextGenerationRequest request = new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of(new AiMessage(AiMessageRole.USER, "Hello")));

        assertEquals(List.of(), request.contextBlocks());
    }

    @Test
    void rejectsNullContextBlocksAndEntries() {
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY, "instructions",
                List.of(new AiMessage(AiMessageRole.USER, "Hello")), null));
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY, "instructions",
                List.of(new AiMessage(AiMessageRole.USER, "Hello")), java.util.Arrays.asList((AiContextBlock) null)));
    }

    @Test
    void rejectsNullWorkload() {
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                null,
                "instructions",
                List.of(new AiMessage(AiMessageRole.USER, "Hello"))));
    }

    @Test
    void rejectsNullInstructions() {
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                null,
                List.of(new AiMessage(AiMessageRole.USER, "Hello"))));
    }

    @Test
    void rejectsBlankInstructions() {
        assertThrows(IllegalArgumentException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                " \t\n ",
                List.of(new AiMessage(AiMessageRole.USER, "Hello"))));
    }

    @Test
    void rejectsNullMessages() {
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                null));
    }

    @Test
    void rejectsEmptyMessages() {
        assertThrows(IllegalArgumentException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of()));
    }

    @Test
    void rejectsNullMessageRole() {
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of(new AiMessage(null, "Hello"))));
    }

    @Test
    void rejectsNullMessageContent() {
        assertThrows(NullPointerException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of(new AiMessage(AiMessageRole.USER, null))));
    }

    @Test
    void rejectsEmptyMessageContent() {
        assertThrows(IllegalArgumentException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of(new AiMessage(AiMessageRole.USER, ""))));
    }

    @Test
    void requiresLastMessageToBeUser() {
        assertThrows(IllegalArgumentException.class, () -> new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of(
                        new AiMessage(AiMessageRole.USER, "Hello"),
                        new AiMessage(AiMessageRole.ASSISTANT, "Hi"))));
    }

    @Test
    void allowsConsecutiveMessagesWithSameRole() {
        TextGenerationRequest request = new TextGenerationRequest(
                AiWorkload.CONVERSATION_REPLY,
                "instructions",
                List.of(
                        new AiMessage(AiMessageRole.USER, "First"),
                        new AiMessage(AiMessageRole.USER, "Second")));

        assertEquals(2, request.messages().size());
    }
}
