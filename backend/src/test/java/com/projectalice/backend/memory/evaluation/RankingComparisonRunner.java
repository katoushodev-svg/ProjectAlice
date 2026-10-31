package com.projectalice.backend.memory.evaluation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Test-scope comparison only. It does not define or register a production ranking policy.
 * The evaluator reads relevance labels only after selection has completed.
 */
public final class RankingComparisonRunner {
    public static final String VERSION = "ranking-comparison-v1";
    public static final long SHUFFLE_SEED = 20261022L;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    public record PolicySummary(
            String policy,
            int scenarios,
            int invalidRuns,
            long foundTargets,
            long totalTargets,
            Map<RankingEvaluationDataset.Group, RankingEvaluationHarness.Metric> groupRecall,
            long paraphraseTargets,
            long paraphraseMissedTargets,
            int safetyViolations,
            long irrelevantInTop8,
            Map<String, Long> missedByCategory,
            long precision8Hits,
            long precision8Denominator,
            long precision8CeilingHits,
            List<CaseDetail> cases) {
        public double recall() {
            return totalTargets == 0 ? 0.0 : (double) foundTargets / totalTargets;
        }

        /** Existing harness definition: relevant (REQUIRED/RELATED, eligible) in top 8 / 8, answer group only. */
        public double precisionAt8() {
            return precision8Denominator == 0 ? 0.0 : (double) precision8Hits / precision8Denominator;
        }

        /** Best achievable value given how few relevant fixtures exist per scenario. */
        public double precisionAt8Ceiling() {
            return precision8Denominator == 0 ? 0.0 : (double) precision8CeilingHits / precision8Denominator;
        }
    }

    public record CaseDetail(String scenarioId, RankingEvaluationDataset.Group group, String category,
            List<String> missedTargetIds, List<String> irrelevantInTop8Ids) {}

    public record Report(
            String datasetVersion,
            String experimentVersion,
            long shuffleSeed,
            PolicySummary shuffledInputBaseline,
            PolicySummary queryContentOverlap,
            PolicySummary japaneseCharNgram,
            PolicySummary hybrid) {}

    /*
     * EXPERIMENTAL ASSUMPTION, not a ranking specification: NFKC + lower-case, letters/digits only,
     * character bigrams (set), Dice coefficient as score, ties by shuffled input order.
     */
    static final int NGRAM_SIZE = 2;

    public Report run() {
        return run(SHUFFLE_SEED);
    }

    public Report run(long shuffleSeed) {
        return run(RankingEvaluationDataset.generate(), RankingEvaluationDataset.DATASET_VERSION, shuffleSeed);
    }

    public Report run(List<RankingEvaluationDataset.Scenario> dataset, String datasetVersion, long shuffleSeed) {
        CandidateSelectionExperiment experiment = new CandidateSelectionExperiment();

        PolicySummary baseline = evaluate(dataset, experiment, "shuffled-input-baseline", shuffleSeed,
                eligible -> eligible.stream()
                        .limit(activeOutputLimit.get())
                        .map(RankingEvaluationDataset.MemoryFixture::id)
                        .toList());

        PolicySummary overlap = evaluate(dataset, experiment, "query-content-overlap", shuffleSeed,
                eligible -> {
                    // Observable query/content only. Never reads relevance or expectedSignals.
                    String query = normalize(activeQuery.get());
                    return eligible.stream()
                            .map(f -> new ScoredFixture(f, overlapCount(query, normalize(f.content()))))
                            .sorted(Comparator.comparingDouble(ScoredFixture::score).reversed()
                                    .thenComparingInt(s -> activeOrder.get().get(s.fixture().id())))
                            .limit(activeOutputLimit.get())
                            .map(s -> s.fixture().id())
                            .toList();
                });

        PolicySummary ngram = evaluate(dataset, experiment, "japanese-char-ngram", shuffleSeed,
                eligible -> {
                    // Observable query/content only. Never reads relevance or expectedSignals.
                    Set<String> queryGrams = ngrams(activeQuery.get());
                    return eligible.stream()
                            .map(f -> new ScoredFixture(f, dice(queryGrams, ngrams(f.content()))))
                            .sorted(Comparator.comparingDouble(ScoredFixture::score).reversed()
                                    .thenComparingInt(s -> activeOrder.get().get(s.fixture().id())))
                            .limit(activeOutputLimit.get())
                            .map(s -> s.fixture().id())
                            .toList();
                });

        PolicySummary hybrid = evaluate(dataset, experiment, "hybrid-overlap-ngram", shuffleSeed,
                eligible -> {
                    // Experimental 50:50 blend. Uses observable query/content only.
                    String query = normalize(activeQuery.get());
                    int tokenCount = queryTokenCount(query);
                    Set<String> queryGrams = ngrams(query);
                    return eligible.stream()
                            .map(f -> {
                                double overlapScore = tokenCount == 0 ? 0.0
                                        : (double) overlapCount(query, normalize(f.content())) / tokenCount;
                                double ngramScore = dice(queryGrams, ngrams(f.content()));
                                return new ScoredFixture(f, (overlapScore + ngramScore) / 2.0);
                            })
                            .sorted(Comparator.comparingDouble(ScoredFixture::score).reversed()
                                    .thenComparingInt(s -> activeOrder.get().get(s.fixture().id())))
                            .limit(activeOutputLimit.get())
                            .map(s -> s.fixture().id())
                            .toList();
                });

        return new Report(datasetVersion,
                CandidateSelectionExperiment.EXPERIMENT_VERSION, shuffleSeed,
                baseline, overlap, ngram, hybrid);
    }

