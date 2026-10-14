package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KwV1AnalyzerTest {

    private final KwV1Analyzer analyzer = new KwV1Analyzer();

    @Test
    void normalizesLineEndingsNfkcAndCaseWithoutChangingSourceContent() {
        String input = "ＡＢＣ\r\n札幌\rJava";

        assertEquals("abc\n札幌\njava", KwV1Analyzer.normalize(input));
        assertEquals("ＡＢＣ\r\n札幌\rJava", input);
    }

    @Test
    void emitsLexicalAndCjkNgramTermsInStablePriorityOrder() {
        KwV1Analyzer.Analysis analysis = analyzer.analyzeDocument("Alice 札幌");

        assertEquals(List.of("alice", "札幌", "札", "幌"),
                analysis.terms().stream().map(KwV1Analyzer.TermDescriptor::normalizedTerm).toList());
        assertEquals(List.of(
                        KwV1Analyzer.TermKind.LEXICAL,
                        KwV1Analyzer.TermKind.NGRAM_2,
                        KwV1Analyzer.TermKind.NGRAM_1,
                        KwV1Analyzer.TermKind.NGRAM_1),
                analysis.terms().stream().map(KwV1Analyzer.TermDescriptor::kind).toList());
        assertFalse(analysis.fallbackRequired());
    }

    @Test
    void queryHintsTakePriorityAndQueryIsCappedAtThirtyTwoTerms() {
        StringBuilder queryBuilder = new StringBuilder();
        for (int codePoint = 0x4e00; codePoint < 0x4e28; codePoint++) {
            queryBuilder.appendCodePoint(codePoint);
        }
        String query = queryBuilder.toString();
        KwV1Analyzer.Analysis analysis = analyzer.analyzeQuery(query, List.of("chosen-hint"));

        assertEquals(KwV1Analyzer.MAX_QUERY_TERMS, analysis.terms().size());
        assertEquals("chosen", analysis.terms().getFirst().normalizedTerm());
        assertFalse(analysis.fallbackRequired());
    }

    @Test
    void documentTermCapRequiresFallbackInsteadOfSilentlyDroppingSearchability() {
        String content = java.util.stream.IntStream.range(0, 520)
                .mapToObj(index -> "term" + index)
                .collect(java.util.stream.Collectors.joining(" "));

        KwV1Analyzer.Analysis analysis = analyzer.analyzeDocument(content);

        assertEquals(KwV1Analyzer.MAX_DOCUMENT_TERMS, analysis.terms().size());
        assertTrue(analysis.fallbackRequired());
    }

    @Test
    void longNonCjkRunsUseNgramsAndNeverBecomeTruncatedLexicalTerms() {
        KwV1Analyzer.Analysis analysis = analyzer.analyzeDocument("a".repeat(65));

        assertFalse(analysis.terms().stream().anyMatch(term -> term.kind() == KwV1Analyzer.TermKind.LEXICAL));
        assertTrue(analysis.terms().stream().anyMatch(term -> term.kind() == KwV1Analyzer.TermKind.NGRAM_3));
    }

    @Test
    void analysisIsDeterministicAndDeduplicatesTerms() {
        assertEquals(analyzer.analyzeDocument("札幌 札幌"), analyzer.analyzeDocument("札幌 札幌"));
        assertEquals(3, analyzer.analyzeDocument("札幌 札幌").terms().size());
    }
}
