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

class PostingSearchPipelineFinalValidationTest {

    private static final String DIGEST = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final MemoryId MEMORY_ID = new MemoryId("123e4567-e89b-42d3-a456-426614174000");

    private DynamoDbPostingReader postingReader;
    private PostingCandidateAccumulator accumulator;
    private PersonalMemoryRepository memoryRepository;
    private DynamoDbSearchEvidenceReader evidenceReader;
    private SearchCandidateFinalValidator validator;
    private PostingSearchPipeline pipeline;
    private PersonalMemory memory;
    private DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot projection;
    private DynamoDbSearchEvidenceReader.ResetPointSnapshot resetPoint;

    @BeforeEach
    void setUp() {
        postingReader = mock(DynamoDbPostingReader.class);
        accumulator = new PostingCandidateAccumulator();
        memoryRepository = mock(PersonalMemoryRepository.class);
        evidenceReader = mock(DynamoDbSearchEvidenceReader.class);
        validator = new SearchCandidateFinalValidator();
        pipeline = new PostingSearchPipeline(
                postingReader, accumulator, memoryRepository, evidenceReader, validator);
        memory = PersonalMemory.reconstitute(
                MEMORY_ID,
                new MemoryContent("Javaを仕事で使っている。"),
                MemoryCategory.ENGINEERING,
                CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL,
                MemoryState.ACTIVE,
                12,
                OffsetDateTime.parse("2026-08-30T10:00:00+09:00"),
                OffsetDateTime.parse("2026-10-12T10:00:00+09:00"),
                null);
        projection = new DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot(
                MEMORY_ID, 7, "KW_V1", 1, "READY", false);
        resetPoint = new DynamoDbSearchEvidenceReader.ResetPointSnapshot(3, 142, false);

        when(evidenceReader.findResetPoint()).thenReturn(Optional.of(resetPoint));
        when(evidenceReader.findCurrentProjection(MEMORY_ID)).thenReturn(Optional.of(projection));
        when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.of(memory));
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(posting(7)), false));
    }

    @Test
    void returnsAuthoritativeMemoryWhenCurrentCandidatePassesAllChecks() {
        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.candidates().size());
        assertSame(memory, result.candidates().get(0).memory());
        assertEquals(7L, result.candidates().get(0).signals().key().semanticVersion());
        verify(memoryRepository).findById(MEMORY_ID);
        verify(evidenceReader, times(2)).findResetPoint();
    }

    @Test
    void postingsFromDifferentSemanticVersionsAreNotMergedIntoOneCandidate() {
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(posting(6), posting(7)), false));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.candidates().size());
        assertEquals(7L, result.candidates().getFirst().signals().key().semanticVersion());
    }

    @Test
    void stalePostingIsExcludedAgainstActiveProjectionSemanticVersion() {
        when(evidenceReader.findCurrentProjection(MEMORY_ID)).thenReturn(Optional.of(
                new DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot(
                        MEMORY_ID, 8, "KW_V1", 1, "READY", false)));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void resetGenerationChangeMakesWholeSearchUnavailable() {
        when(evidenceReader.findResetPoint()).thenReturn(
                Optional.of(resetPoint),
                Optional.of(new DynamoDbSearchEvidenceReader.ResetPointSnapshot(4, 143, false)));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void missingProjectionExcludesCandidateWithoutExposingMemory() {
        when(evidenceReader.findCurrentProjection(MEMORY_ID)).thenReturn(Optional.empty());

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void missingAuthoritativeMemoryExcludesCandidate() {
        when(memoryRepository.findById(MEMORY_ID)).thenReturn(Optional.empty());

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void latestContentMustMatchTheQuery() {
        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(),
                new PostingSearchPipeline.CurrentValidationRequest("KW_V1", ignored -> true, ignored -> false));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void repositoryFailureFailsClosedAndDropsPreviouslyAcceptedCandidates() {
        when(memoryRepository.findById(MEMORY_ID)).thenThrow(new IllegalStateException("db detail"));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void resetPointMissingAtRequestStartFailsClosedBeforePostingRead() {
        when(evidenceReader.findResetPoint()).thenReturn(Optional.empty());

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        verifyNoInteractions(postingReader);
    }

    @Test
    void evidenceReadFailureFailsClosed() {
        when(evidenceReader.findCurrentProjection(MEMORY_ID)).thenThrow(new IllegalStateException("db detail"));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void partialPostingReadRemainsPartialAfterValidation() {
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.PARTIAL, List.of(posting(7)), true));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertTrue(result.hasMore());
        assertEquals(1, result.candidates().size());
    }

    @Test
    void validatedPipelineCannotBeBypassedThroughLegacySearchOverload() {
        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        verifyNoInteractions(postingReader);
    }

    @Test
    void historicalPurposeIsUnavailableUntilRevisionSnapshotValidationExists() {
        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_HISTORICAL, queryTerms(), request());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        verifyNoInteractions(postingReader);
    }

    @Test
    void projectionNotReadyExcludesCandidate() {
        when(evidenceReader.findCurrentProjection(MEMORY_ID)).thenReturn(Optional.of(
                new DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot(
                        MEMORY_ID, 7, "KW_V1", 1, "BUILDING", false)));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void missingPostingSemanticVersionFailsClosed() {
        when(postingReader.read(eq(MemoryRetrievalPurpose.ANSWER_CURRENT), any())).thenReturn(
                readResult(SearchCompleteness.COMPLETE, List.of(postingWithoutSemanticVersion()), false));

        var result = pipeline.search(MemoryRetrievalPurpose.ANSWER_CURRENT, queryTerms(), request());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    private static PostingSearchPipeline.CurrentValidationRequest request() {
        return new PostingSearchPipeline.CurrentValidationRequest("KW_V1", ignored -> true,
                candidate -> candidate.content().value().contains("Java"));
    }

    private static List<DynamoDbPostingReader.QueryTerm> queryTerms() {
        return List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0));
    }

    private static PostingCandidateAccumulator.PostingObservation posting(long semanticVersion) {
        return new PostingCandidateAccumulator.PostingObservation(
                MEMORY_ID, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL, semanticVersion);
    }

    private static PostingCandidateAccumulator.PostingObservation postingWithoutSemanticVersion() {
        return new PostingCandidateAccumulator.PostingObservation(
                MEMORY_ID, null, 0, PostingCandidateAccumulator.TermKind.LEXICAL);
    }

    private static DynamoDbPostingReader.ReadResult readResult(
            SearchCompleteness completeness,
            List<PostingCandidateAccumulator.PostingObservation> postings,
            boolean hasMore) {
        return new DynamoDbPostingReader.ReadResult(completeness, postings, postings.size(), hasMore);
    }
}
