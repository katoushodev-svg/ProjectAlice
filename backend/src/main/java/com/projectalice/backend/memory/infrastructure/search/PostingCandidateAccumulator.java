package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.domain.MemoryId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded, deterministic aggregation of already-read inverted-index postings.
 *
 * <p>This component does not perform DynamoDB I/O, hydrate authoritative memory,
 * or choose the final top-40/top-500 candidates. Those responsibilities belong
 * to the repository/hydration and purpose-specific ranking stages.</p>
 */
public final class PostingCandidateAccumulator {

    public static final int ANSWER_POSTING_LIMIT = 1_000;
    public static final int ANSWER_UNIQUE_CANDIDATE_LIMIT = 200;
    public static final int ANSWER_FINAL_POOL_LIMIT = 40;
    public static final int MANAGEMENT_POSTING_LIMIT = 5_000;
    public static final int MANAGEMENT_UNIQUE_CANDIDATE_LIMIT = 500;
    public static final int MANAGEMENT_FINAL_POOL_LIMIT = 500;

    public enum TermKind {
        LEXICAL(4),
        NGRAM_3(3),
        NGRAM_2(2),
        NGRAM_1(1);

        private final int weightCode;

        TermKind(int weightCode) {
            this.weightCode = weightCode;
        }

        public int weightCode() {
            return weightCode;
        }
    }

    /**
     * One posting encountered while reading a query-term posting stream.
     *
     * @param memoryId authoritative memory identifier
     * @param revisionNumber null for Current; positive for Historical
     * @param queryTermOrdinal stable ordinal of the query term (0..31)
     * @param termKind kind recorded by the posting
     */
    public record PostingObservation(
            MemoryId memoryId,
            Long revisionNumber,
            int queryTermOrdinal,
            TermKind termKind,
            Long semanticVersion) {

        /** Compatibility constructor for candidate-aggregation-only callers and fixtures. */
        public PostingObservation(
                MemoryId memoryId,
                Long revisionNumber,
                int queryTermOrdinal,
                TermKind termKind) {
            this(memoryId, revisionNumber, queryTermOrdinal, termKind, null);
        }

        public PostingObservation {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            Objects.requireNonNull(termKind, "termKind must not be null");
            if (revisionNumber != null && revisionNumber < 1) {
                throw new IllegalArgumentException("revisionNumber must be >= 1");
            }
            if (semanticVersion != null && semanticVersion < 1) {
                throw new IllegalArgumentException("semanticVersion must be >= 1");
            }
            if (revisionNumber != null && semanticVersion != null) {
                throw new IllegalArgumentException("historical postings must not carry semanticVersion");
            }
            if (queryTermOrdinal < 0 || queryTermOrdinal >= 32) {
                throw new IllegalArgumentException("queryTermOrdinal must be in [0, 31]");
            }
        }

        public boolean historical() {
            return revisionNumber != null;
        }
    }

    /** Current candidate uses a null revisionNumber; historical candidates do not. */
    public record CandidateKey(MemoryId memoryId, Long revisionNumber, Long semanticVersion) {
        /** Compatibility constructor for older aggregation-only callers and fixtures. */
        public CandidateKey(MemoryId memoryId, Long revisionNumber) {
            this(memoryId, revisionNumber, null);
        }

        public CandidateKey {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            if (revisionNumber != null && revisionNumber < 1) {
                throw new IllegalArgumentException("revisionNumber must be >= 1");
            }
            if (semanticVersion != null && semanticVersion < 1) {
                throw new IllegalArgumentException("semanticVersion must be >= 1");
            }
            if (revisionNumber != null && semanticVersion != null) {
                throw new IllegalArgumentException("historical candidate must not carry semanticVersion");
            }
        }

        public boolean historical() {
            return revisionNumber != null;
        }
    }

    /**
     * Aggregated signals only. No memory content, normalized term, or digest is retained.
     * termWeightSum counts only the strongest TermKind observed for each distinct
     * queryTermOrdinal, so duplicate postings cannot inflate the score.
     */
    public record Candidate(
            CandidateKey key,
            Set<Integer> matchedQueryTermOrdinals,
            Set<TermKind> matchedTermKinds,
            int termWeightSum) {

        public Candidate {
            Objects.requireNonNull(key, "key must not be null");
            matchedQueryTermOrdinals = Collections.unmodifiableSet(
                    new LinkedHashSet<>(matchedQueryTermOrdinals));
            matchedTermKinds = Collections.unmodifiableSet(
                    matchedTermKinds.isEmpty()
                            ? EnumSet.noneOf(TermKind.class)
                            : EnumSet.copyOf(matchedTermKinds));
            if (termWeightSum < 0) {
                throw new IllegalArgumentException("termWeightSum must be >= 0");
            }
        }

        public int distinctTermCoverage() {
            return matchedQueryTermOrdinals.size();
        }
    }

    public record Limits(int maxPostingEvaluations, int maxUniqueCandidates, int finalCandidatePoolLimit) {
        public Limits {
            if (maxPostingEvaluations < 1 || maxUniqueCandidates < 1 || finalCandidatePoolLimit < 1) {
                throw new IllegalArgumentException("all limits must be positive");
            }
            if (finalCandidatePoolLimit > maxUniqueCandidates) {
                throw new IllegalArgumentException("final pool cannot exceed unique candidate limit");
            }
        }

