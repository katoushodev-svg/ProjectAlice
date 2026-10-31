
package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeightedMemoryRelevanceRankingPolicyTest {

    private final WeightedMemoryRelevanceRankingPolicy policy =
            new WeightedMemoryRelevanceRankingPolicy();

    @Test
    void ranksDirectEntityMatchAboveKeywordMatchAndEmptySignals() {
        MemoryRankingCandidate noSignals = candidate(
                "00000000-0000-4000-8000-000000000001",
                Set.of());
        MemoryRankingCandidate keyword = candidate(
                "00000000-0000-4000-8000-000000000002",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE));
        MemoryRankingCandidate entity = candidate(
                "00000000-0000-4000-8000-000000000003",
                Set.of(MemoryRelevanceSignal.DIRECT_SUBJECT_ENTITY_MATCH));

        List<MemoryRankingCandidate> ranked =
                policy.rank(query(), List.of(noSignals, keyword, entity));

        assertEquals(List.of(entity, keyword, noSignals), ranked);
    }

    @Test
    void ranksStrongerRelevanceSignalAboveCandidateWithMoreSearchEvidence() {
        MemoryRankingCandidate strongerSignal = candidate(
                "00000000-0000-4000-8000-000000000001",
                Set.of(MemoryRelevanceSignal.DIRECT_SUBJECT_ENTITY_MATCH),
                MemorySearchEvidence.EMPTY);

        MemoryRankingCandidate weakerSignalWithMoreEvidence = candidate(
                "00000000-0000-4000-8000-000000000002",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE),
                new MemorySearchEvidence(100, 1_000));

        List<MemoryRankingCandidate> ranked =
                policy.rank(
                        query(),
                        List.of(weakerSignalWithMoreEvidence, strongerSignal));

        assertEquals(
                List.of(strongerSignal, weakerSignalWithMoreEvidence),
                ranked);
    }

    @Test
    void ranksHigherSearchEvidenceFirstWhenSignalsAreEqual() {
        MemoryRankingCandidate weakerEvidence = candidate(
                "00000000-0000-4000-8000-000000000001",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE),
                new MemorySearchEvidence(1, 1));

        MemoryRankingCandidate strongerEvidence = candidate(
                "00000000-0000-4000-8000-000000000002",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE),
                new MemorySearchEvidence(3, 10));

        List<MemoryRankingCandidate> ranked =
                policy.rank(
                        query(),
                        List.of(weakerEvidence, strongerEvidence));

        assertEquals(List.of(strongerEvidence, weakerEvidence), ranked);
    }

    @Test
    void producesSameRankingRegardlessOfInputOrder() {
        MemoryRankingCandidate candidateA = candidate(
                "00000000-0000-4000-8000-000000000001",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE),
                new MemorySearchEvidence(2, 4));

        MemoryRankingCandidate candidateB = candidate(
                "00000000-0000-4000-8000-000000000002",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE),
                new MemorySearchEvidence(2, 4));

        MemoryRankingCandidate candidateC = candidate(
                "00000000-0000-4000-8000-000000000003",
                Set.of(MemoryRelevanceSignal.DIRECT_SUBJECT_ENTITY_MATCH),
                MemorySearchEvidence.EMPTY);

        List<MemoryRankingCandidate> firstRanking =
                policy.rank(
                        query(),
                        List.of(candidateA, candidateB, candidateC));

        List<MemoryRankingCandidate> secondRanking =
                policy.rank(
                        query(),
                        List.of(candidateC, candidateB, candidateA));

        assertEquals(firstRanking, secondRanking);
    }

    @Test
    void resolvesEqualScoresDeterministicallyByMemoryId() {
        MemoryRankingCandidate laterId = candidate(
                "00000000-0000-4000-8000-000000000002",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE));

        MemoryRankingCandidate earlierId = candidate(
                "00000000-0000-4000-8000-000000000001",
                Set.of(MemoryRelevanceSignal.KEYWORD_RELEVANCE));

        List<MemoryRankingCandidate> ranked =
                policy.rank(query(), List.of(laterId, earlierId));

        assertEquals(List.of(earlierId, laterId), ranked);
    }

    @Test
    void retainsCandidatesEvenWhenSignalsAreEmptyOrNegative() {
        MemoryRankingCandidate penalty = candidate(
                "00000000-0000-4000-8000-000000000001",
                Set.of(MemoryRelevanceSignal.REDUNDANCY_PENALTY));

        MemoryRankingCandidate empty = candidate(
                "00000000-0000-4000-8000-000000000002",
                Set.of());

        List<MemoryRankingCandidate> input = List.of(empty, penalty);

        List<MemoryRankingCandidate> ranked =
                policy.rank(query(), input);

        assertEquals(2, ranked.size());
        assertTrue(ranked.containsAll(input));
        assertEquals(List.of(empty, penalty), ranked);
    }

    private static MemoryRankingCandidate candidate(
            String id,
            Set<MemoryRelevanceSignal> signals) {
        return candidate(id, signals, MemorySearchEvidence.EMPTY);
    }

    private static MemoryRankingCandidate candidate(
            String id,
            Set<MemoryRelevanceSignal> signals,
            MemorySearchEvidence evidence) {
        MemoryContextItem item = new MemoryContextItem(
                new MemoryId(id),
                1,
                "Test memory " + id,
                MemoryCategory.ENGINEERING,
                MemoryState.ACTIVE,
                SensitivityLevel.NORMAL,
                MemoryTemporalRole.CURRENT,
                Set.of(MemoryRelevanceReasonCode.SEARCH_CANDIDATE));

        return new MemoryRankingCandidate(item, signals, evidence);
    }

    private static MemoryRetrievalQuery query() {
        return new MemoryRetrievalQuery(
                MemoryRetrievalPurpose.ANSWER_CURRENT,
                "test query",
                null,
                null,
                null,
                null,
                MemoryTemporalIntent.UNSPECIFIED,
                MemoryContextBudget.baseline(),
                "ranking-policy-test");
    }
}
