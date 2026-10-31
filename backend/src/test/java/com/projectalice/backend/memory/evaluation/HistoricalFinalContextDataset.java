package com.projectalice.backend.memory.evaluation;

import com.projectalice.backend.memory.domain.MemoryState;
import java.util.ArrayList;
import java.util.List;

/**
 * Historical Final Context evaluation dataset.
 *
 * This is a test-only transformed synthetic dataset. Eligible fixtures are
 * converted to RESOLVED so the production historical eligibility policy can
 * be evaluated without changing production code or the base dataset.
 */
public final class HistoricalFinalContextDataset {

    public static final String DATASET_VERSION =
            "historical-final-context-v1";
    public static final String GENERATOR_VERSION =
            "historical-state-transform-v1";

    private HistoricalFinalContextDataset() {}

    public static List<RankingEvaluationDataset.Scenario> generate() {
        List<RankingEvaluationDataset.Scenario> source =
                RankingEvaluationDataset.generate().stream()
                        .filter(s -> s.group()
                                == RankingEvaluationDataset.Group.ANSWER_CURRENT_HISTORICAL)
                        .filter(s -> s.purpose()
                                == RankingEvaluationDataset.SearchPurpose.ANSWER_HISTORICAL)
                        .toList();

        if (source.size() != 50) {
            throw new IllegalStateException(
                    "Expected 50 historical source scenarios, got " + source.size());
        }

        List<RankingEvaluationDataset.Scenario> result =
                new ArrayList<>(source.size());

        for (RankingEvaluationDataset.Scenario base : source) {
            List<RankingEvaluationDataset.MemoryFixture> memories =
                    base.memories().stream()
                            .map(HistoricalFinalContextDataset::toHistoricalFixture)
                            .toList();

            long eligibleHistorical = memories.stream()
                    .filter(m -> m.eligibility()
                            == RankingEvaluationDataset.Eligibility.ELIGIBLE)
                    .filter(m -> m.state() == MemoryState.RESOLVED)
                    .filter(m -> m.temporalRole()
                            == RankingEvaluationDataset.TemporalRole.HISTORICAL)
                    .count();

            if (eligibleHistorical < 8) {
                throw new IllegalStateException(
                        "Insufficient eligible historical fixtures for " + base.id()
                                + ": " + eligibleHistorical);
            }

            result.add(new RankingEvaluationDataset.Scenario(
                    "historical-final-context-" + base.id(),
                    DATASET_VERSION,
                    GENERATOR_VERSION,
                    base.group(),
                    RankingEvaluationDataset.SearchPurpose.ANSWER_HISTORICAL,
                    base.category(),
                    base.query(),
                    memories,
                    base.recallTargetIds(),
                    base.safetyExcludedIds(),
                    base.requiredMemoryIds(),
                    base.relatedMemoryIds()));
        }

        return List.copyOf(result);
    }

    private static RankingEvaluationDataset.MemoryFixture toHistoricalFixture(
            RankingEvaluationDataset.MemoryFixture fixture) {

        if (fixture.eligibility()
                != RankingEvaluationDataset.Eligibility.ELIGIBLE) {
            // Preserve safety fixtures and their original exclusion labels.
            return fixture;
        }

        return new RankingEvaluationDataset.MemoryFixture(
                fixture.id(),
                fixture.content(),
                fixture.category(),
                fixture.captureType(),
                MemoryState.RESOLVED,
                fixture.sensitivityLevel(),
                Math.max(2L, fixture.version()),
                fixture.createdAt(),
                fixture.updatedAt(),
                fixture.confirmedAt(),
                fixture.deleted(),
                fixture.sensitiveUseAllowed(),
                RankingEvaluationDataset.TemporalRole.HISTORICAL,
                fixture.eligibility(),
                fixture.eligibilityReason(),
                fixture.relevance(),
                RankingEvaluationDataset.SafetyCondition.NONE,
                fixture.expectedSignals());
    }
}
