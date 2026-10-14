package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthoritativeCandidateValidatorTest {

    private static final MemoryId ID = new MemoryId("123e4567-e89b-42d3-a456-426614174000");
    private static final String SEMANTIC_VERSION = "KW_V1";

    private PersonalMemoryRepository repository;
    private AuthoritativeCandidateValidator.CandidateEvidenceReader evidence;
    private AuthoritativeCandidateValidator validator;
    private PersonalMemory memory;
    private PostingCandidateAccumulator.Candidate currentCandidate;

    @BeforeEach
    void setUp() {
        repository = mock(PersonalMemoryRepository.class);
        evidence = mock(AuthoritativeCandidateValidator.CandidateEvidenceReader.class);
        memory = PersonalMemory.reconstitute(
                ID,
                new MemoryContent("Sapporo"),
                MemoryCategory.PLACE,
                CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL,
                MemoryState.ACTIVE,
                4,
                OffsetDateTime.parse("2026-01-01T10:00:00+09:00"),
                OffsetDateTime.parse("2026-01-02T10:00:00+09:00"),
                null);
        currentCandidate = candidate(ID, null);
        when(repository.findById(ID)).thenReturn(Optional.of(memory));
        when(evidence.findCurrentProjection(ID)).thenReturn(Optional.of(
                new AuthoritativeCandidateValidator.CurrentProjectionEvidence(4, SEMANTIC_VERSION, 8)));
        when(evidence.findCurrentResetGeneration()).thenReturn(OptionalLong.of(8));
        validator = new AuthoritativeCandidateValidator(
                repository, evidence, (purpose, candidate, authoritative, revision) ->
                        authoritative.state() == MemoryState.ACTIVE
                                && authoritative.sensitivityLevel() == SensitivityLevel.NORMAL,
                SEMANTIC_VERSION);
    }

    @Test
    void acceptsCurrentCandidateOnlyAfterStrongRootAndProjectionEvidenceMatch() {
        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(currentCandidate), SearchCompleteness.COMPLETE);

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.acceptedCount());
        assertSame(memory, result.acceptedCandidates().getFirst().authoritativeMemory());
        verify(repository).findById(ID);
    }

    @Test
    void rejectsCandidateWhenRootNoLongerExists() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(currentCandidate), SearchCompleteness.COMPLETE);

        assertEquals(AuthoritativeCandidateValidator.CandidateStatus.REJECTED,
                result.candidates().getFirst().status());
        assertEquals(AuthoritativeCandidateValidator.ReasonCode.MEMORY_NOT_FOUND,
                result.candidates().getFirst().reasonCode());
        assertTrue(result.acceptedCandidates().isEmpty());
    }

    @Test
    void rejectsStaleProjectionVersion() {
        when(evidence.findCurrentProjection(ID)).thenReturn(Optional.of(
                new AuthoritativeCandidateValidator.CurrentProjectionEvidence(3, SEMANTIC_VERSION, 8)));

        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(currentCandidate), SearchCompleteness.COMPLETE);

        assertEquals(AuthoritativeCandidateValidator.ReasonCode.STALE_PROJECTION,
                result.candidates().getFirst().reasonCode());
        assertTrue(result.acceptedCandidates().isEmpty());
    }

    @Test
    void rejectsResetGenerationMismatch() {
        when(evidence.findCurrentResetGeneration()).thenReturn(OptionalLong.of(9));

        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(currentCandidate), SearchCompleteness.COMPLETE);

        assertEquals(AuthoritativeCandidateValidator.ReasonCode.RESET_GENERATION_MISMATCH,
                result.candidates().getFirst().reasonCode());
    }

    @Test
    void missingProjectionEvidenceMakesResultPartialAndDoesNotExposeMemory() {
        when(evidence.findCurrentProjection(ID)).thenReturn(Optional.empty());

        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(currentCandidate), SearchCompleteness.COMPLETE);

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertEquals(1, result.unverifiableCount());
        assertNull(result.candidates().getFirst().authoritativeMemory());
    }

    @Test
    void rejectsHistoricalCandidateForNonHistoricalPurpose() {
        var historical = candidate(ID, 2L);

        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(historical), SearchCompleteness.COMPLETE);

        assertEquals(AuthoritativeCandidateValidator.ReasonCode.PURPOSE_MISMATCH,
                result.candidates().getFirst().reasonCode());
        verify(repository, never()).findById(any());
    }

    @Test
    void acceptsHistoricalCandidateOnlyWhenRevisionAndIntentAreVerified() {
        var historical = candidate(ID, 2L);
        when(evidence.findHistoricalRevision(ID, 2L)).thenReturn(Optional.of(
                new AuthoritativeCandidateValidator.HistoricalRevisionEvidence(2, true)));

        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_HISTORICAL, List.of(historical), SearchCompleteness.COMPLETE);

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.acceptedCount());
        verify(evidence).findHistoricalRevision(ID, 2L);
    }

    @Test
    void unavailableUpstreamDoesNotAttemptHydration() {
        var result = validator.validate(
                MemoryRetrievalPurpose.ANSWER_CURRENT, List.of(), SearchCompleteness.UNAVAILABLE);

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        verifyNoInteractions(repository, evidence);
    }

    private static PostingCandidateAccumulator.Candidate candidate(MemoryId id, Long revision) {
        return new PostingCandidateAccumulator.Candidate(
                new PostingCandidateAccumulator.CandidateKey(id, revision),
                java.util.Set.of(0),
                java.util.Set.of(PostingCandidateAccumulator.TermKind.LEXICAL),
                4);
    }
}
