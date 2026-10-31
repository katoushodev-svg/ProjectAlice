package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingMultiSeedComparisonTest {
    @Test
    void reportsComparisonAcrossTenDeterministicSeeds() {
        RankingComparisonRunner runner = new RankingComparisonRunner();
        List<RankingComparisonRunner.Report> reports = new ArrayList<>();

        for (long seed = 20261022L; seed < 20261032L; seed++) {
            reports.add(runner.run(seed));
        }

        double baselineSum = 0;
        double overlapSum = 0;
        double baselineMin = Double.POSITIVE_INFINITY;
        double baselineMax = Double.NEGATIVE_INFINITY;
        double overlapMin = Double.POSITIVE_INFINITY;
        double overlapMax = Double.NEGATIVE_INFINITY;

        for (var report : reports) {
            var baseline = report.shuffledInputBaseline();
            var overlap = report.queryContentOverlap();

            assertEquals(300, baseline.scenarios());
            assertEquals(300, overlap.scenarios());
            assertEquals(0, baseline.invalidRuns());
            assertEquals(0, overlap.invalidRuns());

            baselineSum += baseline.recall();
            overlapSum += overlap.recall();
            baselineMin = Math.min(baselineMin, baseline.recall());
            baselineMax = Math.max(baselineMax, baseline.recall());
            overlapMin = Math.min(overlapMin, overlap.recall());
            overlapMax = Math.max(overlapMax, overlap.recall());

            System.out.printf(
                "seed=%d baseline=%.4f overlap=%.4f delta=%+.4f%n",
                report.shuffleSeed(), baseline.recall(), overlap.recall(),
                overlap.recall() - baseline.recall());
        }

        double baselineMean = baselineSum / reports.size();
        double overlapMean = overlapSum / reports.size();

        System.out.printf(
            "SUMMARY seeds=%d baselineMean=%.4f baselineRange=[%.4f, %.4f] "
                + "overlapMean=%.4f overlapRange=[%.4f, %.4f] meanDelta=%+.4f%n",
            reports.size(), baselineMean, baselineMin, baselineMax,
            overlapMean, overlapMin, overlapMax, overlapMean - baselineMean);

        assertEquals(10, reports.size());

        // Experimental comparison of all three policies; synthetic data, not a production claim.
        java.util.function.Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>[] pick =
                new java.util.function.Function[] {
                    (java.util.function.Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>)
                            RankingComparisonRunner.Report::shuffledInputBaseline,
                    (java.util.function.Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>)
                            RankingComparisonRunner.Report::queryContentOverlap,
                    (java.util.function.Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>)
                            RankingComparisonRunner.Report::japaneseCharNgram,
                    (java.util.function.Function<RankingComparisonRunner.Report, RankingComparisonRunner.PolicySummary>)
                            RankingComparisonRunner.Report::hybrid
                };
        for (var p : pick) {
            String name = p.apply(reports.get(0)).policy();
            var all = new java.util.ArrayList<Double>();
            for (var group : RankingEvaluationDataset.Group.values()) {
                double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (var r : reports) {
                    double v = p.apply(r).groupRecall().get(group).value();
                    sum += v; min = Math.min(min, v); max = Math.max(max, v);
                }
                System.out.printf("POLICY_GROUP policy=%s group=%s mean=%.4f min=%.4f max=%.4f%n",
                        name, group, sum / reports.size(), min, max);
            }
            double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            long missed = 0, paraTargets = 0;
            for (var r : reports) {
                var s = p.apply(r);
                assertEquals(300, s.scenarios());
                assertEquals(0, s.invalidRuns());
                assertEquals(0, s.safetyViolations());
                sum += s.recall(); min = Math.min(min, s.recall()); max = Math.max(max, s.recall());
                missed += s.paraphraseMissedTargets();
                paraTargets += s.paraphraseTargets();
            }
            System.out.printf("POLICY_OVERALL policy=%s mean=%.4f min=%.4f max=%.4f paraphraseMissedTotal=%d/%d%n",
                    name, sum / reports.size(), min, max, missed, paraTargets);
        }

        for (var group : RankingEvaluationDataset.Group.values()) {
            double baselineGroupSum = 0;
            double overlapGroupSum = 0;
            double baselineGroupMin = Double.POSITIVE_INFINITY;
            double baselineGroupMax = Double.NEGATIVE_INFINITY;
            double overlapGroupMin = Double.POSITIVE_INFINITY;
            double overlapGroupMax = Double.NEGATIVE_INFINITY;

            for (var report : reports) {
                var baselineMetric = report.shuffledInputBaseline().groupRecall().get(group);
                var overlapMetric = report.queryContentOverlap().groupRecall().get(group);
                assertTrue(baselineMetric.value() != null);
                assertTrue(overlapMetric.value() != null);

                double baselineValue = baselineMetric.value();
                double overlapValue = overlapMetric.value();

                baselineGroupSum += baselineValue;
                overlapGroupSum += overlapValue;
                baselineGroupMin = Math.min(baselineGroupMin, baselineValue);
                baselineGroupMax = Math.max(baselineGroupMax, baselineValue);
                overlapGroupMin = Math.min(overlapGroupMin, overlapValue);
                overlapGroupMax = Math.max(overlapGroupMax, overlapValue);
            }

            double baselineGroupMean = baselineGroupSum / reports.size();
            double overlapGroupMean = overlapGroupSum / reports.size();
            System.out.printf(
                "GROUP_SUMMARY group=%s baselineMean=%.4f baselineRange=[%.4f, %.4f] "
                    + "overlapMean=%.4f overlapRange=[%.4f, %.4f] meanDelta=%+.4f%n",
                group, baselineGroupMean, baselineGroupMin, baselineGroupMax,
                overlapGroupMean, overlapGroupMin, overlapGroupMax,
                overlapGroupMean - baselineGroupMean);
        }
    }
}
