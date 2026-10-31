package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Exploratory analysis on synthetic data. Output is not evidence of production ranking performance. */
class RankingBiasAnalysisTest {
    private static final long FIRST_SEED = 20261022L;

    @Test
    void explainsWhyNgramReachesFullRecallOnBaseDataset() {
        var dataset = RankingEvaluationDataset.generate();
        int scenarios = 0;
        int targetsContainSubject = 0;
        int positiveMargin = 0;
        double minMargin = Double.POSITIVE_INFINITY;
        long targets = 0;
        long eligible = 0;
        for (var s : dataset) {
            var expected = switch (s.group()) {
                case ANSWER_CURRENT_HISTORICAL -> s.requiredMemoryIds();
                case RELATION_DECISION -> s.relatedMemoryIds();
                case MANAGEMENT_SEARCH -> s.recallTargetIds();
            };
            var queryGrams = RankingComparisonRunner.ngrams(s.query());
            double minTarget = Double.POSITIVE_INFINITY;
            double maxOther = 0;
            boolean allContainSubject = true;
            for (var m : s.memories()) {
                if (m.eligibility() != RankingEvaluationDataset.Eligibility.ELIGIBLE) continue;
                eligible++;
                double score = RankingComparisonRunner.dice(queryGrams, RankingComparisonRunner.ngrams(m.content()));
                if (expected.contains(m.id())) {
                    minTarget = Math.min(minTarget, score);
                    allContainSubject &= m.content().contains("試験用プロジェクトAL-");
                } else {
                    maxOther = Math.max(maxOther, score);
                }
            }
            targets += expected.size();
            scenarios++;
            if (allContainSubject) targetsContainSubject++;
            double margin = minTarget - maxOther;
            minMargin = Math.min(minMargin, margin);
            if (margin > 0) positiveMargin++;
        }
        System.out.printf("BASE_CAUSE scenarios=%d targetsShareSubjectString=%d strictMarginPositive=%d minMargin=%.4f "
                + "avgTargetsPerScenario=%.2f eligiblePerScenario=%.1f randomExpectedRecall@40=%.4f%n",
                scenarios, targetsContainSubject, positiveMargin, minMargin,
                (double) targets / scenarios, (double) eligible / scenarios,
                40.0 / ((double) eligible / scenarios));
        assertEquals(300, scenarios);
    }

    @Test
    void comparesPoliciesOnAdversarialDatasetAcrossTenSeeds() {
        var dataset = RankingAdversarialDataset.generate();
        assertEquals(125, dataset.size());
        var runner = new RankingComparisonRunner();
        List<RankingComparisonRunner.Report> reports = new ArrayList<>();
        for (long seed = FIRST_SEED; seed < FIRST_SEED + 10; seed++) {
            reports.add(runner.run(dataset, RankingAdversarialDataset.DATASET_VERSION, seed));
        }
        assertEquals(reports.get(3), runner.run(dataset, RankingAdversarialDataset.DATASET_VERSION, FIRST_SEED + 3));

        Map<String, Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>> policies = new TreeMap<>();
        policies.put("1-shuffled-input-baseline", RankingComparisonRunner.Report::shuffledInputBaseline);
        policies.put("2-query-content-overlap", RankingComparisonRunner.Report::queryContentOverlap);
        policies.put("3-japanese-char-ngram", RankingComparisonRunner.Report::japaneseCharNgram);
        policies.put("4-hybrid-overlap-ngram", RankingComparisonRunner.Report::hybrid);

        policies.forEach((name, pick) -> {
            double sum = 0, min = 1, max = 0;
            long irrelevantTop8 = 0;
            int invalid = 0, safety = 0;
            Map<String, Long> missed = new TreeMap<>();
            Map<RankingEvaluationDataset.Group, double[]> groups = new java.util.EnumMap<>(RankingEvaluationDataset.Group.class);
            for (var r : reports) {
                var s = pick.apply(r);
                sum += s.recall();
                min = Math.min(min, s.recall());
                max = Math.max(max, s.recall());
                irrelevantTop8 += s.irrelevantInTop8();
                invalid += s.invalidRuns();
                safety += s.safetyViolations();
                s.missedByCategory().forEach((k, v) -> missed.merge(k, v, Long::sum));
                s.groupRecall().forEach((g, m) -> {
                    double[] a = groups.computeIfAbsent(g, ignored -> new double[] {0, 1, 0});
                    a[0] += m.value();
                    a[1] = Math.min(a[1], m.value());
                    a[2] = Math.max(a[2], m.value());
                });
            }
            System.out.printf("ADV policy=%s recallMean=%.4f min=%.4f max=%.4f invalid=%d safety=%d "
                    + "irrelevantInTop8(total over 10 seeds)=%d missedByCategory=%s%n",
                    name, sum / 10, min, max, invalid, safety, irrelevantTop8, missed);
            groups.forEach((g, a) -> System.out.printf("ADV   %s group=%s mean=%.4f min=%.4f max=%.4f%n",
                    name, g, a[0] / 10, a[1], a[2]));
            assertEquals(0, invalid);
            assertEquals(0, safety);
            assertTrue(reports.stream().allMatch(r -> pick.apply(r).scenarios() == 125));
        });
    }
}
