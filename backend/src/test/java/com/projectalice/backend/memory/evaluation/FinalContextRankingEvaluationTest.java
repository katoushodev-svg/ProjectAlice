package com.projectalice.backend.memory.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.AnswerMemorySearchResult;
import com.projectalice.backend.memory.application.FindRelevantMemoriesResult;
import com.projectalice.backend.memory.application.FindRelevantMemoriesService;
import com.projectalice.backend.memory.application.MemoryContextBudget;
import com.projectalice.backend.memory.application.MemoryContextItem;
import com.projectalice.backend.memory.application.MemoryContextPayloadSerializer;
import com.projectalice.backend.memory.application.MemoryRankingCandidate;
import com.projectalice.backend.memory.application.MemoryRelevanceRankingPolicy;
import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.MemorySearchCandidate;
import com.projectalice.backend.memory.application.MemorySearchEvidence;
import com.projectalice.backend.memory.application.MemorySearchSignal;
import com.projectalice.backend.memory.application.WeightedMemoryRelevanceRankingPolicy;
import com.projectalice.backend.memory.application.MemoryTemporalIntent;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.TokenEstimationResult;
import com.projectalice.backend.memory.application.port.TokenEstimator;
import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.infrastructure.JTokkitTokenEstimator;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Integration evaluation of actual Final Context selection.
 *
 * This test does not define or approve a production ranking policy.
 * Synthetic deletion/sensitive-use exclusions are tracked separately because
 * the current PersonalMemory model does not represent those fixture attributes.
 */
class FinalContextRankingEvaluationTest {

    private static final long SHUFFLE_SEED = 20261022L;
    private static final int FINAL_CONTEXT_SIZE = 8;

    private enum CandidatePolicy {
        SHUFFLED_INPUT,
        QUERY_CONTENT_OVERLAP,
        JAPANESE_CHAR_NGRAM,
        KW_V1_SEARCH_EVIDENCE    }

    private enum FinalRankingPolicy {
        SHUFFLED_INPUT,
        QUERY_CONTENT_OVERLAP,
        JAPANESE_CHAR_NGRAM,
        PRODUCTION_WEIGHTED
    }

    private record Totals(
            long precisionHits,
            long requiredHits,
            long relatedHits,
            long precisionSlots,
            long candidateRequiredHits,
            long candidateRequiredTargets,
            long requiredRecallHits,
            long requiredRecallTargets,
            long relatedRecallHits,
            long relatedRecallTargets,
            long fullContexts,
            long underfilledContexts,
            long safetyFixturesExcluded,
            long estimationFailures) {

        double precisionAt8() {
            return precisionSlots == 0 ? 0.0 : (double) precisionHits / precisionSlots;
        }

        double requiredAt8() {
            return precisionSlots == 0 ? 0.0 : (double) requiredHits / precisionSlots;
        }

        double relatedAt8() {
            return precisionSlots == 0 ? 0.0 : (double) relatedHits / precisionSlots;
        }

        double candidateRecallAt40() {
            return candidateRequiredTargets == 0 ? 0.0
                    : (double) candidateRequiredHits / candidateRequiredTargets;
        }

        double requiredRecallAt8() {
            return requiredRecallTargets == 0 ? 0.0
                    : (double) requiredRecallHits / requiredRecallTargets;
        }

        double relatedRecallAt8() {
            return relatedRecallTargets == 0 ? 0.0
                    : (double) relatedRecallHits / relatedRecallTargets;
        }
    }

