package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Hydrates Posting candidates from the authoritative PersonalMemory root and validates
 * the search projection evidence before returning candidates for ranking.
 *
 * <p>This component deliberately does not implement DynamoDB access for projection,
 * reset-generation, or historical-revision records. Those reads are supplied through
 * {@link CandidateEvidenceReader}; missing evidence fails closed as UNVERIFIABLE.</p>
 */
public final class AuthoritativeCandidateValidator {

    public enum CandidateStatus {
        ACCEPTED,
        REJECTED,
        UNVERIFIABLE
    }

    public enum ReasonCode {
        VERIFIED,
        MEMORY_NOT_FOUND,
        PURPOSE_MISMATCH,
        STALE_PROJECTION,
        RESET_GENERATION_MISMATCH,
        HISTORICAL_REVISION_NOT_FOUND,
        HISTORICAL_INTENT_NOT_READY,
        PROJECTION_EVIDENCE_MISSING,
        RESET_GENERATION_UNAVAILABLE,
        HISTORICAL_EVIDENCE_MISSING,
        POLICY_REJECTED
    }

    public record CurrentProjectionEvidence(
            long memoryVersion,
            String semanticVersion,
            long resetGeneration) {
        public CurrentProjectionEvidence {
            if (memoryVersion < 1) {
                throw new IllegalArgumentException("memoryVersion must be >= 1");
            }
            if (semanticVersion == null || semanticVersion.isBlank()) {
                throw new IllegalArgumentException("semanticVersion must not be blank");
            }
            if (resetGeneration < 0) {
                throw new IllegalArgumentException("resetGeneration must be >= 0");
            }
        }
    }

    public record HistoricalRevisionEvidence(
            long revisionNumber,
            boolean historicalIntentReady) {
        public HistoricalRevisionEvidence {
            if (revisionNumber < 1) {
                throw new IllegalArgumentException("revisionNumber must be >= 1");
            }
        }
    }

    public interface CandidateEvidenceReader {
        Optional<CurrentProjectionEvidence> findCurrentProjection(MemoryId memoryId);

        OptionalLong findCurrentResetGeneration();

        Optional<HistoricalRevisionEvidence> findHistoricalRevision(
                MemoryId memoryId,
                long revisionNumber);
    }

    /**
     * Purpose-specific policy for state/sensitivity and other eligibility rules.
     * It must not use ranking signals to override an ineligible result.
     */
    @FunctionalInterface
    public interface EligibilityPolicy {
        boolean isEligible(
                MemoryRetrievalPurpose purpose,
                PostingCandidateAccumulator.Candidate candidate,
                PersonalMemory authoritativeMemory,
                Optional<HistoricalRevisionEvidence> historicalEvidence);
    }

    public record ValidatedCandidate(
            PostingCandidateAccumulator.Candidate candidate,
            PersonalMemory authoritativeMemory,
            CandidateStatus status,
            ReasonCode reasonCode) {
        public ValidatedCandidate {
            Objects.requireNonNull(candidate, "candidate must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(reasonCode, "reasonCode must not be null");
            if (status == CandidateStatus.ACCEPTED && authoritativeMemory == null) {
                throw new IllegalArgumentException("accepted candidate requires authoritative memory");
            }
            if (status != CandidateStatus.ACCEPTED && authoritativeMemory != null) {
                throw new IllegalArgumentException("non-accepted candidate must not expose memory");
            }
        }
    }

    public record Result(
            SearchCompleteness completeness,
            List<ValidatedCandidate> candidates,
            int acceptedCount,
            int rejectedCount,
            int unverifiableCount) {
        public Result {
            Objects.requireNonNull(completeness, "completeness must not be null");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
            if (acceptedCount < 0 || rejectedCount < 0 || unverifiableCount < 0
                    || acceptedCount + rejectedCount + unverifiableCount != candidates.size()) {
                throw new IllegalArgumentException("candidate status counts must match result size");
            }
            if (completeness == SearchCompleteness.UNAVAILABLE && !candidates.isEmpty()) {
                throw new IllegalArgumentException("unavailable result must not contain candidates");
            }
        }

        public List<ValidatedCandidate> acceptedCandidates() {
            return candidates.stream()
                    .filter(candidate -> candidate.status() == CandidateStatus.ACCEPTED)
                    .toList();
        }
    }

    private final PersonalMemoryRepository memoryRepository;
    private final CandidateEvidenceReader evidenceReader;
    private final EligibilityPolicy eligibilityPolicy;
    private final String expectedSemanticVersion;

    public AuthoritativeCandidateValidator(
            PersonalMemoryRepository memoryRepository,
            CandidateEvidenceReader evidenceReader,
            EligibilityPolicy eligibilityPolicy,
            String expectedSemanticVersion) {
        this.memoryRepository = Objects.requireNonNull(memoryRepository, "memoryRepository must not be null");
        this.evidenceReader = Objects.requireNonNull(evidenceReader, "evidenceReader must not be null");
        this.eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy must not be null");
        if (expectedSemanticVersion == null || expectedSemanticVersion.isBlank()) {
            throw new IllegalArgumentException("expectedSemanticVersion must not be blank");
        }
        this.expectedSemanticVersion = expectedSemanticVersion;
    }

    public Result validate(
            MemoryRetrievalPurpose purpose,
            List<PostingCandidateAccumulator.Candidate> candidates,
            SearchCompleteness upstreamCompleteness) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        List<PostingCandidateAccumulator.Candidate> input =
                List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        Objects.requireNonNull(upstreamCompleteness, "upstreamCompleteness must not be null");

        if (upstreamCompleteness == SearchCompleteness.UNAVAILABLE) {
            if (!input.isEmpty()) {
                throw new IllegalArgumentException("unavailable upstream result must not contain candidates");
            }
            return new Result(SearchCompleteness.UNAVAILABLE, List.of(), 0, 0, 0);
        }

        List<ValidatedCandidate> output = new ArrayList<>(input.size());
        int accepted = 0;
        int rejected = 0;
        int unverifiable = 0;

        for (PostingCandidateAccumulator.Candidate candidate : input) {
            ValidatedCandidate validated = validateOne(purpose, candidate);
            output.add(validated);
            switch (validated.status()) {
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
                case UNVERIFIABLE -> unverifiable++;
            }
        }

        SearchCompleteness completeness =
                upstreamCompleteness == SearchCompleteness.PARTIAL || unverifiable > 0
                        ? SearchCompleteness.PARTIAL
                        : SearchCompleteness.COMPLETE;
        return new Result(completeness, output, accepted, rejected, unverifiable);
    }

