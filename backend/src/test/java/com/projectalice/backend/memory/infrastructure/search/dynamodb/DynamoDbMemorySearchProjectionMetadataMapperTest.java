package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import com.projectalice.backend.memory.infrastructure.search.KwV1MemoryProjectionBuilder;
import com.projectalice.backend.memory.infrastructure.search.MemorySearchTermDigest;
import com.projectalice.backend.memory.infrastructure.search.MemoryProjectionDigest;
import com.projectalice.backend.memory.infrastructure.search.MemoryProjectionDigestCanonicalEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamoDbMemorySearchProjectionMetadataMapperTest {

    private static final MemoryId MEMORY_ID = new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc");
    private static final MemoryProjectionDigest.VersionedDigest PROJECTION_DIGEST =
            new MemoryProjectionDigest(new MemoryProjectionDigestCanonicalEncoder(),
                    Map.of(5, "synthetic-projection-digest-key-v5".getBytes(StandardCharsets.UTF_8)), 5)
                    .digestForWrite(new MemoryProjectionDigestCanonicalEncoder.Input(
                            MEMORY_ID, 7, "KW_V1", 2, 1, 2, java.util.List.of(
                                    new MemoryProjectionDigestCanonicalEncoder.Page(0, java.util.List.of(
                                            new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                                    KwV1Analyzer.TermKind.LEXICAL, 4),
                                            new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                                    "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
                                                    KwV1Analyzer.TermKind.LEXICAL, 4))))));
    private static final String BUILT_AT = "2026-08-30T21:00:00.000+09:00";

    @Test
    void mapsReadyMetadataUsingAcceptedProjectionKeysAndAttributes() {
        var item = DynamoDbMemorySearchProjectionMetadataMapper.activationAttributes(
                projection("alpha beta"), PROJECTION_DIGEST, BUILT_AT);

        assertEquals("MEMORY_SEARCH#PROJECTION#" + MEMORY_ID.value(), item.get("pk").s());
        assertEquals("META", item.get("sk").s());
        assertEquals("MEMORY_SEARCH_PROJECTION", item.get("itemType").s());
        assertEquals("1", item.get("schemaVersion").n());
        assertEquals(MEMORY_ID.value(), item.get("memoryId").s());
        assertEquals("7", item.get("activeSemanticVersion").n());
        assertEquals("KW_V1", item.get("analyzerVersion").s());
        assertEquals("2", item.get("tokenKeyVersion").n());
        assertEquals("5", item.get("projectionDigestKeyVersion").n());
        assertEquals("2", item.get("currentTermCount").n());
        assertEquals("1", item.get("currentTermPageCount").n());
        assertEquals(false, item.get("fallbackRequired").bool());
        assertEquals(PROJECTION_DIGEST.value(), item.get("projectionDigest").s());
        assertEquals("READY", item.get("status").s());
        assertEquals(BUILT_AT, item.get("builtAt").s());
        assertFalse(item.containsKey("content"));
        assertFalse(item.containsKey("normalizedTerm"));
    }

    @Test
    void mapsEmptyProjectionWithZeroPagesAndPreservesFallbackFlag() {
        var empty = new KwV1MemoryProjectionBuilder.Projection(
                MEMORY_ID, 7, "KW_V1", 2, 0, true, java.util.List.of());

        var item = DynamoDbMemorySearchProjectionMetadataMapper.activationAttributes(empty, PROJECTION_DIGEST, BUILT_AT);

        assertEquals("0", item.get("currentTermCount").n());
        assertEquals("0", item.get("currentTermPageCount").n());
        assertEquals(true, item.get("fallbackRequired").bool());
    }

    @Test
    void rejectsMissingProjectionDigestOrBuildTime() {
        var projection = projection("design");

        assertThrows(NullPointerException.class, () ->
                DynamoDbMemorySearchProjectionMetadataMapper.activationAttributes(projection, null, BUILT_AT));
        assertThrows(IllegalArgumentException.class, () ->
                DynamoDbMemorySearchProjectionMetadataMapper.activationAttributes(projection, PROJECTION_DIGEST, " "));
    }

    private static KwV1MemoryProjectionBuilder.Projection projection(String content) {
        var digest = new MemorySearchTermDigest(
                Map.of(2, "synthetic-memory-search-key-v2".getBytes(StandardCharsets.UTF_8)), 2);
        return new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), digest)
                .build(MEMORY_ID, 7, new MemoryContent(content));
    }
}
