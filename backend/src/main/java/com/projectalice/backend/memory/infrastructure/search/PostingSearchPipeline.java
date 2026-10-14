package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.PersonalMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Connects bounded DynamoDB Posting reads to deterministic candidate aggregation.
 *
 * <p>The two-argument constructor retains the original candidate-generation-only mode for
 * existing infrastructure tests and internal callers. That mode does not validate candidates
 * and must not be treated as a user-facing search result. The validation-enabled constructor
 * deliberately makes the no-context {@link #search(MemoryRetrievalPurpose, List)} method fail
 * closed. Current-purpose callers must use the overload that supplies purpose eligibility and
 * exact-query re-evaluation policies.</p>
 *
 * <p>Historical candidates have a separate validation path. It requires an explicit historical
 * intent, a strongly-read Revision afterSnapshot, authoritative Memory existence, and a stable
 * Reset Point generation. Current candidates are not returned by the historical overload.</p>
 */
public final class PostingSearchPipeline {

    public record Result(
            SearchCompleteness completeness,
            List<PostingCandidateAccumulator.Candidate> candidates,
            int postingEvaluations,
            boolean hasMore,
            boolean candidateLimitReached) {
        public Result {
            Objects.requireNonNull(completeness, "completeness must not be null");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
            if (postingEvaluations < 0) {
                throw new IllegalArgumentException("postingEvaluations must be >= 0");
            }
            if (completeness == SearchCompleteness.COMPLETE && (hasMore || candidateLimitReached)) {
                throw new IllegalArgumentException("incomplete conditions cannot be marked COMPLETE");
            }
            if (completeness == SearchCompleteness.UNAVAILABLE && !candidates.isEmpty()) {
                throw new IllegalArgumentException("unavailable result must not contain candidates");
            }
        }
    }

    /** A candidate together with the authoritative root that passed final validation. */
    public record ValidatedCandidate(
            PostingCandidateAccumulator.Candidate signals,
            PersonalMemory memory) {
        public ValidatedCandidate {
            Objects.requireNonNull(signals, "signals must not be null");
            Objects.requireNonNull(memory, "memory must not be null");
            if (!signals.key().memoryId().equals(memory.memoryId())) {
                throw new IllegalArgumentException("candidate and authoritative Memory IDs must match");
            }
        }
    }

    /**
     * Request-specific policy. The exact query matcher must evaluate the authoritative current
     * content; query-term digests cannot be reversed to reconstruct the user's query.
     */
    public record CurrentValidationRequest(
            String expectedAnalyzerVersion,
            Predicate<PersonalMemory> eligibilityPolicy,
            Predicate<PersonalMemory> queryMatcher) {
        public CurrentValidationRequest {
            if (expectedAnalyzerVersion == null || expectedAnalyzerVersion.isBlank()) {
                throw new IllegalArgumentException("expectedAnalyzerVersion must not be blank");
            }
            Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy must not be null");
            Objects.requireNonNull(queryMatcher, "queryMatcher must not be null");
        }
    }

    public record ValidatedResult(
            SearchCompleteness completeness,
            List<ValidatedCandidate> candidates,
            int postingEvaluations,
            boolean hasMore,
            boolean candidateLimitReached) {
        public ValidatedResult {
            Objects.requireNonNull(completeness, "completeness must not be null");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
            if (postingEvaluations < 0) {
                throw new IllegalArgumentException("postingEvaluations must be >= 0");
            }
            if (completeness == SearchCompleteness.COMPLETE && (hasMore || candidateLimitReached)) {
                throw new IllegalArgumentException("incomplete conditions cannot be marked COMPLETE");
            }
            if (completeness == SearchCompleteness.UNAVAILABLE && !candidates.isEmpty()) {
                throw new IllegalArgumentException("unavailable result must not contain candidates");
            }
        }
    }


    /** A historical candidate is paired with the authoritative Revision afterSnapshot. */
    public record ValidatedHistoricalCandidate(
            PostingCandidateAccumulator.Candidate signals,
            DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot revision) {
        public ValidatedHistoricalCandidate {
            Objects.requireNonNull(signals, "signals must not be null");
            Objects.requireNonNull(revision, "revision must not be null");
            if (!signals.key().historical()) {
                throw new IllegalArgumentException("historical candidate must identify a revision");
            }
            if (!signals.key().memoryId().equals(revision.memoryId())
                    || signals.key().revisionNumber() != revision.revisionNumber()) {
                throw new IllegalArgumentException("candidate and authoritative Revision identity must match");
            }
        }
    }

    /**
     * Historical search policy. The caller must explicitly affirm historical intent and provide
     * purpose-specific eligibility plus exact-query re-evaluation against the Revision snapshot.
     */
    public record HistoricalValidationRequest(
            boolean explicitHistoricalIntent,
            Predicate<DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot> eligibilityPolicy,
            Predicate<DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot> queryMatcher) {
        public HistoricalValidationRequest {
            Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy must not be null");
            Objects.requireNonNull(queryMatcher, "queryMatcher must not be null");
        }
    }

    public record ValidatedHistoricalResult(
            SearchCompleteness completeness,
            List<ValidatedHistoricalCandidate> candidates,
            int postingEvaluations,
            boolean hasMore,
            boolean candidateLimitReached) {
        public ValidatedHistoricalResult {
            Objects.requireNonNull(completeness, "completeness must not be null");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
            if (postingEvaluations < 0) {
                throw new IllegalArgumentException("postingEvaluations must be >= 0");
            }
            if (completeness == SearchCompleteness.COMPLETE && (hasMore || candidateLimitReached)) {
                throw new IllegalArgumentException("incomplete conditions cannot be marked COMPLETE");
            }
            if (completeness == SearchCompleteness.UNAVAILABLE && !candidates.isEmpty()) {
                throw new IllegalArgumentException("unavailable result must not contain candidates");
            }
        }
    }

    private final DynamoDbPostingReader postingReader;
    private final PostingCandidateAccumulator accumulator;
    private final PersonalMemoryRepository personalMemoryRepository;
    private final DynamoDbSearchEvidenceReader evidenceReader;
    private final SearchCandidateFinalValidator finalValidator;

    /** Candidate-generation-only compatibility constructor. Not a final validated search path. */
    public PostingSearchPipeline(
            DynamoDbPostingReader postingReader,
            PostingCandidateAccumulator accumulator) {
        this(postingReader, accumulator, null, null, null);
    }

    /** Constructor for the Current-candidate final-validation path. */
    public PostingSearchPipeline(
            DynamoDbPostingReader postingReader,
            PostingCandidateAccumulator accumulator,
            PersonalMemoryRepository personalMemoryRepository,
            DynamoDbSearchEvidenceReader evidenceReader,
            SearchCandidateFinalValidator finalValidator) {
        this.postingReader = Objects.requireNonNull(postingReader, "postingReader must not be null");
        this.accumulator = Objects.requireNonNull(accumulator, "accumulator must not be null");
        boolean allAbsent = personalMemoryRepository == null && evidenceReader == null && finalValidator == null;
        boolean allPresent = personalMemoryRepository != null && evidenceReader != null && finalValidator != null;
        if (!allAbsent && !allPresent) {
            throw new IllegalArgumentException("final-validation dependencies must be supplied together");
        }
        this.personalMemoryRepository = personalMemoryRepository;
        this.evidenceReader = evidenceReader;
        this.finalValidator = finalValidator;
    }

    /**
     * Candidate-generation-only API. When final validation is configured, this method returns
     * UNAVAILABLE instead of allowing callers to bypass the required request-specific policies.
     */
    public Result search(
            MemoryRetrievalPurpose purpose,
            List<DynamoDbPostingReader.QueryTerm> queryTerms) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        List<DynamoDbPostingReader.QueryTerm> terms = List.copyOf(
                Objects.requireNonNull(queryTerms, "queryTerms must not be null"));
        if (finalValidator != null) {
            return new Result(SearchCompleteness.UNAVAILABLE, List.of(), 0, false, false);
        }
        return searchCandidatesOnly(purpose, terms);
    }

    /**
     * Search Current candidates, validate each against strongly-consistent authoritative evidence,
     * and return the accepted authoritative PersonalMemory alongside its aggregated signals.
     * Any evidence read failure makes the entire result UNAVAILABLE (with no accepted candidates).
     */
    public ValidatedResult search(
            MemoryRetrievalPurpose purpose,
            List<DynamoDbPostingReader.QueryTerm> queryTerms,
            CurrentValidationRequest validationRequest) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        List<DynamoDbPostingReader.QueryTerm> terms = List.copyOf(
                Objects.requireNonNull(queryTerms, "queryTerms must not be null"));
        Objects.requireNonNull(validationRequest, "validationRequest must not be null");
        if (terms.size() > 32) {
            throw new IllegalArgumentException("queryTerms must not exceed 32");
        }
        if (finalValidator == null) {
            throw new IllegalStateException("final-validation dependencies were not configured");
        }
        if (!isCurrentPurpose(purpose)) {
            return unavailableValidated(0, false, false);
        }

        final DynamoDbSearchEvidenceReader.ResetPointSnapshot startReset;
        final DynamoDbPostingReader.ReadResult read;
        try {
            Optional<DynamoDbSearchEvidenceReader.ResetPointSnapshot> reset = evidenceReader.findResetPoint();
            if (reset.isEmpty() || reset.get().resetInProgress()) {
                return unavailableValidated(0, false, false);
            }
            startReset = reset.get();
            read = postingReader.read(purpose, terms);
        } catch (RuntimeException exception) {
            return unavailableValidated(0, false, false);
        }

        if (read.completeness() == SearchCompleteness.UNAVAILABLE) {
            return unavailableValidated(read.postingEvaluations(), read.hasMore(), false);
        }

        PostingCandidateAccumulator.Result aggregation;
        try {
            aggregation = accumulator.accumulate(purpose, read.postings());
        } catch (RuntimeException exception) {
            return unavailableValidated(read.postingEvaluations(), read.hasMore(), false);
        }

        boolean hasMore = read.hasMore() || aggregation.postingLimitReached();
        boolean candidateLimitReached = aggregation.candidateLimitReached();
        List<ValidatedCandidate> accepted = new ArrayList<>();

        for (PostingCandidateAccumulator.Candidate candidate : aggregation.candidates()) {
            // Current search candidates must carry the semantic version read from their Posting.
            if (candidate.key().historical() || candidate.key().semanticVersion() == null) {
                return unavailableValidated(read.postingEvaluations(), hasMore, candidateLimitReached);
            }

            final Optional<PersonalMemory> memory;
            final Optional<DynamoDbSearchEvidenceReader.CurrentProjectionSnapshot> projection;
            final Optional<DynamoDbSearchEvidenceReader.ResetPointSnapshot> latestReset;
            try {
                memory = personalMemoryRepository.findById(candidate.key().memoryId());
                projection = evidenceReader.findCurrentProjection(candidate.key().memoryId());
                latestReset = evidenceReader.findResetPoint();
            } catch (RuntimeException exception) {
                return unavailableValidated(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            if (memory == null || projection == null || latestReset == null) {
                return unavailableValidated(read.postingEvaluations(), hasMore, candidateLimitReached);
            }

            SearchCandidateFinalValidator.ValidationResult validation = finalValidator.validateCurrent(
                    new SearchCandidateFinalValidator.CurrentCandidateEvidence(
                            memory,
                            projection,
                            latestReset,
                            startReset.generation(),
                            candidate.key().semanticVersion(),
                            validationRequest.expectedAnalyzerVersion(),
                            validationRequest.eligibilityPolicy(),
                            validationRequest.queryMatcher()));

            if (isUnavailableDecision(validation.decision())
                    || validation.decision() == SearchCandidateFinalValidator.Decision.EXCLUDE_RESET_GENERATION_CHANGED) {
                // A missing/changed reset boundary invalidates the whole request, not just one ID.
                return unavailableValidated(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            if (validation.accepted()) {
                accepted.add(new ValidatedCandidate(candidate, validation.acceptedMemory().orElseThrow()));
            }
        }

        SearchCompleteness completeness = read.completeness() == SearchCompleteness.COMPLETE
                && aggregation.completeness() == SearchCompleteness.COMPLETE
                ? SearchCompleteness.COMPLETE
                : SearchCompleteness.PARTIAL;
        return new ValidatedResult(completeness, accepted, read.postingEvaluations(), hasMore,
                candidateLimitReached);
    }


    /**
     * Search historical revisions only when the caller has established explicit historical
     * intent. Candidate content is taken from the authoritative Revision afterSnapshot, never from
     * the Posting or the current PersonalMemory content. Any evidence read failure fails closed.
     */
    public ValidatedHistoricalResult searchHistorical(
            MemoryRetrievalPurpose purpose,
            List<DynamoDbPostingReader.QueryTerm> queryTerms,
            HistoricalValidationRequest validationRequest) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        List<DynamoDbPostingReader.QueryTerm> terms = List.copyOf(
                Objects.requireNonNull(queryTerms, "queryTerms must not be null"));
        Objects.requireNonNull(validationRequest, "validationRequest must not be null");
        if (terms.size() > 32) {
            throw new IllegalArgumentException("queryTerms must not exceed 32");
        }
        if (finalValidator == null) {
            throw new IllegalStateException("final-validation dependencies were not configured");
        }
        if (purpose != MemoryRetrievalPurpose.ANSWER_HISTORICAL
                || !validationRequest.explicitHistoricalIntent()) {
            return unavailableHistorical(0, false, false);
        }

        final DynamoDbSearchEvidenceReader.ResetPointSnapshot startReset;
        final DynamoDbPostingReader.ReadResult read;
        try {
            Optional<DynamoDbSearchEvidenceReader.ResetPointSnapshot> reset = evidenceReader.findResetPoint();
            if (reset.isEmpty() || reset.get().resetInProgress()) {
                return unavailableHistorical(0, false, false);
            }
            startReset = reset.get();
            read = postingReader.read(purpose, terms);
        } catch (RuntimeException exception) {
            return unavailableHistorical(0, false, false);
        }
        if (read.completeness() == SearchCompleteness.UNAVAILABLE) {
            return unavailableHistorical(read.postingEvaluations(), read.hasMore(), false);
        }

        final PostingCandidateAccumulator.Result aggregation;
        try {
            aggregation = accumulator.accumulate(purpose, read.postings());
        } catch (RuntimeException exception) {
            return unavailableHistorical(read.postingEvaluations(), read.hasMore(), false);
        }

        boolean hasMore = read.hasMore() || aggregation.postingLimitReached();
        boolean candidateLimitReached = aggregation.candidateLimitReached();
        List<ValidatedHistoricalCandidate> accepted = new ArrayList<>();

        for (PostingCandidateAccumulator.Candidate candidate : aggregation.candidates()) {
            // Current Posting candidates are deliberately outside this historical-only result.
            if (!candidate.key().historical()) {
                continue;
            }
            final Optional<PersonalMemory> root;
            final Optional<DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot> revision;
            final Optional<DynamoDbSearchEvidenceReader.ResetPointSnapshot> latestReset;
            try {
                root = personalMemoryRepository.findById(candidate.key().memoryId());
                revision = evidenceReader.findHistoricalRevision(
                        candidate.key().memoryId(), candidate.key().revisionNumber());
                latestReset = evidenceReader.findResetPoint();
            } catch (RuntimeException exception) {
                return unavailableHistorical(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            if (root == null || revision == null || latestReset == null) {
                return unavailableHistorical(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            if (latestReset.isEmpty() || latestReset.get().resetInProgress()
                    || latestReset.get().generation() != startReset.generation()) {
                return unavailableHistorical(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            if (root.isEmpty() || revision.isEmpty()) {
                // Orphan/stale Historical Posting: omit from results; repair is handled separately.
                continue;
            }
            DynamoDbSearchEvidenceReader.HistoricalRevisionSnapshot snapshot = revision.get();
            if (!candidate.key().memoryId().equals(snapshot.memoryId())
                    || !candidate.key().revisionNumber().equals(snapshot.revisionNumber())) {
                return unavailableHistorical(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            final boolean eligible;
            final boolean queryMatches;
            try {
                eligible = validationRequest.eligibilityPolicy().test(snapshot);
                queryMatches = validationRequest.queryMatcher().test(snapshot);
            } catch (RuntimeException exception) {
                return unavailableHistorical(read.postingEvaluations(), hasMore, candidateLimitReached);
            }
            if (eligible && queryMatches) {
                accepted.add(new ValidatedHistoricalCandidate(candidate, snapshot));
            }
        }

        SearchCompleteness completeness = read.completeness() == SearchCompleteness.COMPLETE
                && aggregation.completeness() == SearchCompleteness.COMPLETE
                ? SearchCompleteness.COMPLETE
                : SearchCompleteness.PARTIAL;
        return new ValidatedHistoricalResult(completeness, accepted, read.postingEvaluations(),
                hasMore, candidateLimitReached);
    }

    private static ValidatedHistoricalResult unavailableHistorical(
            int postingEvaluations, boolean hasMore, boolean candidateLimitReached) {
        return new ValidatedHistoricalResult(SearchCompleteness.UNAVAILABLE, List.of(), postingEvaluations,
                hasMore, candidateLimitReached && hasMore);
    }

    private Result searchCandidatesOnly(
            MemoryRetrievalPurpose purpose,
            List<DynamoDbPostingReader.QueryTerm> queryTerms) {
        DynamoDbPostingReader.ReadResult read = postingReader.read(purpose, queryTerms);
        if (read.completeness() == SearchCompleteness.UNAVAILABLE) {
            return new Result(SearchCompleteness.UNAVAILABLE, List.of(),
                    read.postingEvaluations(), read.hasMore(), false);
        }
        PostingCandidateAccumulator.Result aggregation = accumulator.accumulate(purpose, read.postings());
        boolean hasMore = read.hasMore() || aggregation.postingLimitReached();
        boolean candidateLimitReached = aggregation.candidateLimitReached();
        SearchCompleteness completeness = read.completeness() == SearchCompleteness.COMPLETE
                && aggregation.completeness() == SearchCompleteness.COMPLETE
                ? SearchCompleteness.COMPLETE
                : SearchCompleteness.PARTIAL;
        return new Result(completeness, aggregation.candidates(), read.postingEvaluations(), hasMore,
                candidateLimitReached);
    }

    private static boolean isCurrentPurpose(MemoryRetrievalPurpose purpose) {
        return purpose == MemoryRetrievalPurpose.ANSWER_CURRENT
                || purpose == MemoryRetrievalPurpose.RELATION_DECISION
                || purpose == MemoryRetrievalPurpose.MANAGEMENT_SEARCH;
    }

    private static boolean isUnavailableDecision(SearchCandidateFinalValidator.Decision decision) {
        return decision.name().startsWith("UNAVAILABLE_");
    }

    private static ValidatedResult unavailableValidated(
            int postingEvaluations, boolean hasMore, boolean candidateLimitReached) {
        // UNAVAILABLE is never allowed to expose candidates or an incomplete status flag.
        return new ValidatedResult(SearchCompleteness.UNAVAILABLE, List.of(), postingEvaluations,
                hasMore, candidateLimitReached && hasMore);
    }
}
