package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.domain.MemoryId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostingSearchPipelineTest {

    private static final String DIGEST = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final DynamoDbPostingReader reader = mock(DynamoDbPostingReader.class);
    private final PostingSearchPipeline pipeline =
            new PostingSearchPipeline(reader, new PostingCandidateAccumulator());

    @Test
    void combinesReadAndAggregationIntoCandidates() {
        when(reader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.NGRAM_1),
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL),
                        posting(2, null, 1, PostingCandidateAccumulator.TermKind.NGRAM_3)), false));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(2, result.candidates().size());
        assertEquals(3, result.postingEvaluations());
        assertEquals(4, result.candidates().get(0).termWeightSum());
        verify(reader).read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any());
    }

    @Test
    void preservesPartialReadStatusEvenWhenReturnedPageAggregatesCleanly() {
        when(reader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.PARTIAL, List.of(
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL)), true));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertTrue(result.hasMore());
        assertEquals(1, result.candidates().size());
    }

    @Test
    void historicalPurposePreservesRevisionIdentity() {
        when(reader.read(eq(MemoryRetrievalPurpose.ANSWER_HISTORICAL), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(
                        posting(1, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL),
                        posting(1, 2L, 0, PostingCandidateAccumulator.TermKind.LEXICAL)), false));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(2, result.candidates().size());
        assertFalse(result.candidates().get(0).key().historical());
        assertEquals(2L, result.candidates().get(1).key().revisionNumber());
    }

    @Test
    void propagatesUnavailableWithoutCandidates() {
        when(reader.read(eq(MemoryRetrievalPurpose.RELATION_DECISION), any())).thenReturn(
                new DynamoDbPostingReader.ReadResult(SearchCompleteness.UNAVAILABLE, List.of(), 0, false));

        var result = pipeline.search(MemoryRetrievalPurpose.RELATION_DECISION,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void aggregationCapCannotBeReportedAsComplete() {
        List<PostingCandidateAccumulator.PostingObservation> postings = new ArrayList<>();
        for (int i = 1; i <= PostingCandidateAccumulator.ANSWER_UNIQUE_CANDIDATE_LIMIT + 1; i++) {
            postings.add(posting(i, null, i % 32, PostingCandidateAccumulator.TermKind.LEXICAL));
        }
        when(reader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, postings, false));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertEquals(PostingCandidateAccumulator.ANSWER_UNIQUE_CANDIDATE_LIMIT, result.candidates().size());
        assertTrue(result.candidateLimitReached());
    }

    @Test
    void emptyQueryReturnsReaderResultWithoutInventingCandidates() {
        when(reader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(), false));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, List.of());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        assertEquals(0, result.postingEvaluations());
    }

    private static DynamoDbPostingReader.ReadResult readResult(
            SearchCompleteness completeness,
            List<PostingCandidateAccumulator.PostingObservation> postings,
            boolean hasMore) {
        return new DynamoDbPostingReader.ReadResult(completeness, postings, postings.size(), hasMore);
    }

    private static PostingCandidateAccumulator.PostingObservation posting(
            int memory, Long revision, int queryOrdinal,
            PostingCandidateAccumulator.TermKind kind) {
        return new PostingCandidateAccumulator.PostingObservation(
                new MemoryId("00000000-0000-4000-8000-" + String.format("%012d", memory)),
                revision, queryOrdinal, kind);
    }
}