    @Test
    void evaluatesActualFinalContextForAnswerCurrentAndHistorical() {
        List<RankingEvaluationDataset.Scenario> scenarios =
                RankingEvaluationDataset.generate().stream()
                        .filter(s -> s.group()
                                == RankingEvaluationDataset.Group.ANSWER_CURRENT_HISTORICAL)
                        .toList();

        assertEquals(100, scenarios.size());

        for (MemoryRetrievalPurpose purpose : List.of(
                MemoryRetrievalPurpose.ANSWER_CURRENT,
                MemoryRetrievalPurpose.ANSWER_HISTORICAL)) {

            List<RankingEvaluationDataset.Scenario> purposeScenarios =
                    purpose == MemoryRetrievalPurpose.ANSWER_HISTORICAL
                            ? HistoricalFinalContextDataset.generate()
                            : scenarios.stream()
                                    .filter(s -> s.purpose().name().equals(purpose.name()))
                                    .toList();

            assertEquals(50, purposeScenarios.size(),
                    "Unexpected scenario count for " + purpose);

            for (CandidatePolicy candidatePolicy : CandidatePolicy.values()) {
                for (FinalRankingPolicy finalRankingPolicy : FinalRankingPolicy.values()) {
                    long hits = 0;
                    long requiredHits = 0;
                    long relatedHits = 0;
                    long slots = 0;
                    long candidateRequiredHits = 0;
                    long candidateRequiredTargets = 0;
                    long requiredRecallHits = 0;
                    long requiredRecallTargets = 0;
                    long relatedRecallHits = 0;
                    long relatedRecallTargets = 0;
                    long fullContexts = 0;
                    long underfilledContexts = 0;
                    long safetyExcluded = 0;
                    long estimationFailures = 0;

                    for (RankingEvaluationDataset.Scenario scenario : purposeScenarios) {
                        ScenarioResult result = evaluateScenario(
                                scenario, candidatePolicy, finalRankingPolicy);

                        ScenarioResult repeatedResult = evaluateScenario(
                                scenario, candidatePolicy, finalRankingPolicy);
                        assertEquals(
                                result.result().items().stream()
                                        .map(MemoryContextItem::memoryId)
                                        .toList(),
                                repeatedResult.result().items().stream()
                                        .map(MemoryContextItem::memoryId)
                                        .toList(),
                                "Final Context order changed between repeated evaluations: "
                                        + scenario.id() + "/" + candidatePolicy
                                        + "/" + finalRankingPolicy);
                        safetyExcluded += result.safetyExcluded();

                        candidateRequiredHits += result.candidateRequiredHits();
                        candidateRequiredTargets += scenario.requiredMemoryIds().size();
                        requiredRecallTargets += scenario.requiredMemoryIds().size();
                        relatedRecallTargets += scenario.relatedMemoryIds().size();

                        if (result.result().estimationStatus()
                                != FindRelevantMemoriesResult.EstimationStatus.ESTIMATED) {
                            estimationFailures++;
                            continue;
                        }

                        List<MemoryContextItem> items = result.result().items();
                        assertTrue(items.size() <= FINAL_CONTEXT_SIZE,
                                "Final Context exceeded 8 items: " + scenario.id());

                        long rawContentBytes = items.stream()
                                .mapToLong(i -> i.content()
                                        .getBytes(StandardCharsets.UTF_8).length)
                                .sum();
                        assertTrue(rawContentBytes
                                        <= MemoryContextBudget.DEFAULT_MAX_UTF8_BYTES,
                                "Final Context exceeded UTF-8 byte budget: " + scenario.id());

                        assertTrue(result.result().estimatedTokenCount().isPresent());
                        assertTrue(result.result().estimatedTokenCount().getAsLong()
                                        <= MemoryContextBudget.DEFAULT_MAX_ESTIMATED_TOKENS,
                                "Final Context exceeded token budget: " + scenario.id());

                        Map<MemoryId, RankingEvaluationDataset.MemoryFixture> fixturesByMemoryId =
                                result.fixturesByMemoryId();

                        // Fail explicitly if any selected MemoryId has no matching fixture.
                        // Do not silently omit unmapped items from the evaluation.
                        List<RankingEvaluationDataset.MemoryFixture> topFixtures = items.stream()
                                .limit(FINAL_CONTEXT_SIZE)
                                .map(MemoryContextItem::memoryId)
                                .map(memoryId -> {
                                    RankingEvaluationDataset.MemoryFixture fixture =
                                            fixturesByMemoryId.get(memoryId);

                                    assertTrue(
                                            fixture != null,
                                            "Final Context memory ID has no matching fixture: "
                                                    + memoryId.value()
                                                    + " in scenario "
                                                    + scenario.id());

                                    return fixture;
                                })
                                .filter(fixture -> fixture.eligibility()
                                        == RankingEvaluationDataset.Eligibility.ELIGIBLE)
                                .toList();

                        // Recall is measured even when the context contains fewer than 8 items.
                        long scenarioRequiredRecall = topFixtures.stream()
                                .filter(f -> scenario.requiredMemoryIds().contains(f.id()))
                                .count();
                        long scenarioRelatedRecall = topFixtures.stream()
                                .filter(f -> scenario.relatedMemoryIds().contains(f.id()))
                                .count();

                        requiredRecallHits += scenarioRequiredRecall;
                        relatedRecallHits += scenarioRelatedRecall;

                        if (items.size() < FINAL_CONTEXT_SIZE) {
                            underfilledContexts++;
                            continue;
                        }

                        fullContexts++;

                        long scenarioRequired = topFixtures.stream()
                                .filter(f -> f.relevance()
                                        == RankingEvaluationDataset.Relevance.REQUIRED)
                                .count();
                        long scenarioRelated = topFixtures.stream()
                                .filter(f -> f.relevance()
                                        == RankingEvaluationDataset.Relevance.RELATED)
                                .count();

                        requiredHits += scenarioRequired;
                        relatedHits += scenarioRelated;
                        hits += scenarioRequired + scenarioRelated;
                        slots += FINAL_CONTEXT_SIZE;
                    }

                    Totals totals = new Totals(
                            hits, requiredHits, relatedHits, slots,
                            candidateRequiredHits, candidateRequiredTargets,
                            requiredRecallHits, requiredRecallTargets,
                            relatedRecallHits, relatedRecallTargets,
                            fullContexts, underfilledContexts,
                            safetyExcluded, estimationFailures);

                    String candidateRecall = candidateRequiredTargets == 0
                            ? "UNMEASURED"
                            : "%.4f".formatted(totals.candidateRecallAt40());
                    String requiredRecall = requiredRecallTargets == 0
                            ? "UNMEASURED"
                            : "%.4f".formatted(totals.requiredRecallAt8());
                    String relatedRecall = relatedRecallTargets == 0
                            ? "UNMEASURED"
                            : "%.4f".formatted(totals.relatedRecallAt8());
                    String precision = slots == 0
                            ? "UNMEASURED"
                            : "%.4f".formatted(totals.precisionAt8());
                    String requiredPrecision = slots == 0
                            ? "UNMEASURED"
                            : "%.4f".formatted(totals.requiredAt8());
                    String relatedPrecision = slots == 0
                            ? "UNMEASURED"
                            : "%.4f".formatted(totals.relatedAt8());

                    System.out.printf(
                            "FINAL_CONTEXT purpose=%s candidatePolicy=%s "
                                    + "finalRankingPolicy=%s scenarios=%d "
                                    + "candidateRecallAt40=%s candidateRequiredHits=%d/%d "
                                    + "requiredRecallAt8=%s requiredRecallHits=%d/%d "
                                    + "relatedRecallAt8=%s relatedRecallHits=%d/%d "
                                    + "precisionAt8=%s hits=%d/%d "
                                    + "requiredAt8=%s requiredHits=%d "
                                    + "relatedAt8=%s relatedHits=%d "
                                    + "fullContexts=%d underfilled=%d "
                                    + "safetyFixturePreExcluded=%d estimationFailures=%d%n",
                            purpose,
                            candidatePolicy,
                            finalRankingPolicy,
                            purposeScenarios.size(),
                            candidateRecall,
                            candidateRequiredHits,
                            candidateRequiredTargets,
                            requiredRecall,
                            requiredRecallHits,
                            requiredRecallTargets,
                            relatedRecall,
                            relatedRecallHits,
                            relatedRecallTargets,
                            precision,
                            hits,
                            slots,
                            requiredPrecision,
                            requiredHits,
                            relatedPrecision,
                            relatedHits,
                            fullContexts,
                            underfilledContexts,
                            safetyExcluded,
                            estimationFailures);

                    assertEquals(0, estimationFailures,
                            "Token estimation must be available for this evaluation: "
                                    + purpose + "/" + candidatePolicy + "/" + finalRankingPolicy);
                }
            }
        }
    }

