package com.projectalice.backend.memory.evaluation;

import static com.projectalice.backend.memory.evaluation.RankingEvaluationDataset.Group;
import static com.projectalice.backend.memory.evaluation.RankingEvaluationDataset.Relevance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Scores explicitly supplied stage outputs; it does not execute or define a ranking policy. */
public final class RankingEvaluationHarness {
    public enum Stage { CANDIDATE_GENERATION, CANDIDATE_POOL, FINAL_CONTEXT }
    public enum Decision { PASS, FAIL, UNMEASURED }
    public record StageResults(List<String> candidateGenerationIds, List<String> candidatePoolIds,
            List<String> finalContextIds) {
        public StageResults {
            candidateGenerationIds = List.copyOf(candidateGenerationIds);
            candidatePoolIds = List.copyOf(candidatePoolIds);
            finalContextIds = List.copyOf(finalContextIds);
        }
        List<String> at(Stage stage) {
            return switch (stage) {
                case CANDIDATE_GENERATION -> candidateGenerationIds;
                case CANDIDATE_POOL -> candidatePoolIds;
                case FINAL_CONTEXT -> finalContextIds;
            };
        }
    }
    public record Metric(long numerator, long denominator, Double value) {
        public String display() { return value == null ? "N/A" : "%.4f".formatted(value); }
    }
    public record TargetResult(String target, Decision decision, String reason) {}
    public record ScenarioResult(String scenarioId, Group group, RankingEvaluationDataset.SearchPurpose purpose,
            String category, Stage stage,
            int recallCutoff, Metric recall, Metric precision, List<String> safetyViolations) {}
    public record Report(List<ScenarioResult> scenarioResults, List<String> missingScenarioIds, Map<String, Metric> categoryRecall,
            Map<String, Metric> groupRecall, Map<String, Metric> purposeRecall, Metric overallRecall, Metric answerPrecision,
            List<String> underfilledPrecisionScenarioIds, List<String> safetyViolations, List<TargetResult> targets) {}