        public static Limits forPurpose(MemoryRetrievalPurpose purpose) {
            Objects.requireNonNull(purpose, "purpose must not be null");
            return switch (purpose) {
                case MANAGEMENT_SEARCH -> new Limits(
                        MANAGEMENT_POSTING_LIMIT,
                        MANAGEMENT_UNIQUE_CANDIDATE_LIMIT,
                        MANAGEMENT_FINAL_POOL_LIMIT);
                case ANSWER_CURRENT, ANSWER_HISTORICAL, RELATION_DECISION -> new Limits(
                        ANSWER_POSTING_LIMIT,
                        ANSWER_UNIQUE_CANDIDATE_LIMIT,
                        ANSWER_FINAL_POOL_LIMIT);
            };
        }
    }

    public record Result(
            SearchCompleteness completeness,
            List<Candidate> candidates,
            int postingEvaluations,
            Limits limits,
            boolean postingLimitReached,
            boolean candidateLimitReached) {

        public Result {
            Objects.requireNonNull(completeness, "completeness must not be null");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
            Objects.requireNonNull(limits, "limits must not be null");
            if (postingEvaluations < 0 || postingEvaluations > limits.maxPostingEvaluations()) {
                throw new IllegalArgumentException("postingEvaluations outside configured limit");
            }
            if (candidates.size() > limits.maxUniqueCandidates()) {
                throw new IllegalArgumentException("candidate count exceeds configured limit");
            }
            if (completeness == SearchCompleteness.COMPLETE
                    && (postingLimitReached || candidateLimitReached)) {
                throw new IllegalArgumentException("a limit-truncated result cannot be COMPLETE");
            }
        }
    }

    private static final class MutableCandidate {
        private final CandidateKey key;
        private final Map<Integer, TermKind> bestKindByQueryTerm = new TreeMap<>();
        private final Set<TermKind> allMatchedKinds = EnumSet.noneOf(TermKind.class);

        private MutableCandidate(CandidateKey key) {
            this.key = key;
        }

        private void add(PostingObservation observation) {
            allMatchedKinds.add(observation.termKind());
            bestKindByQueryTerm.merge(
                    observation.queryTermOrdinal(),
                    observation.termKind(),
                    (left, right) -> left.weightCode() >= right.weightCode() ? left : right);
        }

        private Candidate freeze() {
            int weightSum = bestKindByQueryTerm.values().stream()
                    .mapToInt(TermKind::weightCode)
                    .sum();
            return new Candidate(
                    key,
                    bestKindByQueryTerm.keySet(),
                    allMatchedKinds,
                    weightSum);
        }
    }

    public Result accumulate(MemoryRetrievalPurpose purpose, Iterable<PostingObservation> postings) {
        Objects.requireNonNull(postings, "postings must not be null");
        return accumulate(purpose, postings.iterator());
    }

    public Result accumulate(MemoryRetrievalPurpose purpose, Iterator<PostingObservation> postings) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(postings, "postings must not be null");

        Limits limits = Limits.forPurpose(purpose);
        Map<CandidateKey, MutableCandidate> candidates = new LinkedHashMap<>();
        int evaluated = 0;
        boolean postingLimitReached = false;
        boolean candidateLimitReached = false;

        while (postings.hasNext()) {
            if (evaluated >= limits.maxPostingEvaluations()) {
                postingLimitReached = true;
                break;
            }

            PostingObservation observation = Objects.requireNonNull(
                    postings.next(), "posting observation must not be null");
            evaluated++;

            if (!isAllowedForPurpose(purpose, observation)) {
                // A purpose-incompatible posting is still an evaluated posting.
                continue;
            }

            CandidateKey key = new CandidateKey(
                    observation.memoryId(), observation.revisionNumber(), observation.semanticVersion());
            MutableCandidate existing = candidates.get(key);
            if (existing == null) {
                if (candidates.size() >= limits.maxUniqueCandidates()) {
                    candidateLimitReached = true;
                    break;
                }
                existing = new MutableCandidate(key);
                candidates.put(key, existing);
            }
            existing.add(observation);
        }

        boolean truncated = postingLimitReached || candidateLimitReached;
        List<Candidate> frozen = new ArrayList<>(candidates.size());
        for (MutableCandidate candidate : candidates.values()) {
            frozen.add(candidate.freeze());
        }

        return new Result(
                truncated ? SearchCompleteness.PARTIAL : SearchCompleteness.COMPLETE,
                frozen,
                evaluated,
                limits,
                postingLimitReached,
                candidateLimitReached);
    }

    private static boolean isAllowedForPurpose(
            MemoryRetrievalPurpose purpose,
            PostingObservation observation) {
        return switch (purpose) {
            case MANAGEMENT_SEARCH, ANSWER_CURRENT, RELATION_DECISION -> !observation.historical();
            case ANSWER_HISTORICAL -> true; // Current and Historical are both allowed.
        };
    }
}
