package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.*;

import com.projectalice.backend.memory.domain.*;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchCandidateFinalValidatorTest {

    private static final MemoryId ID =
            new MemoryId("123e4567-e89b-42d3-a456-426614174000");
    private SearchCandidateFinalValidator validator;
    private PersonalMemory memory;
    private DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot projection;
    private DynamoDbSearchEvidenceReader.ResetPointSnapshot resetPoint;

    @BeforeEach
    void setUp() {
        validator = new SearchCandidateFinalValidator();
        memory = PersonalMemory.reconstitute(
                ID,
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
                ID, 7, "KW_V1", 1, "READY", false);
        resetPoint = new DynamoDbSearchEvidenceReader.ResetPointSnapshot(3, 142, false);
    }

    @Test
    void acceptsCandidateWhenAllEvidenceAndPoliciesPass() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> true, m -> true);

        assertTrue(result.accepted());
        assertEquals(SearchCandidateFinalValidator.Decision.ACCEPT, result.decision());
        assertSame(memory, result.acceptedMemory().orElseThrow());
    }

    @Test
    void doesNotCompareRootVersionToProjectionSemanticVersion() {
        // Root version 12 and semantic version 7 are separate counters.
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> true, m -> true);

        assertTrue(result.accepted());
    }

    @Test
    void missingMemoryIsExcludedAndNeverReturned() {
        var result = validate(Optional.empty(), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> true, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.EXCLUDE_MEMORY_MISSING,
                result.decision());
        assertTrue(result.acceptedMemory().isEmpty());
    }

    @Test
    void missingResetPointFailsClosed() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.empty(), 7, m -> true, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.UNAVAILABLE_RESET_POINT_MISSING,
                result.decision());
        assertTrue(result.acceptedMemory().isEmpty());
    }

    @Test
    void pendingResetFailsClosed() {
        var pending = new DynamoDbSearchEvidenceReader.ResetPointSnapshot(3, 142, true);
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(pending), 7, m -> true, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.UNAVAILABLE_RESET_IN_PROGRESS,
                result.decision());
    }

    @Test
    void changedResetGenerationExcludesCandidate() {
        var latest = new DynamoDbSearchEvidenceReader.ResetPointSnapshot(4, 143, false);
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(latest), 7, m -> true, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.EXCLUDE_RESET_GENERATION_CHANGED,
                result.decision());
    }

    @Test
    void stalePostingSemanticVersionIsExcluded() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 6, m -> true, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.EXCLUDE_STALE_POSTING,
                result.decision());
    }

    @Test
    void ineligibleMemoryIsExcludedBeforeQueryMatchCanAcceptIt() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> false, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.EXCLUDE_INELIGIBLE,
                result.decision());
        assertTrue(result.acceptedMemory().isEmpty());
    }

    @Test
    void latestContentMustStillMatch() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> true, m -> false);

        assertEquals(SearchCandidateFinalValidator.Decision.EXCLUDE_QUERY_MISMATCH,
                result.decision());
        assertTrue(result.acceptedMemory().isEmpty());
    }

    @Test
    void policyExceptionIsUnavailableRatherThanAccept() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> {
                    throw new IllegalStateException("sensitive diagnostic must not leak");
                }, m -> true);

        assertEquals(SearchCandidateFinalValidator.Decision.UNAVAILABLE_ELIGIBILITY_CHECK_FAILED,
                result.decision());
        assertTrue(result.acceptedMemory().isEmpty());
        assertFalse(result.reason().contains("sensitive diagnostic"));
    }

    @Test
    void queryRecheckExceptionIsUnavailableRatherThanAccept() {
        var result = validate(Optional.of(memory), Optional.of(projection),
                Optional.of(resetPoint), 7, m -> true, m -> {
                    throw new IllegalStateException("content diagnostic must not leak");
                });

        assertEquals(SearchCandidateFinalValidator.Decision.UNAVAILABLE_QUERY_RECHECK_FAILED,
                result.decision());
        assertTrue(result.acceptedMemory().isEmpty());
        assertFalse(result.reason().contains("content diagnostic"));
    }

    private SearchCandidateFinalValidator.ValidationResult validate(
            Optional<PersonalMemory> candidateMemory,
            Optional<DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot> candidateProjection,
            Optional<DynamoDbSearchEvidenceReader.ResetPointSnapshot> candidateReset,
            long postingSemanticVersion,
            java.util.function.Predicate<PersonalMemory> eligibility,
            java.util.function.Predicate<PersonalMemory> queryMatcher) {
        return validator.validateCurrent(new SearchCandidateFinalValidator.CurrentCandidateEvidence(
                candidateMemory, candidateProjection, candidateReset,
                3, postingSemanticVersion, "KW_V1", eligibility, queryMatcher));
    }
}
