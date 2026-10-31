package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.RelatedMemorySearchResult;
import com.projectalice.backend.memory.application.port.out.RelatedMemorySearchPort;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.infrastructure.MemorySearchTermCapability;
import java.util.List;
import java.util.Objects;

/** KW_V1 adapter for Relation candidates, reusing the validated Posting pipeline. */
public final class DynamoDbRelatedMemorySearchAdapter implements RelatedMemorySearchPort {
    private final KwV1QueryTermDigester termDigester;
    private final PostingSearchPipeline pipeline;

    public DynamoDbRelatedMemorySearchAdapter(MemorySearchTermCapability capability,
            PostingSearchPipeline pipeline) {
        Objects.requireNonNull(capability, "capability must not be null");
        this.termDigester = capability.digest()
                .map(digest -> new KwV1QueryTermDigester(new KwV1Analyzer(), digest)).orElse(null);
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
    }

    @Override
    public RelatedMemorySearchResult findCandidates(MemoryContent candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (termDigester == null) return unavailable();
        try {
            List<DynamoDbPostingReader.QueryTerm> terms = termDigester.digestQuery(candidate.value(), List.of())
                    .stream()
                    .map(term -> new DynamoDbPostingReader.QueryTerm(
                            term.keyVersion(), term.termDigest(), term.queryTermOrdinal()))
                    .toList();
            // Empty analysis cannot establish a complete no-match result.
            if (terms.isEmpty()) return unavailable();
            PostingSearchPipeline.ValidatedResult result = pipeline.search(
                    MemoryRetrievalPurpose.RELATION_DECISION,
                    terms,
                    new PostingSearchPipeline.CurrentValidationRequest(
                            KwV1Analyzer.VERSION,
                            memory -> memory.state() == MemoryState.ACTIVE,
                            ignored -> true));
            List<RelatedMemorySearchResult.Candidate> candidates = result.candidates().stream()
                    .map(value -> new RelatedMemorySearchResult.Candidate(
                            value.memory().memoryId(), value.memory().version(), value.memory().content(),
                            value.memory().category(), value.memory().captureType(),
                            value.memory().sensitivityLevel(), value.memory().state()))
                    .toList();
            return new RelatedMemorySearchResult(result.completeness(), candidates);
        } catch (RuntimeException unavailable) {
            return unavailable();
        }
    }

    private static RelatedMemorySearchResult unavailable() {
        return new RelatedMemorySearchResult(
                com.projectalice.backend.memory.application.SearchCompleteness.UNAVAILABLE, List.of());
    }
}
