package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RankingEvaluationHarnessTest {
    @Test
    void reportsCandidateLatencyOnlyWhenBothPurposeSeriesHaveEnoughRuntimeSamples() {
        var ticks = new java.util.concurrent.atomic.AtomicLong();
        var metrics = new com.projectalice.backend.memory.infrastructure.search.CandidateGenerationLatencyMetrics(
                () -> ticks.getAndAdd(10_000_000L));
        for (int i = 0; i < 20; i++) {
            int sample = i;
            metrics.measure(com.projectalice.backend.memory.infrastructure.search.CandidateGenerationLatencyMetrics.Purpose.ANSWER,
                    () -> sample, ignored -> com.projectalice.backend.memory.application.SearchCompleteness.COMPLETE);
            metrics.measure(com.projectalice.backend.memory.infrastructure.search.CandidateGenerationLatencyMetrics.Purpose.RELATION,
                    () -> sample, ignored -> com.projectalice.backend.memory.application.SearchCompleteness.COMPLETE);
        }

        var report = new RankingEvaluationHarness().evaluate(RankingEvaluationDataset.generate(), Map.of(),
                RankingEvaluationHarness.Stage.CANDIDATE_GENERATION, metrics);

        assertEquals(RankingEvaluationHarness.Decision.PASS,
                report.targets().stream()
                        .filter(target -> target.target().startsWith("Answer / Relation Candidate Generation p95"))
                        .findFirst().orElseThrow().decision());
    }

    @Test
    void datasetIsFixedSizedVersionedReferentiallyValidAndReproducible() {
        var first = RankingEvaluationDataset.generate();
        var second = RankingEvaluationDataset.generate();
        assertEquals(300, first.size());
        assertEquals(first, second);
        assertEquals(200, first.get(0).memories().size());

        for (var group : RankingEvaluationDataset.Group.values()) {
            assertEquals(
                    100,
                    first.stream()
                            .filter(s -> s.group() == group)
                            .count());
        }

        assertEquals(
                50,
                first.stream()
                        .filter(s -> s.purpose()
                                == RankingEvaluationDataset.SearchPurpose.ANSWER_CURRENT)
                        .count());

        assertEquals(
                50,
                first.stream()
                        .filter(s -> s.purpose()
                                == RankingEvaluationDataset.SearchPurpose.ANSWER_HISTORICAL)
                        .count());

        assertEquals(
                10,
                first.stream()
                        .map(RankingEvaluationDataset.Scenario::category)
                        .distinct()
                        .count());

        assertTrue(first.stream()
                .anyMatch(s -> s.requiredMemoryIds().size() > 1));

        assertTrue(first.stream()
                .anyMatch(s -> s.relatedMemoryIds().size() > 1));

        assertTrue(first.stream().allMatch(s ->
                s.requiredMemoryIds().stream().allMatch(id ->
                        s.memories().stream().anyMatch(m ->
                                m.id().equals(id)
                                        && m.relevance()
                                                == RankingEvaluationDataset.Relevance.REQUIRED))));

        var labels = first.stream()
                .flatMap(s -> s.memories().stream())
                .map(RankingEvaluationDataset.MemoryFixture::expectedSignals)
                .toList();

        assertTrue(labels.stream().anyMatch(s ->
                s.entity() == RankingEvaluationDataset.SignalLabel.UNKNOWN
                        || s.intent() == RankingEvaluationDataset.SignalLabel.UNKNOWN));

        assertTrue(labels.stream().anyMatch(s ->
                s.scope() == RankingEvaluationDataset.SignalLabel.NOT_APPLICABLE
                        || s.temporal() == RankingEvaluationDataset.SignalLabel.NOT_APPLICABLE));

        assertThrows(
                IllegalArgumentException.class,
                () -> RankingEvaluationDataset.validate(first.subList(0, 299)));
    }

    @Test
    void harnessScoresSuppliedStageOutputsAndReportsSafetySeparately() {
        var dataset = RankingEvaluationDataset.generate();

        Map<String, RankingEvaluationHarness.StageResults> outputs =
                new HashMap<>();

        for (var scenario : dataset) {
            java.util.LinkedHashSet<String> selectedSet =
                    new java.util.LinkedHashSet<>(scenario.recallTargetIds());

            selectedSet.add(scenario.id() + "-m001");
            selectedSet.add(scenario.id() + "-m002");

            for (int index = 3;
                    index <= 9 && selectedSet.size() < 8;
                    index++) {
                if (index != 4) {
                    selectedSet.add(
                            scenario.id() + "-m%03d".formatted(index));
                }
            }

            List<String> selected =
                    new java.util.ArrayList<>(selectedSet);

            if (scenario.id().equals("answer_current_historical-009")) {
                selected.set(3, scenario.safetyExcludedIds().get(0));
            }

            outputs.put(
                    scenario.id(),
                    new RankingEvaluationHarness.StageResults(
                            selected, selected, selected));
        }

        var harness = new RankingEvaluationHarness();

        var report = harness.evaluate(
                dataset,
                outputs,
                RankingEvaluationHarness.Stage.FINAL_CONTEXT);

        assertEquals(300, report.scenarioResults().size());
        assertEquals("1.0000", report.overallRecall().display());
        assertEquals("0.2925", report.answerPrecision().display());
        assertEquals(1, report.safetyViolations().size());

        // Recall target evaluation remains independent of final-context precision.
        var candidateReport = harness.evaluate(
                dataset,
                outputs,
                RankingEvaluationHarness.Stage.CANDIDATE_POOL);

        assertEquals(
                RankingEvaluationHarness.Decision.PASS,
                candidateReport.targets().stream()
                        .filter(t -> t.target().startsWith("Required Memory Recall"))
                        .findFirst()
                        .orElseThrow()
                        .decision());

        assertEquals(
                RankingEvaluationHarness.Decision.FAIL,
                report.targets().stream()
                        .filter(t -> t.target().startsWith("Safety contamination"))
                        .findFirst()
                        .orElseThrow()
                        .decision());

        assertTrue(report.targets().stream()
                .filter(t -> t.target().contains("p95")
                        || t.target().contains("Pending Age"))
                .allMatch(t ->
                        t.decision()
                                == RankingEvaluationHarness.Decision.UNMEASURED));

        assertEquals(4, report.purposeRecall().size());

        // The diagnostic metric is retained, but the synthetic labels cannot
        // support a meaningful PASS/FAIL decision against the 85% target.
        var precisionTarget = report.targets().stream()
                .filter(t -> t.target().startsWith("Answer Context Precision"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                RankingEvaluationHarness.Decision.UNMEASURED,
                precisionTarget.decision());

        assertTrue(precisionTarget.reason()
                .contains("Insufficient relevant labels"));
    }

    @Test
    void harnessRejectsDuplicateAndUnknownOutputIds() {
        var scenario = RankingEvaluationDataset.generate().get(0);

        var duplicate = new RankingEvaluationHarness.StageResults(
                List.of(
                        scenario.recallTargetIds().get(0),
                        scenario.recallTargetIds().get(0)),
                List.of(),
                List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new RankingEvaluationHarness().evaluate(
                        List.of(scenario),
                        Map.of(scenario.id(), duplicate),
                        RankingEvaluationHarness.Stage.CANDIDATE_GENERATION));

        var unknown = new RankingEvaluationHarness.StageResults(
                List.of("missing-id"),
                List.of(),
                List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new RankingEvaluationHarness().evaluate(
                        List.of(scenario),
                        Map.of(scenario.id(), unknown),
                        RankingEvaluationHarness.Stage.CANDIDATE_GENERATION));
    }

    @Test
    void candidatePoolRecallUsesAllRequiredAndRelatedLabels() {
        var dataset = RankingEvaluationDataset.generate();

        var scenario = dataset.stream()
                .filter(s ->
                        s.group()
                                == RankingEvaluationDataset.Group.ANSWER_CURRENT_HISTORICAL
                                && s.requiredMemoryIds().size() > 1)
                .findFirst()
                .orElseThrow();

        var allRequired = scenario.requiredMemoryIds();

        var stage = new RankingEvaluationHarness.StageResults(
                allRequired, allRequired, allRequired);

        // Keep the required 300-scenario dataset; only this scenario has supplied stage output.
        var report = new RankingEvaluationHarness().evaluate(
                dataset,
                Map.of(scenario.id(), stage),
                RankingEvaluationHarness.Stage.CANDIDATE_POOL);

        assertEquals(
                allRequired.size(),
                report.overallRecall().numerator());

        assertEquals(
                allRequired.size(),
                report.overallRecall().denominator());
    }

    @Test
    void missingStageOutputsAreNotTreatedAsEmptySuccessfulResults() {
        var report = new RankingEvaluationHarness().evaluate(
                RankingEvaluationDataset.generate(),
                Map.of(),
                RankingEvaluationHarness.Stage.FINAL_CONTEXT);

        assertTrue(report.scenarioResults().isEmpty());
        assertEquals("N/A", report.overallRecall().display());

        assertEquals(
                RankingEvaluationHarness.Decision.UNMEASURED,
                report.targets().stream()
                        .filter(t -> t.target().startsWith("Safety contamination"))
                        .findFirst()
                        .orElseThrow()
                        .decision());

        assertEquals(
                RankingEvaluationHarness.Decision.UNMEASURED,
                report.targets().stream()
                        .filter(t -> t.target().startsWith("Answer Context"))
                        .findFirst()
                        .orElseThrow()
                        .decision());
    }

    @Test
    void precisionTargetRemainsUnmeasuredWhenContextHasFewerThanEightItems() {
        var dataset = RankingEvaluationDataset.generate();

        Map<String, RankingEvaluationHarness.StageResults> outputs =
                new HashMap<>();

        for (var scenario : dataset) {
            List<String> one =
                    List.of(scenario.recallTargetIds().get(0));

            outputs.put(
                    scenario.id(),
                    new RankingEvaluationHarness.StageResults(
                            one, one, one));
        }

        var report = new RankingEvaluationHarness().evaluate(
                dataset,
                outputs,
                RankingEvaluationHarness.Stage.FINAL_CONTEXT);

        assertEquals(100, report.underfilledPrecisionScenarioIds().size());
        assertEquals("N/A", report.answerPrecision().display());

        assertEquals(
                RankingEvaluationHarness.Decision.UNMEASURED,
                report.targets().stream()
                        .filter(t -> t.target().startsWith("Answer Context"))
                        .findFirst()
                        .orElseThrow()
                        .decision());
    }

    @Test
    void candidateSelectionExperimentIsVersionedAndEnforcesEligibilityBoundary() {
        var scenario = RankingEvaluationDataset.generate().get(8);

        // Deleted-fixture scenario.
        List<String> candidates = scenario.memories().stream()
                .map(RankingEvaluationDataset.MemoryFixture::id)
                .toList();

        var experiment = new CandidateSelectionExperiment();

        var valid = experiment.run(
                scenario,
                CandidateSelectionExperiment.EXPERIMENT_VERSION,
                candidates,
                eligible -> {
                    assertTrue(eligible.stream()
                            .noneMatch(f -> scenario.safetyExcludedIds()
                                    .contains(f.id())));

                    // Empty test policy exercises the boundary without defining a ranking rule.
                    return List.of();
                });

        assertEquals(
                CandidateSelectionExperiment.Status.VALID,
                valid.status());

        assertEquals(
                RankingEvaluationDataset.DATASET_VERSION,
                valid.datasetVersion());

        assertEquals(200, valid.inputCandidateIds().size());
        assertEquals(199, valid.eligibleCandidateIds().size());

        String prohibitedId = scenario.safetyExcludedIds().get(0);

        var invalid = experiment.run(
                scenario,
                CandidateSelectionExperiment.EXPERIMENT_VERSION,
                candidates,
                eligible -> List.of(prohibitedId));

        assertEquals(
                CandidateSelectionExperiment.Status.INVALID_OUTPUT,
                invalid.status());

        assertEquals(
                List.of(prohibitedId),
                invalid.safetyViolations());

        var tooMany = experiment.run(
                scenario,
                CandidateSelectionExperiment.EXPERIMENT_VERSION,
                candidates,
                eligible -> eligible.stream()
                        .limit(41)
                        .map(RankingEvaluationDataset.MemoryFixture::id)
                        .toList());

        assertEquals(
                CandidateSelectionExperiment.Status.INVALID_OUTPUT,
                tooMany.status());

        var callCount = new java.util.concurrent.atomic.AtomicInteger();

        var unstable = experiment.run(
                scenario,
                CandidateSelectionExperiment.EXPERIMENT_VERSION,
                candidates,
                eligible -> callCount.getAndIncrement() == 0
                        ? List.of()
                        : List.of(eligible.get(0).id()));

        assertEquals(
                CandidateSelectionExperiment.Status.NON_DETERMINISTIC,
                unstable.status());

        for (var condition : List.of(
                RankingEvaluationDataset.SafetyCondition.DELETED,
                RankingEvaluationDataset.SafetyCondition.DISALLOWED_SENSITIVE,
                RankingEvaluationDataset.SafetyCondition.NON_HISTORICAL_RESOLVED)) {

            var unsafeScenario = RankingEvaluationDataset.generate().stream()
                    .filter(s -> s.memories().stream()
                            .anyMatch(f -> f.safetyCondition() == condition))
                    .findFirst()
                    .orElseThrow();

            String unsafeId = unsafeScenario.safetyExcludedIds().get(0);

            var detected = experiment.run(
                    unsafeScenario,
                    CandidateSelectionExperiment.EXPERIMENT_VERSION,
                    unsafeScenario.memories().stream()
                            .map(RankingEvaluationDataset.MemoryFixture::id)
                            .toList(),
                    eligible -> List.of(unsafeId));

            assertEquals(
                    List.of(unsafeId),
                    detected.safetyViolations(),
                    condition.name());
        }
    }

    @Test
    void candidatePoolExperimentDoesNotSelectAnOfficialPolicyAndSupportsRecallEvaluation() {
        var scenario = RankingEvaluationDataset.generate().get(0);

        List<String> allCandidates = scenario.memories().stream()
                .map(RankingEvaluationDataset.MemoryFixture::id)
                .toList();

        var experiment = new CandidateSelectionExperiment();

        var result = experiment.run(
                scenario,
                "test-empty-policy-v1",
                allCandidates,
                eligible -> List.of());

        assertEquals(
                CandidateSelectionExperiment.Status.VALID,
                result.status());

        assertEquals("0.0000", result.recallAtLimit().display());
        assertEquals(40, result.outputLimit());
        assertEquals(200, result.inputCandidateIds().size());
    }

    @Test
    void precisionForUnderfilledContextIsNotAssignedAnUnacceptedDenominator() {
        var dataset = RankingEvaluationDataset.generate();

        Map<String, RankingEvaluationHarness.StageResults> outputs =
                new HashMap<>();

        for (var scenario : dataset) {
            List<String> one =
                    List.of(scenario.recallTargetIds().get(0));

            outputs.put(
                    scenario.id(),
                    new RankingEvaluationHarness.StageResults(
                            one, one, one));
        }

        var report = new RankingEvaluationHarness().evaluate(
                dataset,
                outputs,
                RankingEvaluationHarness.Stage.FINAL_CONTEXT);

        assertEquals("N/A", report.answerPrecision().display());
        assertEquals(100, report.underfilledPrecisionScenarioIds().size());

        assertEquals(
                RankingEvaluationHarness.Decision.UNMEASURED,
                report.targets().stream()
                        .filter(t -> t.target().startsWith("Answer Context"))
                        .findFirst()
                        .orElseThrow()
                        .decision());
    }
}