    private ValidatedCandidate validateOne(
            MemoryRetrievalPurpose purpose,
            PostingCandidateAccumulator.Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        PostingCandidateAccumulator.CandidateKey key = candidate.key();

        if (key.historical() && purpose != MemoryRetrievalPurpose.ANSWER_HISTORICAL) {
            return rejected(candidate, ReasonCode.PURPOSE_MISMATCH);
        }

        Optional<PersonalMemory> memoryOptional = memoryRepository.findById(key.memoryId());
        if (memoryOptional.isEmpty()) {
            return rejected(candidate, ReasonCode.MEMORY_NOT_FOUND);
        }
        PersonalMemory memory = memoryOptional.get();

        Optional<HistoricalRevisionEvidence> historicalEvidence = Optional.empty();
        if (key.historical()) {
            historicalEvidence = evidenceReader.findHistoricalRevision(key.memoryId(), key.revisionNumber());
            if (historicalEvidence.isEmpty()) {
                return unverifiable(candidate, ReasonCode.HISTORICAL_EVIDENCE_MISSING);
            }
            HistoricalRevisionEvidence evidence = historicalEvidence.get();
            if (evidence.revisionNumber() != key.revisionNumber()) {
                return rejected(candidate, ReasonCode.HISTORICAL_REVISION_NOT_FOUND);
            }
            if (!evidence.historicalIntentReady()) {
                return rejected(candidate, ReasonCode.HISTORICAL_INTENT_NOT_READY);
            }
        } else {
            Optional<CurrentProjectionEvidence> projectionOptional =
                    evidenceReader.findCurrentProjection(key.memoryId());
            if (projectionOptional.isEmpty()) {
                return unverifiable(candidate, ReasonCode.PROJECTION_EVIDENCE_MISSING);
            }

            OptionalLong currentResetGeneration = evidenceReader.findCurrentResetGeneration();
            if (currentResetGeneration.isEmpty()) {
                return unverifiable(candidate, ReasonCode.RESET_GENERATION_UNAVAILABLE);
            }

            CurrentProjectionEvidence projection = projectionOptional.get();
            if (projection.memoryVersion() != memory.version()
                    || !projection.semanticVersion().equals(expectedSemanticVersion)) {
                return rejected(candidate, ReasonCode.STALE_PROJECTION);
            }
            if (projection.resetGeneration() != currentResetGeneration.getAsLong()) {
                return rejected(candidate, ReasonCode.RESET_GENERATION_MISMATCH);
            }
        }

        if (!eligibilityPolicy.isEligible(purpose, candidate, memory, historicalEvidence)) {
            return rejected(candidate, ReasonCode.POLICY_REJECTED);
        }

        return new ValidatedCandidate(candidate, memory, CandidateStatus.ACCEPTED, ReasonCode.VERIFIED);
    }

    private static ValidatedCandidate rejected(
            PostingCandidateAccumulator.Candidate candidate,
            ReasonCode reasonCode) {
        return new ValidatedCandidate(candidate, null, CandidateStatus.REJECTED, reasonCode);
    }

    private static ValidatedCandidate unverifiable(
            PostingCandidateAccumulator.Candidate candidate,
            ReasonCode reasonCode) {
        return new ValidatedCandidate(candidate, null, CandidateStatus.UNVERIFIABLE, reasonCode);
    }
}