    private record ScenarioResult(
            FindRelevantMemoriesResult result,
            Map<MemoryId, RankingEvaluationDataset.MemoryFixture> fixturesByMemoryId,
            long safetyExcluded,
            long candidateRequiredHits) {}

    private ScenarioResult evaluateScenario(
            RankingEvaluationDataset.Scenario scenario,
            CandidatePolicy candidatePolicy,
            FinalRankingPolicy finalRankingPolicy) {

        List<String> shuffledIds = new ArrayList<>(
                scenario.memories().stream()
                        .map(RankingEvaluationDataset.MemoryFixture::id)
                        .toList());

        Random random = new Random(SHUFFLE_SEED ^ scenario.id().hashCode());
        java.util.Collections.shuffle(shuffledIds, random);

        CandidateSelectionExperiment experiment = new CandidateSelectionExperiment();
        CandidateSelectionExperiment.Result pool = experiment.run(
                scenario,
                "final-context-integration:" + candidatePolicy + ":" + finalRankingPolicy,
                shuffledIds,
                eligible -> selectPool(scenario.query(), eligible, candidatePolicy));

        assertEquals(CandidateSelectionExperiment.Status.VALID, pool.status(),
                "Candidate Pool selection failed: " + scenario.id());

        long candidateRequiredHits = pool.selectedIds().stream()
                .filter(scenario.requiredMemoryIds()::contains)
                .distinct()
                .count();

        Map<String, RankingEvaluationDataset.MemoryFixture> fixtureById =
                new HashMap<>();
        scenario.memories().forEach(f -> fixtureById.put(f.id(), f));

        Map<String, MemoryId> memoryIdByFixtureId = new LinkedHashMap<>();
        Map<MemoryId, RankingEvaluationDataset.MemoryFixture> fixturesByMemoryId =
                new HashMap<>();
        Map<MemoryId, PersonalMemory> memories = new HashMap<>();
        List<MemorySearchCandidate> candidates = new ArrayList<>();
        long safetyExcluded = 0;

        for (String fixtureId : pool.selectedIds()) {
            RankingEvaluationDataset.MemoryFixture fixture = fixtureById.get(fixtureId);
            if (fixture == null) {
                continue;
            }

            // The domain model currently cannot represent these two fixture properties.
            // Exclude them explicitly here, and do not count this as service safety validation.
            if (scenario.safetyExcludedIds().contains(fixtureId)) {
                safetyExcluded++;
                continue;
            }

            MemoryId memoryId = deterministicMemoryId(scenario.id(), fixtureId);
            memoryIdByFixtureId.put(fixtureId, memoryId);
            fixturesByMemoryId.put(memoryId, fixture);

            PersonalMemory memory = PersonalMemory.reconstitute(
                    memoryId,
                    new MemoryContent(fixture.content()),
                    fixture.category(),
                    fixture.captureType(),
                    fixture.sensitivityLevel(),
                    fixture.state(),
                    fixture.version(),
                    fixture.createdAt(),
                    fixture.updatedAt(),
                    fixture.confirmedAt());

            memories.put(memoryId, memory);
            candidates.add(buildSearchCandidate(scenario.query(), fixture, memoryId));
        }

        PersonalMemoryRepository repository = new PersonalMemoryRepository() {
            @Override
            public void save(PersonalMemory memory) {
                memories.put(memory.memoryId(), memory);
            }

            @Override
            public java.util.Optional<PersonalMemory> findById(MemoryId memoryId) {
                return java.util.Optional.ofNullable(memories.get(memoryId));
            }

            @Override
            public void update(PersonalMemory memory, long expectedVersion) {
                memories.put(memory.memoryId(), memory);
            }
        };

        AnswerMemorySearchPort searchPort = query ->
                new AnswerMemorySearchResult(SearchCompleteness.COMPLETE, candidates);

        MemoryRelevanceRankingPolicy rankingPolicy = rankingPolicy(finalRankingPolicy);

        // Deterministic test estimator, not the production tokenizer.
        TokenEstimator estimator = new JTokkitTokenEstimator();

        FindRelevantMemoriesService service = new FindRelevantMemoriesService(
                searchPort,
                repository,
                rankingPolicy,
                estimator,
                new MemoryContextPayloadSerializer());

        MemoryRetrievalQuery query = new MemoryRetrievalQuery(
                MemoryRetrievalPurpose.valueOf(scenario.purpose().name()),
                scenario.query(),
                null,
                null,
                null,
                Set.of(),
                MemoryTemporalIntent.UNSPECIFIED,
                MemoryContextBudget.baseline(),
                "final-context-eval-" + scenario.id());

        FindRelevantMemoriesResult result = service.execute(query);

        assertEquals(SearchCompleteness.COMPLETE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.COMPLETE,
                result.hydrationStatus());

        return new ScenarioResult(
                result,
                Map.copyOf(fixturesByMemoryId),
                safetyExcluded,
                candidateRequiredHits);
    }

