package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.out.RelatedMemorySearchPort;
import com.projectalice.backend.memory.domain.MemoryContent;
import java.util.Objects;
import java.util.Optional;

/**
 * Fails closed on incomplete search. Exact duplicate text is the only automatic
 * relation decision; semantic or conflicting relations require the durable review flow.
 */
public final class MemoryRelationDecisionService implements MemoryRelationDecisionUseCase {
    private final RelatedMemorySearchPort searchPort;

    public MemoryRelationDecisionService(RelatedMemorySearchPort searchPort) {
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort must not be null");
    }

    @Override
    public MemoryRelationDecision evaluate(MemoryContent candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        final RelatedMemorySearchResult result;
        try {
            result = searchPort.findCandidates(candidate);
        } catch (RuntimeException unavailable) {
            return unavailable();
        }
        if (result == null || result.completeness() == SearchCompleteness.UNAVAILABLE)
            return unavailable();
        if (result.completeness() == SearchCompleteness.PARTIAL)
            return new MemoryRelationDecision(MemoryRelationDecision.Status.SEARCH_INCOMPLETE,
                    result.candidates().size(), Optional.empty(), result.candidates());
        if (result.candidates().isEmpty()) {
            return new MemoryRelationDecision(MemoryRelationDecision.Status.NO_MATCH, 0,
                    Optional.of(RelationType.NONE), result.candidates());
        }
        boolean exactDuplicate = result.candidates().stream()
                .anyMatch(existing -> existing.content().value().equals(candidate.value()));
        return new MemoryRelationDecision(exactDuplicate ? MemoryRelationDecision.Status.EXACT_DUPLICATE
                : MemoryRelationDecision.Status.REVIEW_REQUIRED, result.candidates().size(),
                Optional.of(exactDuplicate ? RelationType.EQUIVALENT : RelationType.UNCERTAIN),
                result.candidates());
    }

    private static MemoryRelationDecision unavailable() {
        return new MemoryRelationDecision(MemoryRelationDecision.Status.SEARCH_UNAVAILABLE, 0,
                Optional.empty());
    }
}
