package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Candidate generation, authoritative hydration, eligibility filtering, then a pluggable ranking stage.
 * Deduplication and budget enforcement are deferred.
 */
public final class FindRelevantMemoriesService implements FindRelevantMemoriesUseCase {

    private final AnswerMemorySearchPort searchPort;
    private final PersonalMemoryRepository personalMemoryRepository;
    private final MemoryEligibilityPolicy eligibilityPolicy = new MemoryEligibilityPolicy();
    private final MemoryRelevanceRankingPolicy rankingPolicy;

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort, PersonalMemoryRepository personalMemoryRepository) {
        this(searchPort, personalMemoryRepository, MemoryRelevanceRankingPolicy.preservingInputOrder());
    }

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort,
            PersonalMemoryRepository personalMemoryRepository,
            MemoryRelevanceRankingPolicy rankingPolicy) {
        this.rankingPolicy = Objects.requireNonNull(rankingPolicy, "rankingPolicy must not be null");
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort must not be null");
        this.personalMemoryRepository = Objects.requireNonNull(
                personalMemoryRepository, "personalMemoryRepository must not be null");
    }

    @Override
    public FindRelevantMemoriesResult execute(MemoryRetrievalQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        MemoryRetrievalPurpose purpose = query.purpose();
        if (purpose != MemoryRetrievalPurpose.ANSWER_CURRENT
                && purpose != MemoryRetrievalPurpose.ANSWER_HISTORICAL) {
            throw new IllegalArgumentException("unsupported purpose: " + purpose);
        }

        List<MemorySearchCandidate> candidates;
        try {
            candidates = searchPort.findCandidates(query);
        } catch (RuntimeException e) {
            return FindRelevantMemoriesResult.unavailable();
        }
        if (candidates == null) {
            return FindRelevantMemoriesResult.unavailable();
        }

        List<MemoryRankingCandidate> eligible = new ArrayList<>();
        boolean partial = false;
        for (MemorySearchCandidate candidate : candidates) {
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

        List<MemoryContextItem> items = rankingPolicy.rank(query, List.copyOf(eligible)).stream()
                .map(MemoryRankingCandidate::item)
                .toList();

        return new FindRelevantMemoriesResult(
                partial ? FindRelevantMemoriesResult.Status.PARTIAL : FindRelevantMemoriesResult.Status.COMPLETE,
                items);
    }

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
