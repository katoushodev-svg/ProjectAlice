package com.projectalice.backend.memory.infrastructure.search;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, provider-independent implementation of the KW_V1 text analysis boundary.
 *
 * <p>This class produces search terms only. It does not persist content, compute keyed digests,
 * access DynamoDB, or decide whether a Memory is eligible for use.</p>
 */
public final class KeywordAnalyzer {

    public static final String VERSION = "KW_V1";
    public static final int MAX_DOCUMENT_UNIQUE_TERMS = 512;
    public static final int MAX_QUERY_UNIQUE_TERMS = 32;
    public static final int MAX_TERM_CODE_POINTS = 64;
    public static final int MAX_TERM_UTF8_BYTES = 256;

    public enum TermKind {
        LEXICAL(4),
        NGRAM_3(3),
        NGRAM_2(2),
        NGRAM_1(1);

        private final int weightCode;

        TermKind(int weightCode) {
            this.weightCode = weightCode;
        }

        public int weightCode() {
            return weightCode;
        }
    }

    /** Normalized term is transient analysis output and must not be written to persistent search keys. */
    public record Term(String normalizedTerm, TermKind kind, int weightCode, int firstPosition) {
        public Term {
            Objects.requireNonNull(normalizedTerm, "normalizedTerm must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
            if (normalizedTerm.isEmpty()) {
                throw new IllegalArgumentException("normalizedTerm must not be empty");
            }
            int codePoints = normalizedTerm.codePointCount(0, normalizedTerm.length());
            int utf8Bytes = normalizedTerm.getBytes(StandardCharsets.UTF_8).length;
            if (codePoints > MAX_TERM_CODE_POINTS || utf8Bytes > MAX_TERM_UTF8_BYTES) {
                throw new IllegalArgumentException("Term exceeds KW_V1 length limits");
            }
            if (weightCode != kind.weightCode()) {
                throw new IllegalArgumentException("weightCode must match term kind");
            }
            if (firstPosition < 0) {
                throw new IllegalArgumentException("firstPosition must be non-negative");
            }
        }
    }

    /**
     * {@code fallbackRequired} is meaningful for document analysis. It is true when the document
     * has more unique terms than the bounded posting projection can represent.
     * {@code truncated} means the returned list was capped by the requested analysis boundary.
     */
    public record Analysis(List<Term> terms, boolean fallbackRequired, boolean truncated) {
        public Analysis {
            terms = List.copyOf(Objects.requireNonNull(terms, "terms must not be null"));
        }
    }

    private static final Comparator<Term> TERM_ORDER = Comparator
            .comparingInt((Term term) -> term.kind().ordinal())
            .thenComparingInt(Term::firstPosition)
            .thenComparing(Term::normalizedTerm);

    public Analysis analyzeDocument(String content) {
        Objects.requireNonNull(content, "content must not be null");
        List<Term> allTerms = analyzeNormalized(normalize(content));
        boolean overflow = allTerms.size() > MAX_DOCUMENT_UNIQUE_TERMS;
        List<Term> bounded = allTerms.stream().limit(MAX_DOCUMENT_UNIQUE_TERMS).toList();
        return new Analysis(bounded, overflow, overflow);
    }

    public Analysis analyzeQuery(String query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Term> allTerms = analyzeNormalized(normalize(query));
        boolean overflow = allTerms.size() > MAX_QUERY_UNIQUE_TERMS;
        List<Term> bounded = allTerms.stream().limit(MAX_QUERY_UNIQUE_TERMS).toList();
        return new Analysis(bounded, false, overflow);
    }

    static String normalize(String input) {
        String lineNormalized = input.replace("\r\n", "\n").replace('\r', '\n');
        return Normalizer.normalize(lineNormalized, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static List<Term> analyzeNormalized(String normalized) {
        Map<String, Term> unique = new LinkedHashMap<>();
        int position = 0;
        int offset = 0;
        while (offset < normalized.length()) {
            int codePoint = normalized.codePointAt(offset);
            if (!isWordCodePoint(codePoint)) {
                offset += Character.charCount(codePoint);
                position++;
                continue;
            }

            boolean cjkRun = isCjkKana(codePoint);
            int runStart = offset;
            int runStartPosition = position;
            int runEnd = offset;
            int runCodePoints = 0;
            while (runEnd < normalized.length()) {
                int current = normalized.codePointAt(runEnd);
                if (!isWordCodePoint(current) || isCjkKana(current) != cjkRun) {
                    break;
                }
                runEnd += Character.charCount(current);
                runCodePoints++;
            }

            String run = normalized.substring(runStart, runEnd);
            if (cjkRun) {
                addNgrams(unique, run, runStartPosition);
            } else if (runCodePoints <= MAX_TERM_CODE_POINTS
                    && run.getBytes(StandardCharsets.UTF_8).length <= MAX_TERM_UTF8_BYTES) {
                addIfAbsent(unique, new Term(run, TermKind.LEXICAL, TermKind.LEXICAL.weightCode(), runStartPosition));
            } else {
                // Do not split an overlong lexical sequence into artificial exact-match chunks.
                // N-grams provide bounded search terms for such a sequence instead.
                addNgrams(unique, run, runStartPosition);
            }

            offset = runEnd;
            position += runCodePoints;
        }

        List<Term> terms = new ArrayList<>(unique.values());
        terms.sort(TERM_ORDER);
        return List.copyOf(terms);
    }

    private static void addNgrams(Map<String, Term> unique, String run, int startPosition) {
        int[] codePoints = run.codePoints().toArray();
        for (int n : new int[] {3, 2, 1}) {
            TermKind kind = switch (n) {
                case 3 -> TermKind.NGRAM_3;
                case 2 -> TermKind.NGRAM_2;
                default -> TermKind.NGRAM_1;
            };
            if (codePoints.length < n) {
                continue;
            }
            for (int start = 0; start <= codePoints.length - n; start++) {
                String termText = new String(codePoints, start, n);
                addIfAbsent(unique, new Term(termText, kind, kind.weightCode(), startPosition + start));
            }
        }
    }

    private static void addIfAbsent(Map<String, Term> unique, Term candidate) {
        Term existing = unique.get(candidate.normalizedTerm());
        if (existing == null || candidate.kind().ordinal() < existing.kind().ordinal()
                || (candidate.kind() == existing.kind() && candidate.firstPosition() < existing.firstPosition())) {
            unique.put(candidate.normalizedTerm(), candidate);
        }
    }

    private static boolean isWordCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static boolean isCjkKana(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
