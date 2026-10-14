package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KwV1QueryTermDigesterTest {

    private static final byte[] KEY_V1 = "synthetic-memory-search-key-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_V2 = "synthetic-memory-search-key-v2".getBytes(StandardCharsets.UTF_8);

    @Test
    void analyzesThenDigestsTermsForEveryReadableVersionWithoutReturningPlainTerms() {
        var digester = new KwV1QueryTermDigester(
                new KwV1Analyzer(), new MemorySearchTermDigest(Map.of(2, KEY_V2, 1, KEY_V1), 2));

        List<KwV1QueryTermDigester.DigestedTerm> terms = digester.digestQuery("ＡＢＣ 札幌", List.of());

        assertEquals(8, terms.size()); // two lexical/CJK terms, two key versions each
        assertEquals(List.of(1, 2, 1, 2, 1, 2, 1, 2), terms.stream()
                .map(KwV1QueryTermDigester.DigestedTerm::keyVersion).toList());
        assertEquals(List.of("LEXICAL", "LEXICAL", "NGRAM_2", "NGRAM_2",
                        "NGRAM_1", "NGRAM_1", "NGRAM_1", "NGRAM_1"),
                terms.stream().map(term -> term.termKind().name()).toList());
        assertEquals(terms, digester.digestQuery("ＡＢＣ 札幌", List.of()));
        assertFalse(java.util.Arrays.stream(KwV1QueryTermDigester.DigestedTerm.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase(java.util.Locale.ROOT).contains("normalized")));
        assertFalse(terms.stream().anyMatch(term -> term.termDigest().contains("abc")
                || term.termDigest().contains("札幌")));
    }

    @Test
    void queryHintsArePassedThroughTheAcceptedAnalyzerPriority() {
        var digester = new KwV1QueryTermDigester(
                new KwV1Analyzer(), new MemorySearchTermDigest(Map.of(1, KEY_V1), 1));

        List<KwV1QueryTermDigester.DigestedTerm> terms = digester.digestQuery("札幌の計画", List.of("priority-hint"));

        assertEquals("LEXICAL", terms.getFirst().termKind().name());
        assertEquals(1, terms.getFirst().keyVersion());
        assertEquals(43, terms.getFirst().termDigest().length());
    }
}
