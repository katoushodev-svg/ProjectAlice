package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds content-free, versioned KW_V1 projection pages from canonical Memory content. */
public final class KwV1MemoryProjectionBuilder {

    public static final int TERMS_PER_PAGE = 100;

    private final KwV1Analyzer analyzer;
    private final MemorySearchTermDigest digest;

    public KwV1MemoryProjectionBuilder(KwV1Analyzer analyzer, MemorySearchTermDigest digest) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
    }

    public Projection build(MemoryId memoryId, long semanticVersion, MemoryContent canonicalContent) {
        return build(memoryId, semanticVersion, canonicalContent, digest.writeKeyVersion());
    }

    /** Rebuilds a projection under an explicitly readable term key version. */
    public Projection build(MemoryId memoryId, long semanticVersion, MemoryContent canonicalContent,
            int tokenKeyVersion) {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(canonicalContent, "canonicalContent must not be null");
        if (semanticVersion < 1) {
            throw new IllegalArgumentException("semanticVersion must be positive");
        }
        if (!digest.readableKeyVersions().contains(tokenKeyVersion)) {
            throw new IllegalArgumentException("token key version is not available");
        }

        KwV1Analyzer.Analysis analysis = analyzer.analyzeDocument(canonicalContent.value());
        List<TermDescriptor> descriptors = analysis.terms().stream()
                .map(term -> new TermDescriptor(
                        digest.digestForRead(tokenKeyVersion, KwV1Analyzer.VERSION, term.normalizedTerm()),
                        term.kind(),
                        term.weightCode()))
                .toList();
        List<TermPage> pages = new ArrayList<>();
        for (int offset = 0; offset < descriptors.size(); offset += TERMS_PER_PAGE) {
            pages.add(new TermPage(descriptors.subList(
                    offset, Math.min(offset + TERMS_PER_PAGE, descriptors.size()))));
        }
        return new Projection(
                memoryId,
                semanticVersion,
                KwV1Analyzer.VERSION,
                tokenKeyVersion,
                descriptors.size(),
                analysis.fallbackRequired(),
                pages);
    }

    /** A term page in stable analyzer-priority order; physical page numbering is persistence-owned. */
    public record TermPage(List<TermDescriptor> terms) {
        public TermPage {
            terms = List.copyOf(Objects.requireNonNull(terms, "terms must not be null"));
            if (terms.isEmpty() || terms.size() > TERMS_PER_PAGE) {
                throw new IllegalArgumentException("term page must contain between 1 and 100 terms");
            }
        }
    }

    /** Stores only keyed digests and term metadata, never normalized terms or Memory content. */
    public record TermDescriptor(String termDigest, KwV1Analyzer.TermKind termKind, int weightCode) {
        public TermDescriptor {
            Objects.requireNonNull(termDigest, "termDigest must not be null");
            Objects.requireNonNull(termKind, "termKind must not be null");
            if (!termDigest.matches("[A-Za-z0-9_-]{43}") || weightCode != termKind.weightCode()) {
                throw new IllegalArgumentException("invalid projection term descriptor");
            }
        }
    }

    /** In-memory build result; it does not activate or persist a Projection. */
    public record Projection(
            MemoryId memoryId,
            long semanticVersion,
            String analyzerVersion,
            int tokenKeyVersion,
            int termCount,
            boolean fallbackRequired,
            List<TermPage> pages) {
        public Projection {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            Objects.requireNonNull(analyzerVersion, "analyzerVersion must not be null");
            pages = List.copyOf(Objects.requireNonNull(pages, "pages must not be null"));
            if (semanticVersion < 1 || tokenKeyVersion < 1 || termCount < 0
                    || pages.stream().mapToInt(page -> page.terms().size()).sum() != termCount) {
                throw new IllegalArgumentException("invalid projection build result");
            }
        }
    }
}
