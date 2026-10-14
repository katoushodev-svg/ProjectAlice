package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PostingSearchPipelineHistoricalValidationTest {
    private static final String DIGEST = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final MemoryId MEMORY_ID = new MemoryId("123e4567-e89b-42d3-a456-426614174000");

    private DynamoDbPostingReader postingReader;
    private PersonalMemoryRepository memoryRepository;
    private DynamoDbSearchEvidenceReader evidenceReader;
    private PostingSearchPipeline pipeline;
    private DynamoDbSearchEvidenceReader.ResetPointSnapshot resetPoint;
    private DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot revision;

    @BeforeEach
    void setUp() {
        postingReader = mock(DynamoDbPostingReader.class);
        memoryRepository = mock(PersonalMemoryRepository.class);
        evidenceReader = mock(DynamoDbSearchEvidenceReader.class);
        pipeline = new PostingSearchPipeline(postingReader, new PostingCandidateAccumulator(),
                memoryRepository, evidenceReader, new SearchCandidateFinalValidator());
        resetPoint = new DynamoDbSearchEvidenceReader.ResetPointSnapshot(3, 142, false);
        revision = new DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot(
                MEMORY_ID, 4, "UPDATE", "以前はJavaを使っていた。", "ENGINEERING", "EXPLICIT",
                "NORMAL", "ACTIVE", "2026-08-30T10:00:00+09:00", null);
        when(evidenceReader.findResetPoint()).thenReturn(Optional.of(resetPoint));
        when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(currentMemory()));
        when(evidenceReader.findHistoricalRevision(MEMORY_ID, 4)).thenReturn(Optional.of(revision));
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_HISTORICAL), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(historicalPosting(4)), false));
    }

    @Test
    void acceptsOnlyWhenExplicitIntentAndAuthoritativeRevisionSnapshotMatches() {
        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.candidates().size());
        assertEquals(4L, result.candidates().getFirst().signals().key().revisionNumber());
        assertEquals("以前はJavaを使っていた。", result.candidates().getFirst().revision().content());
        verify(evidenceReader).findHistoricalRevision(MEMORY_ID, 4);
    }

    @Test
    void rejectsSearchWithoutExplicitHistoricalIntentBeforeReadingPostings() {
        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(false));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        verifyNoInteractions(postingReader);
    }

    @Test
    void rejectsHistoricalRequestForNonHistoricalPurpose() {
        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_CURRENT,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        verifyNoInteractions(postingReader);
    }

    @Test
    void excludesCandidateWhenRevisionIsMissing() {
        when(evidenceReader.findHistoricalRevision(MEMORY_ID, 4)).thenReturn(Optional.empty());

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void excludesOrphanRevisionWhenAuthoritativeMemoryIsMissing() {
        when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.empty());

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void reEvaluatesExactQueryAgainstHistoricalSnapshotNotCurrentMemory() {
        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), new PostingSearchPipeline.HistoricalValidationRequest(true,
                        ignored -> true, snapshot -> snapshot.content().contains("Python")));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void appliesPurposeSpecificEligibilityToHistoricalSnapshot() {
        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), new PostingSearchPipeline.HistoricalValidationRequest(true,
                        snapshot -> "SENSITIVE".equals(snapshot.sensitivityLevel()), ignored -> true));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void resetGenerationChangeMakesHistoricalSearchUnavailable() {
        when(evidenceReader.findResetPoint()).thenReturn(Optional.of(resetPoint),
                Optional.of(new DynamoDbSearchEvidenceReader.ResetPointSnapshot(4, 143, false)));

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void missingOrPendingResetPointFailsClosedBeforePostingRead() {
        when(evidenceReader.findResetPoint()).thenReturn(Optional.of(
                new DynamoDbSearchEvidenceReader.ResetPointSnapshot(3, 142, true)));

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        verifyNoInteractions(postingReader);
    }

    @Test
    void evidenceFailureFailsClosedWithoutReturningPreviouslyAcceptedCandidate() {
        when(evidenceReader.findHistoricalRevision(MEMORY_ID, 4))
                .thenThrow(new IllegalStateException("database detail"));

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void partialReadRemainsPartial() {
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_HISTORICAL), any())).thenReturn(
                readResult(SearchCompleteness.PARTIAL, List.of(historicalPosting(4)), true));

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertTrue(result.hasMore());
        assertEquals(1, result.candidates().size());
    }

    @Test
    void currentPostingCandidatesAreNotReturnedAsHistoricalResults() {
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_HISTORICAL), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(
                        new PostingCandidateAccumulator.PostingObservation(MEMORY_ID, null, 0,
                                PostingCandidateAccumulator.TermKind.LEXICAL, 7L)), false));

        var result = pipeline.searchHistorical(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                queryTerms(), request(true));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        verifyNoInteractions(memoryRepository);
    }

    private static PostingSearchPipeline.HistoricalValidationRequest request(boolean explicit) {
        return new PostingSearchPipeline.HistoricalValidationRequest(explicit,
                ignored -> true, snapshot -> snapshot.content().contains("Java"));
    }

    private static List<DynamoDbPostingReader.QueryTerm> queryTerms() {
        return List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0));
    }

    private static PostingCandidateAccumulator.PostingObservation historicalPosting(long revision) {
        return new PostingCandidateAccumulator.PostingObservation(MEMORY_ID, revision, 0,
                PostingCandidateAccumulator.TermKind.LEXICAL, null);
    }

    private static DynamoDbPostingReader.ReadResult readResult(
            SearchCompleteness completeness,
            List<PostingCandidateAccumulator.PostingObservation> postings,
            boolean hasMore) {
        return new DynamoDbPostingReader.ReadResult(completeness, postings, postings.size(), hasMore);
    }

    private static PersonalMemory currentMemory() {
        return PersonalMemory.reconstitute(MEMORY_ID, new MemoryContent("現在はTypeScriptを使っている。"),
                MemoryCategory.ENGINEERING, CaptureType.EXPLICIT, SensitivityLevel.NORMAL,
                MemoryState.ACTIVE, 12,
                OffsetDateTime.parse("2026-08-30T10:00:00+09:00"),
                OffsetDateTime.parse("2026-10-12T10:00:00+09:00"), null);
    }
}
