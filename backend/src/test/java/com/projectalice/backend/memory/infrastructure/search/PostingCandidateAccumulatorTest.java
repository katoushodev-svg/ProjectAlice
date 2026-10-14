package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.domain.MemoryId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostingCandidateAccumulatorTest {

    private final PostingCandidateAccumulator accumulator = new PostingCandidateAccumulator();

    private static MemoryId id(int suffix) {
        return new MemoryId("00000000-0000-4000-8000-" + String.format("%012d", suffix));
    }

    private static PostingCandidateAccumulator.PostingObservation posting(
            int memory, Long revision, int queryOrdinal,
            PostingCandidateAccumulator.TermKind kind) {
        return new PostingCandidateAccumulator.PostingObservation(
                id(memory), revision, queryOrdinal, kind);
    }

    @Test
    void mergesDuplicatePostingsAndCountsOnlyDistinctQueryTerms() {
        var result = accumulator.accumulate(
                MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.NGRAM_1),
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL),
                        posting(1, null, 1, PostingCandidateAccumulator.TermKind.NGRAM_3),
                        posting(2, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL)));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(2, result.candidates().size());
        var first = result.candidates().get(0);
        assertEquals(id(1), first.key().memoryId());
        assertEquals(2, first.distinctTermCoverage());
        assertEquals(7, first.termWeightSum()); // max(1,4) + 3; duplicate term not double-counted.
        assertTrue(first.matchedTermKinds().contains(PostingCandidateAccumulator.TermKind.NGRAM_1));
        assertTrue(first.matchedTermKinds().contains(PostingCandidateAccumulator.TermKind.LEXICAL));
    }

    @Test
    void historicalRevisionIsDistinctFromCurrentAndOtherRevisions() {
        var result = accumulator.accumulate(
                MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                List.of(
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL),
                        posting(1, 2L, 0, PostingCandidateAccumulator.TermKind.LEXICAL),
                        posting(1, 3L, 1, PostingCandidateAccumulator.TermKind.NGRAM_2)));

        assertEquals(3, result.candidates().size());
        assertFalse(result.candidates().get(0).key().historical());
        assertEquals(2L, result.candidates().get(1).key().revisionNumber());
        assertEquals(3L, result.candidates().get(2).key().revisionNumber());
    }

    @Test
    void currentOnlyPurposesIgnoreHistoricalPostingsButStillCountThemAsEvaluated() {
        var result = accumulator.accumulate(
                MemoryRetrievalPurpose.MANAGEMENT_SEARCH,
                List.of(
                        posting(1, 9L, 0, PostingCandidateAccumulator.TermKind.LEXICAL),
                        posting(2, null, 1, PostingCandidateAccumulator.TermKind.NGRAM_3)));

        assertEquals(2, result.postingEvaluations());
        assertEquals(1, result.candidates().size());
        assertEquals(id(2), result.candidates().get(0).key().memoryId());
        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
    }

    @Test
    void answerPostingLimitProducesPartialInsteadOfComplete() {
        List<PostingCandidateAccumulator.PostingObservation> postings = new ArrayList<>();
        for (int i = 0; i < PostingCandidateAccumulator.ANSWER_POSTING_LIMIT + 1; i++) {
            postings.add(posting(1, null, i % 32, PostingCandidateAccumulator.TermKind.NGRAM_1));
        }

        var result = accumulator.accumulate(MemoryRetrievalPurpose.ANSWER_CURRENT, postings);

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertEquals(PostingCandidateAccumulator.ANSWER_POSTING_LIMIT, result.postingEvaluations());
        assertTrue(result.postingLimitReached());
        assertFalse(result.candidateLimitReached());
        assertEquals(1, result.candidates().size());
    }

    @Test
    void uniqueCandidateLimitProducesPartialWithoutReturningOverLimitCandidate() {
        List<PostingCandidateAccumulator.PostingObservation> postings = new ArrayList<>();
        for (int i = 1; i <= PostingCandidateAccumulator.ANSWER_UNIQUE_CANDIDATE_LIMIT + 1; i++) {
            postings.add(posting(i, null, i % 32, PostingCandidateAccumulator.TermKind.LEXICAL));
        }

        var result = accumulator.accumulate(MemoryRetrievalPurpose.RELATION_DECISION, postings);

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertEquals(PostingCandidateAccumulator.ANSWER_UNIQUE_CANDIDATE_LIMIT, result.candidates().size());
        assertEquals(PostingCandidateAccumulator.ANSWER_UNIQUE_CANDIDATE_LIMIT + 1, result.postingEvaluations());
        assertTrue(result.candidateLimitReached());
    }

    @Test
    void managementUsesLargerIndependentBounds() {
        var limits = PostingCandidateAccumulator.Limits.forPurpose(MemoryRetrievalPurpose.MANAGEMENT_SEARCH);
        assertEquals(5_000, limits.maxPostingEvaluations());
        assertEquals(500, limits.maxUniqueCandidates());
        assertEquals(500, limits.finalCandidatePoolLimit());
    }

    @Test
    void validatesObservationAndCandidateBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new PostingCandidateAccumulator.PostingObservation(
                        id(1), null, 32, PostingCandidateAccumulator.TermKind.LEXICAL));
        assertThrows(IllegalArgumentException.class,
                () -> new PostingCandidateAccumulator.PostingObservation(
                        id(1), 0L, 0, PostingCandidateAccumulator.TermKind.LEXICAL));
        assertThrows(IllegalArgumentException.class,
                () -> new PostingCandidateAccumulator.Limits(10, 5, 6));
    }
}