    private static List<String> selectPool(
            String query,
            List<RankingEvaluationDataset.MemoryFixture> eligible,
            CandidatePolicy policy) {

        Comparator<RankingEvaluationDataset.MemoryFixture> comparator = switch (policy) {
            case SHUFFLED_INPUT -> (a, b) -> 0;
            case QUERY_CONTENT_OVERLAP -> Comparator
                    .comparingInt((RankingEvaluationDataset.MemoryFixture f) ->
                            overlapCount(query, f.content()))
                    .reversed();
            case JAPANESE_CHAR_NGRAM -> Comparator
                    .comparingDouble((RankingEvaluationDataset.MemoryFixture f) ->
                            ngramDice(query, f.content()))
                    .reversed();
            case KW_V1_SEARCH_EVIDENCE -> Comparator
                    .comparingInt((RankingEvaluationDataset.MemoryFixture f) ->
                            kwV1EvidenceScore(query, f.content()))
                    .reversed()
                    .thenComparing(RankingEvaluationDataset.MemoryFixture::id);
        };

        return eligible.stream()
                .sorted(comparator)
                .limit(CandidateSelectionExperiment.ANSWER_OUTPUT_LIMIT)
                .map(RankingEvaluationDataset.MemoryFixture::id)
                .toList();
    }

