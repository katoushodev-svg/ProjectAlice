package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordAnalyzerTest {

    private final KeywordAnalyzer analyzer = new KeywordAnalyzer();

    @Test
    void normalizesLineEndingsNfkcAndCaseWithoutChangingSourceInput() {
        String input = "ＡＢＣ\r\nProject AL-１２３\r漢字";
        var analysis = analyzer.analyzeQuery(input);

        assertTrue(analysis.terms().stream().anyMatch(t -> t.normalizedTerm().equals("abc")));
        assertTrue(analysis.terms().stream().anyMatch(t -> t.normalizedTerm().equals("project")));
        assertFalse(analysis.terms().stream().anyMatch(t -> t.normalizedTerm().equals("al-123")));
        assertTrue(analysis.terms().stream().anyMatch(t -> t.normalizedTerm().equals("al")));
        assertEquals("ＡＢＣ\r\nProject AL-１２３\r漢字", input);
    }

    @Test
    void createsJapaneseNgramsWithStablePriorityAndWeights() {
        var analysis = analyzer.analyzeQuery("東京大学");
        List<KeywordAnalyzer.Term> terms = analysis.terms();

        assertTrue(terms.stream().anyMatch(t -> t.normalizedTerm().equals("東京大")
                && t.kind() == KeywordAnalyzer.TermKind.NGRAM_3 && t.weightCode() == 3));
        assertTrue(terms.stream().anyMatch(t -> t.normalizedTerm().equals("東京")
                && t.kind() == KeywordAnalyzer.TermKind.NGRAM_2 && t.weightCode() == 2));
        assertTrue(terms.stream().anyMatch(t -> t.normalizedTerm().equals("東")
                && t.kind() == KeywordAnalyzer.TermKind.NGRAM_1 && t.weightCode() == 1));
        assertEquals(terms.stream().sorted(java.util.Comparator
                .comparingInt((KeywordAnalyzer.Term t) -> t.kind().ordinal())
                .thenComparingInt(KeywordAnalyzer.Term::firstPosition)
                .thenComparing(KeywordAnalyzer.Term::normalizedTerm)).toList(), terms);
    }

    @Test
    void deduplicatesTermsAndIsDeterministic() {
        var first = analyzer.analyzeDocument("Alice Alice 東京大学 東京大学");
        var second = analyzer.analyzeDocument("Alice Alice 東京大学 東京大学");

        assertEquals(first, second);
        assertEquals(first.terms().size(), first.terms().stream()
                .map(KeywordAnalyzer.Term::normalizedTerm).distinct().count());
        assertEquals(1L, first.terms().stream().filter(t -> t.normalizedTerm().equals("alice")).count());
    }

    @Test
    void capsQueryAtThirtyTwoUniqueTerms() {
        String query = "東京大学札幌北海道京都大阪名古屋福岡仙台横浜神戸";
        var analysis = analyzer.analyzeQuery(query);

        assertTrue(analysis.terms().size() <= KeywordAnalyzer.MAX_QUERY_UNIQUE_TERMS);
        assertTrue(analysis.truncated());
        assertFalse(analysis.fallbackRequired());
    }

    @Test
    void documentOverflowRequiresFallbackAndKeepsAtMost512Terms() {
        StringBuilder content = new StringBuilder();
        for (int cp = 0x4E00; cp < 0x4E00 + 850; cp++) {
            content.appendCodePoint(cp);
        }

        var analysis = analyzer.analyzeDocument(content.toString());

        assertEquals(KeywordAnalyzer.MAX_DOCUMENT_UNIQUE_TERMS, analysis.terms().size());
        assertTrue(analysis.fallbackRequired());
        assertTrue(analysis.truncated());
    }

    @Test
    void overlongLexicalRunIsNotSplitIntoArtificialExactMatchChunks() {
        String overlong = "a".repeat(70);
        var analysis = analyzer.analyzeDocument(overlong);

        assertNotEquals(overlong.substring(0, 64),
                analysis.terms().stream().filter(t -> t.kind() == KeywordAnalyzer.TermKind.LEXICAL)
                        .map(KeywordAnalyzer.Term::normalizedTerm).findFirst().orElse(""));
        assertTrue(analysis.terms().stream().anyMatch(t -> t.kind() == KeywordAnalyzer.TermKind.NGRAM_3));
    }
}
