package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KwV1MemoryProjectionBuilderTest {

    private static final byte[] KEY_V1 = "synthetic-memory-search-key-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_V2 = "synthetic-memory-search-key-v2".getBytes(StandardCharsets.UTF_8);
    private static final MemoryId MEMORY_ID = new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc");

    @Test
    void buildsDeterministicContentFreePagesOfAtMostOneHundredTerms() {
        String content = java.util.stream.IntStream.range(0, 120)
                .mapToObj(index -> "term" + index)
                .collect(java.util.stream.Collectors.joining(" "));
        var builder = builder(Map.of(2, KEY_V2, 1, KEY_V1), 2);

        var projection = builder.build(MEMORY_ID, 4, new MemoryContent(content));

        assertEquals(MEMORY_ID, projection.memoryId());
        assertEquals(4, projection.semanticVersion());
        assertEquals("KW_V1", projection.analyzerVersion());
        assertEquals(2, projection.tokenKeyVersion());
        assertEquals(120, projection.termCount());
        assertEquals(List.of(100, 20), projection.pages().stream().map(page -> page.terms().size()).toList());
        assertFalse(projection.fallbackRequired());
        assertEquals(projection, builder.build(MEMORY_ID, 4, new MemoryContent(content)));
        assertTrue(projection.pages().stream().flatMap(page -> page.terms().stream())
                .noneMatch(term -> term.termDigest().contains("term")));
    }

    @Test
    void preservesAnalyzerFallbackRequirementWhenDocumentTermsExceedTheAcceptedCap() {
        String content = java.util.stream.IntStream.range(0, 520)
                .mapToObj(index -> "unique" + index)
                .collect(java.util.stream.Collectors.joining(" "));

        var projection = builder(Map.of(1, KEY_V1), 1)
                .build(MEMORY_ID, 1, new MemoryContent(content));

        assertEquals(KwV1Analyzer.MAX_DOCUMENT_TERMS, projection.termCount());
        assertEquals(6, projection.pages().size());
        assertTrue(projection.pages().stream().allMatch(page -> page.terms().size() <= 100));
        assertTrue(projection.fallbackRequired());
    }

    private static KwV1MemoryProjectionBuilder builder(Map<Integer, byte[]> keys, int writeVersion) {
        return new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), new MemorySearchTermDigest(keys, writeVersion));
    }
}
