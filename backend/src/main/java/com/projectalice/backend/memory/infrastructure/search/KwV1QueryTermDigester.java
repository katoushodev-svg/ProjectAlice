package com.projectalice.backend.memory.infrastructure.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Prepares deterministic, key-versioned digest descriptors for a KW_V1 query.
 * This does not access persistence or assign candidate relevance/order.
 */
public final class KwV1QueryTermDigester {

    private final KwV1Analyzer analyzer;
    private final MemorySearchTermDigest digest;

    public KwV1QueryTermDigester(KwV1Analyzer analyzer, MemorySearchTermDigest digest) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
    }

    public List<DigestedTerm> digestQuery(String query, List<String> queryHints) {
        KwV1Analyzer.Analysis analysis = analyzer.analyzeQuery(query, queryHints);
        List<Integer> keyVersions = digest.readableKeyVersions();
        List<DigestedTerm> result = new ArrayList<>(analysis.terms().size() * keyVersions.size());
        for (KwV1Analyzer.TermDescriptor term : analysis.terms()) {
            for (int keyVersion : keyVersions) {
                result.add(new DigestedTerm(
                        keyVersion,
                        digest.digestForRead(keyVersion, KwV1Analyzer.VERSION, term.normalizedTerm()),
                        term.kind(),
                        term.weightCode()));
            }
        }
        return List.copyOf(result);
    }

    /** A content-free descriptor for constructing a Search Posting partition query. */
    public record DigestedTerm(
            int keyVersion, String termDigest, KwV1Analyzer.TermKind termKind, int weightCode) {
        public DigestedTerm {
            if (keyVersion < 1) {
                throw new IllegalArgumentException("keyVersion must be positive");
            }
            Objects.requireNonNull(termDigest, "termDigest must not be null");
            Objects.requireNonNull(termKind, "termKind must not be null");
            if (weightCode != termKind.weightCode()) {
                throw new IllegalArgumentException("weightCode must match termKind");
            }
        }
    }
}
