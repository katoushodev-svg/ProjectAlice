package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.TokenEstimator;
import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.application.port.out.MemoryResetPointRepository;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Candidate generation, authoritative hydration, eligibility filtering, then ranking,
 * deduplication and diversity stages.
 */
public final class FindRelevantMemoriesService implements FindRelevantMemoriesUseCase {

    private final AnswerMemorySearchPort searchPort;
    private final PersonalMemoryRepository personalMemoryRepository;
    private final MemoryEligibilityPolicy eligibilityPolicy = new MemoryEligibilityPolicy();
    private final MemoryRelevanceRankingPolicy rankingPolicy;
    private final MemoryDeduplicationPolicy deduplicationPolicy;
    private final MemoryDiversityPolicy diversityPolicy;
    private final TokenEstimator tokenEstimator;
    private final MemoryContextPayloadSerializer payloadSerializer;
    private final MemoryResetPointRepository resetPointRepository;

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort,
            PersonalMemoryRepository personalMemoryRepository,
            TokenEstimator tokenEstimator,
            MemoryContextPayloadSerializer payloadSerializer) {
        this(searchPort, personalMemoryRepository,
                MemoryRelevanceRankingPolicy.preservingInputOrder(),
                MemoryDeduplicationPolicy.preservingCandidates(),
                MemoryDiversityPolicy.preservingCandidates(), tokenEstimator, payloadSerializer, null);
    }

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort,
            PersonalMemoryRepository personalMemoryRepository,
            MemoryRelevanceRankingPolicy rankingPolicy,
            TokenEstimator tokenEstimator,
            MemoryContextPayloadSerializer payloadSerializer) {
        this(searchPort, personalMemoryRepository, rankingPolicy,
                MemoryDeduplicationPolicy.preservingCandidates(),
                MemoryDiversityPolicy.preservingCandidates(), tokenEstimator, payloadSerializer, null);
    }

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort,
            PersonalMemoryRepository personalMemoryRepository,
            MemoryRelevanceRankingPolicy rankingPolicy,
            MemoryDeduplicationPolicy deduplicationPolicy,
            MemoryDiversityPolicy diversityPolicy,
            TokenEstimator tokenEstimator,
            MemoryContextPayloadSerializer payloadSerializer) {
        this(searchPort, personalMemoryRepository, rankingPolicy, deduplicationPolicy, diversityPolicy,
                tokenEstimator, payloadSerializer, null);
    }

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort,
            PersonalMemoryRepository personalMemoryRepository,
            MemoryRelevanceRankingPolicy rankingPolicy,
            MemoryDeduplicationPolicy deduplicationPolicy,
            MemoryDiversityPolicy diversityPolicy,
            TokenEstimator tokenEstimator,
            MemoryContextPayloadSerializer payloadSerializer,
            MemoryResetPointRepository resetPointRepository) {
        this.rankingPolicy = Objects.requireNonNull(rankingPolicy, "rankingPolicy must not be null");
        this.deduplicationPolicy =
                Objects.requireNonNull(deduplicationPolicy, "deduplicationPolicy must not be null");
        this.diversityPolicy = Objects.requireNonNull(diversityPolicy, "diversityPolicy must not be null");
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort must not be null");
        this.personalMemoryRepository = Objects.requireNonNull(
                personalMemoryRepository, "personalMemoryRepository must not be null");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator must not be null");
        this.payloadSerializer = Objects.requireNonNull(payloadSerializer, "payloadSerializer must not be null");
        this.resetPointRepository = resetPointRepository;
    }

    @Override
    public FindRelevantMemoriesResult execute(MemoryRetrievalQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        MemoryRetrievalPurpose purpose = query.purpose();
        if (purpose != MemoryRetrievalPurpose.ANSWER_CURRENT
                && purpose != MemoryRetrievalPurpose.ANSWER_HISTORICAL) {
            throw new IllegalArgumentException("unsupported purpose: " + purpose);
        }

        long selectedResetGeneration = -1;
        if (resetPointRepository != null) {
            try { selectedResetGeneration = resetPointRepository.currentGeneration(); }
            catch (RuntimeException unavailable) { return FindRelevantMemoriesResult.unavailable(); }
        }

        AnswerMemorySearchResult searchResult;
        try {
            searchResult = searchPort.findCandidates(query);
        } catch (RuntimeException e) {
            return FindRelevantMemoriesResult.unavailable();
        }
        if (searchResult == null || searchResult.completeness() == SearchCompleteness.UNAVAILABLE) {
            return FindRelevantMemoriesResult.unavailable();
        }
        List<MemorySearchCandidate> candidates = searchResult.candidates();

        if (candidates.size() > query.contextBudget().maxCandidateItems()) {
            // The 40-item Application Pool must be selected after authoritative hydration and
            // relevance ranking. Until the accepted ranking parameters are implemented, do not
            // silently turn the adapter's list order into a selected prefix.
            return new FindRelevantMemoriesResult(
                    searchResult.completeness(),
                    FindRelevantMemoriesResult.HydrationStatus.NOT_ATTEMPTED,
                    List.of(),
                    "",
                    FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED,
                    OptionalLong.empty());
        }

        int candidateLimit = candidates.size();
        List<MemoryRankingCandidate> eligible = new ArrayList<>();
        boolean partial = false;
        for (int i = 0; i < candidateLimit; i++) {
            MemorySearchCandidate candidate = candidates.get(i);
            if (candidate == null) {
                continue;
            }
            Optional<PersonalMemory> memory;
            try {
                memory = personalMemoryRepository.findById(candidate.memoryId());
            } catch (RuntimeException e) {
                partial = true;
                continue;
            }
            if (memory == null) {
                partial = true;
                continue;
            }
            memory.flatMap(m -> toRankingCandidate(query, candidate, m)).ifPresent(eligible::add);
        }

        if (candidates.isEmpty()) {
            return new FindRelevantMemoriesResult(
                    searchResult.completeness(),
                    FindRelevantMemoriesResult.HydrationStatus.COMPLETE,
                    List.of(),
                    "",
                    FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED,
                    OptionalLong.empty());
        }

        List<MemoryRankingCandidate> ranked = List.copyOf(rankingPolicy.rank(query, List.copyOf(eligible)));
        List<MemoryRankingCandidate> deduplicated =
                List.copyOf(deduplicationPolicy.deduplicate(query, ranked));
        List<MemoryRankingCandidate> diversified =
                List.copyOf(diversityPolicy.diversify(query, deduplicated));
        Selection selection = applyContextBudget(diversified, query.contextBudget());

        return new FindRelevantMemoriesResult(
                searchResult.completeness(),
                partial ? FindRelevantMemoriesResult.HydrationStatus.PARTIAL
                        : FindRelevantMemoriesResult.HydrationStatus.COMPLETE,
                selection.items(), selection.payload(), selection.estimationStatus(), selection.estimatedTokenCount(),
                resetPointRepository == null || selection.items().isEmpty()
                        ? Optional.empty()
                        : Optional.of(MemorySelectionReference.capture(
                                selectedResetGeneration, searchResult.completeness(), selection.items())));
    }

    private Selection applyContextBudget(
            List<MemoryRankingCandidate> diversified, MemoryContextBudget budget) {
        List<MemoryContextItem> selected = new ArrayList<>();
        long usedUtf8Bytes = 0;
        String selectedPayload = "";
        long selectedTokenCount = 0;
        for (MemoryRankingCandidate candidate : diversified) {
            if (selected.size() >= budget.maxFinalItems()) {
                break;
            }

            MemoryContextItem item = candidate.item();
            long itemUtf8Bytes = item.content().getBytes(StandardCharsets.UTF_8).length;
            if (itemUtf8Bytes > budget.maxUtf8Bytes() - usedUtf8Bytes) {
                continue;
            }

            List<MemoryContextItem> proposed = new ArrayList<>(selected);
            proposed.add(item);
            String proposedPayload = payloadSerializer.serialize(proposed);
            TokenEstimationResult estimate;
            try {
                estimate = tokenEstimator.estimate(proposedPayload);
            } catch (RuntimeException exception) {
                return fallbackSelection(diversified, budget);
            }
            if (estimate == null || estimate instanceof TokenEstimationResult.Unavailable) {
                return fallbackSelection(diversified, budget);
            }
            long proposedTokenCount = ((TokenEstimationResult.Estimated) estimate).tokenCount();
            if (proposedTokenCount > budget.maxEstimatedTokens()) {
                continue;
            }

            selected.add(item);
            usedUtf8Bytes += itemUtf8Bytes;
            selectedPayload = proposedPayload;
            selectedTokenCount = proposedTokenCount;
        }
        return new Selection(
                List.copyOf(selected),
                selectedPayload,
                FindRelevantMemoriesResult.EstimationStatus.ESTIMATED,
                OptionalLong.of(selectedTokenCount));
    }

    private Selection fallbackSelection(List<MemoryRankingCandidate> diversified, MemoryContextBudget budget) {
        List<MemoryContextItem> selected = new ArrayList<>();
        long usedUtf8Bytes = 0;
        for (MemoryRankingCandidate candidate : diversified) {
            if (selected.size() >= budget.maxFinalItems()) {
                break;
            }
            MemoryContextItem item = candidate.item();
            long itemUtf8Bytes = item.content().getBytes(StandardCharsets.UTF_8).length;
            if (itemUtf8Bytes > budget.maxUtf8Bytes() - usedUtf8Bytes) {
                continue;
            }
            selected.add(item);
            usedUtf8Bytes += itemUtf8Bytes;
        }
        List<MemoryContextItem> result = List.copyOf(selected);
        return new Selection(
                result,
                payloadSerializer.serialize(result),
                FindRelevantMemoriesResult.EstimationStatus.UNAVAILABLE,
                OptionalLong.empty());
    }

    private record Selection(
            List<MemoryContextItem> items,
            String payload,
            FindRelevantMemoriesResult.EstimationStatus estimationStatus,
            OptionalLong estimatedTokenCount) {}

    private Optional<MemoryRankingCandidate> toRankingCandidate(
            MemoryRetrievalQuery query, MemorySearchCandidate candidate, PersonalMemory memory) {
        Optional<MemoryTemporalRole> role = eligibilityPolicy.evaluate(
                query.purpose(), query, candidate, memory);
        if (role.isEmpty()) {
            return Optional.empty();
        }

        Set<MemoryRelevanceReasonCode> reasons = EnumSet.of(MemoryRelevanceReasonCode.SEARCH_CANDIDATE);
        if (candidate.version() != memory.version()) {
            reasons.add(MemoryRelevanceReasonCode.SEARCH_VERSION_STALE);
        }

        MemoryContextItem item = new MemoryContextItem(
                memory.memoryId(),
                memory.version(),
                memory.content().value(),
                memory.category(),
                memory.state(),
                memory.sensitivityLevel(),
                role.orElseThrow(),
                reasons);
        return Optional.of(new MemoryRankingCandidate(item, signalsOf(candidate)));
    }

    private static Set<MemoryRelevanceSignal> signalsOf(MemorySearchCandidate candidate) {
        Set<MemoryRelevanceSignal> signals = EnumSet.noneOf(MemoryRelevanceSignal.class);
        if (candidate.signals().contains(MemorySearchSignal.ENTITY_MATCH)) {
            signals.add(MemoryRelevanceSignal.DIRECT_SUBJECT_ENTITY_MATCH);
        }
        if (candidate.signals().contains(MemorySearchSignal.SEMANTIC_MATCH)) {
            signals.add(MemoryRelevanceSignal.SEMANTIC_RELEVANCE);
        }
        return signals;
    }
}
