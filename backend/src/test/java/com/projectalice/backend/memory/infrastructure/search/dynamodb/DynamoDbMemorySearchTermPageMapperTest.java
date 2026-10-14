package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import com.projectalice.backend.memory.infrastructure.search.KwV1MemoryProjectionBuilder;
import com.projectalice.backend.memory.infrastructure.search.MemorySearchTermDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DynamoDbMemorySearchTermPageMapperTest {

    private static final MemoryId MEMORY_ID = new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc");
    private static final String DIGEST = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";

    @Test
    void mapsZeroBasedPageAndItsDescriptorsToDynamoDbListOfMaps() {
        var projection = projection("alpha beta");

        var item = DynamoDbMemorySearchTermPageMapper.map(projection, 0);

        assertEquals("MEMORY_SEARCH#PROJECTION#" + MEMORY_ID.value(), item.get("pk").s());
        assertEquals("VERSION#00000000000000000007#TERMS#0000", item.get("sk").s());
        assertEquals("7", item.get("semanticVersion").n());
        assertEquals("KW_V1", item.get("analyzerVersion").s());
        assertEquals("2", item.get("tokenKeyVersion").n());
        assertEquals("0", item.get("pageNumber").n());

        AttributeValue descriptorList = item.get("descriptors");
        assertEquals(2, descriptorList.l().size());
        var descriptor = descriptorList.l().get(0).m();
        assertEquals(Set.of("termDigest", "termKind", "weightCode"), descriptor.keySet());
        assertEquals("LEXICAL", descriptor.get("termKind").s());
        assertEquals("4", descriptor.get("weightCode").n());
        assertEquals(43, descriptor.get("termDigest").s().length());
        assertFalse(item.containsKey("content"));
        assertFalse(item.containsKey("normalizedTerm"));
        assertFalse(item.containsKey("term"));
    }

    @Test
    void mapsSecondPageWithMatchingZeroBasedNumberAndSortKey() {
        String content = java.util.stream.IntStream.range(0, 120)
                .mapToObj(index -> "term" + index)
                .collect(java.util.stream.Collectors.joining(" "));
        var projection = projection(content);

        var first = DynamoDbMemorySearchTermPageMapper.map(projection, 0);
        var second = DynamoDbMemorySearchTermPageMapper.map(projection, 1);

        assertEquals("0", first.get("pageNumber").n());
        assertEquals("VERSION#00000000000000000007#TERMS#0000", first.get("sk").s());
        assertEquals(100, first.get("descriptors").l().size());
        assertEquals("1", second.get("pageNumber").n());
        assertEquals("VERSION#00000000000000000007#TERMS#0001", second.get("sk").s());
        assertEquals(20, second.get("descriptors").l().size());
    }

    @Test
    void rejectsPageNumbersThatDoNotIdentifyAProjectionPage() {
        var projection = projection("alpha beta");

        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemorySearchTermPageMapper.map(projection, -1));
        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemorySearchTermPageMapper.map(projection, 1));
    }

    @Test
    void readsPersistedPageAndRejectsMismatchedOrMalformedAttributes() {
        var projection = projection("alpha beta");
        var item = DynamoDbMemorySearchTermPageMapper.map(projection, 0);

        var restored = DynamoDbMemorySearchTermPageMapper.read(item, MEMORY_ID, 7, "KW_V1", 2, 0);
        assertEquals(0, restored.pageNumber());
        assertEquals(projection.pages().get(0).terms(), restored.descriptors());
        assertThrows(IllegalArgumentException.class, () ->
                DynamoDbMemorySearchTermPageMapper.read(item, MEMORY_ID, 8, "KW_V1", 2, 0));

        var malformed = new java.util.HashMap<>(item);
        malformed.put("pageNumber", AttributeValue.builder().n("1").build());
        assertThrows(IllegalArgumentException.class, () ->
                DynamoDbMemorySearchTermPageMapper.read(malformed, MEMORY_ID, 7, "KW_V1", 2, 0));
    }

    @Test
    void termPageRejectsMoreThanOneHundredDescriptors() {
        var descriptor = new KwV1MemoryProjectionBuilder.TermDescriptor(
                DIGEST, KwV1Analyzer.TermKind.LEXICAL, KwV1Analyzer.TermKind.LEXICAL.weightCode());

        assertThrows(IllegalArgumentException.class, () ->
                new KwV1MemoryProjectionBuilder.TermPage(java.util.Collections.nCopies(101, descriptor)));
    }

    private static KwV1MemoryProjectionBuilder.Projection projection(String content) {
        var digest = new MemorySearchTermDigest(
                Map.of(2, "synthetic-memory-search-key-v2".getBytes(StandardCharsets.UTF_8)), 2);
        return new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), digest)
                .build(MEMORY_ID, 7, new MemoryContent(content));
    }
}
