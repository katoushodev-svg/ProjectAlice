package com.projectalice.backend.memory.evaluation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RankingFailureDiagnosticTest {
    private static final long SEED = 20261022L;
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    @Test
    void printsAnswerMissesForContentOverlapBaseline() {
        var dataset = RankingEvaluationDataset.generate();
        int totalTargets = 0;
        int foundTargets = 0;
        int missedScenarios = 0;
        Map<String, int[]> categoryStats = new LinkedHashMap<>();

        for (var scenario : dataset) {
            if (scenario.group() != RankingEvaluationDataset.Group.ANSWER_CURRENT_HISTORICAL) {
                continue;
            }

            List<String> ids = new ArrayList<>(
                    scenario.memories().stream()
                            .map(RankingEvaluationDataset.MemoryFixture::id).toList());
            java.util.Collections.shuffle(ids, new Random(SEED ^ scenario.id().hashCode()));

            Map<String, Integer> inputOrder = new LinkedHashMap<>();
            for (int i = 0; i < ids.size(); i++) inputOrder.put(ids.get(i), i);

            var byId = scenario.memories().stream().collect(
                    java.util.stream.Collectors.toMap(
                            RankingEvaluationDataset.MemoryFixture::id, f -> f));

            List<RankingEvaluationDataset.MemoryFixture> eligible = ids.stream()
                    .map(byId::get)
                    .filter(f -> f.eligibility() == RankingEvaluationDataset.Eligibility.ELIGIBLE)
                    .toList();

            String query = normalize(scenario.query());
            List<String> selected = eligible.stream()
                    .sorted(Comparator
                            .comparingInt((RankingEvaluationDataset.MemoryFixture f) ->
                                    overlap(query, normalize(f.content()))).reversed()
                            .thenComparingInt(f -> inputOrder.get(f.id())))
                    .limit(40)
                    .map(RankingEvaluationDataset.MemoryFixture::id)
                    .toList();

            for (String target : scenario.requiredMemoryIds()) {
                totalTargets++;
                if (selected.contains(target)) {
                    foundTargets++;
                } else {
                    missedScenarios++;
                    var fixture = byId.get(target);
                    int score = overlap(query, normalize(fixture.content()));
                    categoryStats.computeIfAbsent(scenario.category(), ignored -> new int[2])[1]++;
                    System.out.printf(
                            "MISS scenario=%s category=%s purpose=%s target=%s overlap=%d%n"
                                    + "  query=%s%n  targetContent=%s%n",
                            scenario.id(), scenario.category(), scenario.purpose(),
                            target, score, scenario.query(), fixture.content());
                }
            }
            categoryStats.computeIfAbsent(scenario.category(), ignored -> new int[2])[0]++;
        }

        System.out.printf(
                "%nANSWER_SUMMARY recall=%.4f (%d/%d) missedTargets=%d%n",
                totalTargets == 0 ? 0.0 : (double) foundTargets / totalTargets,
                foundTargets, totalTargets, missedScenarios);
        categoryStats.forEach((category, counts) ->
                System.out.printf("CATEGORY=%s scenarios=%d missedTargets=%d%n",
                        category, counts[0], counts[1]));
    }

    private static int overlap(String query, String content) {
        int count = 0;
        for (String token : SPLIT.split(query)) {
            if (token.length() > 1 && content.contains(token)) count++;
        }
        return count;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
