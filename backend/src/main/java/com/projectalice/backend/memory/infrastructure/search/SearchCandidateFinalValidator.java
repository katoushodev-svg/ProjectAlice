package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pure final-validation policy for Current Posting candidates.
 *
 * <p>Posting and Projection semantic versions are compared to each other.
 * They are deliberately not compared to PersonalMemory.version(): confirmation-only
 * updates can advance the authoritative root version without changing search semantics.</p>
 *
 * <p>Eligibility and exact query matching are injected because their policies belong
 * to the request/purpose layer. This class does not invent sensitivity or RESOLVED
 * policy and never treats a missing evidence source as a successful validation.</p>
 */
public final class SearchCandidateFinalValidator {

    public ValidationResult validateCurrent(CurrentCandidateEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");

        if (evidence.memory().isEmpty()) {
            return rejected(Decision.EXCLUDE_MEMORY_MISSING,
                    "Authoritative PersonalMemory is absent.");
        }
        PersonalMemory memory = evidence.memory().orElseThrow();

        if (evidence.resetPoint().isEmpty()) {
            return rejected(Decision.UNAVAILABLE_RESET_POINT_MISSING,
                    "Authoritative Reset Point is unavailable.");
        }
        DynamoDbSearchEvidenceReader.ResetPointSnapshot reset =
                evidence.resetPoint().orElseThrow();

        if (reset.resetInProgress()) {
            return rejected(Decision.UNAVAILABLE_RESET_IN_PROGRESS,
                    "A reset is in progress.");
        }
        if (reset.generation() != evidence.expectedResetGeneration()) {
            return rejected(Decision.EXCLUDE_RESET_GENERATION_CHANGED,
                    "The request Reset Generation no longer matches the authoritative value.");
        }

        if (evidence.projection().isEmpty()) {
            return rejected(Decision.EXCLUDE_PROJECTION_MISSING,
                    "Current Projection Metadata is absent.");
        }
        DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot projection =
                evidence.projection().orElseThrow();

        if (!projection.memoryId().equals(memory.memoryId())) {
            return rejected(Decision.EXCLUDE_PROJECTION_ID_MISMATCH,
                    "Projection metadata belongs to a different Memory ID.");
        }
        if (!projection.readyFor(evidence.expectedAnalyzerVersion())) {
            return rejected(Decision.EXCLUDE_PROJECTION_NOT_READY,
                    "Projection is not READY for the expected analyzer version.");
        }
        if (projection.activeSemanticVersion() != evidence.postingSemanticVersion()) {
            return rejected(Decision.EXCLUDE_STALE_POSTING,
                    "Posting semantic version is not the active projection semantic version.");
        }

        final boolean eligible;
        try {
            eligible = evidence.eligibilityPolicy().test(memory);
        } catch (RuntimeException exception) {
            return rejected(Decision.UNAVAILABLE_ELIGIBILITY_CHECK_FAILED,
                    "Purpose-specific eligibility evaluation failed.");
        }
        if (!eligible) {
            return rejected(Decision.EXCLUDE_INELIGIBLE,
                    "The authoritative Memory is not eligible for this purpose.");
        }

        final boolean matches;
        try {
            matches = evidence.queryMatcher().test(memory);
        } catch (RuntimeException exception) {
            return rejected(Decision.UNAVAILABLE_QUERY_RECHECK_FAILED,
                    "Authoritative content re-evaluation failed.");
        }
        if (!matches) {
            return rejected(Decision.EXCLUDE_QUERY_MISMATCH,
                    "The latest authoritative content no longer matches the request.");
        }

        return new ValidationResult(Decision.ACCEPT, Optional.of(memory),
                "All current-candidate final checks passed.");
    }

    private static ValidationResult rejected(Decision decision, String reason) {
        return new ValidationResult(decision, Optional.empty(), reason);
    }

    public enum Decision {
        ACCEPT,
        EXCLUDE_MEMORY_MISSING,
        UNAVAILABLE_RESET_POINT_MISSING,
        UNAVAILABLE_RESET_IN_PROGRESS,
        EXCLUDE_RESET_GENERATION_CHANGED,
        EXCLUDE_PROJECTION_MISSING,
        EXCLUDE_PROJECTION_ID_MISMATCH,
        EXCLUDE_PROJECTION_NOT_READY,
        EXCLUDE_STALE_POSTING,
        UNAVAILABLE_ELIGIBILITY_CHECK_FAILED,
        EXCLUDE_INELIGIBLE,
        UNAVAILABLE_QUERY_RECHECK_FAILED,
        EXCLUDE_QUERY_MISMATCH
    }

    public record CurrentCandidateEvidence(
            Optional<PersonalMemory> memory,
            Optional<DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot> projection,
            Optional<DynamoDbSearchEvidenceReader.ResetPointSnapshot> resetPoint,
            long expectedResetGeneration,
            long postingSemanticVersion,
            String expectedAnalyzerVersion,
            Predicate<PersonalMemory> eligibilityPolicy,
            Predicate<PersonalMemory> queryMatcher) {

        public CurrentCandidateEvidence {
            memory = Objects.requireNonNull(memory, "memory must not be null");
            projection = Objects.requireNonNull(projection, "projection must not be null");
            resetPoint = Objects.requireNonNull(resetPoint, "resetPoint must not be null");
            if (expectedResetGeneration < 0) {
                throw new IllegalArgumentException("expectedResetGeneration must be non-negative");
            }
            if (postingSemanticVersion < 1) {
                throw new IllegalArgumentException("postingSemanticVersion must be >= 1");
            }
            if (expectedAnalyzerVersion == null || expectedAnalyzerVersion.isBlank()) {
                throw new IllegalArgumentException("expectedAnalyzerVersion must not be blank");
            }
            eligibilityPolicy = Objects.requireNonNull(
                    eligibilityPolicy, "eligibilityPolicy must not be null");
            queryMatcher = Objects.requireNonNull(queryMatcher, "queryMatcher must not be null");
        }
    }

    public record ValidationResult(
            Decision decision,
            Optional<PersonalMemory> acceptedMemory,
            String reason) {

        public ValidationResult {
            Objects.requireNonNull(decision, "decision must not be null");
            acceptedMemory = Objects.requireNonNull(
                    acceptedMemory, "acceptedMemory must not be null");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            if ((decision == Decision.ACCEPT) != acceptedMemory.isPresent()) {
                throw new IllegalArgumentException(
                        "Only ACCEPT results may expose an authoritative Memory.");
            }
        }

        public boolean accepted() {
            return decision == Decision.ACCEPT;
        }
    }
}