    /*
     * Selector's interface accepts fixtures but not query context. Thread-local state keeps
     * the experiment API unchanged and is scoped to one scenario execution.
     */
    private final ThreadLocal<String> activeQuery = new ThreadLocal<>();
    private final ThreadLocal<Map<String, Integer>> activeOrder = new ThreadLocal<>();
    private final ThreadLocal<Integer> activeOutputLimit = new ThreadLocal<>();

    private record ScoredFixture(RankingEvaluationDataset.MemoryFixture fixture, double score) {}

    private PolicySummary evaluate(
            List<RankingEvaluationDataset.Scenario> dataset,
            CandidateSelectionExperiment experiment,
            String policyName,
            long shuffleSeed,
            CandidateSelectionExperiment.Selector selector) {

        long found = 0;
        long targets = 0;
        int invalid = 0;
        int safety = 0;
        long paraTargets = 0;
        long paraMissed = 0;
        long irrelevantTop8 = 0;
        Map<String, Long> missedByCategory = new java.util.TreeMap<>();
        long p8Hits = 0;
        long p8Denominator = 0;
        long p8Ceiling = 0;
        List<CaseDetail> cases = new ArrayList<>();
        Map<RankingEvaluationDataset.Group, long[]> groupCounts = new LinkedHashMap<>();
        for (RankingEvaluationDataset.Group group : RankingEvaluationDataset.Group.values()) {
            groupCounts.put(group, new long[2]);
        }

        for (RankingEvaluationDataset.Scenario scenario : dataset) {
            List<String> ids = new ArrayList<>(
                    scenario.memories().stream().map(RankingEvaluationDataset.MemoryFixture::id).toList());
            long seed = shuffleSeed ^ scenario.id().hashCode();
            java.util.Collections.shuffle(ids, new Random(seed));

            Map<String, Integer> order = new LinkedHashMap<>();
            for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);

            activeQuery.set(scenario.query());
            activeOrder.set(order);
            activeOutputLimit.set(switch (scenario.group()) {
                case ANSWER_CURRENT_HISTORICAL ->
                        CandidateSelectionExperiment.ANSWER_OUTPUT_LIMIT;
                case RELATION_DECISION ->
                        CandidateSelectionExperiment.RELATION_OUTPUT_LIMIT;
                case MANAGEMENT_SEARCH ->
                        CandidateSelectionExperiment.MANAGEMENT_OUTPUT_LIMIT;
            });

            CandidateSelectionExperiment.Result result;
            try {
                result = experiment.run(scenario, VERSION + ":" + policyName, ids, selector);
            } finally {
                activeQuery.remove();
                activeOrder.remove();
                activeOutputLimit.remove();
            }

            if (result.status() != CandidateSelectionExperiment.Status.VALID
                    || !result.safetyViolations().isEmpty()) {
                invalid++;
            }
            safety += result.safetyViolations().size();
            // Labels are read only after selection, for evaluation.
            Map<String, RankingEvaluationDataset.Relevance> relevanceById = new java.util.HashMap<>();
            scenario.memories().forEach(m -> relevanceById.put(m.id(), m.relevance()));
            List<String> top8 = result.selectedIds().stream().limit(8).toList();
            List<String> falseTop = top8.stream()
                    .filter(id -> relevanceById.get(id) == RankingEvaluationDataset.Relevance.IRRELEVANT).toList();
            irrelevantTop8 += falseTop.size();
            List<String> expectedIds = switch (scenario.group()) {
                case ANSWER_CURRENT_HISTORICAL -> scenario.requiredMemoryIds();
                case RELATION_DECISION -> scenario.relatedMemoryIds();
                case MANAGEMENT_SEARCH -> scenario.recallTargetIds();
            };
            List<String> missedIds = expectedIds.stream().filter(id -> !result.selectedIds().contains(id)).toList();
            cases.add(new CaseDetail(scenario.id(), scenario.group(), scenario.category(), missedIds, falseTop));
            if (scenario.group() == RankingEvaluationDataset.Group.ANSWER_CURRENT_HISTORICAL
                    && result.selectedIds().size() >= 8) {
                long relevantCount = scenario.memories().stream()
                        .filter(m -> m.eligibility() == RankingEvaluationDataset.Eligibility.ELIGIBLE)
                        .filter(m -> m.relevance() != RankingEvaluationDataset.Relevance.IRRELEVANT).count();
                p8Hits += top8.stream()
                        .filter(id -> relevanceById.get(id) != RankingEvaluationDataset.Relevance.IRRELEVANT).count();
                p8Denominator += 8;
                p8Ceiling += Math.min(8, relevantCount);
            }
            missedByCategory.merge(scenario.category(),
                    result.recallAtLimit().denominator() - result.recallAtLimit().numerator(), Long::sum);

            if ("paraphrase".equals(scenario.category())) {
                paraTargets += result.recallAtLimit().denominator();
                paraMissed += result.recallAtLimit().denominator() - result.recallAtLimit().numerator();
            }

            long numerator = result.recallAtLimit().numerator();
            long denominator = result.recallAtLimit().denominator();
            found += numerator;
            targets += denominator;
            long[] group = groupCounts.get(scenario.group());
            group[0] += numerator;
            group[1] += denominator;
        }

