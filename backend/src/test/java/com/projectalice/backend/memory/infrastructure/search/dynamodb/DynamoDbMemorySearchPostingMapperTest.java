package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import org.junit.jupiter.api.Test;

class DynamoDbMemorySearchPostingMapperTest {

    private static final MemoryId MEMORY_ID = new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc");
    private static final String DIGEST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";

    @Test
    void mapsCurrentPostingUsingAcceptedKeysAndOnlyContentFreeAttributes() {
        var item = DynamoDbMemorySearchPostingMapper.current(
                2, DIGEST, MEMORY_ID, 7, KwV1Analyzer.TermKind.NGRAM_2);

        assertEquals("MEMORY_SEARCH#TERM#2#CURRENT#" + DIGEST, item.get("pk").s());
        assertEquals("MEMORY#" + MEMORY_ID.value(), item.get("sk").s());
        assertEquals("MEMORY_SEARCH_POSTING", item.get("itemType").s());
        assertEquals("1", item.get("schemaVersion").n());
        assertEquals("7", item.get("semanticVersion").n());
        assertEquals("KW_V1", item.get("analyzerVersion").s());
        assertEquals("2", item.get("tokenKeyVersion").n());
        assertEquals("NGRAM_2", item.get("termKind").s());
        assertEquals("2", item.get("weightCode").n());
        assertFalse(item.containsKey("content"));
        assertFalse(item.containsKey("normalizedTerm"));
        assertFalse(item.containsKey("term"));
    }

    @Test
    void mapsHistoricalPostingWithZeroPaddedRevisionKey() {
        var item = DynamoDbMemorySearchPostingMapper.historical(
                1, DIGEST, MEMORY_ID, 12, KwV1Analyzer.TermKind.LEXICAL);

        assertEquals("MEMORY_SEARCH#TERM#1#HISTORICAL#" + DIGEST, item.get("pk").s());
        assertEquals("MEMORY#" + MEMORY_ID.value() + "#REVISION#00000000000000000012", item.get("sk").s());
        assertEquals("12", item.get("revisionNumber").n());
        assertEquals("4", item.get("weightCode").n());
        assertFalse(item.containsKey("semanticVersion"));
        assertFalse(item.containsKey("content"));
    }

    @Test
    void rejectsInvalidKeyOrVersionFields() {
        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemorySearchPostingMapper.current(
                0, DIGEST, MEMORY_ID, 1, KwV1Analyzer.TermKind.LEXICAL));
        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemorySearchPostingMapper.current(
                1, "plain-term", MEMORY_ID, 1, KwV1Analyzer.TermKind.LEXICAL));
        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemorySearchPostingMapper.current(
                1, DIGEST, MEMORY_ID, 0, KwV1Analyzer.TermKind.LEXICAL));
        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemorySearchPostingMapper.historical(
                1, DIGEST, MEMORY_ID, 0, KwV1Analyzer.TermKind.LEXICAL));
    }
}
