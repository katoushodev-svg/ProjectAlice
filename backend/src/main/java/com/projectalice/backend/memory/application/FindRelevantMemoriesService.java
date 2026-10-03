package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Candidate generation followed by authoritative hydration. Ranking, deduplication,
 * eligibility beyond state/purpose and budget enforcement are deferred.
 */
public final class FindRelevantMemoriesService implements FindRelevantMemoriesUseCase {

    private final AnswerMemorySearchPort searchPort;
    private final PersonalMemoryRepository personalMemoryRepository;

    public FindRelevantMemoriesService(
            AnswerMemorySearchPort searchPort, PersonalMemoryRepository personalMemoryRepository) {
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

        List<MemoryContextItem> items = new ArrayList<>();
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
            memory.flatMap(m -> toContextItem(purpose, candidate, m)).ifPresent(items::add);
        }

        return new FindRelevantMemoriesResult(
                partial ? FindRelevantMemoriesResult.Status.PARTIAL : FindRelevantMemoriesResult.Status.COMPLETE,
                items);
    }

    private static Optional<MemoryContextItem> toContextItem(
            MemoryRetrievalPurpose purpose, MemorySearchCandidate candidate, PersonalMemory memory) {

        MemoryTemporalRole role;
        if (purpose == MemoryRetrievalPurpose.ANSWER_CURRENT && memory.state() == MemoryState.ACTIVE) {
            role = MemoryTemporalRole.CURRENT;
        } else {
            // Historical retrieval needs revision data and is deferred to a later step.
            return Optional.empty();
        }

        Set<MemoryRelevanceReasonCode> reasons = EnumSet.of(MemoryRelevanceReasonCode.SEARCH_CANDIDATE);
        if (candidate.version() != memory.version()) {
            reasons.add(MemoryRelevanceReasonCode.SEARCH_VERSION_STALE);
        }

        return Optional.of(new MemoryContextItem(
                memory.memoryId(),
                memory.version(),
                memory.content().value(),
                memory.category(),
                memory.state(),
                memory.sensitivityLevel(),
                role,
                reasons));
    }
}