        Map<RankingEvaluationDataset.Group, RankingEvaluationHarness.Metric> metrics = new LinkedHashMap<>();
        groupCounts.forEach((group, counts) -> metrics.put(group,
                new RankingEvaluationHarness.Metric(counts[0], counts[1],
                        counts[1] == 0 ? null : (double) counts[0] / counts[1])));
        return new PolicySummary(policyName, dataset.size(), invalid, found, targets, Map.copyOf(metrics),
                paraTargets, paraMissed, safety, irrelevantTop8, Map.copyOf(missedByCategory),
                p8Hits, p8Denominator, p8Ceiling, List.copyOf(cases));
    }

    static Set<String> ngrams(String value) {
        String s = normalize(value).replaceAll("[^\\p{L}\\p{N}]", "");
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + NGRAM_SIZE <= s.length(); i++) grams.add(s.substring(i, i + NGRAM_SIZE));
        return grams;
    }

    static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int shared = 0;
        for (String g : a) if (b.contains(g)) shared++;
        return 2.0 * shared / (a.size() + b.size());
    }

    private static int overlapCount(String query, String content) {
        if (query.isBlank() || content.isBlank()) return 0;
        int count = 0;
        for (String token : TOKEN_SPLIT.split(query)) {
            if (token.length() > 1 && content.contains(token)) count++;
        }
        return count;
    }

    private static int queryTokenCount(String query) {
        if (query.isBlank()) return 0;
        int count = 0;
        for (String token : TOKEN_SPLIT.split(query)) {
            if (token.length() > 1) count++;
        }
        return count;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        Report report = new RankingComparisonRunner().run();
        System.out.println("datasetVersion=" + report.datasetVersion());
        System.out.println("experimentVersion=" + report.experimentVersion());
        System.out.println("shuffleSeed=" + report.shuffleSeed());
        print(report.shuffledInputBaseline());
        print(report.queryContentOverlap());
        print(report.japaneseCharNgram());
        print(report.hybrid());
        System.out.println("NOTE: synthetic-data results are exploratory, not production ranking approval.");
    }

    private static void print(PolicySummary summary) {
        System.out.printf("%npolicy=%s scenarios=%d invalidRuns=%d recall=%.4f (%d/%d)%n",
                summary.policy(), summary.scenarios(), summary.invalidRuns(),
                summary.recall(), summary.foundTargets(), summary.totalTargets());

        System.out.printf("  paraphraseMissed=%d/%d safetyViolations=%d%n",
                summary.paraphraseMissedTargets(), summary.paraphraseTargets(), summary.safetyViolations());

        System.out.printf("  precisionAt8=%.4f ceiling=%.4f (%d/%d)%n",
                summary.precisionAt8(),
                summary.precisionAt8Ceiling(),
                summary.precision8Hits(),
                summary.precision8Denominator());

        summary.groupRecall().forEach((group, metric) ->
                System.out.printf("  %s recall=%s (%d/%d)%n",
                        group, metric.display(), metric.numerator(), metric.denominator()));
    }
}
