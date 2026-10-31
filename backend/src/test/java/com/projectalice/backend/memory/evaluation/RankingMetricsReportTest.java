package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Exploratory metrics on synthetic data; not a production-performance claim or a formal target decision. */
class RankingMetricsReportTest {
    private static final long FIRST_SEED = 20261022L;

    private static final Map<String, Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>> POLICIES =
            new LinkedHashMap<>();

    static {
        POLICIES.put("shuffled-input-baseline", RankingComparisonRunner.Report::shuffledInputBaseline);
        POLICIES.put("query-content-overlap", RankingComparisonRunner.Report::queryContentOverlap);
        POLICIES.put("japanese-char-ngram", RankingComparisonRunner.Report::japaneseCharNgram);
        POLICIES.put("hybrid-overlap-ngram", RankingComparisonRunner.Report::hybrid);
    }

    @Test
    void reportsRecallPrecisionSafetyAndFailureCasesOnBothDatasets() {
        var runner = new RankingComparisonRunner();
        report("BASE", RankingEvaluationDataset.generate(), RankingEvaluationDataset.DATASET_VERSION, runner);
        report("ADV", RankingAdversarialDataset.generate(), RankingAdversarialDataset.DATASET_VERSION, runner);
        report("DENSE", RankingDenseRelevanceDataset.generate(), RankingDenseRelevanceDataset.DATASET_VERSION, runner);
    }

    @Test
    void denseDatasetCanRepresentPrecisionTargetBeforeScoring() {
        var report = new RankingComparisonRunner().run(RankingDenseRelevanceDataset.generate(),
                RankingDenseRelevanceDataset.DATASET_VERSION, FIRST_SEED);
        // Ceiling must reach the existing 85% target, otherwise the target is unmeasurable.
        assertTrue(report.shuffledInputBaseline().precisionAt8Ceiling() >= 0.85);
        assertEquals(report.shuffledInputBaseline().precision8CeilingHits(),
                report.japaneseCharNgram().precision8CeilingHits());
        assertEquals(report.shuffledInputBaseline().precision8CeilingHits(),
                report.hybrid().precision8CeilingHits());
    }

    private static void report(String label, List<RankingEvaluationDataset.Scenario> dataset, String version,
            RankingComparisonRunner runner) {
        List<RankingComparisonRunner.Report> reports = new ArrayList<>();
        for (long seed = FIRST_SEED; seed < FIRST_SEED + 10; seed++) {
            reports.add(runner.run(dataset, version, seed));
        }
        Map<String, RankingEvaluationDataset.Scenario> scenarios = new LinkedHashMap<>();
        dataset.forEach(s -> scenarios.put(s.id(), s));

        POLICIES.forEach((name, pick) -> {
            double recall = 0;
            double precision = 0;
            double ceiling = 0;
            int safety = 0;
            int invalid = 0;
            Map<RankingEvaluationDataset.Group, Double> groupRecall = new java.util.EnumMap<>(RankingEvaluationDataset.Group.class);
            for (var r : reports) {
                var s = pick.apply(r);
                recall += s.recall() / reports.size();
                precision += s.precisionAt8() / reports.size();
                ceiling += s.precisionAt8Ceiling() / reports.size();
                safety += s.safetyViolations();
                invalid += s.invalidRuns();
                s.groupRecall().forEach((g, m) -> groupRecall.merge(g, m.value() / reports.size(), Double::sum));
            }
            System.out.printf("%s policy=%s scenarios=%d recall@40=%.4f groupRecall=%s contextPrecision@8=%.4f "
                    + "precisionCeiling=%.4f safetyViolations=%d invalidRuns=%d%n",
                    label, name, dataset.size(), recall, groupRecall, precision, ceiling, safety, invalid);
            assertEquals(0, safety);
            assertEquals(0, invalid);

            // Representative failures from the first seed: one missed-target case and one false-top case per category.
            var first = pick.apply(reports.get(0));
            Map<String, String> shownMiss = new LinkedHashMap<>();
            Map<String, String> shownFalse = new LinkedHashMap<>();
            for (var c : first.cases()) {
                var scenario = scenarios.get(c.scenarioId());
                Map<String, String> content = new java.util.HashMap<>();
                scenario.memories().forEach(m -> content.put(m.id(), m.content()));
                if (!c.missedTargetIds().isEmpty()) {
                    shownMiss.putIfAbsent(c.category(), "query=" + scenario.query() + " missed="
                            + content.get(c.missedTargetIds().get(0)));
                }
                if (!c.irrelevantInTop8Ids().isEmpty() && !c.category().equals("ordinary")) {
                    shownFalse.putIfAbsent(c.category(), "query=" + scenario.query() + " falseTop="
                            + content.get(c.irrelevantInTop8Ids().get(0)));
                }
            }
            shownMiss.forEach((k, v) -> System.out.printf("%s   MISS policy=%s category=%s %s%n", label, name, k, v));
            shownFalse.forEach((k, v) -> System.out.printf("%s   FALSE_TOP policy=%s category=%s %s%n", label, name, k, v));
            assertTrue(first.cases().size() == dataset.size());
        });
    }
}
