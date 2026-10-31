package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RankingComparisonRunnerTest {
    @Test
    void comparesAllScenariosDeterministicallyWithoutInvalidOutputs() {
        var runner = new RankingComparisonRunner();
        var first = runner.run();
        var second = runner.run();

        assertEquals(RankingEvaluationDataset.DATASET_VERSION, first.datasetVersion());
        assertEquals(300, first.shuffledInputBaseline().scenarios());
        assertEquals(300, first.queryContentOverlap().scenarios());
        assertEquals(first, second);
        assertEquals(0, first.shuffledInputBaseline().invalidRuns());
        assertEquals(0, first.queryContentOverlap().invalidRuns());
        assertEquals(3, first.queryContentOverlap().groupRecall().size());
        assertTrue(first.shuffledInputBaseline().totalTargets() > 0);
        assertEquals(300, first.japaneseCharNgram().scenarios());
        assertEquals(0, first.japaneseCharNgram().invalidRuns());
        assertEquals(3, first.japaneseCharNgram().groupRecall().size());
        assertEquals(300, first.hybrid().scenarios());
        assertEquals(0, first.hybrid().invalidRuns());
        assertEquals(3, first.hybrid().groupRecall().size());
        assertEquals(first, second);
        for (var s : java.util.List.of(first.shuffledInputBaseline(), first.queryContentOverlap(),
                first.japaneseCharNgram(), first.hybrid())) {
            assertEquals(0, s.safetyViolations());
            assertTrue(s.paraphraseTargets() > 0);
            assertEquals(first.shuffledInputBaseline().totalTargets(), s.totalTargets());
        }
    }
}