    private static MemoryRelevanceRankingPolicy rankingPolicy(FinalRankingPolicy policy) {
        if (policy == FinalRankingPolicy.PRODUCTION_WEIGHTED) {
            return new WeightedMemoryRelevanceRankingPolicy();
        }
        return (query, candidates) -> {
            if (policy == FinalRankingPolicy.SHUFFLED_INPUT) {
                return List.copyOf(candidates);
            }

            Comparator<MemoryRankingCandidate> comparator = switch (policy) {
                case SHUFFLED_INPUT -> (a, b) -> 0;
                case QUERY_CONTENT_OVERLAP -> Comparator
                        .comparingInt((MemoryRankingCandidate c) ->
                                overlapCount(query.currentUserContent(), c.item().content()))
                        .reversed();
                case JAPANESE_CHAR_NGRAM -> Comparator
                        .comparingDouble((MemoryRankingCandidate c) ->
                                ngramDice(query.currentUserContent(), c.item().content()))
                        .reversed();
                case PRODUCTION_WEIGHTED -> (a, b) -> 0;
            };

            return candidates.stream().sorted(comparator).toList();
        };
    }

    private static MemoryId deterministicMemoryId(String scenarioId, String fixtureId) {
        String input = scenarioId + ":" + fixtureId;
        java.util.UUID hash = java.util.UUID.nameUUIDFromBytes(
                input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Preserve the deterministic hash while conforming to MemoryId's UUID v4 format.
        long most = (hash.getMostSignificantBits() & 0xffffffffffff0fffL)
                | 0x0000000000004000L;
        long least = (hash.getLeastSignificantBits() & 0x3fffffffffffffffL)
                | 0x8000000000000000L;

        return new MemoryId(new java.util.UUID(most, least).toString());
    }

    /**
     * Synthetic KW_V1-like evidence derived from query and content only.
     * This is not a DynamoDB integration test and never uses relevance labels.
     */
    private static MemorySearchCandidate buildSearchCandidate(
            String query,
            RankingEvaluationDataset.MemoryFixture fixture,
            MemoryId memoryId) {
        KwV1Analyzer analyzer = new KwV1Analyzer();
        List<KwV1Analyzer.TermDescriptor> queryTerms =
                analyzer.analyzeQuery(query, List.of()).terms();
        Set<String> documentTerms = analyzer.analyzeDocument(fixture.content()).terms().stream()
                .map(KwV1Analyzer.TermDescriptor::normalizedTerm)
                .collect(java.util.stream.Collectors.toSet());

        int coverage = 0;
        int weightSum = 0;
        for (KwV1Analyzer.TermDescriptor term : queryTerms) {
            if (documentTerms.contains(term.normalizedTerm())) {
                coverage++;
                weightSum += term.weightCode();
            }
        }

        Set<MemorySearchSignal> signals = coverage > 0
                ? Set.of(MemorySearchSignal.KEYWORD_MATCH)
                : Set.of();
        MemorySearchEvidence evidence = coverage == 0
                ? MemorySearchEvidence.EMPTY
                : new MemorySearchEvidence(coverage, weightSum);

        return new MemorySearchCandidate(memoryId, fixture.version(), signals, evidence);
    }

    /**
     * Test-only candidate score derived from KW_V1 query/document analysis.
     * This is a synthetic comparison policy, not a DynamoDB integration test.
     */
    private static int kwV1EvidenceScore(String query, String content) {
        KwV1Analyzer analyzer = new KwV1Analyzer();

        List<KwV1Analyzer.TermDescriptor> queryTerms =
                analyzer.analyzeQuery(query, List.of()).terms();

        Set<String> documentTerms = analyzer.analyzeDocument(content).terms().stream()
                .map(KwV1Analyzer.TermDescriptor::normalizedTerm)
                .collect(java.util.stream.Collectors.toSet());

        int coverage = 0;
        int weightSum = 0;

        for (KwV1Analyzer.TermDescriptor term : queryTerms) {
            if (documentTerms.contains(term.normalizedTerm())) {
                coverage++;
                weightSum += term.weightCode();
            }
        }

        return coverage * 5 + weightSum * 3;
    }

    private static int overlapCount(String query, String content) {
        Set<String> terms = new HashSet<>(List.of(
                query.toLowerCase(java.util.Locale.ROOT)
                        .split("[^\\p{L}\\p{N}]+")));

        int count = 0;
        String normalizedContent = content.toLowerCase(java.util.Locale.ROOT);
        for (String term : terms) {
            if (term.length() > 1 && normalizedContent.contains(term)) {
                count++;
            }
        }
        return count;
    }

    private static double ngramDice(String left, String right) {
        Set<String> a = bigrams(left);
        Set<String> b = bigrams(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        long shared = a.stream().filter(b::contains).count();
        return 2.0 * shared / (a.size() + b.size());
    }

    private static Set<String> bigrams(String value) {
        String normalized = java.text.Normalizer.normalize(
                value, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");

        Set<String> result = new HashSet<>();
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            result.add(normalized.substring(i, i + 2));
        }
        return result;
    }
}