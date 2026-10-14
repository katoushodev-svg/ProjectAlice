package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.TokenEstimator;
import com.projectalice.backend.memory.application.port.out.MemoryResetPointRepository;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/** Revalidates an existing selection against the authoritative Memory and Reset Point. */
public final class ValidateMemorySelectionService implements ValidateMemorySelectionUseCase {
    private final MemoryResetPointRepository resetPoints;
    private final PersonalMemoryRepository memories;
    private final TokenEstimator estimator;
    private final MemoryContextPayloadSerializer serializer;
    private final MemoryEligibilityPolicy eligibility = new MemoryEligibilityPolicy();

    public ValidateMemorySelectionService(MemoryResetPointRepository resetPoints, PersonalMemoryRepository memories,
            TokenEstimator estimator, MemoryContextPayloadSerializer serializer) {
        this.resetPoints = Objects.requireNonNull(resetPoints, "resetPoints must not be null");
        this.memories = Objects.requireNonNull(memories, "memories must not be null");
        this.estimator = Objects.requireNonNull(estimator, "estimator must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    @Override
    public FindRelevantMemoriesResult validate(MemorySelectionReference reference, MemoryRetrievalQuery query) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(query, "query must not be null");
        try {
            if (resetPoints.currentGeneration() != reference.resetGeneration()) return empty(reference);
            List<MemoryContextItem> valid = new ArrayList<>();
            boolean partial = false;
            for (MemorySelectionReference.Entry entry : reference.entries()) {
                PersonalMemory memory = memories.findById(entry.memoryId()).orElse(null);
                var candidate = new MemorySearchCandidate(entry.memoryId(), entry.version(),
                        Set.of(MemorySearchSignal.KEYWORD_MATCH));
                if (memory == null || memory.version() != entry.version()
                        // The reference intentionally carries no relevance rationale. Until that
                        // rationale is represented, final validation fails closed for Sensitive data.
                        || memory.sensitivityLevel() == SensitivityLevel.SENSITIVE
                        || eligibility.evaluate(query.purpose(), query, candidate, memory).isEmpty()) {
                    partial = true;
                    continue;
                }
                MemoryTemporalRole role = eligibility.evaluate(query.purpose(), query, candidate, memory).orElseThrow();
                valid.add(new MemoryContextItem(memory.memoryId(), memory.version(), memory.content().value(),
                        memory.category(), memory.state(), memory.sensitivityLevel(), role,
                        EnumSet.of(MemoryRelevanceReasonCode.SEARCH_CANDIDATE)));
            }
            if (resetPoints.currentGeneration() != reference.resetGeneration()) return empty(reference);
            return budgeted(reference, valid, partial, query.contextBudget());
        } catch (RuntimeException unavailable) {
            return empty(reference);
        }
    }

    private FindRelevantMemoriesResult budgeted(MemorySelectionReference reference, List<MemoryContextItem> values,
            boolean partial, MemoryContextBudget budget) {
        List<MemoryContextItem> kept = new ArrayList<>();
        int bytes = 0;
        String payload = "";
        OptionalLong tokenCount = OptionalLong.empty();
        FindRelevantMemoriesResult.EstimationStatus estimation = FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED;
        for (MemoryContextItem item : values) {
            if (kept.size() >= budget.maxFinalItems()) break;
            int itemBytes = item.content().getBytes(StandardCharsets.UTF_8).length;
            if (itemBytes > budget.maxUtf8Bytes() - bytes) { partial = true; continue; }
            List<MemoryContextItem> proposed = new ArrayList<>(kept);
            proposed.add(item);
            String nextPayload = serializer.serialize(proposed);
            TokenEstimationResult result;
            try { result = estimator.estimate(nextPayload); }
            catch (RuntimeException failed) { result = new TokenEstimationResult.Unavailable(); }
            if (result instanceof TokenEstimationResult.Estimated estimated) {
                if (estimated.tokenCount() > budget.maxEstimatedTokens()) { partial = true; continue; }
                estimation = FindRelevantMemoriesResult.EstimationStatus.ESTIMATED;
                tokenCount = OptionalLong.of(estimated.tokenCount());
            } else {
                estimation = FindRelevantMemoriesResult.EstimationStatus.UNAVAILABLE;
                tokenCount = OptionalLong.empty();
            }
            kept.add(item);
            bytes += itemBytes;
            payload = nextPayload;
        }
        return new FindRelevantMemoriesResult(reference.searchCompleteness(),
                partial ? FindRelevantMemoriesResult.HydrationStatus.PARTIAL
                        : FindRelevantMemoriesResult.HydrationStatus.COMPLETE,
                kept, payload, estimation, tokenCount,
                kept.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(MemorySelectionReference.capture(
                                reference.resetGeneration(), reference.searchCompleteness(), kept)));
    }

    private static FindRelevantMemoriesResult empty(MemorySelectionReference reference) {
        return new FindRelevantMemoriesResult(reference.searchCompleteness(),
                FindRelevantMemoriesResult.HydrationStatus.PARTIAL, List.of(), "",
                FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED, OptionalLong.empty(),
                java.util.Optional.empty());
    }
}
