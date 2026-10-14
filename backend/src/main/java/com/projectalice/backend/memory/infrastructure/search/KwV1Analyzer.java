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

/** Deterministic KW_V1 analyzer. Terms are derived data and never rewrite stored content. */
public final class KwV1Analyzer {

    public static final String VERSION = "KW_V1";
    public static final int MAX_DOCUMENT_TERMS = 512;
    public static final int MAX_QUERY_TERMS = 32;
    public static final int MAX_TERM_CODE_POINTS = 64;
    public static final int MAX_TERM_UTF8_BYTES = 256;

    public enum TermKind {
        LEXICAL(4, 0),
        NGRAM_3(3, 1),
        NGRAM_2(2, 2),
        NGRAM_1(1, 3);

        private final int weightCode;
        private final int priority;

        TermKind(int weightCode, int priority) {
            this.weightCode = weightCode;
            this.priority = priority;
        }

        public int weightCode() {
            return weightCode;
        }
    }

    public record TermDescriptor(String normalizedTerm, TermKind kind, int weightCode) {
        public TermDescriptor {
            Objects.requireNonNull(normalizedTerm, "normalizedTerm must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
            if (normalizedTerm.isEmpty()
                    || normalizedTerm.codePointCount(0, normalizedTerm.length()) > MAX_TERM_CODE_POINTS
                    || normalizedTerm.getBytes(StandardCharsets.UTF_8).length > MAX_TERM_UTF8_BYTES
                    || weightCode != kind.weightCode()) {
                throw new IllegalArgumentException("invalid KW_V1 term descriptor");
            }
        }
    }

    /** fallbackRequired signals that capped document terms need the accepted Fallback Posting path. */
    public record Analysis(List<TermDescriptor> terms, boolean fallbackRequired) {
        public Analysis {
            terms = List.copyOf(Objects.requireNonNull(terms, "terms must not be null"));
        }
    }

    public Analysis analyzeDocument(String content) {
        return analyze(content, List.of(), MAX_DOCUMENT_TERMS, false);
    }

    public Analysis analyzeQuery(String query, List<String> hints) {
        return analyze(query, hints == null ? List.of() : List.copyOf(hints), MAX_QUERY_TERMS, true);
    }

    public static String normalize(String value) {
        Objects.requireNonNull(value, "value must not be null");
        String lineNormalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return Normalizer.normalize(lineNormalized, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private Analysis analyze(String value, List<String> hints, int limit, boolean prioritizeHints) {
        Objects.requireNonNull(value, "value must not be null");
        Map<String, LocatedTerm> unique = new LinkedHashMap<>();
        int position = 0;
        if (prioritizeHints) {
            for (String hint : hints) {
                if (hint != null) {
                    position = collect(normalize(hint), unique, position, true);
                }
            }
        }
        collect(normalize(value), unique, position, false);

        List<LocatedTerm> ordered = new ArrayList<>(unique.values());
        Comparator<LocatedTerm> comparator = Comparator
                .comparingInt((LocatedTerm term) -> term.hint() ? -1 : term.descriptor().kind().priority)
                .thenComparingInt(LocatedTerm::position);
        ordered.sort(comparator);
        boolean fallbackRequired = !prioritizeHints && ordered.size() > limit;
        List<TermDescriptor> result = ordered.stream()
                .limit(limit)
                .map(LocatedTerm::descriptor)
                .toList();
        return new Analysis(result, fallbackRequired);
    }

    private static int collect(String normalized, Map<String, LocatedTerm> terms, int position, boolean hint) {
        int[] codePoints = normalized.codePoints().toArray();
        int cursor = 0;
        while (cursor < codePoints.length) {
            int cp = codePoints[cursor];
            if (!isLetterOrNumber(cp)) {
                cursor++;
                continue;
            }
            boolean cjk = isCjk(cp);
            int end = cursor + 1;
            while (end < codePoints.length
                    && isLetterOrNumber(codePoints[end])
                    && isCjk(codePoints[end]) == cjk) {
                end++;
            }
            String run = new String(codePoints, cursor, end - cursor);
            if (cjk) {
                addNgrams(run, terms, position, hint);
            } else if (run.codePointCount(0, run.length()) <= MAX_TERM_CODE_POINTS) {
                add(terms, run, TermKind.LEXICAL, position, hint);
            } else {
                addNgrams(run, terms, position, hint);
            }
            position += end - cursor;
            cursor = end;
        }
        return position;
    }

    private static void addNgrams(String run, Map<String, LocatedTerm> terms, int position, boolean hint) {
        int[] points = run.codePoints().toArray();
        for (int n : new int[] {3, 2, 1}) {
            for (int start = 0; start + n <= points.length; start++) {
                String gram = new String(points, start, n);
                add(terms, gram, switch (n) {
                    case 3 -> TermKind.NGRAM_3;
                    case 2 -> TermKind.NGRAM_2;
                    default -> TermKind.NGRAM_1;
                }, position + start, hint);
            }
        }
    }

    private static void add(
            Map<String, LocatedTerm> terms, String term, TermKind kind, int position, boolean hint) {
        TermDescriptor descriptor = new TermDescriptor(term, kind, kind.weightCode());
        LocatedTerm incoming = new LocatedTerm(descriptor, position, hint);
        terms.merge(term, incoming, (existing, replacement) -> {
            if (replacement.hint() && !existing.hint()) {
                return replacement;
            }
            if (replacement.hint() == existing.hint()
                    && replacement.descriptor().kind().priority < existing.descriptor().kind().priority) {
                return replacement;
            }
            return existing;
        });
    }

    private static boolean isLetterOrNumber(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.UPPERCASE_LETTER
                || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER
                || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER
                || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }

    private record LocatedTerm(TermDescriptor descriptor, int position, boolean hint) {}
}