    public Report evaluate(List<RankingEvaluationDataset.Scenario> dataset,
            Map<String, StageResults> outputs, Stage stage) {
        RankingEvaluationDataset.validate(dataset);
        Objects.requireNonNull(outputs);
        Objects.requireNonNull(stage);
        Set<String> scenarioIds = dataset.stream().map(RankingEvaluationDataset.Scenario::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!scenarioIds.containsAll(outputs.keySet())) throw new IllegalArgumentException("Output contains unknown scenario id");
        List<ScenarioResult> rows = new ArrayList<>();
        List<String> allSafety = new ArrayList<>();
        for (var scenario : dataset) {
            StageResults result = outputs.get(scenario.id());
            if (result == null) continue; // Missing execution output is unmeasured, never an empty successful result.
            List<String> ids = result.at(stage);
            Set<String> fixtureIds = scenario.memories().stream().map(RankingEvaluationDataset.MemoryFixture::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (!fixtureIds.containsAll(ids)) throw new IllegalArgumentException("Output references unknown fixture id for " + scenario.id());
            if (new HashSet<>(ids).size() != ids.size())
                throw new IllegalArgumentException("Output contains duplicate fixture id for " + scenario.id());
            Set<String> relevant = scenario.memories().stream()
                    .filter(f -> f.eligibility() == RankingEvaluationDataset.Eligibility.ELIGIBLE)
                    .filter(f -> f.relevance() == Relevance.REQUIRED || f.relevance() == Relevance.RELATED)
                    .map(RankingEvaluationDataset.MemoryFixture::id).collect(java.util.stream.Collectors.toSet());
            int recallCutoff = cutoff(stage, scenario.group());
            List<String> expectedRecallIds = recallTargets(scenario, stage);
            Metric recall = ratio(intersection(ids.stream().limit(recallCutoff).toList(), new HashSet<>(expectedRecallIds)),
                    expectedRecallIds.size());
            Metric precision = stage != Stage.FINAL_CONTEXT || scenario.group() == Group.MANAGEMENT_SEARCH || ids.size() < 8
                    ? ratio(0, 0) : ratio(intersection(ids.stream().limit(8).toList(), relevant), 8);
            List<String> violations = ids.stream().filter(scenario.safetyExcludedIds()::contains)
                    .map(id -> scenario.id() + ":" + id).toList();
            allSafety.addAll(violations);
            rows.add(new ScenarioResult(scenario.id(), scenario.group(), scenario.purpose(), scenario.category(), stage, recallCutoff,
                    recall, precision, violations));
        }
        Map<String, Metric> categoryRecall = aggregate(rows, row -> row.category());
        Map<String, Metric> groupRecall = aggregate(rows, row -> row.group().name());
        Map<String, Metric> purposeRecall = aggregate(rows, row -> row.purpose().name());
        Metric overall = aggregateMetric(rows);
        // Answer precision is defined for answer contexts only; relation-decision contexts must not affect it.
        Metric precision = aggregateMetric(rows.stream()
                .filter(r -> r.group() == Group.ANSWER_CURRENT_HISTORICAL)
                .filter(r -> r.precision().denominator() > 0)
                .toList(), true);
        List<String> missing = dataset.stream().map(RankingEvaluationDataset.Scenario::id)
                .filter(id -> !outputs.containsKey(id)).toList();
        List<String> underfilled = rows.stream().filter(row -> row.stage() == Stage.FINAL_CONTEXT
                && row.group() == Group.ANSWER_CURRENT_HISTORICAL && row.precision().denominator() == 0)
                .map(ScenarioResult::scenarioId).toList();
        List<TargetResult> targets = targets(dataset, rows, allSafety, stage, missing, underfilled);
        return new Report(List.copyOf(rows), missing, Map.copyOf(categoryRecall), Map.copyOf(groupRecall), Map.copyOf(purposeRecall),
                overall, precision, List.copyOf(underfilled), List.copyOf(allSafety), List.copyOf(targets));
    }

    private static List<String> recallTargets(RankingEvaluationDataset.Scenario scenario, Stage stage) {
        if (stage != Stage.CANDIDATE_POOL) return scenario.recallTargetIds();
        return switch (scenario.group()) {
            case ANSWER_CURRENT_HISTORICAL -> scenario.requiredMemoryIds();
            case RELATION_DECISION -> scenario.relatedMemoryIds();
            case MANAGEMENT_SEARCH -> scenario.recallTargetIds();
        };
    }

    private static int cutoff(Stage stage, Group group) {
        return switch (stage) {
            case CANDIDATE_GENERATION -> 200;
            case CANDIDATE_POOL -> group == Group.MANAGEMENT_SEARCH ? 100 : 40;
            case FINAL_CONTEXT -> 8;
        };
    }

    private static List<TargetResult> targets(List<RankingEvaluationDataset.Scenario> dataset,
            List<ScenarioResult> rows, List<String> safety, Stage stage, List<String> missing,
            List<String> underfilledPrecision) {
        List<TargetResult> result = new ArrayList<>();
        if (stage == Stage.CANDIDATE_POOL) {
            result.add(target("Required Memory Recall@40 >= 95%", metricDecision(filter(rows, Group.ANSWER_CURRENT_HISTORICAL), .95,
                    missingFor(dataset, missing, Group.ANSWER_CURRENT_HISTORICAL)), "DB2-065"));
            result.add(target("Related Memory Recall@40 >= 98%", metricDecision(filter(rows, Group.RELATION_DECISION), .98,
                    missingFor(dataset, missing, Group.RELATION_DECISION)), "DB2-065"));
            result.add(target("Expected Result Recall@100 >= 95%", metricDecision(filter(rows, Group.MANAGEMENT_SEARCH), .95,
                    missingFor(dataset, missing, Group.MANAGEMENT_SEARCH)), "DB2-065"));
        } else {
            result.add(new TargetResult("Required Memory Recall@40 >= 95%", Decision.UNMEASURED, "Requires candidate-pool stage output"));
            result.add(new TargetResult("Related Memory Recall@40 >= 98%", Decision.UNMEASURED, "Requires candidate-pool stage output"));
            result.add(new TargetResult("Expected Result Recall@100 >= 95%", Decision.UNMEASURED, "Requires candidate-pool stage output"));
        }
        Metric answerPrecision = aggregateMetric(rows.stream().filter(r -> r.group() == Group.ANSWER_CURRENT_HISTORICAL
                && r.precision().denominator() > 0).toList(), true);
        boolean precisionUnmeasured = stage != Stage.FINAL_CONTEXT
                || missingFor(dataset, missing, Group.ANSWER_CURRENT_HISTORICAL) || !underfilledPrecision.isEmpty();
        result.add(target("Answer Context Precision@8 >= 85%", precisionUnmeasured
                ? Decision.UNMEASURED : decision(answerPrecision, .85),
                precisionUnmeasured ? "Missing output or fewer than 8 results; accepted denominator rule is unspecified"
                        : "N/A is UNMEASURED, not PASS"));
        result.add(new TargetResult("Safety contamination = 0", !safety.isEmpty() ? Decision.FAIL
                : !missing.isEmpty() ? Decision.UNMEASURED : Decision.PASS,
                !safety.isEmpty() ? safety.size() + " prohibited-id occurrence(s)"
                        : !missing.isEmpty() ? "Missing scenario outputs" : "Complete scenario coverage"));
        result.add(new TargetResult("Answer / Relation Candidate Generation p95 <= 300 ms", Decision.UNMEASURED, "No timed production candidate generation"));
        result.add(new TargetResult("Management Search Session Build p95 <= 1,000 ms", Decision.UNMEASURED, "No timed production session build"));
        result.add(new TargetResult("Projection Pending Age p95 <= 5 s; max <= 30 s", Decision.UNMEASURED, "No worker/projection timing input"));
        result.add(new TargetResult("Management candidate-cap failure rate < 1%", Decision.UNMEASURED, "No production search/cap outcome input"));
        return result;
    }

    private static TargetResult target(String name, Decision decision, String reason) { return new TargetResult(name, decision, reason); }
    private static Decision metricDecision(List<ScenarioResult> rows, double target, boolean missing) {
        return missing || rows.isEmpty() ? Decision.UNMEASURED : decision(aggregateMetric(rows), target);
    }
    private static boolean missingFor(List<RankingEvaluationDataset.Scenario> dataset, List<String> missing, Group group) {
        Set<String> targetIds = dataset.stream().filter(s -> s.group() == group).map(RankingEvaluationDataset.Scenario::id)
                .collect(java.util.stream.Collectors.toSet());
        return missing.stream().anyMatch(targetIds::contains);
    }
    private static Decision decision(Metric metric, double target) {
        return metric.value() == null ? Decision.UNMEASURED : metric.value() >= target ? Decision.PASS : Decision.FAIL;
    }
    private static long intersection(List<String> actual, Set<String> expected) {
        return actual.stream().distinct().filter(expected::contains).count();
    }
    private static Metric ratio(long n, long d) { return new Metric(n, d, d == 0 ? null : (double) n / d); }
    private interface Key { String key(ScenarioResult row); }
    private static Map<String, Metric> aggregate(List<ScenarioResult> rows, Key key) {
        java.util.Map<String, List<ScenarioResult>> groups = new java.util.TreeMap<>();
        rows.forEach(row -> groups.computeIfAbsent(key.key(row), ignored -> new ArrayList<>()).add(row));
        java.util.Map<String, Metric> result = new java.util.TreeMap<>();
        groups.forEach((name, values) -> result.put(name, aggregateMetric(values)));
        return Map.copyOf(result);
    }
    private static Metric aggregateMetric(List<ScenarioResult> rows) { return aggregateMetric(rows, false); }
    private static Metric aggregateMetric(List<ScenarioResult> rows, boolean precision) {
        long n = 0, d = 0;
        for (ScenarioResult row : rows) {
            Metric metric = precision ? row.precision() : row.recall();
            n += metric.numerator(); d += metric.denominator();
        }
        return ratio(n, d);
    }
    private static List<ScenarioResult> filter(List<ScenarioResult> rows, Group group) {
        return rows.stream().filter(r -> r.group() == group).toList();
    }
}
