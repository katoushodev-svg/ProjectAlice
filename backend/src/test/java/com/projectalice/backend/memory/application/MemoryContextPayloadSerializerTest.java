package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryContextPayloadSerializerTest {

    private final MemoryContextPayloadSerializer serializer = new MemoryContextPayloadSerializer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesNoMemoriesAsEmptyPayload() {
        assertEquals("", serializer.serialize(List.of()));
    }

    @Test
    void serializesOneAndMultipleMemoriesInInputOrder() throws Exception {
        List<MemoryContextItem> items = List.of(item("first"), item("second"), item("third"));

        String payload = serializer.serialize(items);

        assertEquals("[\"first\",\"second\",\"third\"]", payload);
        assertEquals(List.of("first", "second", "third"), parse(payload));
    }

    @Test
    void escapesQuotesBackslashesAndControlCharactersDeterministically() throws Exception {
        String content = "quote=\" slash=\\ path=/a controls=\b\t\n\f\r\u0000\u001f";

        String payload = serializer.serialize(List.of(item(content)));

        assertEquals("[\"quote=\\\" slash=\\\\ path=/a controls=\\b\\t\\n\\f\\r\\u0000\\u001f\"]", payload);
        assertEquals(List.of(content), parse(payload));
    }

    @Test
    void preservesUnicodeEmojiCombiningSequencesAndWhitespace() throws Exception {
        String composed = "  札幌 🙂 🚀 é\n";
        String decomposed = "e\u0301";

        String payload = serializer.serialize(List.of(item(composed), item(decomposed)));

        assertEquals("[\"  札幌 🙂 🚀 é\\n\",\"e\u0301\"]", payload);
        assertEquals(List.of(composed, decomposed), parse(payload));
    }

    @Test
    void serializationIsDeterministicForTheSameInput() {
        List<MemoryContextItem> items = List.of(item("札幌"), item("line one\nline two"));

        assertEquals(serializer.serialize(items), serializer.serialize(items));
    }

    @Test
    void escapesUnpairedSurrogatesAsJsonUnicodeEscapes() throws Exception {
        String content = "high=\uD800 low=\uDC00";

        String payload = serializer.serialize(List.of(item(content)));

        assertEquals("[\"high=\\ud800 low=\\udc00\"]", payload);
        assertEquals(List.of(content), parse(payload));
    }

    @Test
    void rejectsNullListAndNullEntries() {
        assertThrows(NullPointerException.class, () -> serializer.serialize(null));
        assertThrows(NullPointerException.class, () -> serializer.serialize(java.util.Arrays.asList(item("ok"), null)));
    }

    private List<String> parse(String payload) throws Exception {
        return objectMapper.readValue(payload, new TypeReference<>() {});
    }

    private static MemoryContextItem item(String content) {
        return new MemoryContextItem(
                MemoryId.generate(),
                1,
                content,
                MemoryCategory.PROFILE,
                MemoryState.ACTIVE,
                SensitivityLevel.NORMAL,
                MemoryTemporalRole.CURRENT,
                Set.of());
    }
}
