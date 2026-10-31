package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RankingLabelDensityDiagnosticTest {

    @Test
    void reportsRelevanceLabelDensityAcrossAllScenarios() {
        List<RankingEvaluationDataset.Scenario> scenarios =
                RankingEvaluationDataset.generate();

        assertEquals(300, scenarios.size(),
                "The diagnostic must cover all 300 evaluation scenarios.");

        System.out.println("===== RANKING LABEL DENSITY DIAGNOSTIC =====");
        printSummary("ALL", scenarios);

        for (RankingEvaluationDataset.Group group
                : RankingEvaluationDataset.Group.values()) {
            List<RankingEvaluationDataset.Scenario> groupScenarios =
                    scenarios.stream()
                            .filter(s -> s.group() == group)
                            .toList();

            printSummary(group.name(), groupScenarios);
        }

        System.out.println("===== END LABEL DENSITY DIAGNOSTIC =====");
    }

    private static void printSummary(
            String name,
            List<RankingEvaluationDataset.Scenario> scenarios) {

        long eligible = 0;
        long required = 0;
        long related = 0;
        long irrelevant = 0;

        long minRelevantPerScenario = Long.MAX_VALUE;
        long maxRelevantPerScenario = Long.MIN_VALUE;
        long totalRelevantPerScenario = 0;

        for (RankingEvaluationDataset.Scenario scenario : scenarios) {
            List<RankingEvaluationDataset.MemoryFixture> eligibleMemories =
                    scenario.memories().stream()
                            .filter(f -> f.eligibility()
                                    == RankingEvaluationDataset.Eligibility.ELIGIBLE)
                            .toList();

            long scenarioRequired = eligibleMemories.stream()
                    .filter(f -> f.relevance()
                            == RankingEvaluationDataset.Relevance.REQUIRED)
                    .count();

            long scenarioRelated = eligibleMemories.stream()
                    .filter(f -> f.relevance()
                            == RankingEvaluationDataset.Relevance.RELATED)
                    .count();

            long scenarioIrrelevant = eligibleMemories.stream()
                    .filter(f -> f.relevance()
                            == RankingEvaluationDataset.Relevance.IRRELEVANT)
                    .count();

            long scenarioRelevant = scenarioRequired + scenarioRelated;

            eligible += eligibleMemories.size();
            required += scenarioRequired;
            related += scenarioRelated;
            irrelevant += scenarioIrrelevant;

            totalRelevantPerScenario += scenarioRelevant;
            minRelevantPerScenario =
                    Math.min(minRelevantPerScenario, scenarioRelevant);
            maxRelevantPerScenario =
                    Math.max(maxRelevantPerScenario, scenarioRelevant);
        }

        long relevant = required + related;
        double labelDensity = eligible == 0
                ? 0.0
                : (double) relevant / eligible;

        double averageRelevantPerScenario = scenarios.isEmpty()
                ? 0.0
                : (double) totalRelevantPerScenario / scenarios.size();

        System.out.printf(
                "LABEL_DENSITY group=%s scenarios=%d eligible=%d "
                        + "required=%d related=%d irrelevant=%d "
                        + "relevant=%d labelDensity=%.4f "
                        + "avgRelevantPerScenario=%.2f "
                        + "minRelevantPerScenario=%d "
                        + "maxRelevantPerScenario=%d%n",
                name,
                scenarios.size(),
                eligible,
                required,
                related,
                irrelevant,
                relevant,
                labelDensity,
                averageRelevantPerScenario,
                scenarios.isEmpty() ? 0 : minRelevantPerScenario,
                scenarios.isEmpty() ? 0 : maxRelevantPerScenario);
    }
}
