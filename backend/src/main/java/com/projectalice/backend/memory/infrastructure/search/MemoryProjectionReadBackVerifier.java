package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageMapper;
import java.util.List;
import java.util.Objects;

/** Rebuilds the canonical projection input from persisted page values and verifies its digest. */
public final class MemoryProjectionReadBackVerifier {
    private final MemoryProjectionDigest digest;

    public MemoryProjectionReadBackVerifier(MemoryProjectionDigest digest) {
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
    }

    public boolean verify(KwV1MemoryProjectionBuilder.Projection expected,
            List<DynamoDbMemorySearchTermPageMapper.PersistedPage> persistedPages,
            MemoryProjectionDigest.VersionedDigest expectedDigest) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(persistedPages, "persistedPages must not be null");
        Objects.requireNonNull(expectedDigest, "expectedDigest must not be null");
        if (persistedPages.size() != expected.pages().size()) return false;
        int total = 0;
        java.util.ArrayList<MemoryProjectionDigestCanonicalEncoder.Page> pages = new java.util.ArrayList<>();
        for (int index = 0; index < persistedPages.size(); index++) {
            var page = persistedPages.get(index);
            if (page.pageNumber() != index || page.semanticVersion() != expected.semanticVersion()
                    || !page.analyzerVersion().equals(expected.analyzerVersion())
                    || page.tokenKeyVersion() != expected.tokenKeyVersion()) return false;
            total = Math.addExact(total, page.descriptors().size());
            pages.add(new MemoryProjectionDigestCanonicalEncoder.Page(index, page.descriptors().stream()
                    .map(term -> new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                            term.termDigest(), term.termKind(), term.weightCode())).toList()));
        }
        if (total != expected.termCount()) return false;
        var input = new MemoryProjectionDigestCanonicalEncoder.Input(expected.memoryId(),
                expected.semanticVersion(), expected.analyzerVersion(), expected.tokenKeyVersion(),
                persistedPages.size(), total, pages);
        String expectedValue = digest.digestForWrite(input).value();
        return digest.verify(expectedDigest.projectionDigestKeyVersion(), input, expectedDigest.value())
                && java.security.MessageDigest.isEqual(
                        expectedValue.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        expectedDigest.value().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
