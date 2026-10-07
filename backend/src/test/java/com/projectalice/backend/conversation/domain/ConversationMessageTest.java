package com.projectalice.backend.conversation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConversationMessageTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 8, 14, 15, 0, 2, 123_000_000, ZoneOffset.ofHours(9));

    private static ConversationMessage message(
            long sequence, ConversationMessageRole role, String content, OffsetDateTime createdAt) {
        return new ConversationMessage("a690f9b2-21b7-41a8-929c-20f9f9703d6f", sequence, role, content, createdAt);
    }

    @Test
    void createsMessage() {
        ConversationMessage m = message(1, ConversationMessageRole.USER, "こんにちは", NOW);

        assertEquals(1, m.sequence());
        assertEquals(ConversationMessageRole.USER, m.role());
        assertEquals("こんにちは", m.content());
        assertEquals(NOW, m.createdAt());
    }

    @Test
    void rejectsInvalidSequence() {
        assertThrows(IllegalArgumentException.class, () -> message(0, ConversationMessageRole.USER, "a", NOW));
    }

    @Test
    void rejectsNullRole() {
        assertThrows(NullPointerException.class, () -> message(1, null, "a", NOW));
    }

    @Test
    void rejectsNullOrBlankContent() {
        assertThrows(NullPointerException.class, () -> message(1, ConversationMessageRole.USER, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> message(1, ConversationMessageRole.USER, "", NOW));
        assertThrows(IllegalArgumentException.class, () -> message(1, ConversationMessageRole.USER, " \n", NOW));
    }

    @Test
    void rejectsNonJstOrSubMillisecondCreatedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> message(1, ConversationMessageRole.ASSISTANT, "a", NOW.withOffsetSameInstant(ZoneOffset.UTC)));
        assertThrows(IllegalArgumentException.class,
                () -> message(1, ConversationMessageRole.ASSISTANT, "a", NOW.withNano(123_456_789)));
    }
}
