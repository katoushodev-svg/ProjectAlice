package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageMapper;
import org.junit.jupiter.api.Test;

class MemoryProjectionReadBackVerifierTest {
    private final MemoryId id = new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc");
    private final MemoryProjectionDigestCanonicalEncoder encoder = new MemoryProjectionDigestCanonicalEncoder();
    private final MemoryProjectionDigest digest = new MemoryProjectionDigest(encoder,
            Map.of(4, "projection-purpose-test-key".getBytes(StandardCharsets.UTF_8)), 4);

    @Test
    void acceptsMatchingReadBackAndRejectsModifiedPageOrDigest() {
        var termDigest = new MemorySearchTermDigest(Map.of(2,
                "term-purpose-test-key".getBytes(StandardCharsets.UTF_8)), 2);
        var projection = new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), termDigest)
                .build(id, 3, new MemoryContent("alpha beta"));
        var pages = projection.pages().stream().map(page -> new DynamoDbMemorySearchTermPageMapper.PersistedPage(
                0, 3, "KW_V1", 2, page.terms())).toList();
        var input = new MemoryProjectionDigestCanonicalEncoder.Input(id, 3, "KW_V1", 2,
                pages.size(), projection.termCount(), pages.stream().map(page ->
                        new MemoryProjectionDigestCanonicalEncoder.Page(0, page.descriptors().stream()
                                .map(term -> new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                        term.termDigest(), term.termKind(), term.weightCode())).toList())).toList());
        var expected = digest.digestForWrite(input);
        var verifier = new MemoryProjectionReadBackVerifier(digest);

        assertTrue(verifier.verify(projection, pages, expected));
        assertFalse(verifier.verify(projection, pages, new MemoryProjectionDigest.VersionedDigest(
                expected.projectionDigestKeyVersion(), "A".repeat(43))));
        var altered = List.of(new DynamoDbMemorySearchTermPageMapper.PersistedPage(
                1, 3, "KW_V1", 2, pages.get(0).descriptors()));
        assertFalse(verifier.verify(projection, altered, expected));
    }
}
