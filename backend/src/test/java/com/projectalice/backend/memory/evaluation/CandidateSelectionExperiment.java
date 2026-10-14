package com.projectalice.backend.memory.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Test-scope experiment boundary. It is not registered or referenced by production DI. */
public final class CandidateSelectionExperiment {
    public static final String EXPERIMENT_VERSION = "candidate-selection-experiment-v2";
    public static final int INPUT_LIMIT = 200;
    public static final int OUTPUT_LIMIT = 40;

    // Recall is evaluated against all purpose-specific targets in the scenario, not a single fixture.

    public enum Status { VALID, NON_DETERMINISTIC, INVALID_INPUT, INVALID_OUTPUT, FAILED }
    @FunctionalInterface public interface Selector { List<String> select(List<RankingEvaluationDataset.MemoryFixture> eligible); }
    public record Result(String datasetVersion, String experimentVersion, String scenarioId,
            List<String> inputCandidateIds, List<String> eligibleCandidateIds, List<String> selectedIds,
            RankingEvaluationHarness.Metric recallAt40, List<String> safetyViolations,
            List<String> invalidSelectedIds, Status status) {}

    public Result run(RankingEvaluationDataset.Scenario scenario, String experimentVersion,
            List<String> inputCandidateIds, Selector selector) {
        Objects.requireNonNull(scenario);
        Objects.requireNonNull(inputCandidateIds);
        Objects.requireNonNull(selector);
        if (experimentVersion == null || experimentVersion.isBlank())
            throw new IllegalArgumentException("Experiment version is required");

        Set<String> allIds = scenario.memories().stream().map(RankingEvaluationDataset.MemoryFixture::id)
                .collect(java.util.stream.Collectors.toSet());
        if (inputCandidateIds.size() > INPUT_LIMIT || new HashSet<>(inputCandidateIds).size() != inputCandidateIds.size()
                || !allIds.containsAll(inputCandidateIds))
            return result(scenario, experimentVersion, inputCandidateIds, List.of(), List.of(), Status.INVALID_INPUT);

        List<RankingEvaluationDataset.MemoryFixture> eligible = scenario.memories().stream()
                .filter(f -> inputCandidateIds.contains(f.id()))
                .filter(f -> f.eligibility() == RankingEvaluationDataset.Eligibility.ELIGIBLE).toList();
        List<String> eligibleIds = eligible.stream().map(RankingEvaluationDataset.MemoryFixture::id).toList();
        List<String> first;
        List<String> second;
        try {
            first = selector.select(eligible);
            second = selector.select(eligible);
            if (first == null || second == null) return result(scenario, experimentVersion,
                    inputCandidateIds, eligibleIds, List.of(), Status.INVALID_OUTPUT);
            first = List.copyOf(first);
            second = List.copyOf(second);
        } catch (RuntimeException failure) {
            return result(scenario, experimentVersion, inputCandidateIds, eligibleIds, List.of(), Status.FAILED);
        }

        Set<String> eligibleSet = Set.copyOf(eligibleIds);
        List<String> invalid = java.util.stream.Stream.concat(invalidIds(first, eligibleSet).stream(),
                invalidIds(second, eligibleSet).stream()).distinct().toList();
        List<String> safety = java.util.stream.Stream.concat(first.stream(), second.stream())
                .filter(scenario.safetyExcludedIds()::contains).distinct().toList();
        Status status = !invalid.isEmpty() || first.size() > OUTPUT_LIMIT || second.size() > OUTPUT_LIMIT ? Status.INVALID_OUTPUT
                : !first.equals(second) ? Status.NON_DETERMINISTIC : Status.VALID;
        return result(scenario, experimentVersion, inputCandidateIds, eligibleIds, first, status, safety, invalid);
    }

    private static List<String> invalidIds(List<String> selected, Set<String> eligibleIds) {
        Set<String> seen = new HashSet<>();
        Set<String> invalid = new java.util.LinkedHashSet<>();
        for (String id : selected) if (!eligibleIds.contains(id) || !seen.add(id)) invalid.add(id);
        return List.copyOf(invalid);
    }

    private static Result result(RankingEvaluationDataset.Scenario scenario, String experimentVersion,
            List<String> input, List<String> eligible, List<String> selected, Status status) {
        return result(scenario, experimentVersion, input, eligible, selected, status, List.of(), List.of());
    }
    private static Result result(RankingEvaluationDataset.Scenario scenario, String experimentVersion,
            List<String> input, List<String> eligible, List<String> selected, Status status,
            List<String> safety, List<String> invalid) {
        Set<String> expected = Set.copyOf(scenario.recallTargetIds());
        long found = selected.stream().distinct().filter(expected::contains).count();
        var recall = new RankingEvaluationHarness.Metric(found, expected.size(),
                expected.isEmpty() ? null : (double) found / expected.size());
        return new Result(scenario.datasetVersion(), experimentVersion, scenario.id(), List.copyOf(input),
                List.copyOf(eligible), List.copyOf(selected), recall, List.copyOf(safety), List.copyOf(invalid), status);
    }
}
